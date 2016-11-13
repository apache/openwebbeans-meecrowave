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
package org.apache.meecrowave.tomcat;

import org.apache.tomcat.InstanceManager;
import org.apache.webbeans.servlet.WebBeansConfigurationListener;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.naming.NamingException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.ofNullable;

public class CDIInstanceManager implements InstanceManager {
    private final Map<Object, Runnable> destroyables = new ConcurrentHashMap<>();

    @Override
    public Object newInstance(final Class<?> clazz) throws IllegalAccessException, InvocationTargetException,
            NamingException, InstantiationException {
        final Object newInstance = clazz.newInstance();
        newInstance(newInstance);
        return newInstance;
    }

    @Override
    public Object newInstance(final String className) throws IllegalAccessException, InvocationTargetException,
            NamingException, InstantiationException, ClassNotFoundException {
        return newInstance(className, Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Object newInstance(final String fqcn, final ClassLoader classLoader) throws IllegalAccessException,
            InvocationTargetException, NamingException, InstantiationException, ClassNotFoundException {
        return newInstance(classLoader.loadClass(fqcn));
    }

    @Override
    public void newInstance(final Object o) throws IllegalAccessException, InvocationTargetException, NamingException {
        if (WebBeansConfigurationListener.class.isInstance(o) || o.getClass().getName().startsWith("org.apache.catalina.servlets.")) {
            return;
        }

        final BeanManager bm = CDI.current().getBeanManager();
        final AnnotatedType<?> annotatedType = bm.createAnnotatedType(o.getClass());
        final InjectionTarget injectionTarget = bm.createInjectionTarget(annotatedType);
        final CreationalContext<Object> creationalContext = bm.createCreationalContext(null);
        injectionTarget.inject(o, creationalContext);
        try {
            injectionTarget.postConstruct(o);
        } catch (final RuntimeException e) {
            creationalContext.release();
            throw e;
        }
        destroyables.put(o, () -> {
            try {
                injectionTarget.preDestroy(o);
            } finally {
                creationalContext.release();
            }
        });
    }

    @Override
    public void destroyInstance(final Object o) throws IllegalAccessException, InvocationTargetException {
        ofNullable(destroyables.remove(o)).ifPresent(Runnable::run);
    }
}
