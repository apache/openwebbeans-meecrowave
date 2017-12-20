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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.apache.ziplock.JarLocation.jarLocation;

public class MavenConfiguration extends BaseGenerator {
    @Override
    protected String generate() {
        return super.tableConfig() + "|===\n|Name|Default|Property\n" +
                loadConfiguration()
                        .sorted(Comparator.comparing(o -> o.name))
                        .map(opt -> "|" + opt.name + "|" + ofNullable(opt.defaultValue).orElse("-") + '|' + opt.property)
                        .collect(joining("\n")) + "\n|===\n";
    }

    private Stream<Config> loadConfiguration() {
        try (final InputStream stream = new FileInputStream(new File(
                jarLocation(MavenConfiguration.class).getParentFile().getParentFile().getParentFile(),
                "meecrowave-maven-plugin/target/classes/META-INF/maven/plugin.xml"))) {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            final SAXParser parser = factory.newSAXParser();
            final Collection<Config> configs = new ArrayList<>();
            parser.parse(stream, new DefaultHandler() {
                public Config config;
                private boolean inMojo;
                private boolean inConfiguration;
                private StringBuilder builder;
                private String goal;

                @Override
                public void startElement(final String uri, final String localName,
                                         final String qName, final Attributes attributes) throws SAXException {
                    if ("mojo".equals(localName)) {
                        inMojo = true;
                    } else if ("goal".equals(localName)) {
                        builder = new StringBuilder();
                    } else if ("run".equals(goal) && "configuration".equals(localName)) {
                        inConfiguration = true;
                    } else if (inConfiguration) {
                        config = new Config();
                        configs.add(config);
                        config.name = localName;
                        config.defaultValue = attributes.getValue("default-value");
                        builder = new StringBuilder();
                    }
                }

                @Override
                public void characters(final char[] ch, final int start, final int length) throws SAXException {
                    if (builder != null) {
                        builder.append(ch, start, length);
                    }
                }

                @Override
                public void endElement(final String uri, final String localName, final String qName) throws SAXException {
                    if ("mojo".equals(localName)) {
                        inMojo = false;
                        goal = null;
                    } else if ("goal".equals(localName)) {
                        goal = builder.toString();
                        builder = null;
                    } else if ("run".equals(goal) && "configuration".equals(localName)) {
                        inConfiguration = false;
                    } else if (inConfiguration) {
                        config.property = builder.toString();
                        builder = null;
                        config = null;
                    }
                }
            });
            return configs.stream();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Config {
        private String name;
        private String defaultValue;
        private String property;
    }
}
