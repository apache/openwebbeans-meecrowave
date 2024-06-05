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
package org.apache.meecrowave.oauth2.resource;

import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.services.TokenRevocationService;
import org.apache.meecrowave.oauth2.configuration.OAuth2Configurer;

@Path("revoke")
@ApplicationScoped
public class OAuth2RevokeTokenService extends TokenRevocationService {
    @Inject
    private OAuth2Configurer configurer;

    @Inject
    private LayImpl delegate;

    @POST
    @Override
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Produces(APPLICATION_JSON)
    public Response handleTokenRevocation(final MultivaluedMap<String, String> params) {
        return getDelegate().handleTokenRevocation(params);
    }

    private TokenRevocationService getDelegate() {
        delegate.setMessageContext(getMessageContext());
        configurer.accept(delegate);
        return delegate;
    }

    @RequestScoped
    @Typed(LayImpl.class)
    public static class LayImpl extends TokenRevocationService implements OAuth2Application.Defaults {
        @Inject
        private OAuth2Configurer configurer;

        @POST
        @Override
        @Consumes(APPLICATION_FORM_URLENCODED)
        @Produces(APPLICATION_JSON)
        public Response handleTokenRevocation(final MultivaluedMap<String, String> params) {
            return super.handleTokenRevocation(params);
        }

        @Override // don't fail without a client
        protected Client getClientFromBasicAuthScheme(final MultivaluedMap<String, String> params) {
            final List<String> authorization = getMessageContext().getHttpHeaders().getRequestHeader("Authorization");
            if (authorization == null || authorization.isEmpty()) {
                if (!configurer.getConfiguration().isForceClient()) {
                    return DEFAULT_CLIENT;
                }
            }
            return super.getClientFromBasicAuthScheme(params);
        }
    }
}
