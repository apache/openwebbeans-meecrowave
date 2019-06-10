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

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
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
            .with((server, helper) -> server.createContext("/simple", exchange -> {
                final byte[] out = ("{\"message\":\"" + ofNullable(exchange.getRequestBody()).map(it -> {
                    try {
                        return IO.toString(it);
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                }).orElse("ok") + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Fake-Server", "true");
                exchange.getResponseHeaders().add("Foo", ofNullable(exchange.getRequestURI().getQuery()).orElse("-"));
                helper.response(exchange, HttpURLConnection.HTTP_OK, out);
            }))
            .with((server, helper) -> server.createContext("/data1", exchange -> {
                final byte[] out = ("{\"message\":\"" + IO.toString(exchange.getRequestBody()) + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Fake-Server", "posted");
                helper.response(exchange, HttpURLConnection.HTTP_OK, out);
            }))
            .with((server, helper) -> server.createContext("/upload1", exchange -> {
                final byte[] out = ("{\"message\":\"" + IO.toString(exchange.getRequestBody()) + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                helper.response(exchange, HttpURLConnection.HTTP_OK, out);
            }));

    @ClassRule(order = 2)
    public static final MeecrowaveRule MW = new MeecrowaveRule(new Meecrowave.Builder().randomHttpPort()
            .property("proxy-configuration", "target/test-classes/routes.json"), "");

    @Test
    public void upload() {
        withClient(target -> {
            final Response response = target.path("/upload1").request()
                    .post(entity(new MultipartBody(asList(
                            new Attachment("metadata", APPLICATION_JSON, "{\"content\":\"text\"}"),
                            new Attachment(
                                    "file",
                                    Thread.currentThread().getContextClassLoader().getResourceAsStream("ProxyServletTest/upload/file.txt"),
                                    new ContentDisposition("uploadded.txt"))
                    )), MULTIPART_FORM_DATA));
            assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            final String actual = response.readEntity(String.class);
            assertTrue(actual, actual.contains("uuid:"));
            assertTrue(actual, actual.contains("Content-Type: application/json"));
            assertTrue(actual, actual.contains("{\"content\":\"text\"}"));
            assertTrue(actual, actual.contains("Content-Type: application/octet-stream"));
            assertTrue(actual, actual.contains("test\nfile\nwith\nmultiple\nlines"));
        });
    }

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
