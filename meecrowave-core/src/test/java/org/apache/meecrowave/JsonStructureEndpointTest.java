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

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;

import org.junit.Test;

// mainly here to ensure JsrProvider is not needed thanks to default JsonbJaxrsProvider
public class JsonStructureEndpointTest {
    @Test
    public void run() throws IOException {
        try (final Meecrowave container = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .includePackages(Endpoint.class.getName()))
                .bake()) {
            final String base = "http://localhost:" + container.getConfiguration().getHttpPort() + "/JsonStructureEndpointTest/";
            assertEquals("{\"test\":\"yes\"}", slurp(base + "object"));
            assertEquals("[\"test\",\"yes\"]", slurp(base + "array"));
            assertEquals("true", slurp(base + "value"));
        }
    }

    private String slurp(final String url) {
        final Client client = ClientBuilder.newClient();
        try {
            return client.target(url).request(APPLICATION_JSON_TYPE).get(String.class);
        } finally {
            client.close();
        }
    }

    @ApplicationScoped
    @Path("JsonStructureEndpointTest")
    @Produces(MediaType.APPLICATION_JSON)
    public static class Endpoint {
        @GET
        @Path("object")
        public JsonObject object() {
            return Json.createObjectBuilder().add("test", "yes").build();
        }

        @GET
        @Path("array")
        public JsonArray array() {
            return Json.createArrayBuilder().add("test").add("yes").build();
        }

        @GET
        @Path("value")
        public JsonValue value() {
            return JsonValue.TRUE;
        }
    }
}
