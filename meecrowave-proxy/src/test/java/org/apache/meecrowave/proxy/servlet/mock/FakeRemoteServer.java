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
package org.apache.meecrowave.proxy.servlet.mock;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiConsumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class FakeRemoteServer implements TestRule {
    private HttpServer server;
    private final Collection<BiConsumer<HttpServer, HttpHelper>> configurers = new ArrayList<BiConsumer<HttpServer, HttpHelper>>();

    public HttpServer getServer() {
        return server;
    }

    public FakeRemoteServer with(final BiConsumer<HttpServer, HttpHelper> configurer) {
        configurers.add(configurer);
        return this;
    }

    @Override
    public Statement apply(final Statement statement, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    server = HttpServer.create(new InetSocketAddress(0), 0);
                    configurers.forEach(it -> it.accept(server, (exchange, status, payload) -> {
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, payload.length);
                        try (final OutputStream os = exchange.getResponseBody()) {
                            os.write(payload);
                        }
                    }));
                    server.start();
                    System.setProperty("fake.server.port", Integer.toString(server.getAddress().getPort()));
                    statement.evaluate();
                } finally {
                    server.stop(0);
                    server = null;
                    System.clearProperty("fake.server.port");
                }
            }
        };
    }

    public interface HttpHelper {
        void response(HttpExchange exchange, int status, byte[] payload) throws IOException ;
    }
}
