/**
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
package org.apache.meecrowave.tests.sse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

@Path("/news")
@ApplicationScoped
public class NewsService {

	private final AtomicInteger newsCounter = new AtomicInteger();

	@GET
	public Response news() {
		JsonObject news = Json.createObjectBuilder().add("news", "online").build();
		return Response.status(Response.Status.OK).entity(news).build();
	}
	
	
	@GET
	@Path("/update")
	@Produces(MediaType.SERVER_SENT_EVENTS)
	public void newsUpdate(@Context SseEventSink eventSink, @Context Sse sse) {
		CompletableFuture.runAsync(() -> {
			IntStream.range(1, 6).forEach(c -> {
				JsonObject newsEvent = Json.createObjectBuilder().add("news", String.format("Updated Event %d", newsCounter.incrementAndGet())).build();
				eventSink.send(sse.newEventBuilder().mediaType(MediaType.APPLICATION_JSON_TYPE).data(newsEvent).build());
			});
			//closing only on the client is generating a chunked connection exception that can be troubleshooted at a later date.
			eventSink.close();
		});
	}

}
