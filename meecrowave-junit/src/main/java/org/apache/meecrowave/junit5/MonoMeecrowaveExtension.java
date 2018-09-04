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

import static java.util.Optional.ofNullable;

import javax.enterprise.context.spi.CreationalContext;

import org.apache.meecrowave.testing.Injector;
import org.apache.meecrowave.testing.MonoBase;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MonoMeecrowaveExtension extends BaseLifecycle
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {
    private static final MonoBase BASE = new MonoBase();
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MonoMeecrowaveExtension.class.getName());

    @Override
    public void beforeAll(final ExtensionContext context) {
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(MonoBase.Instance.class, BASE.startIfNeeded());
        if (isPerClass(context)) {
            doInject(context);
            store.put(LifecyleState.class, onInjection(context, null));
        }
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        if (!isPerClass(context)) {
            doInject(context);
            store.put(
                LifecyleState.class,
                onInjection(context.getParent().orElse(context), store.get(LifecyleState.class, LifecyleState.class)));
        }
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        if (!isPerClass(context)) {
            doRelease(context);
        }
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        ofNullable(context.getStore(NAMESPACE)
                .get(LifecyleState.class, LifecyleState.class))
                .ifPresent(s -> s.afterLastTest(context));
        if (isPerClass(context)) {
            doRelease(context);
        }
    }

    private void doRelease(final ExtensionContext context) {
        context.getStore(NAMESPACE).get(CreationalContext.class, CreationalContext.class).release();
    }

    private void doInject(final ExtensionContext context) {
        context.getStore(NAMESPACE).put(CreationalContext.class, Injector.inject(context.getTestInstance().orElse(null)));
        Injector.injectConfig(
                context.getStore(NAMESPACE).get(MonoBase.Instance.class, MonoBase.Instance.class).getConfiguration(),
                context.getTestInstance().orElse(null));
    }
}
