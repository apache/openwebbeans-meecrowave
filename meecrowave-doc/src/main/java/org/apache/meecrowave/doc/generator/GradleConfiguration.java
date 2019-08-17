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
package org.apache.meecrowave.doc.generator;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.gradle.MeecrowaveExtension;
import org.apache.meecrowave.runner.cli.CliOption;

public class GradleConfiguration extends BaseGenerator {
    private final MeecrowaveExtension defaults = new MeecrowaveExtension();

    @Override
    protected String generate() {
        return super.tableConfig() + "|===\n|Name|Default|Description\n" +
                Stream.of(MeecrowaveExtension.class.getDeclaredFields())
                    .sorted(comparing(Field::getName))
                    .map(this::toLine)
                    .collect(joining("\n")) + "\n|===\n";
    }

    private String toLine(final Field opt) {
        opt.setAccessible(true);
        try {
            return '|' + opt.getName() + '|' + ofNullable(opt.get(defaults)).orElse("-") + '|' + findDescription(opt.getName());
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private String findDescription(final String opt) {
        switch (opt) {
            case "skipMavenCentral":
                return "Don't add to repositories `mavenCentral()`";
            case "context":
                return "Default context name";
            case "webapp":
                return "Webapp to deploy";
            case "skip":
                return "Should the extension be skipped completely";
            default:
                try {
                    return Configuration.class.getDeclaredField(opt).getAnnotation(CliOption.class).description();
                } catch (final NoSuchFieldException e) {
                    throw new IllegalArgumentException("option " + opt + " not found");
                }
        }
    }
}
