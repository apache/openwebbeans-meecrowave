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
package org.apache.meecrowave;

import org.apache.catalina.Context;
import org.apache.cxf.helpers.FileUtils;
import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.io.IO;
import org.apache.meecrowave.runner.cli.CliOption;
import org.junit.Test;
import org.superbiz.app.Bounced;
import org.superbiz.app.Endpoint;
import org.superbiz.app.InterfaceApi;
import org.superbiz.app.RsApp;
import org.superbiz.app.TestJsonEndpoint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Properties;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.enumeration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.enterprise.inject.spi.CDI;

public class MeecrowaveTest {
    @Test
    public void noTomcatScanning() {
        try (final Meecrowave meecrowave = new Meecrowave(
                new Meecrowave.Builder().tomcatScanning(false).randomHttpPort())
                .bake()) {
            // ok it started, before the fix it was failing due to a NPE
        }
    }

    @Test
    public void fastStartupSessionId() {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder().randomHttpPort())
                .start().deployClasspath()) {
            assertTrue(Context.class.cast(meecrowave.getTomcat()
                    .getEngine()
                    .findChildren()[0]
                    .findChildren()[0])
                    .getManager().getSessionIdGenerator().toString().startsWith("MeecrowaveSessionIdGenerator@"));
        }
    }

    @Test
    public void conflictingConfig() throws MalformedURLException {
        withConfigClassLoader(() -> {
            try {
                new Meecrowave.Builder().loadFrom("test.config.properties");
                fail("should have failed since it conflicts");
            } catch (final IllegalArgumentException iae) {
                // ok
            }
        }, configUrl("configuration.complete=true\nf=1"), configUrl("configuration.complete=true\nf=2"));
    }

    @Test
    public void masterConfig() throws MalformedURLException {
        withConfigClassLoader(() -> {
            final Meecrowave.Builder builder = new Meecrowave.Builder();
            builder.loadFrom("test.config.properties");
            assertEquals(1, builder.getHttpPort());
        }, configUrl("http=2"), configUrl("configuration.complete=true\nhttp=1"), configUrl("http=3"));
    }

    @Test
    public void mergedConfig() throws MalformedURLException {
        withConfigClassLoader(() -> {
            final Meecrowave.Builder builder = new Meecrowave.Builder();
            builder.loadFrom("test.config.properties");
            assertEquals(2, builder.getHttpPort());
            assertEquals(4, builder.getHttpsPort());
        }, configUrl("http=2\nconfiguration.ordinal=2\nhttps=4"), configUrl("http=3\nconfiguration.ordinal=1"));
    }

    @Test
    public void configBinding() {
        final MyConfig config = new Meecrowave.Builder()
                .property("my-prefix-port", "1234")
                .property("my-prefix-another-port", "5678")
                .property("my-prefix-a-last-port-value", "9632")
                .property("my-prefix-passthrough", "any value")
                .property("my-prefix-bool", "true")
                .bind(new MyConfig());
        assertNotNull(config);
        assertEquals(1234, config.port);
        assertEquals(5678, config.anotherPort);
        assertEquals(9632, config.aLastPortValue);
        assertEquals("any value", config.passthrough);
        assertTrue(config.bool);
    }

    @Test
    public void valueTransformationMainConfig() {
        assertEquals(1234, new Meecrowave.Builder() {{
            loadFromProperties(new Properties() {{
                setProperty("http", "decode:Static3DES:+yYyC7Lb5+k=");
            }});
        }}.getHttpPort());
    }

    @Test
    public void valueTransformationExtension() {
        assertEquals(1234, new Meecrowave.Builder()
                .property("my-prefix-port", "decode:Static3DES:+yYyC7Lb5+k=")
                .bind(new MyConfig()).port);
    }

    @Test
    public void simpleWebapp() {
        final File root = new File("target/MeecrowaveTest/simpleWebapp/app");
        FileUtils.mkDir(root);
        Stream.of(Endpoint.class, RsApp.class).forEach(type -> {
            final String target = type.getName().replace(".", "/");
            File targetFile = new File(root, "WEB-INF/classes/" + target + ".class");
            FileUtils.mkDir(targetFile.getParentFile());
            try (final InputStream from = Thread.currentThread().getContextClassLoader().getResourceAsStream(target + ".class");
                 final OutputStream to = new FileOutputStream(targetFile)) {
                IO.copy(from, to);
            } catch (final IOException e) {
                fail();
            }
        });
        Classes.dump(new File(root, "WEB-INF/classes/"));
        try (final Writer indexHtml = new FileWriter(new File(root, "index.html"))) {
            indexHtml.write("hello");
        } catch (final IOException e) {
            fail(e.getMessage());
        }
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder().randomHttpPort().includePackages("org.superbiz.app")).start()) {
            meecrowave.deployWebapp("", root);
            assertEquals("hello", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/index.html")));
            assertEquals("simplefalse", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/api/test")));
            assertEquals("simpletrue", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort()
                    + "/api/test?checkcustom=true")));
            assertEquals("simple", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/api/other")));
            assertEquals("simplefiltertrue", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/filter")));
            assertEquals("filtertrue", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/other")));
        } catch (final Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void classpath() {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder().randomHttpPort().includePackages("org.superbiz.app")).bake()) {
            assertClasspath(meecrowave);
        } catch (final IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void classpathUsingConfigurationAndNotBuilder() {
        final Meecrowave.Builder randomPortHolder = new Meecrowave.Builder().randomHttpPort();
        final Configuration configuration = new Configuration();
        configuration.setHttpPort(randomPortHolder.getHttpPort());
        configuration.setScanningPackageIncludes("org.superbiz.app");
        try (final Meecrowave meecrowave = new Meecrowave(new Configuration()).bake()) {
            assertClasspath(meecrowave);
        } catch (final IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void json() {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder().randomHttpPort().includePackages("org.superbiz.app")).bake()) {
            assertEquals("{\"name\":\"test\"}", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/api/test/json")));
        } catch (final IOException e) {
            fail(e.getMessage());
        }
    }

    private void assertClasspath(final Meecrowave meecrowave) throws MalformedURLException {
        assertEquals(CDI.current().select(Configuration.class).get(), meecrowave.getConfiguration()); // not symmetric cause of proxy!
        assertEquals("simplefalse", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/api/test")));
        assertEquals("simplefiltertrue", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/filter")));
        assertEquals(
                "sci:" + Bounced.class.getName() + Endpoint.class.getName() + InterfaceApi.class.getName() + RsApp.class.getName() + TestJsonEndpoint.class.getName(),
                slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/sci")));
        assertNotAvailable(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/api/other"));
        assertNotAvailable(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/other"));
    }

    private void assertNotAvailable(final URL url) {
        try {
            URLConnection connection = url.openConnection();
            connection.setReadTimeout(500);
            connection.getInputStream();
            fail(url.toString() + " is available");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e instanceof IOException);
        }
    }

    private String slurp(final URL url) {
        try (final InputStream is = url.openStream()) {
            return IO.toString(is);
        } catch (final IOException e) {
            fail(e.getMessage());
        }
        return null;
    }

    public static class MyConfig {
        @CliOption(name = "my-prefix-port", description = "")
        private int port;

        @CliOption(name = "my-prefix-another-port", description = "")
        private int anotherPort;

        @CliOption(name = "my-prefix-a-last-port-value", description = "")
        private int aLastPortValue;

        @CliOption(name = "my-prefix-passthrough", description = "")
        private String passthrough;

        @CliOption(name = "my-prefix-bool", description = "")
        private boolean bool;
    }

    private static URL configUrl(final String content) throws MalformedURLException {
        return new URL("memory", null, -1, "test.config.properties", new MemoryHandler(content));
    }

    private static void withConfigClassLoader(final Runnable test, final URL... urls) {
        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(loader) {
            @Override
            public Enumeration<URL> getResources(final String name) throws IOException {
                return "test.config.properties".equals(name) ? enumeration(asList(urls)) : super.getResources(name);
            }
        });
        try {
            test.run();
        } finally {
            thread.setContextClassLoader(loader);
        }
    }

    private static class MemoryHandler extends URLStreamHandler
    {
        private final String content;

        private MemoryHandler(final String content)
        {
            this.content = content;
        }

        @Override
        protected URLConnection openConnection(final URL u) {
            return new URLConnection(u)
            {
                @Override
                public void connect()
                {
                    // no-op
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                }
            };
        }
    }
}
