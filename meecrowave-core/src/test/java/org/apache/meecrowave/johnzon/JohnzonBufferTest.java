/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.meecrowave.johnzon;

import org.apache.meecrowave.Meecrowave;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import static org.junit.Assert.assertEquals;

public class JohnzonBufferTest {
    @Test
    public void test() {
        DebugJohnzonBufferStrategy.resetCounter();
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .jsonpBufferStrategy(DebugJohnzonBufferStrategy.class.getName())
                .includePackages("org.superbiz.app.TestJsonEndpoint")).bake()) {
            final Client client = ClientBuilder.newClient();
            try {
                String jsonResponse = client
                        .target("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/testjsonendpoint/book")
                        .request(MediaType.APPLICATION_JSON)
                        .get(String.class);
                assertEquals("{\"isbn\":\"dummyisbn\"}", jsonResponse);
                assertEquals(3, DebugJohnzonBufferStrategy.getCounter()); // reader fact -> parser fact (2 buffers) + writer -> generator (1 buffer)
            } finally {
                client.close();
            }
        }
    }
}
