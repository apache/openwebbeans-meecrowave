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

import org.apache.meecrowave.arquillian.MeecrowaveConfiguration;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class ArquillianConfiguration extends BaseGenerator {
    @Override
    protected String generate() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<arquillian xmlns=\"http://jboss.org/schema/arquillian\"\n" +
                "            xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "            xsi:schemaLocation=\"http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd\">\n" +
                "  <container qualifier=\"meecrowave\" default=\"true\">\n" +
                "    <configuration>\n" +
                Stream.of(MeecrowaveConfiguration.class.getDeclaredFields())
                        .sorted(Comparator.comparing(Field::getName))
                        .map(opt -> "      <property name=\"" + opt.getName() + "\">" + valueFor(opt) + "</property>")
                        .collect(joining("\n")) +
                "\n    </configuration>\n" +
                "  </container>\n" +
                "</arquillian>\n";
    }

    private String valueFor(final Field opt) {
        switch (opt.getName()) {
            case "properties":
                return "\n        jpa.property.openjpa.RuntimeUnenhancedClasses=supported\n" +
                    "        jpa.property.openjpa.jdbc.SynchronizeMappings=buildSchema\n" +
                    "      ";
            case "users":
                return "\n        admin=adminpwd\n" +
                    "        other=secret\n" +
                    "      ";
            case "roles":
                return "\n        admin=admin\n" +
                    "        limited=admin,other\n" +
                    "      ";
            case "cxfServletParams":
                return "\n        hide-service-list-page=true\n" +
                    "      ";
            case "realm":
                return "org.apache.catalina.realm.JAASRealm:configFile=jaas.config;appName=app";
            case "securityConstraints":
                return "collection=sc1:/api/*:POST;authRole=**|collection=sc2:/priv/*:GET;authRole=*";
            case "loginConfig":
                return "authMethod=BASIC;realmName=app";
            default:
        }

        opt.setAccessible(true);
        try {
            return ofNullable(opt.get(new MeecrowaveConfiguration())).map(v -> v == null ? "" : v.toString()).orElse("");
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
