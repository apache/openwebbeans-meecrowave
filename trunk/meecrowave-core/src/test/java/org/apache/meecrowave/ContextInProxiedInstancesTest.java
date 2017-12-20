/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.meecrowave;

import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.junit.Test;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class ContextInProxiedInstancesTest {
    @Test
    public void fields() throws IOException {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .includePackages(ContextInProxiedInstancesTest.class.getName())).bake()) {
            // proxies can use @Context
            try (final InputStream stream = new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/app").openStream()) {
                assertEquals("app", Streams.asString(stream, "UTF-8"));
            }
            try (final InputStream stream = new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/req").openStream()) {
                assertEquals("req", Streams.asString(stream, "UTF-8"));
            }
            // not proxied can also
            try (final InputStream stream = new URL("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/dep").openStream()) {
                assertEquals("dep", Streams.asString(stream, "UTF-8"));
            }
            assertEquals(Dep.class, CDI.current().select(Dep.class).get().getClass()); // ensure it is not proxied but injection works (thanks CXF)
        }
    }

    @Path("app")
    @ApplicationScoped
    public static class App {
        @Context
        private UriInfo uri;

        public void init(@Observes @Initialized(ApplicationScoped.class) final ServletContext sc) {
            // init without a Message
        }

        @GET
        public String get() {
            return uri.getPath();
        }
    }

    @Path("req")
    @RequestScoped
    public static class Req {
        @Context
        private UriInfo uri;

        @GET
        public String get() {
            return uri.getPath();
        }
    }

    @Path("dep")
    @Dependent
    public static class Dep {
        @Context
        private UriInfo uri;

        @GET
        public String get() {
            return uri.getPath();
        }

        @Produces
        @RequestScoped
        public MyRestApi createMyApi() {
            return new MyRestApi() {
                @Override
                public String get() {
                    return null;
                }

                @Override
                public void close() throws Exception {

                }
            };
        }
    }

    @Path("myapi")
    public  interface MyRestApi extends Serializable, AutoCloseable {
        @GET
        String get();
    }


}
