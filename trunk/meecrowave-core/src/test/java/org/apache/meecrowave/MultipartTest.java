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

import org.apache.johnzon.mapper.reflection.JohnzonParameterizedType;
import org.junit.Test;

import javax.enterprise.context.ApplicationScoped;
import javax.json.bind.annotation.JsonbProperty;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

// just a sample using multiparts
public class MultipartTest {
    @Test
    public void configBinding() {
        try (final Meecrowave meecrowave = new Meecrowave(
                new Meecrowave.Builder()
                        .randomHttpPort()
                        .includePackages(MultipartTest.MultiEndpoint.class.getName())).bake()) {
            final Client client = ClientBuilder.newClient();
            try {
                // for mixed types use org.apache.cxf.jaxrs.ext.multipart.MultipartBody
                final Map<String, JsonbModel> response = client.target("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/MultipartTest")
                        .request()
                        .get(new GenericType<Map<String, JsonbModel>>(new JohnzonParameterizedType(Map.class, String.class, JsonbModel.class)) {{}});
                assertEquals(1, response.size());
                assertEquals("ok", response.get(MediaType.APPLICATION_JSON).value);
            } finally {
                client.close();
            }
        }
    }

    @ApplicationScoped
    @Path("MultipartTest")
    public static class MultiEndpoint {
        @Produces("multipart/mixed")
        @GET
        public Map<String, Object> getBooks() {
            final JsonbModel jsonbModel = new JsonbModel();
            jsonbModel.value = "ok";

            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("application/json", jsonbModel);

            return map;
        }
    }

    public static class JsonbModel {
        @JsonbProperty("test")
        public String value;
    }
}
