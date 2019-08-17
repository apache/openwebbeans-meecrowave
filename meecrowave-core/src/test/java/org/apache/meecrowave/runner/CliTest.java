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

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.io.IO;
import org.apache.meecrowave.runner.cli.CliOption;
import org.junit.Test;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CliTest {
    private static final AtomicBoolean OPTS_SET = new AtomicBoolean();

    @Test
    public void bake() throws IOException {
        OPTS_SET.set(false);

        int stop;
        int http;
        try (final ServerSocket s1 = new ServerSocket(0)) {
            stop = s1.getLocalPort();
            try (final ServerSocket s2 = new ServerSocket(0)) {
                http = s2.getLocalPort();
            }
        }
        final Thread runner = new Thread() {
            {
                setName("CLI thread");
            }

            @Override
            public void run() {
                Cli.main(new String[]{
                        "--context", "app",
                        "--stop", Integer.toString(stop),
                        "--http", Integer.toString(http),
                        "--tmp-dir", "target/CliTest/simple",
                        "--my-config", "val"
                });
            }
        };
        try {
            runner.start();
            boolean jsonOk = false;
            for (int i = 0; i < 60; i++) {
                try {
                    assertEquals("{\"name\":\"test\"}", slurp(new URL("http://localhost:" + http + "/app/api/test/json")));
                    jsonOk = true;
                    break;
                } catch (final AssertionError notYet) {
                    try {
                        Thread.sleep(1000);
                    } catch (final InterruptedException e) {
                        Thread.interrupted();
                        fail();
                    }
                }
            }
            assertTrue(jsonOk);
            assertTrue(OPTS_SET.get());
        } finally {
            try (final Socket client = new Socket("localhost", stop)) {
                client.getOutputStream().write("SHUTDOWN".getBytes(StandardCharsets.UTF_8));
            } catch (final IOException ioe) {
                // ok, container is down now
            } finally {
                try {
                    runner.join(TimeUnit.MINUTES.toMillis(1));
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
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

    public static class MyOpts implements Cli.Options {
        @CliOption(name = "my-config", description = "test")
        private String opt;
    }

    public static class BeanTester implements ServletContainerInitializer {
        @Override
        public void onStartup(final Set<Class<?>> set, final ServletContext servletContext) throws ServletException {
            OPTS_SET.set("val".equals(
                    Meecrowave.Builder.class.cast(servletContext.getAttribute("meecrowave.configuration"))
                            .getExtension(MyOpts.class).opt));
        }
    }
}
