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
package org.apache.meecrowave.junit5;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.enterprise.context.spi.CreationalContext;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.testing.Injector;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.spi.ContextsService;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MeecrowaveExtension extends BaseLifecycle
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MeecrowaveExtension.class.getName());

    @Override
    public void beforeAll(final ExtensionContext context) {
        final Meecrowave.Builder builder = new Meecrowave.Builder();
        final Optional<MeecrowaveConfig> meecrowaveConfig = context.getElement().map(e -> e.getAnnotation(MeecrowaveConfig.class));
        final String ctx;
        if (meecrowaveConfig.isPresent()) {
            final MeecrowaveConfig config = meecrowaveConfig.get();
            ctx = config.context();

            for (final Method method : MeecrowaveConfig.class.getMethods()) {
                if (MeecrowaveConfig.class != method.getDeclaringClass()) {
                    continue;
                }

                try {
                    final Object value = method.invoke(config);

                    final Field configField = Meecrowave.Builder.class.getDeclaredField(method.getName());
                    if (!configField.isAccessible()) {
                        configField.setAccessible(true);
                    }

                    if (value != null && (!String.class.isInstance(value) || !value.toString().isEmpty())) {
                        if (!configField.isAccessible()) {
                            configField.setAccessible(true);
                        }
                        configField.set(builder, File.class == configField.getType() ? /*we use string instead */new File(value.toString()) : value);
                    }
                } catch (final NoSuchFieldException nsfe) {
                    // ignored
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            if (builder.getHttpPort() < 0) {
                builder.randomHttpPort();
            }
        } else {
            ctx = "";
        }
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        final Meecrowave meecrowave = new Meecrowave(builder);
        store.put(Meecrowave.class, meecrowave);
        store.put(Meecrowave.Builder.class, builder);
        meecrowave.bake(ctx);

        if (isPerClass(context)) {
            doInject(context);
            store.put(LifecyleState.class, onInjection(context, null));
        }
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        ofNullable(store.get(LifecyleState.class, LifecyleState.class))
                .ifPresent(s -> s.afterLastTest(context));
        if (isPerClass(context)) {
            store.get(Meecrowave.class, Meecrowave.class).close();
        }
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        if (!isPerClass(context)) {
            doInject(context);
            final ExtensionContext.Store store = context.getParent().orElse(context).getStore(NAMESPACE);
            store.put(LifecyleState.class, onInjection(context, store.get(LifecyleState.class, LifecyleState.class)));
        }
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        if (!isPerClass(context)) {
            doRelease(context);
        }
    }

    private void doRelease(final ExtensionContext context) {
        ofNullable(context.getStore(NAMESPACE).get(CreationalContext.class, CreationalContext.class))
                .ifPresent(CreationalContext::release);
        getScopes(context).ifPresent(scopes -> {
            final ContextsService contextsService = WebBeansContext.currentInstance().getContextsService();
            final List<Class<? extends Annotation>> list = new ArrayList<>(asList(scopes));
            Collections.reverse(list);
            list.forEach(s -> contextsService.endContext(s, null));
        });
    }

    private void doInject(final ExtensionContext context) {
        getScopes(context).ifPresent(scopes -> {
            final ContextsService contextsService = WebBeansContext.currentInstance().getContextsService();
            Stream.of(scopes).forEach(s -> contextsService.startContext(s, null));
        });
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(CreationalContext.class, Injector.inject(context.getTestInstance().orElse(null)));
        Injector.injectConfig(
                store.get(Meecrowave.Builder.class, Meecrowave.Builder.class),
                context.getTestInstance().orElse(null));
    }

    private Optional<Class<? extends Annotation>[]> getScopes(final ExtensionContext context) {
        return context.getElement()
                      .map(e -> e.getAnnotation(MeecrowaveConfig.class))
                      .map(MeecrowaveConfig::scopes)
                      .filter(s -> s.length > 0);
    }
}
