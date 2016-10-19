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
package org.apache.microwave.runner;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CliTest {
    @Test
    public void bake() throws IOException {
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
                        "--context=app",
                        "--stop=" + stop,
                        "--http=" + http
                });
            }
        };
        try {
            runner.start();
            for (int i = 0; i < 60; i++) {
                try {
                    assertEquals("{\"name\":\"test\"}", IOUtils.toString(new URL("http://localhost:" + http + "/app/api/test/json")));
                    return;
                } catch (final AssertionError | ConnectException | FileNotFoundException notYet) {
                    try {
                        Thread.sleep(1000);
                    } catch (final InterruptedException e) {
                        Thread.interrupted();
                        fail();
                    }
                }
            }
            fail("Didnt achieved the request");
        } finally {
            try (final Socket client = new Socket("localhost", stop)) {
                client.getOutputStream().write("SHUTDOWN".getBytes(StandardCharsets.UTF_8));
            } catch (final IOException ioe) {
                // ok, container is down now
            } finally {
                try {
                    runner.join(TimeUnit.MINUTES.toMillis(1));
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                }
            }
        }
    }
}
