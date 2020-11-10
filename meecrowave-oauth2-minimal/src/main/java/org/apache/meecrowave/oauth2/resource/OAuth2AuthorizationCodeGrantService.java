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

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.provider.AuthorizationCodeResponseFilter;
import org.apache.cxf.rs.security.oauth2.provider.AuthorizationRequestFilter;
import org.apache.cxf.rs.security.oauth2.services.AuthorizationCodeGrantService;
import org.apache.cxf.rs.security.oauth2.services.RedirectionBasedGrantService;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.cxf.security.SecurityContext;
import org.apache.meecrowave.oauth2.configuration.OAuth2Configurer;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Vetoed;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.security.Principal;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_HTML;

@ApplicationScoped
@Path("authorize")
public class OAuth2AuthorizationCodeGrantService {
    @Inject
    private OAuth2Configurer configurer;

    @GET
    @Produces({"application/xhtml+xml", TEXT_HTML, APPLICATION_XML, APPLICATION_JSON })
    public Response authorize(@Context final MessageContext messageContext) {
        return getDelegate(messageContext).authorize();
    }

    @POST
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Produces({"application/xhtml+xml", TEXT_HTML, APPLICATION_XML, APPLICATION_JSON})
    public Response authorizePost(final MultivaluedMap<String, String> params,
                                  @Context final MessageContext messageContext) {
        return getDelegate(messageContext).authorizePost(params);
    }

    @GET
    @Path("decision")
    public Response authorizeDecision(@Context final MessageContext messageContext) {
        return getDelegate(messageContext).authorizeDecision();
    }

    @POST
    @Path("decision")
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response authorizeDecisionForm(final MultivaluedMap<String, String> params,
                                          @Context final MessageContext messageContext) {
        return getDelegate(messageContext).authorizeDecisionForm(params);
    }

    private RedirectionBasedGrantService getDelegate(final MessageContext messageContext) {
        final LazyImpl delegate = new LazyImpl();
        delegate.setMessageContext(messageContext);
        delegate.setConfigurer(configurer);
        configurer.accept(delegate);
        return delegate;
    }

    @Vetoed
    public static class LazyImpl extends AuthorizationCodeGrantService {
        private OAuth2Configurer configurer;
        private AuthorizationRequestFilter filter;

        public void setConfigurer(final OAuth2Configurer configurer) {
            this.configurer = configurer;
        }

        public void setAuthorizationFilter(final AuthorizationRequestFilter authorizationFilter) {
            this.filter = authorizationFilter;
            super.setAuthorizationFilter(authorizationFilter);
        }


        @Override // https://issues.apache.org/jira/browse/CXF-8370
        protected Response startAuthorization(MultivaluedMap<String, String> params) {
            final SecurityContext sc;
            if (configurer.getConfiguration().isRequireUserToStartAuthorizationCodeFlow()) {
                sc = getAndValidateSecurityContext(params);
            } else {
                sc = null;
            }
            final Client client = getClient(params.getFirst(OAuthConstants.CLIENT_ID), params);
            final UserSubject userSubject = createUserSubject(sc, params);
            if (filter != null) {
                params = filter.process(params, userSubject, client);
            }
            final String redirectUri = validateRedirectUri(client, params.getFirst(OAuthConstants.REDIRECT_URI));
            return startAuthorization(params, userSubject, client, redirectUri);
        }

        @Override
        protected UserSubject createUserSubject(final SecurityContext securityContext,
                                                final MultivaluedMap<String, String> params) {
            final MessageContext mc = getMessageContext();
            final UserSubject subject = mc.getContent(UserSubject.class);
            if (subject != null) {
                return subject;
            }
            if (securityContext == null) {
                return null;
            }
            final Principal principal = securityContext.getUserPrincipal();
            return configurer.doCreateUserSubject(principal);
        }
    }
}
