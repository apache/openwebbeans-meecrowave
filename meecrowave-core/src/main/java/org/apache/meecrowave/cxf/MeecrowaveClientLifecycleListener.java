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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

import javax.enterprise.inject.spi.CDI;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientLifeCycleListener;
import org.apache.cxf.jaxrs.client.ClientProviderFactory;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider;
import org.apache.johnzon.jsonb.JohnzonJsonb;
import org.apache.johnzon.jsonb.cdi.JohnzonCdiExtension;
import org.apache.meecrowave.logging.tomcat.LogFacade;

// ensure client providers don't leak if the user relies on johnzon JsonbJaxrsProvider
// in a scope < application (see JohnzonCdiExtension integration)
public class MeecrowaveClientLifecycleListener implements ClientLifeCycleListener {
    private final Method getReadersWriters;
    private final Field delegate;
    private final Field instance;

    public MeecrowaveClientLifecycleListener() {
        try {
            getReadersWriters = ProviderFactory.class.getDeclaredMethod("getReadersWriters");
            getReadersWriters.setAccessible(true);
        } catch (final NoSuchMethodException e) {
            throw new IllegalArgumentException("Incompatible cxf version detected", e);
        }
        try {
            delegate = JsonbJaxrsProvider.class.getDeclaredField("delegate");
            delegate.setAccessible(true);
        } catch (final NoSuchFieldException e) {
            throw new IllegalArgumentException("Incompatible johnzon version detected", e);
        }
        try {
            instance = JsonbJaxrsProvider.class.getClassLoader().loadClass("org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider$ProvidedInstance").getDeclaredField("instance");
            instance.setAccessible(true);
        } catch (final ClassNotFoundException | NoSuchFieldException e) {
            throw new IllegalArgumentException("Incompatible johnzon version detected", e);
        }
    }

    @Override
    public void clientCreated(final Client client) {
        // no-op
    }

    @Override
    public void clientDestroyed(final Client client) {
        try {
            final ClientProviderFactory cpf = ClientProviderFactory.class.cast(
                    client.getEndpoint().get(ClientProviderFactory.class.getName()));
            final Collection<Object> invoke = Collection.class.cast(getReadersWriters.invoke(cpf));
            invoke.stream()
                    .map(p -> ProviderInfo.class.isInstance(p) ? ProviderInfo.class.cast(p).getProvider() : p)
                    .filter(p -> !ConfigurableBus.ConfiguredJsonbJaxrsProvider.class.isInstance(p) && JsonbJaxrsProvider.class.isInstance(p))
                    .map(JsonbJaxrsProvider.class::cast)
                    .map(p -> {
                        try {
                            return instance.get(delegate.get(p));
                        } catch (final IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(JohnzonJsonb.class::isInstance)
                    .map(JohnzonJsonb.class::cast)
                    .distinct()
                    .forEach(jsonb -> CDI.current().select(JohnzonCdiExtension.class).get().untrack(jsonb));
        } catch (final RuntimeException re) {
            new LogFacade(MeecrowaveClientLifecycleListener.class.getName())
                    .debug(re.getMessage(), re);
        } catch (final Exception re) { // reflection etc which shouldn't fail
            new LogFacade(MeecrowaveClientLifecycleListener.class.getName())
                    .error(re.getMessage(), re);
        }
    }
}
