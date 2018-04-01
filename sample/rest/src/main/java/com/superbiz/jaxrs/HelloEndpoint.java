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
package com.superbiz.jaxrs;

import com.superbiz.configuration.Defaults;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import static java.util.Optional.ofNullable;

@Path("hello")
@ApplicationScoped
public class HelloEndpoint {
    @Inject
    private Defaults defaults;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Hello sayHi(@QueryParam("name") final String name) {
        return new Hello(ofNullable(name)
                .orElse(defaults.getName()));
    }

    public static class Hello {
        private String name;

        public Hello() {
            // no-op
        }

        private Hello(final String name) {
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
