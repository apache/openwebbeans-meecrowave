/**
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
package org.apache.meecrowave.cxf;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.Collection;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider;
import org.apache.johnzon.jsonb.cdi.JohnzonCdiExtension;
import org.apache.meecrowave.Meecrowave;
import org.junit.Test;

public class MeecrowaveClientLifecycleListenerTest {
    @Test
    public void autoClose() throws NoSuchFieldException, IllegalAccessException {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder()
                .randomHttpPort()
                .includePackages(MeecrowaveClientLifecycleListenerTest.class.getName())).bake()) {
            final Field jsonbs = JohnzonCdiExtension.class.getDeclaredField("jsonbs");
            jsonbs.setAccessible(true);
            final BeanManager beanManager = CDI.current().getBeanManager();
            final JohnzonCdiExtension extensionInstance = JohnzonCdiExtension.class.cast(beanManager.getContext(ApplicationScoped.class).get(
                    beanManager.resolve(beanManager.getBeans(JohnzonCdiExtension.class))));
            final Collection<?> o = Collection.class.cast(jsonbs.get(extensionInstance));

            { // ensure server is init whatever test suite we run in
                final Client client = ClientBuilder.newClient();
                get(meecrowave, client);
                client.close();
            }

            final int origin = o.size();
            final Client client = ClientBuilder.newClient();
            final JsonbJaxrsProvider<?> provider = new JsonbJaxrsProvider<>();
            client.register(provider);
            get(meecrowave, client);
            assertEquals(origin + 1, o.size());
            client.close();
            assertEquals(origin, o.size());
        }
    }

    private void get(final Meecrowave meecrowave, final Client client) {
        client.target("http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/MeecrowaveClientLifecycleListenerTest")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(Foo.class);
    }

    @Path("MeecrowaveClientLifecycleListenerTest")
    @ApplicationScoped
    public static class Endpoint {
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public Foo get() {
            return new Foo();
        }
    }

    public static class Foo {
    }
}
