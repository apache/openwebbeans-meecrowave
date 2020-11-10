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

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

import org.apache.meecrowave.configuration.Configuration;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;

public class KnownJarsFilter implements JarScanFilter {
    private final Collection<String> forceIncludes = new HashSet<String>() {{
        add("cxf-integration-cdi");
        add("geronimo-jcache-simple");
    }};

    private final Collection<String> excludes = new HashSet<String>() {{
        add("activation-");
        add("activeio-");
        add("activemq-");
        add("aether-");
        add("akka-actor-");
        add("akka-cluster-");
        add("akka-remote-");
        add("animal-sniffer-annotations-");
        add("ant-");
        add("antlr-");
        add("aopalliance-");
        add("ApacheJMeter");
        add("apiguardian-");
        add("args4j-");
        add("arquillian-common");
        add("arquillian-config-");
        add("arquillian-container-");
        add("arquillian-core-api-");
        add("arquillian-core-impl-base");
        add("arquillian-core.jar");
        add("arquillian-core-spi-");
        add("arquillian-junit-");
        add("arquillian-junit.jar");
        add("arquillian-test-api");
        add("arquillian-test-impl-base");
        add("arquillian-testng.jar");
        add("arquillian-test-spi");
        add("arquillian-tomee-");
        add("arquillian-transaction.jar");
        add("asciidoctorj-");
        add("asm-");
        add("async-http-client-");
        add("avalon-framework-");
        add("avro-");
        add("axis-");
        add("axis2-");
        add("batchee-jbatch");
        add("batik-");
        add("bcprov-");
        add("beam-");
        add("bootstrap.jar");
        add("bsf-");
        add("bval-core");
        add("bval-jsr");
        add("c3p0-");
        add("cassandra-driver-core");
        add("catalina-");
        add("catalina.jar");
        add("cglib-");
        add("charsets.jar");
        add("checker-qual");
        add("common-java5");
        add("common-junit");
        add("commons-beanutils");
        add("commons-cli-");
        add("commons-codec-");
        add("commons-collections-");
        add("commons-compress-");
        add("commons-configuration-");
        add("commons-dbcp");
        add("commons-dbcp-all-1.3-");
        add("commons-digester-");
        add("commons-discovery-");
        add("commons-fileupload-");
        add("commons-httpclient-");
        add("commons-io-");
        add("commons-jcs-core-");
        add("commons-lang-");
        add("commons-lang3-");
        add("commons-logging-");
        add("commons-logging-api-");
        add("commons-net-");
        add("commons-pool-");
        add("commons-pool2-");
        add("commons-text-");
        add("cryptacular-");
        add("cssparser-");
        add("cxf-");
        add("deploy.jar");
        add("derby-");
        add("derbyclient-");
        add("derbynet-");
        add("dom4j");
        add("ecj-");
        add("eclipselink-");
        add("ehcache-");
        add("el-api");
        add("error_prone_annotations");
        add("failureaccess");
        add("FastInfoset");
        add("freemarker-");
        add("fusemq-leveldb-");
        add("geronimo-connector-");
        add("geronimo-j2ee");
        add("geronimo-jpa");
        add("geronimo-javamail");
        add("geronimo-transaction");
        add("google-");
        add("gpars-");
        add("gragent.jar");
        add("groovy-");
        add("gson-");
        add("guava-");
        add("guice-");
        add("h2-");
        add("hamcrest-");
        add("hawtbuf-");
        add("hawtdispatch-");
        add("hawtio-");
        add("hawtjni-runtime");
        add("hibernate-");
        add("howl-");
        add("hsqldb-");
        add("htmlunit-");
        add("httpclient-");
        add("httpcore-");
        add("icu4j-");
        add("idb-");
        add("idea_rt.jar");
        add("istack-commons-runtime-");
        add("ivy-");
        add("j2objc-");
        add("jackson-annotations-");
        add("jackson-core-");
        add("jackson-databind-");
        add("jackson-mapper-asl-");
        add("jackson-module-jaxb-annotations-");
        add("janino-");
        add("jansi-");
        add("jasper-el.jar");
        add("jasper.jar");
        add("jasypt-");
        add("java-atk-wrapper");
        add("javaee-");
        add("javaee-api");
        add("javassist-");
        add("java-support-");
        add("javaws.jar");
        add("javax.");
        add("jaxb-");
        add("jaxp-");
        add("jaxws-api");
        add("jbake-");
        add("jboss-");
        add("jbossall-");
        add("jbosscx-");
        add("jbossjts-");
        add("jbosssx-");
        add("jce.jar");
        add("jcommander-");
        add("jersey-");
        add("jettison-");
        add("jetty-");
        add("jfr.jar");
        add("jfxrt.jar");
        add("jib-");
        add("jline");
        add("jmdns-");
        add("jna-");
        add("jnr-");
        add("joda-time-");
        add("johnzon-");
        add("jolokia-core-");
        add("jolokia-jvm-");
        add("jolokia-serv");
        add("jruby-");
        add("jsonb-api");
        add("json-simple-");
        add("jsoup-");
        add("jsp-api");
        add("jsr166");
        add("jsr299-");
        add("jsr305");
        add("jsr311-");
        add("jsse.jar");
        add("juli-");
        add("jul-to-slf4j-");
        add("junit-");
        add("junit5-");
        add("jython-");
        add("kahadb-");
        add("kotlin-runtime");
        add("leveldb");
        add("listenablefuture");
        add("log4j-");
        add("logkit-");
        add("lombok-");
        add("lucene-analyzers-");
        add("lucene-core-");
        add("management-agent.jar");
        add("maven-");
        add("mbean-annotation-api-");
        add("meecrowave-gradle-");
        add("meecrowave-maven-");
        add("meecrowave-specs-api-");
        add("microprofile-config-api-");
        add("microprofile-fault-tolerance-api-");
        add("microprofile-health-api-");
        add("microprofile-jwt-auth-api-");
        add("microprofile-metrics-api-");
        add("microprofile-openapi-api-");
        add("microprofile-opentracing-api-");
        add("mimepull-");
        add("mina-");
        add("mqtt-client-");
        add("multiverse-core-");
        add("myfaces-api");
        add("myfaces-impl");
        add("mysql-connector-java-");
        add("neethi-");
        add("nekohtml-");
        add("netty-");
        add("openjpa-");
        add("openmdx-");
        add("opensaml-");
        add("opentest4j-");
        add("openwebbeans-");
        add("openws-");
        add("ops4j-");
        add("org.apache.aries.blueprint.noosgi");
        add("org.apache.aries.blueprint.web");
        add("org.eclipse.");
        add("org.jacoco.agent");
        add("org.junit.");
        add("org.osgi.annotation.versioning");
        add("org.osgi.core-");
        add("org.osgi.enterprise");
        add("orient-commons-");
        add("orientdb-core-");
        add("orientdb-nativeos-");
        add("oro-");
        add("paranamer-");
        add("pax-url");
        add("PDFBox");
        add("plexus-");
        add("plugin.jar");
        add("poi-");
        add("qdox-");
        add("quartz-2");
        add("quartz-openejb-");
        add("resources.jar");
        add("rhino-");
        add("rmock-");
        add("rt.jar");
        add("saaj-");
        add("sac-");
        add("scala-library-");
        add("scalatest");
        add("scannotation-");
        add("serializer-");
        add("serp-");
        add("servlet-api-");
        add("shrinkwrap-");
        add("sisu-guice");
        add("sisu-inject");
        add("slf4j-");
        add("smack-");
        add("smackx-");
        add("snappy-");
        add("spring-");
        add("sshd-");
        add("stax2-api-");
        add("stax-api-");
        add("stax-ex-");
        add("sunec.jar");
        add("surefire-");
        add("swizzle-");
        add("sxc-");
        add("testng-");
        add("tomcat-annotations");
        add("tomcat-api");
        add("tomcat-catalina");
        add("tomcat-coyote");
        add("tomcat-dbcp");
        add("tomcat-el");
        add("tomcat-i18n");
        add("tomcat-jasper");
        add("tomcat-jaspic");
        add("tomcat-jdbc");
        add("tomcat-jni");
        add("tomcat-jsp");
        add("tomcat-juli");
        add("tomcat-servlet");
        add("tomcat-spdy");
        add("tomcat-tribes");
        add("tomcat-util");
        add("tomcat-websocket-api");
        add("tomee-");
        add("tools.jar");
        add("twitter4j-");
        add("validation-api-");
        add("velocity-");
        add("wagon-");
        add("webbeans-ee");
        add("webbeans-ejb");
        add("webbeans-impl");
        add("webbeans-spi");
        add("websocket-api");
        add("woodstox-core-");
        add("ws-commons-util-");
        add("wsdl4j-");
        add("wss4j-");
        add("wstx-asl-");
        add("xalan-");
        add("xbean-");
        add("xercesImpl-");
        add("xml-apis-");
        add("xmlbeans-");
        add("xmlgraphics-");
        add("xmlpull-");
        add("xml-resolver-");
        add("xmlrpc-");
        add("xmlschema-");
        add("XmlSchema-");
        add("xmlsec-");
        add("xmltooling-");
        add("xmlunit-");
        add("xstream-");
        add("xz-");
        add("zipfs.jar");
        add("ziplock-");
    }};

    public KnownJarsFilter() {
        // no-op
    }

    KnownJarsFilter(final Configuration config) {
        ofNullable(config.getScanningIncludes()).ifPresent(i -> {
            forceIncludes.clear();
            forceIncludes.addAll(Stream.of(i.split(",")).map(String::trim).filter(j -> !j.isEmpty()).collect(toSet()));
        });
        ofNullable(config.getScanningExcludes())
                .ifPresent(i -> excludes.addAll(Stream.of(i.split(",")).map(String::trim).filter(j -> !j.isEmpty()).collect(toSet())));
    }

    @Override
    public boolean check(final JarScanType jarScanType, final String jarName) {
        return forceIncludes.stream().anyMatch(jarName::startsWith) ||
                (excludes.stream().noneMatch(jarName::startsWith) && !isGeronimoSpecJar(jarName));
    }

    private boolean isGeronimoSpecJar(final String jarName) {
        return jarName.startsWith("geronimo") && jarName.contains("_spec");
    }
}
