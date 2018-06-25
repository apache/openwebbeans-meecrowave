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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XHTML_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.cxf.rs.security.oauth2.services.AuthorizationCodeGrantService;
import org.apache.cxf.rs.security.oauth2.services.RedirectionBasedGrantService;
import org.apache.meecrowave.oauth2.configuration.OAuth2Configurer;

@RequestScoped
@Path("authorize")
public class OAuth2AuthorizationCodeGrantService extends AuthorizationCodeGrantService {

    @Inject
    private LazyImpl delegate;

    @Inject
    private OAuth2Configurer configurer;

    @Override
    @GET
    @Produces({ APPLICATION_XHTML_XML, TEXT_HTML, APPLICATION_XML, APPLICATION_JSON })
    public Response authorize() {
        return getDelegate().authorize();
    }

    @Override
    @GET
    @Path("decision")
    public Response authorizeDecision() {
        return getDelegate().authorizeDecision();
    }

    @Override
    @POST
    @Path("decision")
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response authorizeDecisionForm(MultivaluedMap<String, String> params) {
        return getDelegate().authorizeDecisionForm(params);
    }

    private RedirectionBasedGrantService getDelegate() {
        delegate.setMessageContext(getMessageContext());
        configurer.accept(delegate);
        return delegate;
    }

    @RequestScoped
    @Typed(LazyImpl.class)
    static class LazyImpl extends AuthorizationCodeGrantService {
    }
}
