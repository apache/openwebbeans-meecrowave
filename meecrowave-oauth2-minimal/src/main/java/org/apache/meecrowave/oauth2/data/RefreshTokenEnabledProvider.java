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
package org.apache.meecrowave.oauth2.data;

import org.apache.cxf.rs.security.oauth2.common.AccessTokenRegistration;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.OAuthPermission;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.grants.code.AuthorizationCodeDataProvider;
import org.apache.cxf.rs.security.oauth2.grants.code.AuthorizationCodeRegistration;
import org.apache.cxf.rs.security.oauth2.grants.code.ServerAuthorizationCodeGrant;
import org.apache.cxf.rs.security.oauth2.provider.AbstractOAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.OAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.tokens.refresh.RefreshToken;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefreshTokenEnabledProvider implements OAuthDataProvider, AuthorizationCodeDataProvider {
    private final OAuthDataProvider delegate;

    public RefreshTokenEnabledProvider(final OAuthDataProvider delegate) {
        this.delegate = delegate;
        if (AbstractOAuthDataProvider.class.isInstance(delegate)) {
            final AbstractOAuthDataProvider provider = AbstractOAuthDataProvider.class.cast(delegate);
            final Map<String, OAuthPermission> permissionMap = new HashMap<>(provider.getPermissionMap());
            permissionMap.putIfAbsent(OAuthConstants.REFRESH_TOKEN_SCOPE, new OAuthPermission(OAuthConstants.REFRESH_TOKEN_SCOPE, "allow to refresh a token"));
            provider.setPermissionMap(permissionMap);
        }
    }

    @Override
    public Client getClient(final String clientId) throws OAuthServiceException {
        return delegate.getClient(clientId);
    }

    @Override
    public ServerAccessToken createAccessToken(final AccessTokenRegistration accessToken) throws OAuthServiceException {
        if (!accessToken.getRequestedScope().contains(OAuthConstants.REFRESH_TOKEN_SCOPE)) {
            accessToken.setRequestedScope(new ArrayList<>(accessToken.getRequestedScope()));
            accessToken.getRequestedScope().add(OAuthConstants.REFRESH_TOKEN_SCOPE);
        }
        if (!accessToken.getApprovedScope().contains(OAuthConstants.REFRESH_TOKEN_SCOPE)) {
            accessToken.setApprovedScope(new ArrayList<>(accessToken.getApprovedScope()));
            accessToken.getApprovedScope().add(OAuthConstants.REFRESH_TOKEN_SCOPE);
        }
        return delegate.createAccessToken(accessToken);
    }

    @Override
    public ServerAccessToken getAccessToken(final String accessToken) throws OAuthServiceException {
        return delegate.getAccessToken(accessToken);
    }

    @Override
    public ServerAccessToken getPreauthorizedToken(final Client client, final List<String> requestedScopes, final UserSubject subject, final String grantType)
            throws OAuthServiceException {
        return delegate.getPreauthorizedToken(client, requestedScopes, subject, grantType);
    }

    @Override
    public ServerAccessToken refreshAccessToken(final Client client, final String refreshToken, final List<String> requestedScopes) throws OAuthServiceException {
        return delegate.refreshAccessToken(client, refreshToken, requestedScopes);
    }

    @Override
    public List<ServerAccessToken> getAccessTokens(final Client client, final UserSubject subject) throws OAuthServiceException {
        return delegate.getAccessTokens(client, subject);
    }

    @Override
    public List<RefreshToken> getRefreshTokens(final Client client, final UserSubject subject) throws OAuthServiceException {
        return delegate.getRefreshTokens(client, subject);
    }

    @Override
    public void revokeToken(final Client client, final String tokenId, final String tokenTypeHint) throws OAuthServiceException {
        delegate.revokeToken(client, tokenId, tokenTypeHint);
    }

    @Override
    public List<OAuthPermission> convertScopeToPermissions(final Client client, final List<String> requestedScopes) {
        return delegate.convertScopeToPermissions(client, requestedScopes);
    }

    public OAuthDataProvider getDelegate() {
        return delegate;
    }

    private AuthorizationCodeDataProvider getCodeDelegate() {
        if (!AuthorizationCodeDataProvider.class.isInstance(delegate)) {
            throw new UnsupportedOperationException("Not a AuthorizationCodeDataProvider");
        }
        return AuthorizationCodeDataProvider.class.cast(delegate);
    }

    @Override
    public ServerAuthorizationCodeGrant createCodeGrant(final AuthorizationCodeRegistration reg) throws OAuthServiceException {
        return getCodeDelegate().createCodeGrant(reg);
    }

    @Override
    public ServerAuthorizationCodeGrant removeCodeGrant(final String code) throws OAuthServiceException {
        return getCodeDelegate().removeCodeGrant(code);
    }

    @Override
    public List<ServerAuthorizationCodeGrant> getCodeGrants(final Client c, final UserSubject subject) throws OAuthServiceException {
        return getCodeDelegate().getCodeGrants(c, subject);
    }
}
