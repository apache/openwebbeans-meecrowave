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
package org.apache.microwave.cxf;

import org.apache.cxf.cdi.CXFCdiServlet;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;
import org.apache.cxf.transport.ChainInitiationObserver;
import org.apache.johnzon.jaxrs.DelegateProvider;
import org.apache.johnzon.jaxrs.JohnzonProvider;
import org.apache.johnzon.jaxrs.JsrProvider;
import org.apache.microwave.Microwave;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

public class CxfCdiAutoSetup implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) throws ServletException {
        final Microwave.Builder builder = Microwave.Builder.class.cast(ctx.getAttribute("microwave.configuration"));
        final ServletRegistration.Dynamic jaxrs = ctx.addServlet("cxf-cdi", new CXFCdiServlet() {
            @Override
            protected void loadBus(final ServletConfig servletConfig) {
                super.loadBus(servletConfig);
                if (!"true".equalsIgnoreCase(builder.properties().getProperty("microwave.jaxrs.providers.setup", "true"))) {
                    return;
                }

                final List<DelegateProvider<?>> providers = asList(new JohnzonProvider<>(), new JsrProvider());

                // client
                if (bus.getProperty("org.apache.cxf.jaxrs.bus.providers") == null) {
                    bus.setProperty("skip.default.json.provider.registration", "true");
                    bus.setProperty("org.apache.cxf.jaxrs.bus.providers", providers);
                }

                // server
                getDestinationRegistryFromBus().getDestinations()
                        .forEach(d -> {
                            final ChainInitiationObserver observer = ChainInitiationObserver.class.cast(d.getMessageObserver());
                            final ServerProviderFactory providerFactory = ServerProviderFactory.class.cast(observer.getEndpoint().get(ServerProviderFactory.class.getName()));
                            providerFactory.setUserProviders(providers);
                        });
            }
        });
        jaxrs.setLoadOnStartup(1);
        jaxrs.setAsyncSupported(true);
        jaxrs.addMapping(builder.jaxrsMapping());
    }
}
