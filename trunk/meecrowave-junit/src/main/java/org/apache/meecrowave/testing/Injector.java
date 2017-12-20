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
package org.apache.meecrowave.testing;

import org.apache.meecrowave.Meecrowave;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionTarget;
import java.util.stream.Stream;

public final class Injector {
    private Injector() {
        // no-op
    }

    public static CreationalContext<?> inject(final Object testInstance) {
        if (testInstance == null) {
            return null;
        }
        final BeanManager bm = CDI.current().getBeanManager();
        final AnnotatedType<?> annotatedType = bm.createAnnotatedType(testInstance.getClass());
        final InjectionTarget injectionTarget = bm.createInjectionTarget(annotatedType);
        final CreationalContext<?> creationalContext = bm.createCreationalContext(null);
        injectionTarget.inject(testInstance, creationalContext);
        return creationalContext;
    }

    public static void injectConfig(final Meecrowave.Builder config, final Object test) {
        if (test == null) {
            return;
        }
        final Class<?> aClass = test.getClass();
        Stream.of(aClass.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(ConfigurationInject.class))
                .forEach(f -> {
                    if (!f.isAccessible()) {
                        f.setAccessible(true);
                    }
                    try {
                        f.set(test, config);
                    } catch (final IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }
}
