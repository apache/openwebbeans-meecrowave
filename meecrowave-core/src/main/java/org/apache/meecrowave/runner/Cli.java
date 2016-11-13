/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.meecrowave.runner;

import org.apache.catalina.connector.Connector;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.runner.cli.CliOption;
import org.apache.xbean.recipe.ObjectRecipe;

import javax.enterprise.inject.Vetoed;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Vetoed
public class Cli {
    private Cli() {
        // no-op
    }

    public static void main(final String[] args) {
        final Options options = new Options();
        options.addOption("help", false, "Show help");
        options.addOption("context", true, "The context to use to deploy the webapp");
        options.addOption("webapp", true, "Location of the webapp, if not set the classpath will be deployed");
        final List<Field> fields = Stream.of(Meecrowave.Builder.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(CliOption.class))
                .collect(toList());
        fields.forEach(f -> {
            final CliOption opt = f.getAnnotation(CliOption.class);
            options.addOption(null, opt.name(), f.getType() != boolean.class, opt.description());
        });

        final CommandLineParser parser = new PosixParser();
        final CommandLine line;
        try {
            line = parser.parse(options, args, true);
        } catch (final ParseException exp) {
            help(options);
            return;
        }

        if (line.hasOption("help")) {
            help(options);
            return;
        }

        try (final Meecrowave meecrowave = new Meecrowave(buildConfig(line, fields))) {
            final String ctx = line.getOptionValue("context", "");
            final String fixedCtx = !ctx.isEmpty() && !ctx.startsWith("/") ? '/' + ctx : ctx;
            final String war = line.getOptionValue("webapp");
            if (war == null) {
                meecrowave.bake(fixedCtx);
            } else {
                meecrowave.deployWebapp(fixedCtx, new File(war));
            }
            meecrowave.getTomcat().getServer().await();
        }
    }

    private static Meecrowave.Builder buildConfig(final CommandLine line, final List<Field> fields) {
        final Meecrowave.Builder config = new Meecrowave.Builder();
        fields.forEach(f -> {
            final CliOption opt = f.getAnnotation(CliOption.class);
            final String name = opt.name();
            if (line.hasOption(name)) {
                ofNullable(f.getType() == boolean.class ?
                        ofNullable(line.getOptionValue(name)).map(Boolean::parseBoolean).orElse(true) :
                        toValue(name, line.getOptionValues(name), f.getType()))
                        .ifPresent(v -> {
                            if (!f.isAccessible()) {
                                f.setAccessible(true);
                            }
                            try {
                                f.set(config, v);
                            } catch (final IllegalAccessException e) {
                                throw new IllegalStateException(e);
                            }
                        });
            }
        });
        return config;
    }

    private static Object toValue(final String name, final String[] optionValues, final Class<?> type) {
        if (optionValues == null || optionValues.length == 0) {
            return null;
        }
        if (String.class == type) {
            return optionValues[0];
        }
        if (int.class == type) {
            return Integer.parseInt(optionValues[0]);
        }
        if (File.class == type) {
            return new File(optionValues[0]);
        }
        if (Properties.class == type) {
            final Properties props = new Properties();
            Stream.of(optionValues).map(v -> v.split("=")).forEach(v -> props.setProperty(v[0], v[1]));
            return props;
        }
        if (Map.class == type) {
            final Map<String, String> props = new HashMap<>();
            Stream.of(optionValues).map(v -> v.split("=")).forEach(v -> props.put(v[0], v[1]));
            return props;
        }

        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        switch (name) {
            case "realm": // org.foo.Impl:attr1=val1;attr2=val2
                try {
                    int end = optionValues[0].indexOf(':');
                    if (end < 0) {
                        return loader.loadClass(optionValues[0]).newInstance();
                    }
                    final ObjectRecipe recipe = new ObjectRecipe(optionValues[0].substring(0, end));
                    Stream.of(optionValues[0].substring(end + 1, optionValues[0].length()).split(";"))
                            .map(v -> v.split("="))
                            .forEach(v -> recipe.setProperty(v[0], v[1]));
                    return recipe.create(loader);
                } catch (final Exception cnfe) {
                    throw new IllegalArgumentException(optionValues[0]);
                }
            case "security-constraint": // attr1=val1;attr2=val2
                return Stream.of(optionValues)
                        .map(item -> {
                            try {
                                final ObjectRecipe recipe = new ObjectRecipe(Meecrowave.SecurityConstaintBuilder.class);
                                Stream.of(item.split(";"))
                                        .map(v -> v.split("="))
                                        .forEach(v -> recipe.setProperty(v[0], v[1]));
                                return recipe.create(loader);
                            } catch (final Exception cnfe) {
                                throw new IllegalArgumentException(optionValues[0]);
                            }
                        }).collect(toList());
            case "login-config":
                try {
                    final ObjectRecipe recipe = new ObjectRecipe(Meecrowave.LoginConfigBuilder.class);
                    Stream.of(optionValues[0].split(";"))
                            .map(v -> v.split("="))
                            .forEach(v -> recipe.setProperty(v[0], v[1]));
                    return recipe.create(loader);
                } catch (final Exception cnfe) {
                    throw new IllegalArgumentException(optionValues[0]);
                }
            case "connector": // org.foo.Impl:attr1=val1;attr2=val2
                return Stream.of(optionValues)
                        .map(v -> {
                            try {
                                int end = v.indexOf(':');
                                if (end < 0) {
                                    return new Connector(v);
                                }
                                final Connector connector = new Connector(optionValues[0].substring(0, end));
                                Stream.of(v.substring(end + 1, v.length()).split(";"))
                                        .map(i -> i.split("="))
                                        .forEach(i -> connector.setProperty(i[0], i[1]));
                                return connector;
                            } catch (final Exception cnfe) {
                                throw new IllegalArgumentException(optionValues[0]);
                            }
                        }).collect(toList());
            default:
                throw new IllegalArgumentException("Unsupported " + name);
        }
    }

    private static void help(final Options options) {
        new HelpFormatter().printHelp("java -jar meecrowave.jar", options);
    }
}
