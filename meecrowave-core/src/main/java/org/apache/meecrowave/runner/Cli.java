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

import org.apache.catalina.Server;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardServer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.runner.cli.CliOption;
import org.apache.xbean.recipe.ObjectRecipe;

import javax.enterprise.inject.Vetoed;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Vetoed
public class Cli implements Runnable, AutoCloseable {
    private final String[] args;
    private volatile Meecrowave instance;
    private volatile boolean closed;

    public Cli(final String[] args) {
        this.args = args;
    }

    public void run() {
        final ParsedCommand parsedCommand = new ParsedCommand(args).invoke();
        if (parsedCommand.isFailed()) {
            return;
        }

        final Meecrowave.Builder builder = parsedCommand.getBuilder();
        final CommandLine line = parsedCommand.getLine();
        try (final Meecrowave meecrowave = new Meecrowave(builder)) {
            synchronized (this) {
                if (closed) {
                    return;
                }
                this.instance = meecrowave;
            }

            final String ctx = line.getOptionValue("context", "");
            final String fixedCtx = !ctx.isEmpty() && !ctx.startsWith("/") ? '/' + ctx : ctx;
            final String war = line.getOptionValue("webapp");
            meecrowave.start();
            if (war == null) {
                meecrowave.deployClasspath(new Meecrowave.DeploymentMeta(
                        ctx,
                        ofNullable(line.getOptionValue("docbase")).map(File::new).orElseGet(() ->
                                Stream.of("base", "home")
                                    .map(it -> System.getProperty("meecrowave." + it))
                                    .filter(Objects::nonNull)
                                    .map(it -> new File(it, "docBase"))
                                    .filter(File::isDirectory)
                                    .findFirst()
                                    .orElse(null)),
                        null,
                        null));
            } else {
                meecrowave.deployWebapp(fixedCtx, new File(war));
            }
            doWait(meecrowave, line);
        }
    }

    protected void doWait(final Meecrowave meecrowave, final CommandLine line) {
        meecrowave.getTomcat().getServer().await();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        this.closed = true;
        if (instance != null) {
            final Server server = instance.getTomcat().getServer();
            if (StandardServer.class.isInstance(server)) {
                StandardServer.class.cast(server).stopAwait();
            }
        }
    }

    public static void main(final String[] args) {
        new Cli(args).run();
    }

    // utility when user wraps the command, it enables him to manage the instance but reuse most of our options
    public static Meecrowave.Builder create(final String[] args) {
        final ParsedCommand command = new ParsedCommand(args).invoke();
        if (command.isFailed()) {
            return null;
        }
        return command.getBuilder();
    }

    private static void bind(final Meecrowave.Builder builder, final CommandLine line, final List<Field> fields, final Object config) {
        fields.forEach(f -> {
            final CliOption opt = f.getAnnotation(CliOption.class);
            final Optional<String> first = Stream.of(Stream.of(opt.name()), Stream.of(opt.alias()))
                    .flatMap(a -> a)
                    .filter(line::hasOption)
                    .findFirst();
            if (first.isPresent()) {
                final String name = first.get();
                ofNullable(f.getType() == boolean.class ?
                        ofNullable(line.getOptionValue(name)).map(Boolean::parseBoolean).orElse(true) :
                        toValue(builder, name, line.getOptionValues(name), f.getType()))
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
    }

    private static Object toValue(final Meecrowave.Builder builder, final String name, final String[] optionValues, final Class<?> type) {
        if (optionValues == null || optionValues.length == 0) {
            return null;
        }

        // decode options while it is strings
        IntStream.range(0, optionValues.length)
                .forEach(i -> optionValues[i] = builder.getExtension(Meecrowave.ValueTransformers.class).apply(optionValues[i]));

        if (String.class == type) {
            return optionValues[0];
        }
        if (int.class == type) {
            return Integer.parseInt(optionValues[0]);
        }
        if (long.class == type) {
            return Long.parseLong(optionValues[0]);
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

    public interface Options {
    }

    private static final class ParsedCommand {
        private final String[] args;
        private boolean failed;
        private CommandLine line;
        private Meecrowave.Builder builder;

        private ParsedCommand(final String... args) {
            this.args = args;
        }

        private static void help(final org.apache.commons.cli.Options options) {
            new HelpFormatter().printHelp("java -jar meecrowave-runner.jar", options);
        }

        private static Meecrowave.Builder buildConfig(final CommandLine line, final List<Field> fields,
                                                      final Map<Object, List<Field>> propertiesOptions) {
            final Meecrowave.Builder config = new Meecrowave.Builder();
            bind(config, line, fields, config);
            propertiesOptions.forEach((o, f) -> {
                bind(config, line, f, o);
                config.setExtension(o.getClass(), o);
            });
            return config;
        }

        boolean isFailed() {
            return failed;
        }

        public CommandLine getLine() {
            return line;
        }

        public Meecrowave.Builder getBuilder() {
            return builder;
        }

        public ParsedCommand invoke() {
            final org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();
            options.addOption(null, "help", false, "Show help");
            options.addOption(null, "context", true, "The context to use to deploy the webapp");
            options.addOption(null, "webapp", true, "Location of the webapp, if not set the classpath will be deployed");
            options.addOption(null, "docbase", true, "Location of the docbase for a classpath deployment");
            final List<Field> fields = Stream.of(Configuration.class.getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(CliOption.class))
                    .collect(toList());
            final Map<Object, List<Field>> propertiesOptions = StreamSupport.stream(ServiceLoader.load(Options.class).spliterator(), false)
                    .collect(toMap(identity(), o -> Stream.of(o.getClass().getDeclaredFields()).filter(f -> f.isAnnotationPresent(CliOption.class)).collect(toList())));
            fields.forEach(f -> {
                final CliOption opt = f.getAnnotation(CliOption.class);
                final String description = opt.description();
                options.addOption(null, opt.name(), true /*even for booleans otherwise no way to set false for true by default ones*/, description);
                Stream.of(opt.alias()).forEach(a -> options.addOption(null, a, true, description));
            });
            propertiesOptions.values().forEach(all -> all.forEach(f -> {
                final CliOption opt = f.getAnnotation(CliOption.class);
                final String description = opt.description();
                options.addOption(null, opt.name(), true, description);
                Stream.of(opt.alias()).forEach(a -> options.addOption(null, a, true, description));
            }));

            final CommandLineParser parser = new DefaultParser();
            try {
                line = parser.parse(options, args, true);
            } catch (final ParseException exp) {
                help(options);
                failed = true;
                return this;
            }

            if (line.hasOption("help")) {
                help(options);
                failed = true;
                return this;
            }

            builder = buildConfig(line, fields, propertiesOptions);
            failed = false;
            return this;
        }
    }
}
