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
package org.apache.meecrowave.it.jsp.dispatch;

import static javax.ws.rs.core.MediaType.TEXT_HTML_TYPE;
import static org.junit.Assert.assertEquals;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.apache.meecrowave.Meecrowave;
import org.junit.Test;

public class DispatchEndpointTest {
    @Test
    public void dispatch() {
        final Client client = ClientBuilder.newClient();
        try (final Meecrowave container = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .includePackages("org.apache.meecrowave.it.jsp.dispatch"))
                .bake()) {
            final String html = client.target("http://localhost:" + container.getConfiguration().getHttpPort())
                    .path("dispatch")
                    .request(TEXT_HTML_TYPE)
                    .get(String.class);
            assertEquals("\n\n" +
                    "<!DOCTYPE html>\n" +
                    "<html>\n<head>\n<meta charset=\"utf-8\">\n" +
                    "<title>Meecrowave :: IT :: Dispatch</title>\n" +
                    "</head>\n<body>\n" +
                    "    <h2>Endpoint</h2>\n" +
                    "</body>\n</html>\n", html);
        } finally {
            client.close();
        }
    }
}
