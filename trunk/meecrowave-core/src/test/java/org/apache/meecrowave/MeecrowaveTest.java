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

import org.apache.cxf.helpers.FileUtils;
import org.apache.meecrowave.io.IO;
import org.apache.meecrowave.runner.cli.CliOption;
import org.junit.Test;
import org.superbiz.app.Bounced;
import org.superbiz.app.Endpoint;
import org.superbiz.app.RsApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MeecrowaveTest {
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
            assertEquals("simple", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/api/test")));
            assertEquals("simplepathinfo", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort()
                    + "/api/test?checkcustom=pathinfo#is=fine")));
            assertEquals("simple", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/api/other")));
            assertEquals("simplefiltertrue", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/filter")));
            assertEquals("filtertrue", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/other")));
        } catch (final IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void classpath() {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder().randomHttpPort().includePackages("org.superbiz.app")).bake()) {
            assertEquals("simple", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/api/test")));
            assertEquals("simplefiltertrue", slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/filter")));
            assertEquals(
                    "sci:" + Bounced.class.getName() + Endpoint.class.getName() + RsApp.class.getName(),
                    slurp(new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/sci")));
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
}
