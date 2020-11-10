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

import org.apache.meecrowave.runner.Cli;
import org.apache.meecrowave.runner.cli.CliOption;

public class OAuth2Options implements Cli.Options {
    @CliOption(name = "oauth2-refresh-token", description = "Is issuing of access token issuing a refreh token too")
    private boolean refreshToken = true;

    @CliOption(name = "oauth2-client-force", description = "Is a client mandatory or can a token be issued without any client")
    private boolean forceClient;

    @CliOption(name = "oauth2-support-public-client", description = "Are public clients supported")
    private boolean canSupportPublicClients;

    @CliOption(name = "oauth2-use-all-client-scopes", description = "Are all client scopes used for refresh tokens")
    private boolean useAllClientScopes;

    @CliOption(name = "oauth2-require-user-to-start-authorization_code-flow", description = "Should the authorization_code flow require an authenicated user.")
    private boolean requireUserToStartAuthorizationCodeFlow;

    @CliOption(name = "oauth2-use-s256-code-challenge", description = "Are the code_challenge used by PKCE flow digested or not.")
    private boolean useS256CodeChallenge = true;

    @CliOption(name = "oauth2-write-custom-errors", description = "Should custom errors be written")
    private boolean writeCustomErrors;

    @CliOption(name = "oauth2-block-unsecure-requests", description = "Should unsecured requests be blocked")
    private boolean blockUnsecureRequests;

    @CliOption(name = "oauth2-write-optional-parameters", description = "Should optional parameters be written")
    private boolean writeOptionalParameters = true;

    @CliOption(name = "oauth2-partial-match-scope-validation", description = "Is partial match for scope validation activated")
    private boolean partialMatchScopeValidation;

    @CliOption(name = "oauth2-use-jaas", description = "Should jaas be used - alternative (default) is to delegate to meecrowave/tomcat realms")
    private boolean jaas;

    @CliOption(name = "oauth2-forward-role-as-jwt-claims", description = "Should jaas be used - alternative (default) is to delegate to meecrowave/tomcat realms")
    private boolean forwardRoleAsJwtClaims;

    @CliOption(name = "oauth2-access-token-lifetime", description = "How long an access token is valid, default to 3600s")
    private int accessTokenLifetime = 3600;

    @CliOption(name = "oauth2-refresh-token-lifetime", description = "How long a refresh token is valid, default to eternity (0)")
    private long refreshTokenLifetime;

    @CliOption(name = "oauth2-refresh-token-recycling", description = "Should refresh token be recycled")
    private boolean recycleRefreshTokens = true;

    @CliOption(name = "oauth2-default-scopes", description = "Comma separated list of default scopes")
    private String defaultScopes;

    @CliOption(name = "oauth2-required-scopes", description = "Comma separated list of required scopes")
    private String requiredScopes;

    @CliOption(name = "oauth2-invisible-scopes", description = "Comma separated list of invisible to client scopes")
    private String invisibleToClientScopes;

    @CliOption(name = "oauth2-support-pre-authorized-tokens", description = "Are pre-authorized token supported")
    private boolean supportPreauthorizedTokens;

    @CliOption(name = "oauth2-use-jwt-format-for-access-token", description = "Should access token be jwt?")
    private boolean useJwtFormatForAccessTokens;

    @CliOption(name = "oauth2-jwt-access-token-claim-map", description = "The jwt claims configuration")
    private String jwtAccessTokenClaimMap;

    @CliOption(name = "oauth2-jwt-issuer", description = "The jwt issuer (ignored if not set)")
    private String jwtIssuer;

    @CliOption(name = "oauth2-provider", description = "Which provider type to use: jcache[-code], jpa[-code], encrypted[-code]")
    private String provider = "jcache";

    @CliOption(name = "oauth2-jcache-config", description = "JCache configuration uri for the cache manager (jcache or provider)")
    private String jcacheConfigUri = "default-oauth2.jcs";

    @CliOption(name = "oauth2-jcache-store-value", description = "Should JCache store value or not")
    private boolean jcacheStoreValue = true;

    @CliOption(name = "oauth2-jcache-statistics", description = "Should JCache statistics be enabled")
    private boolean jcacheStatistics = false;

    @CliOption(name = "oauth2-jcache-jmx", description = "Should JCache JMX MBeans be enabled")
    private boolean jcacheJmx = false;

    @CliOption(name = "oauth2-jcache-loader", description = "The loader bean or class name")
    private String jcacheLoader;

    @CliOption(name = "oauth2-jcache-writer", description = "The writer bean or class name")
    private String jcacheWriter;

    @CliOption(name = "oauth2-jcache-store-jwt-token-key-only", description = "Should JCache store jwt token key only (jcache provider)")
    private boolean jcacheStoreJwtKeyOnly;

    @CliOption(name = "oauth2-jpa-database-url", description = "JPA database url for jpa provider")
    private String jpaDatabaseUrl = "jdbc:h2:mem:oauth2";

    @CliOption(name = "oauth2-jpa-database-username", description = "JPA database username for jpa provider")
    private String jpdaDatabaseUsername = "sa";

    @CliOption(name = "oauth2-jpa-database-password", description = "JPA database password for jpa provider")
    private String jpdaDatabasePassword = "";

    @CliOption(name = "oauth2-jpa-database-driver", description = "JPA database driver for jpa provider")
    private String jpaDriver = "org.h2.Driver";

    @CliOption(name = "oauth2-jpa-properties", description = "JPA persistence unit properties for jpa provider")
    private String jpaProperties;

    @CliOption(name = "oauth2-jpa-max-active", description = "JPA max active connections for jpa provider")
    private int jpaMaxActive = 30;

    @CliOption(name = "oauth2-jpa-max-idle", description = "JPA max idle connections for jpa provider")
    private int jpaMaxIdle = 10;

    @CliOption(name = "oauth2-jpa-max-wait", description = "JPA max wait for connections for jpa provider")
    private int jpaMaxWait = 30000;

    @CliOption(name = "oauth2-jpa-validation-query", description = "validation query for jpa provider")
    private String jpaValidationQuery;

    @CliOption(name = "oauth2-jpa-validation-interval", description = "validation interval for jpa provider")
    private int jpaValidationInterval = 5 * 1000 * 60;

    @CliOption(name = "oauth2-jpa-test-on-borrow", description = "should connections be tested on borrow for jpa provider")
    private boolean jpaTestOnBorrow;

    @CliOption(name = "oauth2-jpa-test-on-return", description = "should connections be tested on return for jpa provider")
    private boolean jpaTestOnReturn;

    @CliOption(name = "oauth2-encrypted-key", description = "The key for encrypted provider")
    private String encryptedKey;

    @CliOption(name = "oauth2-encrypted-algorithm", description = "The algorithm for the key for the encrypted provider")
    private String encryptedAlgo;

    @CliOption(name = "oauth2-token-support", description = "Are token flows supported")
    private boolean tokenSupport = true;

    @CliOption(name = "oauth2-authorization-code-support", description = "Is authorization code flow supported")
    private boolean authorizationCodeSupport;

    @CliOption(name = "oauth2-redirection-use-registered-redirect-uri-if-possible", description = "For authorization code flow, should the registered uri be used")
    private boolean useRegisteredRedirectUriIfPossible = true;

    /*
    private SessionAuthenticityTokenProvider sessionAuthenticityTokenProvider;
    private SubjectCreator subjectCreator;
    private ResourceOwnerNameProvider resourceOwnerNameProvider;
    private AuthorizationRequestFilter authorizationFilter;
    */

    @CliOption(name = "oauth2-redirection-max-default-session-interval", description = "For authorization code flow, how long a session can be")
    private int maxDefaultSessionInterval;

    @CliOption(
            name = "oauth2-redirection-match-redirect-uri-with-application-uri",
            description = "For authorization code flow, should redirect uri be matched with application one")
    private boolean matchRedirectUriWithApplicationUri;

    @CliOption(name = "oauth2-redirection-scopes-requiring-no-consent", description = "For authorization code flow, the scopes using no consent")
    private String scopesRequiringNoConsent;

    public boolean isRequireUserToStartAuthorizationCodeFlow() {
        return requireUserToStartAuthorizationCodeFlow;
    }

    public void setRequireUserToStartAuthorizationCodeFlow(final boolean requireUserToStartAuthorizationCodeFlow) {
        this.requireUserToStartAuthorizationCodeFlow = requireUserToStartAuthorizationCodeFlow;
    }

    public boolean isUseS256CodeChallenge() {
        return useS256CodeChallenge;
    }

    public void setUseS256CodeChallenge(final boolean useS256CodeChallenge) {
        this.useS256CodeChallenge = useS256CodeChallenge;
    }

    public boolean isForwardRoleAsJwtClaims() {
        return forwardRoleAsJwtClaims;
    }

    public void setForwardRoleAsJwtClaims(final boolean forwardRoleAsJwtClaims) {
        this.forwardRoleAsJwtClaims = forwardRoleAsJwtClaims;
    }

    public String getEncryptedAlgo() {
        return encryptedAlgo;
    }

    public void setEncryptedAlgo(final String encryptedAlgo) {
        this.encryptedAlgo = encryptedAlgo;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(final String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    public int getJpaMaxActive() {
        return jpaMaxActive;
    }

    public void setJpaMaxActive(final int jpaMaxActive) {
        this.jpaMaxActive = jpaMaxActive;
    }

    public int getJpaMaxIdle() {
        return jpaMaxIdle;
    }

    public void setJpaMaxIdle(final int jpaMaxIdle) {
        this.jpaMaxIdle = jpaMaxIdle;
    }

    public int getJpaMaxWait() {
        return jpaMaxWait;
    }

    public void setJpaMaxWait(final int jpaMaxWait) {
        this.jpaMaxWait = jpaMaxWait;
    }

    public String getJpaValidationQuery() {
        return jpaValidationQuery;
    }

    public void setJpaValidationQuery(final String jpaValidationQuery) {
        this.jpaValidationQuery = jpaValidationQuery;
    }

    public int getJpaValidationInterval() {
        return jpaValidationInterval;
    }

    public void setJpaValidationInterval(final int jpaValidationInterval) {
        this.jpaValidationInterval = jpaValidationInterval;
    }

    public boolean isJpaTestOnBorrow() {
        return jpaTestOnBorrow;
    }

    public void setJpaTestOnBorrow(final boolean jpaTestOnBorrow) {
        this.jpaTestOnBorrow = jpaTestOnBorrow;
    }

    public boolean isJpaTestOnReturn() {
        return jpaTestOnReturn;
    }

    public void setJpaTestOnReturn(final boolean jpaTestOnReturn) {
        this.jpaTestOnReturn = jpaTestOnReturn;
    }

    public String getJpaProperties() {
        return jpaProperties;
    }

    public void setJpaProperties(final String jpaProperties) {
        this.jpaProperties = jpaProperties;
    }

    public String getJpaDatabaseUrl() {
        return jpaDatabaseUrl;
    }

    public void setJpaDatabaseUrl(final String jpaDatabaseUrl) {
        this.jpaDatabaseUrl = jpaDatabaseUrl;
    }

    public String getJpdaDatabaseUsername() {
        return jpdaDatabaseUsername;
    }

    public void setJpdaDatabaseUsername(final String jpdaDatabaseUsername) {
        this.jpdaDatabaseUsername = jpdaDatabaseUsername;
    }

    public String getJpdaDatabasePassword() {
        return jpdaDatabasePassword;
    }

    public void setJpdaDatabasePassword(final String jpdaDatabasePassword) {
        this.jpdaDatabasePassword = jpdaDatabasePassword;
    }

    public String getJpaDriver() {
        return jpaDriver;
    }

    public void setJpaDriver(final String jpaDriver) {
        this.jpaDriver = jpaDriver;
    }

    public String getJcacheConfigUri() {
        return jcacheConfigUri;
    }

    public void setJcacheConfigUri(final String jcacheConfigUri) {
        this.jcacheConfigUri = jcacheConfigUri;
    }

    public boolean isJcacheStoreJwtKeyOnly() {
        return jcacheStoreJwtKeyOnly;
    }

    public void setJcacheStoreJwtKeyOnly(final boolean jcacheStoreJwtKeyOnly) {
        this.jcacheStoreJwtKeyOnly = jcacheStoreJwtKeyOnly;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(final String provider) {
        this.provider = provider;
    }

    public boolean isWriteCustomErrors() {
        return writeCustomErrors;
    }

    public void setWriteCustomErrors(final boolean writeCustomErrors) {
        this.writeCustomErrors = writeCustomErrors;
    }

    public boolean isBlockUnsecureRequests() {
        return blockUnsecureRequests;
    }

    public void setBlockUnsecureRequests(final boolean blockUnsecureRequests) {
        this.blockUnsecureRequests = blockUnsecureRequests;
    }

    public boolean isWriteOptionalParameters() {
        return writeOptionalParameters;
    }

    public void setWriteOptionalParameters(final boolean writeOptionalParameters) {
        this.writeOptionalParameters = writeOptionalParameters;
    }

    public boolean isCanSupportPublicClients() {
        return canSupportPublicClients;
    }

    public void setCanSupportPublicClients(final boolean canSupportPublicClients) {
        this.canSupportPublicClients = canSupportPublicClients;
    }

    public boolean isForceClient() {
        return forceClient;
    }

    public void setForceClient(final boolean forceClient) {
        this.forceClient = forceClient;
    }

    public boolean isPartialMatchScopeValidation() {
        return partialMatchScopeValidation;
    }

    public void setPartialMatchScopeValidation(final boolean partialMatchScopeValidation) {
        this.partialMatchScopeValidation = partialMatchScopeValidation;
    }

    public boolean isJaas() {
        return jaas;
    }

    public void setJaas(final boolean jaas) {
        this.jaas = jaas;
    }

    public boolean isRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(final boolean refreshToken) {
        this.refreshToken = refreshToken;
    }

    public int getAccessTokenLifetime() {
        return accessTokenLifetime;
    }

    public void setAccessTokenLifetime(final int accessTokenLifetime) {
        this.accessTokenLifetime = accessTokenLifetime;
    }

    public long getRefreshTokenLifetime() {
        return refreshTokenLifetime;
    }

    public void setRefreshTokenLifetime(final long refreshTokenLifetime) {
        this.refreshTokenLifetime = refreshTokenLifetime;
    }

    public boolean isRecycleRefreshTokens() {
        return recycleRefreshTokens;
    }

    public void setRecycleRefreshTokens(final boolean recycleRefreshTokens) {
        this.recycleRefreshTokens = recycleRefreshTokens;
    }

    public String getDefaultScopes() {
        return defaultScopes;
    }

    public void setDefaultScopes(final String defaultScopes) {
        this.defaultScopes = defaultScopes;
    }

    public String getRequiredScopes() {
        return requiredScopes;
    }

    public void setRequiredScopes(final String requiredScopes) {
        this.requiredScopes = requiredScopes;
    }

    public String getInvisibleToClientScopes() {
        return invisibleToClientScopes;
    }

    public void setInvisibleToClientScopes(final String invisibleToClientScopes) {
        this.invisibleToClientScopes = invisibleToClientScopes;
    }

    public boolean isSupportPreauthorizedTokens() {
        return supportPreauthorizedTokens;
    }

    public void setSupportPreauthorizedTokens(final boolean supportPreauthorizedTokens) {
        this.supportPreauthorizedTokens = supportPreauthorizedTokens;
    }

    public boolean isUseJwtFormatForAccessTokens() {
        return useJwtFormatForAccessTokens;
    }

    public void setUseJwtFormatForAccessTokens(final boolean useJwtFormatForAccessTokens) {
        this.useJwtFormatForAccessTokens = useJwtFormatForAccessTokens;
    }

    public String getJwtAccessTokenClaimMap() {
        return jwtAccessTokenClaimMap;
    }

    public void setJwtAccessTokenClaimMap(final String jwtAccessTokenClaimMap) {
        this.jwtAccessTokenClaimMap = jwtAccessTokenClaimMap;
    }

    public boolean isUseAllClientScopes() {
        return useAllClientScopes;
    }

    public void setUseAllClientScopes(final boolean useAllClientScopes) {
        this.useAllClientScopes = useAllClientScopes;
    }

    public boolean isAuthorizationCodeSupport() {
        return authorizationCodeSupport;
    }

    public void setAuthorizationCodeSupport(final boolean authorizationCodeSupport) {
        this.authorizationCodeSupport = authorizationCodeSupport;
    }

    public boolean isUseRegisteredRedirectUriIfPossible() {
        return useRegisteredRedirectUriIfPossible;
    }

    public void setUseRegisteredRedirectUriIfPossible(final boolean useRegisteredRedirectUriIfPossible) {
        this.useRegisteredRedirectUriIfPossible = useRegisteredRedirectUriIfPossible;
    }

    public int getMaxDefaultSessionInterval() {
        return maxDefaultSessionInterval;
    }

    public void setMaxDefaultSessionInterval(final int maxDefaultSessionInterval) {
        this.maxDefaultSessionInterval = maxDefaultSessionInterval;
    }

    public boolean isMatchRedirectUriWithApplicationUri() {
        return matchRedirectUriWithApplicationUri;
    }

    public void setMatchRedirectUriWithApplicationUri(final boolean matchRedirectUriWithApplicationUri) {
        this.matchRedirectUriWithApplicationUri = matchRedirectUriWithApplicationUri;
    }

    public String getScopesRequiringNoConsent() {
        return scopesRequiringNoConsent;
    }

    public void setScopesRequiringNoConsent(final String scopesRequiringNoConsent) {
        this.scopesRequiringNoConsent = scopesRequiringNoConsent;
    }

    public boolean isTokenSupport() {
        return tokenSupport;
    }

    public void setTokenSupport(final boolean tokenSupport) {
        this.tokenSupport = tokenSupport;
    }

    public boolean isJcacheStoreValue() {
        return jcacheStoreValue;
    }

    public void setJcacheStoreValue(final boolean jcacheStoreValue) {
        this.jcacheStoreValue = jcacheStoreValue;
    }

    public String getJcacheLoader() {
        return jcacheLoader;
    }

    public void setJcacheLoader(final String jcacheLoader) {
        this.jcacheLoader = jcacheLoader;
    }

    public String getJcacheWriter() {
        return jcacheWriter;
    }

    public void setJcacheWriter(final String jcacheWriter) {
        this.jcacheWriter = jcacheWriter;
    }

    public boolean isJcacheStatistics() {
        return jcacheStatistics;
    }

    public void setJcacheStatistics(final boolean jcacheStatistics) {
        this.jcacheStatistics = jcacheStatistics;
    }

    public boolean isJcacheJmx() {
        return jcacheJmx;
    }

    public void setJcacheJmx(final boolean jcacheJmx) {
        this.jcacheJmx = jcacheJmx;
    }

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public void setJwtIssuer(final String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }
}
