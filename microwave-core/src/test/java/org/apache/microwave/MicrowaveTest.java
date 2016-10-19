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
package org.apache.microwave;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.helpers.FileUtils;
import org.superbiz.app.Endpoint;
import org.superbiz.app.RsApp;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MicrowaveTest {
    @Test
    public void simpleWebapp() {
        final File root = new File("target/MicrowaveTest/simpleWebapp/app");
        FileUtils.mkDir(root);
        Stream.of(Endpoint.class, RsApp.class).forEach(type -> {
            final String target = type.getName().replace(".", "/");
            File targetFile = new File(root, "WEB-INF/classes/" + target + ".class");
            FileUtils.mkDir(targetFile.getParentFile());
            try (final InputStream from = Thread.currentThread().getContextClassLoader().getResourceAsStream(target + ".class");
                 final OutputStream to = new FileOutputStream(targetFile)) {
                IOUtils.copy(from, to);
            } catch (final IOException e) {
                fail();
            }
        });
        try (final Microwave microwave = new Microwave(new Microwave.Builder().randomHttpPort()).start()) {
            microwave.deployWebapp("", root);
            assertEquals("simple", IOUtils.toString(new URL("http://localhost:" + microwave.getConfiguration().httpPort() + "/api/test")));
        } catch (final IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void classpath() {
        try (final Microwave microwave = new Microwave(new Microwave.Builder().randomHttpPort()).bake()) {
            assertEquals("simple", IOUtils.toString(new URL("http://localhost:" + microwave.getConfiguration().httpPort() + "/api/test")));
        } catch (final IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void json() {
        try (final Microwave microwave = new Microwave(new Microwave.Builder().randomHttpPort()).bake()) {
            assertEquals("{\"name\":\"test\"}", IOUtils.toString(new URL("http://localhost:" + microwave.getConfiguration().httpPort() + "/api/test/json")));
        } catch (final IOException e) {
            fail(e.getMessage());
        }
    }
}
