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
import org.apache.cxf.rs.security.oauth2.provider.OAuthJSONProvider;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.oauth2.configuration.OAuth2Options;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@Dependent
@ApplicationPath("oauth2")
public class OAuth2Application extends Application {
    @Inject
    private Meecrowave.Builder builder;

    private Set<Class<?>> classes;

    @Override
    public Set<Class<?>> getClasses() {
        return classes != null ? classes : (classes = doGetClasses());
    }

    private Set<Class<?>> doGetClasses() {
        final Set<Class<?>> classes = new HashSet<>(singletonList(OAuthJSONProvider.class));
        if (builder.getExtension(OAuth2Options.class).isAuthorizationCodeSupport()) {
            classes.add(OAuth2AuthorizationCodeGrantService.class);
        }
        if (builder.getExtension(OAuth2Options.class).isTokenSupport()) {
            classes.addAll(asList(OAuth2TokenService.class, OAuth2RevokeTokenService.class));
        }
        return classes;
    }

    interface Defaults {
        Client DEFAULT_CLIENT = new Client("__default", "", true);
    }
}
