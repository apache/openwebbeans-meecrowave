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
package org.apache.meecrowave.oauth2;

import org.apache.cxf.rs.security.oauth2.common.ClientAccessToken;
import org.apache.cxf.rs.security.oauth2.provider.OAuthJSONProvider;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Form;

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class OAuth2Test {
    @ClassRule
    public static final MeecrowaveRule MEECROWAVE = new MeecrowaveRule(
            new Meecrowave.Builder().randomHttpPort().user("test", "pwd").role("test", "admin"), "");

    @Test
    public void getPasswordTokenNoClient() {
        final Client client = ClientBuilder.newClient().register(new OAuthJSONProvider());
        try {
            final ClientAccessToken token = client.target("http://localhost:" + MEECROWAVE.getConfiguration().getHttpPort())
                    .path("oauth2/token")
                    .request(APPLICATION_JSON_TYPE)
                    .post(entity(
                            new Form()
                                    .param("grant_type", "password")
                                    .param("username", "test")
                                    .param("password", "pwd"), APPLICATION_FORM_URLENCODED_TYPE), ClientAccessToken.class);
            assertNotNull(token);
            assertEquals("Bearer", token.getTokenType());
            assertNotNull(token.getTokenKey());
            assertEquals(3600, token.getExpiresIn());
            assertNotEquals(0, token.getIssuedAt());
            assertNotNull(token.getRefreshToken());
        } finally {
            client.close();
        }
    }

    @Test
    public void getRefreshTokenNoClient() {
        final Client client = ClientBuilder.newClient().register(new OAuthJSONProvider());
        try {
            // password
            final ClientAccessToken primary = client.target("http://localhost:" + MEECROWAVE.getConfiguration().getHttpPort())
                    .path("oauth2/token")
                    .request(APPLICATION_JSON_TYPE)
                    .post(entity(
                            new Form()
                                    .param("grant_type", "password")
                                    .param("username", "test")
                                    .param("password", "pwd"), APPLICATION_FORM_URLENCODED_TYPE), ClientAccessToken.class);

            // refresh
            final ClientAccessToken token = client.target("http://localhost:" + MEECROWAVE.getConfiguration().getHttpPort())
                    .path("oauth2/token")
                    .request(APPLICATION_JSON_TYPE)
                    .post(entity(
                            new Form()
                                    .param("grant_type", "refresh_token")
                                    .param("refresh_token", primary.getRefreshToken()), APPLICATION_FORM_URLENCODED_TYPE), ClientAccessToken.class);
            assertNotNull(token);
            assertEquals("Bearer", token.getTokenType());
            assertNotNull(token.getTokenKey());
            assertEquals(3600, token.getExpiresIn());
            assertNotEquals(0, token.getIssuedAt());
            assertNotNull(token.getRefreshToken());
        } finally {
            client.close();
        }
    }
}
