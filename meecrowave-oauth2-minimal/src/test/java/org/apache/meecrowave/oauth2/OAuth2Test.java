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

import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.oauth2.common.ClientAccessToken;
import org.apache.cxf.rs.security.oauth2.common.OAuthAuthorizationData;
import org.apache.cxf.rs.security.oauth2.provider.OAuthJSONProvider;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.geronimo.microprofile.impl.jwtauth.config.GeronimoJwtAuthConfig;
import org.apache.geronimo.microprofile.impl.jwtauth.jwt.DateValidator;
import org.apache.geronimo.microprofile.impl.jwtauth.jwt.JwtParser;
import org.apache.geronimo.microprofile.impl.jwtauth.jwt.KidMapper;
import org.apache.geronimo.microprofile.impl.jwtauth.jwt.SignatureValidator;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.apache.meecrowave.oauth2.provider.JCacheCodeDataProvider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OAuth2Test {
    private static final File KEYSTORE = new File("target/OAuth2Test/keystore.jceks");
    private static PublicKey PUBLIC_KEY;

    @ClassRule
    public static final MeecrowaveRule MEECROWAVE = new MeecrowaveRule(
            new Meecrowave.Builder().randomHttpPort()
                    .user("test", "pwd").role("test", "admin")
                    // jwt requires more config
                    .property("oauth2-use-jwt-format-for-access-token", "true")
                    .property("oauth2-jwt-issuer", "myissuer")
                    // auth code support is optional so activate it
                    .property("oauth2-authorization-code-support", "true")
                    // to ensure this toggle does not trigger any exception
                    .property("oauth2-forward-role-as-jwt-claims", "true")
                    // auth code jose setup to store the tokens
                    .property("oauth2.cxf.rs.security.keystore.type", "jks")
                    .property("oauth2.cxf.rs.security.keystore.file", KEYSTORE.getAbsolutePath())
                    .property("oauth2.cxf.rs.security.keystore.password", "password")
                    .property("oauth2.cxf.rs.security.keystore.alias", "alice")
                    .property("oauth2.cxf.rs.security.key.password", "pwd")
                    // auth code needs a logged pcp to work
                    .loginConfig(new Meecrowave.LoginConfigBuilder().basic())
                    .securityConstraints(new Meecrowave.SecurityConstaintBuilder()
                            .authConstraint(true)
                            .addAuthRole("**")
                            .addCollection("secured", "/oauth2/authorize")
                            .addCollection("secured", "/oauth2/authorize/decision")), "");

    @BeforeClass
    public static void createKeyStore() throws Exception {
        PUBLIC_KEY = Keystores.create(KEYSTORE);
    }

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
            assertIsJwt(token.getTokenKey(), "__default");
            assertEquals(3600, token.getExpiresIn());
            assertNotEquals(0, token.getIssuedAt());
            assertNotNull(token.getRefreshToken());
            validateJwt(token);
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
            assertIsJwt(token.getTokenKey(), "__default");
            assertEquals(3600, token.getExpiresIn());
            assertNotEquals(0, token.getIssuedAt());
            assertNotNull(token.getRefreshToken());
        } finally {
            client.close();
        }
    }

    @Test
    public void authorizationCode() throws URISyntaxException {
        final int httpPort = MEECROWAVE.getConfiguration().getHttpPort();

        createRedirectedClient(httpPort);

        final Client client = ClientBuilder.newClient()
                .property(Message.MAINTAIN_SESSION, true)
                .register(new OAuthJSONProvider());
        try {
            final WebTarget target = client.target("http://localhost:" + httpPort);

            final Response authorization = target
                    .path("oauth2/authorize")
                    .queryParam(OAuthConstants.GRANT_TYPE, OAuthConstants.AUTHORIZATION_CODE_GRANT)
                    .queryParam(OAuthConstants.RESPONSE_TYPE, OAuthConstants.CODE_RESPONSE_TYPE)
                    .queryParam(OAuthConstants.CLIENT_ID, "c1")
                    .queryParam(OAuthConstants.CLIENT_SECRET, "cpwd")
                    .queryParam(OAuthConstants.REDIRECT_URI, "http://localhost:" + httpPort + "/redirected")
                    .request(APPLICATION_JSON_TYPE)
                    .header("authorization", "Basic " + Base64.getEncoder().encodeToString("test:pwd".getBytes(StandardCharsets.UTF_8)))
                    .get();
            final OAuthAuthorizationData data = authorization.readEntity(OAuthAuthorizationData.class);
            assertNotNull(data.getAuthenticityToken());
            assertEquals("c1", data.getClientId());
            assertEquals("http://localhost:" + httpPort + "/oauth2/authorize/decision", data.getReplyTo());
            assertEquals("code", data.getResponseType());
            assertEquals("http://localhost:" + httpPort + "/redirected", data.getRedirectUri());

            final Response decision = target
                    .path("oauth2/authorize/decision")
                    .queryParam(OAuthConstants.SESSION_AUTHENTICITY_TOKEN, data.getAuthenticityToken())
                    .queryParam(OAuthConstants.AUTHORIZATION_DECISION_KEY, "allow")
                    .request(APPLICATION_JSON_TYPE)
                    .cookie(authorization.getCookies().get("JSESSIONID"))
                    .header("authorization", "Basic " + Base64.getEncoder().encodeToString("test:pwd".getBytes(StandardCharsets.UTF_8)))
                    .get();
            assertEquals(Response.Status.SEE_OTHER.getStatusCode(), decision.getStatus());
            assertTrue(decision.getLocation().toASCIIString(), decision.getLocation().toASCIIString().startsWith("http://localhost:" + httpPort + "/redirected?code="));

            final ClientAccessToken token = target
                    .path("oauth2/token")
                    .request(APPLICATION_JSON_TYPE)
                    .post(entity(
                            new Form()
                                    .param(OAuthConstants.GRANT_TYPE, OAuthConstants.AUTHORIZATION_CODE_GRANT)
                                    .param(OAuthConstants.CODE_RESPONSE_TYPE, decision.getLocation().getRawQuery().substring("code=".length()))
                                    .param(OAuthConstants.REDIRECT_URI, "http://localhost:" + httpPort + "/redirected")
                                    .param(OAuthConstants.CLIENT_ID, "c1")
                                    .param(OAuthConstants.CLIENT_SECRET, "cpwd"), APPLICATION_FORM_URLENCODED_TYPE), ClientAccessToken.class);
            assertNotNull(token);
            assertEquals("Bearer", token.getTokenType());
            assertIsJwt(token.getTokenKey(), "c1");
            assertEquals(3600, token.getExpiresIn());
            assertNotEquals(0, token.getIssuedAt());
            assertNotNull(token.getRefreshToken());
        } finally {
            client.close();
        }
    }

    private void createRedirectedClient(final int httpPort) throws URISyntaxException {
        final CachingProvider provider = Caching.getCachingProvider();
        final CacheManager cacheManager = provider.getCacheManager(
                ClassLoaderUtils.getResource("default-oauth2.jcs", OAuth2Test.class).toURI(),
                Thread.currentThread().getContextClassLoader());
        Cache<String, org.apache.cxf.rs.security.oauth2.common.Client> cache;
        try {
            cache = cacheManager
                    .createCache(JCacheCodeDataProvider.CLIENT_CACHE_KEY, new MutableConfiguration<String, org.apache.cxf.rs.security.oauth2.common.Client>()
                            .setTypes(String.class, org.apache.cxf.rs.security.oauth2.common.Client.class)
                            .setStoreByValue(true));
        } catch (final RuntimeException re) {
            cache = cacheManager.getCache(JCacheCodeDataProvider.CLIENT_CACHE_KEY, String.class, org.apache.cxf.rs.security.oauth2.common.Client.class);
        }
        final org.apache.cxf.rs.security.oauth2.common.Client value = new org.apache.cxf.rs.security.oauth2.common.Client("c1", "cpwd", true);
        value.setRedirectUris(singletonList("http://localhost:" + httpPort + "/redirected"));
        cache.put("c1", value);
    }

    private void assertIsJwt(final String tokenKey, final String client) {
        final String[] split = tokenKey.split("\\.");
        assertEquals(3, split.length);
        final BiFunction<Jsonb, String, JsonObject> read = (jsonb, value) ->
                jsonb.fromJson(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8), JsonObject.class);
        try (final Jsonb jsonb = JsonbBuilder.create()) {
            final JsonObject header = read.apply(jsonb, split[0]);
            final JsonObject payload = read.apply(jsonb, split[1]);
            assertEquals("RS256", header.getString("alg"));
            assertEquals("test", payload.getString("username"));
            assertEquals(client, payload.getString("client_id"));

            final JsonObject extraProperties = payload.getJsonObject("extra_properties");
            assertNotNull(extraProperties);
            assertEquals("admin", extraProperties.getString("roles"));
        } catch (final Exception e) {
            fail(e.getMessage());
        }
    }

    private void validateJwt(final ClientAccessToken token) {
        final JwtParser parser = new JwtParser();
        final KidMapper kidMapper = new KidMapper();
        final DateValidator dateValidator = new DateValidator();
        final SignatureValidator signatureValidator = new SignatureValidator();
        final GeronimoJwtAuthConfig config = (value, def) -> {
            switch (value) {
                case "issuer.default":
                    return "myissuer";
                case "jwt.header.kid.default":
                    return "defaultkid";
                case "public-key.default":
                    return Base64.getEncoder().encodeToString(PUBLIC_KEY.getEncoded());
                default:
                    return def;
            }
        };
        setField(kidMapper, "config", config);
        setField(dateValidator, "config", config);
        setField(parser, "config", config);
        setField(signatureValidator, "config", config);
        setField(parser, "kidMapper", kidMapper);
        setField(parser, "dateValidator", dateValidator);
        setField(parser, "signatureValidator", signatureValidator);
        Stream.of(dateValidator, signatureValidator, kidMapper, parser).forEach(this::init);
        final JsonWebToken jsonWebToken = parser.parse(token.getTokenKey());
        assertNotNull(jsonWebToken);
        assertEquals("myissuer", jsonWebToken.getIssuer());
        assertEquals("test", JsonString.class.cast(jsonWebToken.getClaim("username")).getString());
    }

    private void init(final Object o) {
        try {
            final Method init = o.getClass().getDeclaredMethod("init");
            if (!init.isAccessible()) {
                init.setAccessible(true);
            }
            init.invoke(o);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void setField(final Object instance, final String field, final Object value) {
        try {
            final Field declaredField = instance.getClass().getDeclaredField(field);
            if (!declaredField.isAccessible()) {
                declaredField.setAccessible(true);
            }
            declaredField.set(instance, value);
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
