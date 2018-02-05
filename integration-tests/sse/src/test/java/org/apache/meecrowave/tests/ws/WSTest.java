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
package org.apache.meecrowave.tests.ws;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonObject;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.johnzon.websocket.jsr.JsrObjectDecoder;
import org.apache.johnzon.websocket.jsr.JsrObjectEncoder;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.ClassRule;
import org.junit.Test;

public class WSTest {
	@ClassRule
	public static final MeecrowaveRule CONTAINER = new MeecrowaveRule(new Meecrowave.Builder()
			.randomHttpPort()
			.includePackages(ChatWS.class.getPackage().getName()), "");

	@Test
	public void run() throws InterruptedException, DeploymentException, IOException, URISyntaxException {
		CountDownLatch cdl = new CountDownLatch(5);
		WebSocketContainer container = ContainerProvider.getWebSocketContainer();
		String wsEndpoint = String.format("ws://localhost:%d/ws-chat", CONTAINER.getConfiguration().getHttpPort());
		Session session = container.connectToServer(new ChatClient(cdl), new URI(wsEndpoint));
		assertTrue(cdl.await(20, TimeUnit.SECONDS));
		session.close();

	}

	@ClientEndpoint(encoders = JsrObjectEncoder.class, decoders = JsrObjectDecoder.class)
	public class ChatClient {

		private CountDownLatch cdl;

		public ChatClient(CountDownLatch cdl) {
			this.cdl = cdl;
		}

		@OnOpen
		public void onOpen(Session session) {
			try {
				JsonObject ping = Json.createObjectBuilder().add("chat", "ping").build();
				session.getBasicRemote().sendObject(ping);
			} catch (IOException | EncodeException e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}

		@OnMessage
		public void onMessage(JsonObject message, Session session) {
			cdl.countDown();
			if (cdl.getCount() > 0) {
				try {
					JsonObject ping = Json.createObjectBuilder().add("chat", "ping").build();
					session.getBasicRemote().sendObject(ping);
				} catch (IOException | EncodeException e) {
					e.printStackTrace();
					fail(e.getMessage());
				}
			}

		}

		@OnClose
		public void onClose(Session session, CloseReason closeReason) {

		}

	}

}
