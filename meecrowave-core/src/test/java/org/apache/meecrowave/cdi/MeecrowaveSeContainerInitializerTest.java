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
package org.apache.meecrowave.cdi;

import org.apache.meecrowave.Meecrowave;
import org.junit.Test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MeecrowaveSeContainerInitializerTest {
    @Test
    public void run() {
        try (final SeContainer container = SeContainerInitializer.newInstance()
                .addProperty("httpPort", new Meecrowave.Builder().randomHttpPort().getHttpPort())
                .disableDiscovery()
                .addBeanClasses(Configured.class)
                .initialize()) {
            final Client client = ClientBuilder.newClient();
            assertNotNull(container.select(Meecrowave.class).get());
            assertEquals("configured", client
                    .target(String.format("http://localhost:%d/configured", container.select(Meecrowave.Builder.class).get().getHttpPort()))
                    .request(TEXT_PLAIN_TYPE)
                    .get(String.class));
        }
    }

    @ApplicationScoped
    @Path("configured")
    public static class Configured {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return "configured";
        }
    }
}
