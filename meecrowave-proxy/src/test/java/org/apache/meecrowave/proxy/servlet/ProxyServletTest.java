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
package org.apache.meecrowave.proxy.servlet;

import static java.util.Optional.ofNullable;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.io.IO;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.apache.meecrowave.proxy.servlet.mock.FakeRemoteServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;

public class ProxyServletTest {
    @ClassRule(order = 1)
    public static final TestRule FAKE_REMOTE_SERVER = new FakeRemoteServer()
            .with(server -> server.createContext("/simple", exchange -> {
                final byte[] out = ("{\"message\":\"" + ofNullable(exchange.getRequestBody()).map(it -> {
                    try {
                        return IO.toString(it);
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                }).orElse("ok") + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Fake-Server", "true");
                exchange.getResponseHeaders().add("Foo", ofNullable(exchange.getRequestURI().getQuery()).orElse("-"));
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, out.length);
                try (final OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }))
            .with(server -> server.createContext("/data1", exchange -> {
                final byte[] out = ("{\"message\":\"" + IO.toString(exchange.getRequestBody()) + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Fake-Server", "posted");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, out.length);
                try (final OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }));

    @ClassRule(order = 2)
    public static final MeecrowaveRule MW = new MeecrowaveRule(new Meecrowave.Builder()
            .property("proxy-configuration", "target/test-classes/routes.json"), "");

    @Test
    public void get() {
        withClient(target -> {
            final Response response = target.path("/simple").request().get();
            assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            assertEquals("true", response.getHeaderString("Fake-Server"));
            assertEquals("{\"message\":\"\"}", response.readEntity(String.class));
        });
    }

    @Test
    public void getWithQuery() {
        withClient(target -> {
            final Response response = target.path("/simple").queryParam("foo", "bar").request().get();
            assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            assertEquals("foo=bar", response.getHeaderString("Foo"));
            assertEquals("{\"message\":\"\"}", response.readEntity(String.class));
        });
    }

    @Test
    public void post() {
        withClient(target -> {
            final Response response = target.path("/data1").request().post(entity("data were sent", TEXT_PLAIN_TYPE));
            assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            assertEquals("posted", response.getHeaderString("Fake-Server"));
            assertEquals("{\"message\":\"data were sent\"}", response.readEntity(String.class));
        });
    }

    private void withClient(final Consumer<WebTarget> withBase) {
        final Client client = ClientBuilder.newClient();
        try {
            withBase.accept(client.target("http://localhost:" + MW.getConfiguration().getHttpPort()));
        } finally {
            client.close();
        }
    }
}
