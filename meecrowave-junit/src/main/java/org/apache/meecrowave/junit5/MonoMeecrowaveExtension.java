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

import javax.enterprise.context.spi.CreationalContext;

import org.apache.meecrowave.testing.Injector;
import org.apache.meecrowave.testing.MonoBase;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MonoMeecrowaveExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {
    private static final MonoBase BASE = new MonoBase();
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MonoMeecrowaveExtension.class.getName());

    @Override
    public void beforeAll(final ExtensionContext context) {
        context.getStore(NAMESPACE).put(MonoBase.Instance.class.getName(), BASE.startIfNeeded());
        if (isPerClass(context)) {
            doInject(context);
        }
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        if (!isPerClass(context)) {
            doInject(context);
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
        if (isPerClass(context)) {
            doRelease(context);
        }
    }

    private void doRelease(ExtensionContext context) {
        CreationalContext.class.cast(context.getStore(NAMESPACE).get(CreationalContext.class.getName())).release();
    }

    private void doInject(final ExtensionContext context) {
        context.getStore(NAMESPACE).put(CreationalContext.class.getName(), Injector.inject(context.getTestInstance().orElse(null)));
        Injector.injectConfig(
                MonoBase.Instance.class.cast(context.getStore(NAMESPACE).get(MonoBase.Instance.class.getName())).getConfiguration(),
                context.getTestInstance().orElse(null));
    }

    private Boolean isPerClass(final ExtensionContext context) {
        return context.getTestInstanceLifecycle()
                .map(it -> it.equals(TestInstance.Lifecycle.PER_CLASS))
                .orElse(false);
    }
}
