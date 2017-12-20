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
package org.apache.meecrowave.beanvalidation.itest;

import org.apache.meecrowave.Meecrowave;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.Assert.assertEquals;

public class ServiceTest {
    @Test
    public void bval() {
        try (final Meecrowave container = new Meecrowave(new Meecrowave.Builder()
                .includePackages(Service.class.getPackage().getName())
                .randomHttpPort())
                .bake()) {
            final String uri = "http://localhost:" + container.getConfiguration().getHttpPort() + "/test";
            final Client client = ClientBuilder.newClient();
            try {
                assertEquals(
                        "{\"value\":\"ok\"}",
                        client.target(uri)
                                .queryParam("val", "ok")
                                .request(APPLICATION_JSON_TYPE)
                                .get(String.class));
                assertEquals(
                        Response.Status.BAD_REQUEST.getStatusCode(),
                        client.target(uri)
                                .request(APPLICATION_JSON_TYPE)
                                .get()
                                .getStatus());
            } finally {
                client.close();
            }
        }
    }
}
