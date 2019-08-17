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
package org.apache.meecrowave.openwebbeans;

import org.apache.meecrowave.configuration.Configuration;
import org.apache.xbean.finder.filter.Filter;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class KnownClassesFilter implements Filter { // one easy and efficient solution for fatjars
    private static final String[] EMPTY_ARRAY = new String[0];

    // this has to be configured from the root config
    private String[] forced = EMPTY_ARRAY;
    private String[] skipped = EMPTY_ARRAY;

    private final Filter delegateAccept;
    private final Filter delegateSkip;
    private boolean skipAll = false;

    public KnownClassesFilter() {
        final Set<String> excluded = new HashSet<>();
        excluded.add("com.codehale.metrics");
        excluded.add("com.ctc.wstx");
        excluded.add("com.datastax.driver.core");
        excluded.add("com.fasterxml.jackson.annotation");
        excluded.add("com.fasterxml.jackson.core");
        excluded.add("com.fasterxml.jackson.databind");
        excluded.add("com.fasterxml.jackson.dataformat");
        excluded.add("com.fasterxml.jackson.module");
        excluded.add("com.google.common");
        excluded.add("com.google.thirdparty");
        excluded.add("com.ibm.wsdl");
        excluded.add("com.jcraft.jsch");
        excluded.add("com.kenai.jffi");
        excluded.add("com.kenai.jnr");
        excluded.add("com.sun.istack");
        excluded.add("com.sun.xml.bind");
        excluded.add("com.sun.xml.messaging.saaj");
        excluded.add("com.sun.xml.txw2");
        excluded.add("io.jsonwebtoken");
        excluded.add("io.netty");
        excluded.add("io.swagger.annotations");
        excluded.add("io.swagger.config");
        excluded.add("io.swagger.converter");
        excluded.add("io.swagger.core");
        excluded.add("io.swagger.jackson");
        excluded.add("io.swagger.jaxrs");
        excluded.add("io.swagger.model");
        excluded.add("io.swagger.models");
        excluded.add("io.swagger.util");
        excluded.add("javax");
        excluded.add("jnr");
        excluded.add("junit");
        excluded.add("net.sf.ehcache");
        excluded.add("net.shibboleth.utilities.java.support");
        excluded.add("org.aeonbits.owner");
        excluded.add("org.apache.activemq");
        excluded.add("org.apache.bval");
        excluded.add("org.apache.catalina");
        excluded.add("org.apache.camel");
        excluded.add("org.apache.commons.beanutils");
        excluded.add("org.apache.commons.cli");
        excluded.add("org.apache.commons.codec");
        excluded.add("org.apache.commons.collections");
        excluded.add("org.apache.commons.dbcp2");
        excluded.add("org.apache.commons.digester");
        excluded.add("org.apache.commons.io");
        excluded.add("org.apache.commons.jcs.access");
        excluded.add("org.apache.commons.jcs.admin");
        excluded.add("org.apache.commons.jcs.auxiliary");
        excluded.add("org.apache.commons.jcs.engine");
        excluded.add("org.apache.commons.jcs.io");
        excluded.add("org.apache.commons.jcs.utils");
        excluded.add("org.apache.commons.lang");
        excluded.add("org.apache.commons.lang3");
        excluded.add("org.apache.commons.logging");
        excluded.add("org.apache.commons.pool2");
        excluded.add("org.apache.coyote");
        excluded.add("org.apache.cxf");
        excluded.add("org.apache.geronimo.javamail");
        excluded.add("org.apache.geronimo.mail");
        excluded.add("org.apache.geronimo.osgi");
        excluded.add("org.apache.geronimo.specs");
        excluded.add("org.apache.http");
        excluded.add("org.apache.jcp");
        excluded.add("org.apache.johnzon");
        excluded.add("org.apache.juli");
        excluded.add("org.apache.logging.log4j.core");
        excluded.add("org.apache.logging.log4j.jul");
        excluded.add("org.apache.logging.log4j.util");
        excluded.add("org.apache.logging.slf4j");
        excluded.add("org.apache.meecrowave");
        excluded.add("org.apache.myfaces");
        excluded.add("org.apache.naming");
        excluded.add("org.apache.neethi");
        excluded.add("org.apache.openejb");
        excluded.add("org.apache.openjpa");
        excluded.add("org.apache.oro");
        excluded.add("org.apache.tomcat");
        excluded.add("org.apache.tomee");
        excluded.add("org.apache.velocity");
        excluded.add("org.apache.webbeans");
        excluded.add("org.apache.ws");
        excluded.add("org.apache.wss4j");
        excluded.add("org.apache.xbean");
        excluded.add("org.apache.xml");
        excluded.add("org.apache.xml.resolver");
        excluded.add("org.bouncycastle");
        excluded.add("org.codehaus.stax2");
        excluded.add("org.codehaus.swizzle.Grep");
        excluded.add("org.codehaus.swizzle.Lexer");
        excluded.add("org.cryptacular");
        excluded.add("org.eclipse.jdt.core");
        excluded.add("org.eclipse.jdt.internal");
        excluded.add("org.fusesource.hawtbuf");
        excluded.add("org.h2");
        excluded.add("org.hamcrest");
        excluded.add("org.hsqldb");
        excluded.add("org.jasypt");
        excluded.add("org.jboss.marshalling");
        excluded.add("org.joda.time");
        excluded.add("org.jose4j");
        excluded.add("org.junit");
        excluded.add("org.jvnet.mimepull");
        excluded.add("org.metatype.sxc");
        excluded.add("org.objectweb.asm");
        excluded.add("org.objectweb.howl");
        excluded.add("org.openejb");
        excluded.add("org.opensaml");
        excluded.add("org.slf4j");
        excluded.add("org.swizzle");
        excluded.add("org.terracotta.context");
        excluded.add("org.terracotta.entity");
        excluded.add("org.terracotta.modules.ehcache");
        excluded.add("org.terracotta.statistics");
        excluded.add("org.yaml.snakeyaml");
        excluded.add("serp");

        excluded.add("META-INF.versions"); // mjar is not yet supported

        final Set<String> included = new HashSet<>();
        included.add("org.apache.camel.cdi");
        included.add("org.apache.myfaces.cdi");
        // included.add("org.apache.myfaces.application.cdi");
        included.add("org.apache.myfaces.flow.cdi");
        included.add("org.apache.meecrowave.oauth2");

        delegateSkip = new OptimizedExclusionFilter(excluded);
        delegateAccept = new OptimizedExclusionFilter(included);
    }

    public void init(final Configuration config) {
        forced = buildArray(config.getScanningPackageIncludes()).orElse(forced);
        skipped = buildArray(config.getScanningPackageExcludes()).orElse(skipped);

        // if we only got one skip and this is '*' then we skip all packages
       skipAll = skipped.length == 1 && "*".equals(skipped[0]);
    }

    private Optional<String[]> buildArray(final String config) {
        return ofNullable(config)
                .map(v -> Stream.of(v.split(","))
                        .map(String::trim)
                        .filter(i -> !i.isEmpty())
                        .toArray(String[]::new));
    }

    @Override
    public boolean accept(final String name) {
        if (forced != null && startsWith(forced, name)) {
            return true;
        }

        // in case skipped = '*' we skip all and do not delegate
        if (skipAll) {
            return false;
        }

        // skip has the same logic than forced
        if (skipped != null && skipped.length > 0 && startsWith(skipped, name)) {
            return false;
        }

        return delegateAccept.accept(name) || !delegateSkip.accept(name);
    }

    private static boolean startsWith(final String[] array, String name) {
        for (final String prefix : array) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static class OptimizedExclusionFilter implements Filter {
        private final Set<String> included;

        private OptimizedExclusionFilter(final Set<String> exclusions) {
            included = exclusions;
        }

        @Override
        public boolean accept(final String name) {
            int dot = name.indexOf('.');
            while (dot > 0) {
                if (included.contains(name.substring(0, dot))) {
                    return true;
                }
                dot = name.indexOf('.', dot + 1);
            }
            return included.contains(name);
        }
    }
}
