/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.meecrowave.tests.sse;

import java.net.MalformedURLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.SseEventSource;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MeecrowaveRule;

import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class SSETest {
    @ClassRule
    public static final MeecrowaveRule CONTAINER = new MeecrowaveRule(new Meecrowave.Builder()
            .randomHttpPort()
            .excludePackages("org.atmosphere")
            //.cxfServletParam("jaxrs.scope", "singleton")
            .includePackages(NewsService.class.getPackage().getName()), "");

    @Test
    public void normal() {
        //Make sure normal JAX-RS requests function with SSE enabled
        final Client client = ClientBuilder.newBuilder().build();
        WebTarget base = client.target(String.format("http://localhost:%d", CONTAINER.getConfiguration().getHttpPort()));
        Response response = base.path("/rs/news").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        JsonObject responseJson = response.readEntity(JsonObject.class);
        assertEquals("online", responseJson.getString("news"));
        client.close();
    }

    @Test
    public void sse() throws MalformedURLException, InterruptedException {
        final Client client = ClientBuilder.newBuilder().build();
        WebTarget base = client.target(String.format("http://localhost:%d", CONTAINER.getConfiguration().getHttpPort()));
        //the /META-INF/services/javax.ws.rs.sse.SseEventSource.Builder file is only needed until this is fixed:
        //https://issues.apache.org/jira/browse/CXF-7633
        //An exception is not thrown on a 404 response but that is not a Meecrowave issue.
        try (final SseEventSource eventSource = SseEventSource.target(base.path("/rs/news/update")).build()) {
            CountDownLatch cdl = new CountDownLatch(5);
            eventSource.register(sse -> {
                JsonObject data = sse.readData(JsonObject.class, MediaType.APPLICATION_JSON_TYPE);
                assertNotNull(data);
                cdl.countDown();
            }, e -> {
                e.printStackTrace();
                fail(e.getMessage());

            });
            eventSource.open();
            assertTrue(cdl.await(20, TimeUnit.SECONDS));
            assertTrue(eventSource.close(5, TimeUnit.SECONDS));
        }
        client.close();
    }
}
