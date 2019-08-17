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
package org.apache.meecrowave.test.api;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.api.ListeningBase;
import org.apache.meecrowave.api.StartListening;
import org.apache.meecrowave.api.StopListening;
import org.junit.Test;

public class ListeningTest {
    @Test
    public void events() {
        final Listener listener;
        final String base;
        int count = 0;
        try (final Meecrowave meecrowave = new Meecrowave(
                new Meecrowave.Builder()
                        .randomHttpPort()
                        .includePackages(ListeningTest.class.getName())).bake()) {
            count++;
            listener = CDI.current().select(Listener.class).get();
            assertEquals(count, listener.getEvents().size());

            base = "http://localhost:" + meecrowave.getConfiguration().getHttpPort();
            assertEquals(base, listener.getEvents().iterator().next().getFirstBase());
        }

        count++;
        assertEquals(count, listener.getEvents().size());
        assertEquals(base, listener.getEvents().get(count - 1).getFirstBase());
    }

    @Path("ping")
    @ApplicationScoped
    public static class Ping {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return "ping";
        }
    }

    @ApplicationScoped
    public static class Listener {
        private final List<ListeningBase> events = new ArrayList<>();

        public synchronized void onStart(@Observes final StartListening listening) {
            events.add(listening);
            assertPing(listening);
        }

        public synchronized void onStop(@Observes final StopListening listening) {
            events.add(listening);
            assertPing(listening);
        }

        private void assertPing(final ListeningBase listening) {
            final Client actual = ClientBuilder.newClient();
            try {
                assertEquals("ping", actual.target(listening.getFirstBase())
                        .path("ping")
                        .request(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class).trim());
            } finally {
                actual.close();
            }
        }

        List<ListeningBase> getEvents() {
            return events;
        }
    }
}
