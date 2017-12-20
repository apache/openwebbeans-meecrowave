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

import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.services.AccessTokenService;
import org.apache.meecrowave.oauth2.configuration.OAuth2Configurer;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("token")
@RequestScoped
public class OAuth2TokenService extends AccessTokenService implements OAuth2Application.Defaults {
    @Inject
    private OAuth2Configurer configurer;

    @PostConstruct
    private void init() {
        configurer.accept(this);
    }

    @POST
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Produces(APPLICATION_JSON)
    public Response handleTokenRequest(final MultivaluedMap<String, String> params) {
        return super.handleTokenRequest(params);
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
