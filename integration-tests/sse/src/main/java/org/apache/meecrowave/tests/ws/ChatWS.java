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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.johnzon.websocket.jsr.JsrObjectDecoder;
import org.apache.johnzon.websocket.jsr.JsrObjectEncoder;

@ServerEndpoint(value = "/ws-chat", encoders = { JsrObjectEncoder.class }, decoders = { JsrObjectDecoder.class })
public class ChatWS {

	AtomicInteger chatCounter = new AtomicInteger();

	@OnOpen
	public void onOpen(Session session) {

	}

	@OnClose
	public void onClose() {

	}

	@OnMessage
	public void message(Session session, JsonObject msg) {		
		try {
			if (!"ping".equals(msg.getString("chat"))) {
				session.close(new CloseReason(CloseCodes.UNEXPECTED_CONDITION, String.format("unexpected chat value %s", msg.getString("chat"))));
			}
			JsonObject pong = Json.createObjectBuilder().add("chat", "pong " + chatCounter.incrementAndGet()).build();
			session.getBasicRemote().sendObject(pong);
		} catch (IOException | EncodeException e) {
			e.printStackTrace();
		}

	}

}
