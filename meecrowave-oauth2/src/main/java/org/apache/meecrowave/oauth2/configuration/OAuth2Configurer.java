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
package org.apache.meecrowave.oauth2.configuration;

import org.apache.catalina.realm.GenericPrincipal;
import org.apache.cxf.Bus;
import org.apache.cxf.interceptor.security.AuthenticationException;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.grants.AbstractGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.clientcred.ClientCredentialsGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.code.AuthorizationCodeGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.code.DefaultEncryptingCodeDataProvider;
import org.apache.cxf.rs.security.oauth2.grants.code.JPACodeDataProvider;
import org.apache.cxf.rs.security.oauth2.grants.jwt.JwtBearerGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.owner.JAASResourceOwnerLoginHandler;
import org.apache.cxf.rs.security.oauth2.grants.owner.ResourceOwnerGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.owner.ResourceOwnerLoginHandler;
import org.apache.cxf.rs.security.oauth2.grants.refresh.RefreshTokenGrantHandler;
import org.apache.cxf.rs.security.oauth2.provider.AbstractOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.AccessTokenGrantHandler;
import org.apache.cxf.rs.security.oauth2.provider.DefaultEHCacheOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.DefaultEncryptingOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.JCacheOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.JPAOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.OAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.services.AbstractTokenService;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.oauth2.data.RefreshTokenEnabledProvider;
import org.apache.meecrowave.oauth2.provider.JCacheCodeDataProvider;
import org.apache.meecrowave.oauth2.resource.OAuth2TokenService;

import javax.annotation.PostConstruct;
import javax.crypto.spec.SecretKeySpec;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import static java.util.Arrays.asList;
import static java.util.Locale.ENGLISH;
import static java.util.Optional.ofNullable;
import static org.apache.cxf.rs.security.oauth2.common.AuthenticationMethod.PASSWORD;

@ApplicationScoped
public class OAuth2Configurer implements Consumer<AbstractTokenService> {
    @Inject
    private Meecrowave.Builder builder;

    @Inject
    private Bus bus;

    @Inject
    private HttpServletRequest request;

    private Consumer<OAuth2TokenService> tokenServiceConsumer;
    private Consumer<AbstractTokenService> abstractTokenServiceConsumer;
    private OAuth2Options configuration;

    @PostConstruct // TODO: still some missing configuration for jwt etc to add/wire from OAuth2Options
    private void preCompute() {
        configuration = builder.getExtension(OAuth2Options.class);

        AbstractOAuthDataProvider provider;
        switch (configuration.getProvider().toLowerCase(ENGLISH)) {
            case "jpa": {
                final JPAOAuthDataProvider jpaProvider = new JPAOAuthDataProvider();
                jpaProvider.setEntityManagerFactory(JPAAdapter.createEntityManagerFactory(configuration));
                provider = jpaProvider;
                break;
            }
            case "jpa-code": {
                final JPACodeDataProvider jpaProvider = new JPACodeDataProvider();
                jpaProvider.setEntityManagerFactory(JPAAdapter.createEntityManagerFactory(configuration));
                provider = jpaProvider;
                break;
            }
            case "jcache":
                try {
                    provider = new JCacheOAuthDataProvider(configuration.getJcacheConfigUri(), bus, configuration.isJcacheStoreJwtKeyOnly());
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
                break;
            case "jcache-code":
                try {
                    provider = new JCacheCodeDataProvider(configuration.getJcacheConfigUri(), bus);
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
                break;
            case "ehcache": // not sure it makes sense since we have jcache but this one is cheap to support
                provider = new DefaultEHCacheOAuthDataProvider(configuration.getJcacheConfigUri(), bus);
                break;
            case "encrypted":
                provider = new DefaultEncryptingOAuthDataProvider(
                        new SecretKeySpec(configuration.getEncryptedKey().getBytes(StandardCharsets.UTF_8), configuration.getEncryptedAlgo()));
                break;
            case "encrypted-code":
                provider = new DefaultEncryptingCodeDataProvider(
                        new SecretKeySpec(configuration.getEncryptedKey().getBytes(StandardCharsets.UTF_8), configuration.getEncryptedAlgo()));
                break;
            default:
                throw new IllegalArgumentException("Unsupported oauth2 provider: " + configuration.getProvider());
        }

        final RefreshTokenGrantHandler refreshTokenGrantHandler = new RefreshTokenGrantHandler();
        refreshTokenGrantHandler.setDataProvider(provider);
        refreshTokenGrantHandler.setUseAllClientScopes(configuration.isUseAllClientScopes());
        refreshTokenGrantHandler.setPartialMatchScopeValidation(configuration.isPartialMatchScopeValidation());

        final List<AccessTokenGrantHandler> handlers = new ArrayList<>();
        handlers.add(refreshTokenGrantHandler);
        handlers.add(new ClientCredentialsGrantHandler());
        handlers.add(new ResourceOwnerGrantHandler() {{
            setLoginHandler(configuration.isJaas() ? new JAASResourceOwnerLoginHandler() : new ResourceOwnerLoginHandler() {
                @Override
                public UserSubject createSubject(final String name, final String password) {
                    try {
                        request.login(name, password);
                        try {
                            final Principal pcp = request.getUserPrincipal();
                            final UserSubject userSubject = new UserSubject(
                                    name,
                                    GenericPrincipal.class.isInstance(pcp) ?
                                            new ArrayList<>(asList(GenericPrincipal.class.cast(pcp).getRoles())) : Collections.emptyList());
                            userSubject.setAuthenticationMethod(PASSWORD);
                            return userSubject;
                        } finally {
                            request.logout();
                        }
                    } catch (final ServletException e) {
                        throw new AuthenticationException(e.getMessage());
                    }
                }
            });
        }});
        handlers.add(new AuthorizationCodeGrantHandler());
        handlers.add(new JwtBearerGrantHandler());

        provider.setUseJwtFormatForAccessTokens(configuration.isUseJwtFormatForAccessTokens());
        provider.setAccessTokenLifetime(configuration.getAccessTokenLifetime());
        provider.setRefreshTokenLifetime(configuration.getRefreshTokenLifetime());
        provider.setRecycleRefreshTokens(configuration.isRecycleRefreshTokens());
        provider.setSupportPreauthorizedTokens(configuration.isSupportPreauthorizedTokens());
        ofNullable(configuration.getRequiredScopes()).map(s -> asList(s.split(","))).ifPresent(provider::setRequiredScopes);
        ofNullable(configuration.getDefaultScopes()).map(s -> asList(s.split(","))).ifPresent(provider::setDefaultScopes);
        ofNullable(configuration.getInvisibleToClientScopes()).map(s -> asList(s.split(","))).ifPresent(provider::setInvisibleToClientScopes);
        ofNullable(configuration.getJwtAccessTokenClaimMap()).map(s -> new Properties() {{
            try {
                load(new StringReader(s));
            } catch (IOException e) {
                throw new IllegalArgumentException("Bad claim map configuration, use properties syntax");
            }
        }}).ifPresent(m -> provider.setJwtAccessTokenClaimMap(new HashMap<>(Map.class.cast(m))));

        final OAuthDataProvider dataProvider;
        if (configuration.isRefreshToken()) {
            dataProvider = new RefreshTokenEnabledProvider(provider);
            if (provider.getInvisibleToClientScopes() == null) {
                provider.setInvisibleToClientScopes(new ArrayList<>());
            }
            provider.getInvisibleToClientScopes().add(OAuthConstants.REFRESH_TOKEN_SCOPE);
        } else {
            dataProvider = provider;
        }

        handlers.stream()
                .filter(AbstractGrantHandler.class::isInstance)
                .forEach(h -> {
                    final AbstractGrantHandler handler = AbstractGrantHandler.class.cast(h);
                    handler.setDataProvider(dataProvider);
                    handler.setCanSupportPublicClients(configuration.isCanSupportPublicClients());
                    handler.setPartialMatchScopeValidation(configuration.isPartialMatchScopeValidation());
                });

        abstractTokenServiceConsumer = s -> { // this is used @RequestScoped so ensure it is not slow for no reason
            s.setCanSupportPublicClients(configuration.isCanSupportPublicClients());
            s.setBlockUnsecureRequests(configuration.isBlockUnsecureRequests());
            s.setWriteCustomErrors(configuration.isWriteCustomErrors());
            s.setWriteOptionalParameters(configuration.isWriteOptionalParameters());
            s.setDataProvider(dataProvider);
        };
        tokenServiceConsumer = s -> { // this is used @RequestScoped so ensure it is not slow for no reason
            abstractTokenServiceConsumer.accept(s);
            s.setGrantHandlers(handlers);
        };
    }

    @Override
    public void accept(final AbstractTokenService service) {
        abstractTokenServiceConsumer.accept(service);
    }

    public void accept(final OAuth2TokenService service) {
        tokenServiceConsumer.accept(service);
    }

    public OAuth2Options getConfiguration() {
        return configuration;
    }
}
