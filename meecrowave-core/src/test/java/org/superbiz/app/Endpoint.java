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
package org.superbiz.app;

import static org.junit.Assert.assertNotNull;

import java.security.Principal;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("test")
@ApplicationScoped
public class Endpoint {
    @Inject
    private Injectable injectable;

    @Inject
    private Principal pcp;

    @Inject
    private HttpServletRequest request;

    @Inject
    private BeanManager bm;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String simple(@QueryParam("checkcustom") final boolean query) {
        return Boolean.parseBoolean(injectable.injected()) ? "simple" + query : "fail";
    }

    @GET
    @Path("json")
    @Produces(MediaType.APPLICATION_JSON)
    public Simple json() {
        return new Simple("test");
    }

    @GET
    @Path("principal")
    @Produces(MediaType.TEXT_PLAIN)
    public String principal() {
        return request.getUserPrincipal().getClass().getName() + "_" + request.getUserPrincipal().getName() + "  " +
                pcp.getClass().getName().replaceAll("\\$\\$OwbNormalScopeProxy[0-9]+", "") + "_" + pcp.getName();
    }

    @GET
    @Path("load/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String load(@PathParam("name") final boolean ds) {
        try {
            final ClassLoader loader = Thread.currentThread().getContextClassLoader(); // if sharedlib is set should be MeecrowaveClassloader
            if (ds) {
                final Class<?> ce = loader.loadClass("org.apache.deltaspike.core.impl.config.ConfigurationExtension");
                final Object extensionBeanInstance = bm.getReference(bm.resolve(bm.getBeans(ce)), ce, bm.createCreationalContext(null));
                assertNotNull(extensionBeanInstance);
            }
            return loader.loadClass("org.apache.deltaspike.core.api.config.ConfigProperty").getName();
        } catch (final ClassNotFoundException cnfe) {
            return "oops";
        }
    }

    public static class Simple {
        private String name;

        public Simple() {
            // no-op
        }

        public Simple(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
