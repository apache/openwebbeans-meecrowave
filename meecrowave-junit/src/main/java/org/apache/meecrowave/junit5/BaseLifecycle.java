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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;

abstract class BaseLifecycle {
    boolean isPerClass(final ExtensionContext context) {
        return context.getTestInstanceLifecycle()
                .map(it -> it.equals(TestInstance.Lifecycle.PER_CLASS))
                .orElse(false);
    }

    LifecyleState onInjection(final ExtensionContext context, final LifecyleState state) {
        if (state != null && state.injected) {
            return state;
        }
        return context.getTestInstance()
                .map(test -> invoke(test, AfterFirstInjection.class))
                .orElse(state);
    }

    private static LifecyleState invoke(final Object test, final Class<? extends Annotation> marker) {
        Class<?> type = test.getClass();
        while (type != Object.class) {
            Stream.of(type.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(marker))
                    .forEach(method -> {
                        if (!method.isAccessible()) {
                            method.setAccessible(true);
                        }
                        try {
                            method.invoke(test);
                        } catch (final IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        } catch (final InvocationTargetException ite) {
                            throw new IllegalStateException(ite.getTargetException());
                        }
                    });
            type = type.getSuperclass();
        }
        return new LifecyleState(true, test);
    }

    static class LifecyleState {
        private final boolean injected;
        private final Object instance;

        LifecyleState(final boolean injected, final Object instance) {
            this.injected = injected;
            this.instance = instance;
        }

        void afterLastTest(final ExtensionContext context) {
            invoke(instance, AfterLastTest.class);
        }
    }
}
