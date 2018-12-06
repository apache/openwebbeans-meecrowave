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

import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static org.junit.Assert.assertEquals;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.junit.Test;

public class InterfaceBasedTest {
    @Test
    public void run() {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .includePackages("org.superbiz.app.Interface")).bake()) {
            final Client client = ClientBuilder.newClient();
            try {
                assertEquals("api", client
                        .target("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/interfacebased")
                        .request(TEXT_PLAIN_TYPE)
                        .get(String.class));
            } finally {
                client.close();
            }
        }
    }
}
