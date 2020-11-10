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
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.rs.security.jose.jwt.JwtClaims;
import org.apache.cxf.rs.security.oauth2.common.AuthenticationMethod;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.OAuthRedirectionState;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.grants.AbstractGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.clientcred.ClientCredentialsGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.code.AuthorizationCodeGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.code.DefaultEncryptingCodeDataProvider;
import org.apache.cxf.rs.security.oauth2.grants.code.DigestCodeVerifier;
import org.apache.cxf.rs.security.oauth2.grants.code.JPACodeDataProvider;
import org.apache.cxf.rs.security.oauth2.grants.code.PlainCodeVerifier;
import org.apache.cxf.rs.security.oauth2.grants.jwt.JwtBearerGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.owner.JAASResourceOwnerLoginHandler;
import org.apache.cxf.rs.security.oauth2.grants.owner.ResourceOwnerGrantHandler;
import org.apache.cxf.rs.security.oauth2.grants.owner.ResourceOwnerLoginHandler;
import org.apache.cxf.rs.security.oauth2.grants.refresh.RefreshTokenGrantHandler;
import org.apache.cxf.rs.security.oauth2.provider.AbstractOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.AccessTokenGrantHandler;
import org.apache.cxf.rs.security.oauth2.provider.DefaultEncryptingOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.JCacheOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.JPAOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.JoseSessionTokenProvider;
import org.apache.cxf.rs.security.oauth2.provider.OAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.services.AbstractTokenService;
import org.apache.cxf.rs.security.oauth2.services.AccessTokenService;
import org.apache.cxf.rs.security.oauth2.services.AuthorizationCodeGrantService;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.oauth2.data.RefreshTokenEnabledProvider;
import org.apache.meecrowave.oauth2.provider.JCacheCodeDataProvider;

import javax.annotation.PostConstruct;
import javax.crypto.spec.SecretKeySpec;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;
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
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Locale.ENGLISH;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.cxf.rs.security.oauth2.common.AuthenticationMethod.PASSWORD;

@ApplicationScoped
public class OAuth2Configurer {
    @Inject
    private Meecrowave.Builder builder;

    @Inject
    private Bus bus;

    @Inject
    private HttpServletRequest request;

    @Inject
    private JCacheConfigurer jCacheConfigurer;

    private OAuth2Options configuration;

    private Consumer<AccessTokenService> tokenServiceConsumer;
    private Consumer<AuthorizationCodeGrantService> redirectionBasedGrantServiceConsumer;
    private Consumer<AbstractTokenService> abstractTokenServiceConsumer;
    private Map<String, String> securityProperties;

    @PostConstruct // TODO: still some missing configuration for jwt etc to add/wire from OAuth2Options
    private void preCompute() {
        configuration = builder.getExtension(OAuth2Options.class);

        final Function<JwtClaims, JwtClaims> customizeClaims = configuration.isUseJwtFormatForAccessTokens() ? claims -> {
            if (claims.getIssuer() == null) {
                claims.setIssuer(configuration.getJwtIssuer());
            }
            return claims;
        } : identity();

        AbstractOAuthDataProvider provider;
        switch (configuration.getProvider().toLowerCase(ENGLISH)) {
            case "jpa": {
                if (!configuration.isAuthorizationCodeSupport()) { // else use code impl
                    final JPAOAuthDataProvider jpaProvider = new JPAOAuthDataProvider() {
                        @Override
                        protected JwtClaims createJwtAccessToken(final ServerAccessToken at) {
                            return customizeClaims.apply(super.createJwtAccessToken(at));
                        }

                        @Override
                        protected ServerAccessToken createNewAccessToken(final Client client, final UserSubject userSub) {
                            final ServerAccessToken token = super.createNewAccessToken(client, userSub);
                            forwardClaims(client, userSub, token);
                            return token;
                        }
                    };
                    jpaProvider.setEntityManagerFactory(JPAAdapter.createEntityManagerFactory(configuration));
                    provider = jpaProvider;
                    break;
                }
            }
            case "jpa-code": {
                final JPACodeDataProvider jpaProvider = new JPACodeDataProvider() {
                    @Override
                    protected JwtClaims createJwtAccessToken(final ServerAccessToken at) {
                        return customizeClaims.apply(super.createJwtAccessToken(at));
                    }

                    @Override
                    protected ServerAccessToken createNewAccessToken(final Client client, final UserSubject userSub) {
                        final ServerAccessToken token = super.createNewAccessToken(client, userSub);
                        forwardClaims(client, userSub, token);
                        return token;
                    }
                };
                jpaProvider.setEntityManagerFactory(JPAAdapter.createEntityManagerFactory(configuration));
                provider = jpaProvider;
                break;
            }
            case "jcache":
                if (!configuration.isAuthorizationCodeSupport()) { // else use code impl
                    jCacheConfigurer.doSetup(configuration);
                    try {
                        provider = new JCacheOAuthDataProvider(configuration.getJcacheConfigUri(), bus, configuration.isJcacheStoreJwtKeyOnly()) {
                            @Override
                            protected JwtClaims createJwtAccessToken(final ServerAccessToken at) {
                                return customizeClaims.apply(super.createJwtAccessToken(at));
                            }

                            @Override
                            protected ServerAccessToken createNewAccessToken(final Client client, final UserSubject userSub) {
                                final ServerAccessToken token = super.createNewAccessToken(client, userSub);
                                forwardClaims(client, userSub, token);
                                return token;
                            }
                        };
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                    break;
                }
            case "jcache-code":
                jCacheConfigurer.doSetup(configuration);
                try {
                    provider = new JCacheCodeDataProvider(configuration, bus) {
                        @Override
                        protected JwtClaims createJwtAccessToken(final ServerAccessToken at) {
                            return customizeClaims.apply(super.createJwtAccessToken(at));
                        }

                        @Override
                        protected ServerAccessToken createNewAccessToken(final Client client, final UserSubject userSub) {
                            final ServerAccessToken token = super.createNewAccessToken(client, userSub);
                            forwardClaims(client, userSub, token);
                            return token;
                        }
                    };
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
                break;
            case "encrypted":
                if (!configuration.isAuthorizationCodeSupport()) { // else use code impl
                    provider = new DefaultEncryptingOAuthDataProvider(
                            new SecretKeySpec(configuration.getEncryptedKey().getBytes(StandardCharsets.UTF_8), configuration.getEncryptedAlgo())) {
                        @Override
                        protected JwtClaims createJwtAccessToken(final ServerAccessToken at) {
                            return customizeClaims.apply(super.createJwtAccessToken(at));
                        }

                        @Override
                        protected ServerAccessToken createNewAccessToken(final Client client, final UserSubject userSub) {
                            final ServerAccessToken token = super.createNewAccessToken(client, userSub);
                            forwardClaims(client, userSub, token);
                            return token;
                        }
                    };
                    break;
                }
            case "encrypted-code":
                provider = new DefaultEncryptingCodeDataProvider(
                        new SecretKeySpec(configuration.getEncryptedKey().getBytes(StandardCharsets.UTF_8), configuration.getEncryptedAlgo())) {
                    @Override
                    protected JwtClaims createJwtAccessToken(final ServerAccessToken at) {
                        return customizeClaims.apply(super.createJwtAccessToken(at));
                    }

                    @Override
                    protected ServerAccessToken createNewAccessToken(final Client client, final UserSubject userSub) {
                        final ServerAccessToken token = super.createNewAccessToken(client, userSub);
                        forwardClaims(client, userSub, token);
                        return token;
                    }
                };
                break;
            default:
                throw new IllegalArgumentException("Unsupported oauth2 provider: " + configuration.getProvider());
        }

        final RefreshTokenGrantHandler refreshTokenGrantHandler = new RefreshTokenGrantHandler() {
            @Override
            public ServerAccessToken createAccessToken(final Client client,
                                                       final MultivaluedMap<String, String> params) throws OAuthServiceException {
                final ServerAccessToken accessToken = super.createAccessToken(client, params);
                forwardClaims(client, accessToken.getSubject(), accessToken);
                return accessToken;
            }
        };
        refreshTokenGrantHandler.setDataProvider(provider);
        refreshTokenGrantHandler.setUseAllClientScopes(configuration.isUseAllClientScopes());
        refreshTokenGrantHandler.setPartialMatchScopeValidation(configuration.isPartialMatchScopeValidation());

        final ResourceOwnerLoginHandler loginHandler = configuration.isJaas() ? new JAASResourceOwnerLoginHandler() {
            @Override
            public UserSubject createSubject(final Client client, final String name, final String password) {
                final UserSubject subject = super.createSubject(client, name, password);
                forwardRolesAsClaims(subject);
                return subject;
            }
        } : (client, name, password) -> {
            try {
                request.login(name, password);
                try {
                    final Principal pcp = request.getUserPrincipal();
                    return doCreateUserSubject(pcp);
                } finally {
                    request.logout();
                }
            } catch (final ServletException e) {
                throw new AuthenticationException(e.getMessage());
            }
        };

        final List<AccessTokenGrantHandler> handlers = new ArrayList<>();
        handlers.add(refreshTokenGrantHandler);
        handlers.add(new ClientCredentialsGrantHandler() {
            @Override
            protected ServerAccessToken doCreateAccessToken(final Client client,
                                                            final UserSubject subject,
                                                            final String requestedGrant,
                                                            final List<String> requestedScopes,
                                                            final List<String> audiences) {
                final ServerAccessToken serverAccessToken = super.doCreateAccessToken(client, subject, requestedGrant, requestedScopes, audiences);
                forwardClaims(client, subject, serverAccessToken);
                return serverAccessToken;
            }
        });
        handlers.add(new ResourceOwnerGrantHandler() {
            {
                setLoginHandler(loginHandler);
            }

            @Override
            protected ServerAccessToken doCreateAccessToken(final Client client,
                                                            final UserSubject subject,
                                                            final String requestedGrant,
                                                            final List<String> requestedScopes,
                                                            final List<String> audiences) {
                final ServerAccessToken serverAccessToken = super.doCreateAccessToken(client, subject, requestedGrant, requestedScopes, audiences);
                forwardClaims(client, subject, serverAccessToken);
                return serverAccessToken;
            }
        });
        handlers.add(new AuthorizationCodeGrantHandler() {
            @Override
            public ServerAccessToken createAccessToken(final Client client, final MultivaluedMap<String, String> params) throws OAuthServiceException {
                if (configuration.isUseS256CodeChallenge()) {
                    setCodeVerifierTransformer(new DigestCodeVerifier());
                }
                return super.createAccessToken(client, params);
            }

            @Override
            protected ServerAccessToken doCreateAccessToken(final Client client,
                                                            final UserSubject subject,
                                                            final String requestedGrant,
                                                            final List<String> requestedScopes,
                                                            final List<String> audiences) {
                final ServerAccessToken serverAccessToken = super.doCreateAccessToken(client, subject, requestedGrant, requestedScopes, audiences);
                forwardClaims(client, subject, serverAccessToken);
                return serverAccessToken;
            }
        });
        handlers.add(new JwtBearerGrantHandler() {
            @Override
            protected ServerAccessToken doCreateAccessToken(final Client client,
                                                            final UserSubject subject,
                                                            final String requestedGrant,
                                                            final List<String> requestedScopes,
                                                            final List<String> audiences) {
                final ServerAccessToken serverAccessToken = super.doCreateAccessToken(client, subject, requestedGrant, requestedScopes, audiences);
                forwardClaims(client, subject, serverAccessToken);
                return serverAccessToken;
            }
        });

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

        final List<String> noConsentScopes = ofNullable(configuration.getScopesRequiringNoConsent())
                .map(s -> asList(s.split(",")))
                .orElse(null);

        // we prefix them oauth2.cxf. but otherwise it is the plain cxf config
        securityProperties = ofNullable(builder.getProperties()).map(Properties::stringPropertyNames).orElse(emptySet()).stream()
                .filter(s -> s.startsWith("oauth2.cxf.rs.security."))
                .collect(toMap(s -> s.substring("oauth2.cxf.".length()), s -> builder.getProperties().getProperty(s)));

        final JoseSessionTokenProvider sessionAuthenticityTokenProvider = new JoseSessionTokenProvider() {
            @Override
            public String createSessionToken(final MessageContext mc, final MultivaluedMap<String, String> params,
                                             final UserSubject subject, final OAuthRedirectionState secData) {
                secData.setClientCodeChallenge(params.getFirst(OAuthConstants.AUTHORIZATION_CODE_CHALLENGE)); // CXF-8368
                return super.createSessionToken(mc, params, subject, secData);
            }
        };
        sessionAuthenticityTokenProvider.setMaxDefaultSessionInterval(configuration.getMaxDefaultSessionInterval());
        // TODO: other configs

        redirectionBasedGrantServiceConsumer = s -> {
            s.setDataProvider(dataProvider);
            s.setBlockUnsecureRequests(configuration.isBlockUnsecureRequests());
            s.setWriteOptionalParameters(configuration.isWriteOptionalParameters());
            s.setUseAllClientScopes(configuration.isUseAllClientScopes());
            s.setPartialMatchScopeValidation(configuration.isPartialMatchScopeValidation());
            s.setUseRegisteredRedirectUriIfPossible(configuration.isUseRegisteredRedirectUriIfPossible());
            s.setMaxDefaultSessionInterval(configuration.getMaxDefaultSessionInterval());
            s.setMatchRedirectUriWithApplicationUri(configuration.isMatchRedirectUriWithApplicationUri());
            s.setScopesRequiringNoConsent(noConsentScopes);
            s.setSessionAuthenticityTokenProvider(sessionAuthenticityTokenProvider);
            s.setCanSupportPublicClients(configuration.isCanSupportPublicClients());
        };
    }

    public UserSubject doCreateUserSubject(final Principal pcp) {
        final List<String> roles = GenericPrincipal.class.isInstance(pcp) ?
                new ArrayList<>(asList(GenericPrincipal.class.cast(pcp).getRoles())) : Collections.<String>emptyList();
        final String name = pcp.getName();
        final UserSubject userSubject = new UserSubject(name, name, roles);
        final Message m = JAXRSUtils.getCurrentMessage();
        if (m != null && m.get(AuthenticationMethod.class) != null) {
            userSubject.setAuthenticationMethod(m.get(AuthenticationMethod.class));
        } else {
            userSubject.setAuthenticationMethod(PASSWORD);
        }
        forwardRolesAsClaims(userSubject);
        return userSubject;
    }

    protected void forwardRolesAsClaims(final UserSubject subject) {
        if (configuration.isForwardRoleAsJwtClaims() && subject.getRoles() != null) {
            subject.setProperties(new HashMap<>());
            subject.getProperties().put("claim.roles", String.join(", ", subject.getRoles()));
        }
    }

    protected void forwardClaims(final Client client, final UserSubject subject,
                                 final ServerAccessToken serverAccessToken) {
        forwardClientClaims(client, serverAccessToken);
        forwardUserClaims(subject, serverAccessToken);
    }

    protected void forwardUserClaims(final UserSubject subject,
                                     final ServerAccessToken serverAccessToken) {
        if (subject.getProperties() == null || subject.getProperties().isEmpty()) {
            return;
        }
        final Map<String, String> claims = subject.getProperties().entrySet().stream()
                .filter(it -> it.getKey().startsWith("claim."))
                .collect(toMap(it -> it.getKey().substring("claim.".length()), Map.Entry::getValue));
        if (claims.isEmpty()) {
            return;
        }
        if (serverAccessToken.getExtraProperties() == null) {
            serverAccessToken.setExtraProperties(claims);
        } else {
            serverAccessToken.getExtraProperties().putAll(claims);
        }
    }

    private void forwardClientClaims(final Client client, final ServerAccessToken serverAccessToken) {
        if (client.getProperties() == null || client.getProperties().isEmpty()) {
            return;
        }
        final Map<String, String> claims = client.getProperties().entrySet().stream()
                .filter(it -> it.getKey().startsWith("claim."))
                .collect(toMap(it -> it.getKey().substring("claim.".length()), Map.Entry::getValue));
        if (claims.isEmpty()) {
            return;
        }
        if (serverAccessToken.getExtraProperties() == null) {
            serverAccessToken.setExtraProperties(claims);
        } else {
            serverAccessToken.getExtraProperties().putAll(claims);
        }
    }

    private void forwardSecurityProperties() {
        // TODO: make it even more contextual, client based?
        final Message currentMessage = PhaseInterceptorChain.getCurrentMessage();
        securityProperties.forEach(currentMessage::put);
    }

    public void accept(final AbstractTokenService service) {
        abstractTokenServiceConsumer.accept(service);
    }

    public void accept(final AccessTokenService service) {
        tokenServiceConsumer.accept(service);
        forwardSecurityProperties();
    }

    public void accept(final AuthorizationCodeGrantService service) {
        redirectionBasedGrantServiceConsumer.accept(service);
        forwardSecurityProperties();
    }

    public OAuth2Options getConfiguration() {
        return configuration;
    }
}
