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

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.testing.Injector;
import org.apache.meecrowave.testing.MonoBase;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ContainerExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExtensionContext;

import javax.enterprise.context.spi.CreationalContext;

public class MonoMeecrowaveExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {
    private static final MonoBase BASE = new MonoBase();
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MonoMeecrowaveExtension.class.getName());

    @Override
    public void beforeAll(final ContainerExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).put(Meecrowave.Builder.class.getName(), BASE.startIfNeeded());
    }

    @Override
    public void beforeEach(final TestExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).put(CreationalContext.class.getName(), Injector.inject(context.getTestInstance()));
        Injector.injectConfig(Meecrowave.Builder.class.cast(context.getStore(NAMESPACE).get(Meecrowave.Builder.class.getName())), context.getTestInstance());
    }

    @Override
    public void afterEach(final TestExtensionContext context) throws Exception {
        CreationalContext.class.cast(context.getStore(NAMESPACE).get(CreationalContext.class.getName())).release();
    }
}
