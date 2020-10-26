/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.meecrowave.jta;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import javax.transaction.TransactionSynchronizationRegistry;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class TransactionContext implements AlterableContext, Synchronization {
    private TransactionManager transactionManager;
    private Map<Contextual<?>, BeanInstanceBag<?>> componentInstanceMap;

    public void init(final TransactionManager transactionManager, final TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionManager = transactionManager;
        this.componentInstanceMap = Map.class.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{Map.class},
                new TransactionalMapHandler(this, transactionSynchronizationRegistry)));
    }

    @Override
    public boolean isActive() {
        try {
            final int status = transactionManager.getTransaction().getStatus();
            return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK
                    || status == Status.STATUS_PREPARED || status == Status.STATUS_PREPARING
                    || status == Status.STATUS_COMMITTING || status == Status.STATUS_ROLLING_BACK
                    || status == Status.STATUS_UNKNOWN;
        } catch (final Throwable e) {
            return false;
        }
    }

    private void checkActive() {
        if (!isActive()) {
            throw new ContextNotActiveException("Context with scope annotation @" + getScope().getName() + " is not active");
        }
    }

    private <T> BeanInstanceBag<T> createContextualBag(final Contextual<T> contextual, final CreationalContext<T> creationalContext) {
        final BeanInstanceBag<T> bag = new BeanInstanceBag<>(creationalContext);
        componentInstanceMap.put(contextual, bag);
        return bag;
    }

    @Override
    public <T> T get(final Contextual<T> component) {
        checkActive();
        final BeanInstanceBag bag = componentInstanceMap.get(component);
        if (bag != null) {
            return (T) bag.beanInstance;
        }
        return null;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        checkActive();

        return getInstance(contextual, creationalContext);
    }

    private <T> T getInstance(final Contextual<T> contextual, final CreationalContext<T> creationalContext) {
        T instance;
        BeanInstanceBag<T> bag = (BeanInstanceBag<T>) componentInstanceMap.get(contextual);
        if (bag == null) {
            bag = createContextualBag(contextual, creationalContext);
        }

        instance = bag.beanInstance;
        if (instance != null) {
            return instance;
        } else {
            if (creationalContext == null) {
                return null;
            } else {
                instance = bag.create(contextual);
            }
        }

        return instance;
    }

    @Override
    public void destroy(final Contextual<?> contextual) {
        BeanInstanceBag<?> instance = componentInstanceMap.get(contextual);
        if (instance == null) {
            return;
        }

        CreationalContext<Object> cc = (CreationalContext<Object>) instance.beanCreationalContext;
        final Object beanInstance = instance.beanInstance;
        if (beanInstance != null) {
            destroyInstance((Contextual<Object>) contextual, beanInstance, cc);
        }
    }

    private <T> void destroyInstance(final Contextual<T> component, final T instance, final CreationalContext<T> creationalContext) {
        //Destroy component
        component.destroy(instance, creationalContext);
        componentInstanceMap.remove(component);
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return TransactionScoped.class;
    }

    @Override
    public void beforeCompletion() {
        new HashSet<>(componentInstanceMap.keySet()).forEach(this::destroy);
    }

    @Override
    public void afterCompletion(final int status) {
        // no-op
    }

    private static final class TransactionalMapHandler implements InvocationHandler {
        private static final String KEY = "@Transactional#meecrowave.map";
        private final TransactionSynchronizationRegistry registry;
        private final TransactionContext context;

        private TransactionalMapHandler(final TransactionContext transactionContext, final TransactionSynchronizationRegistry registry) {
            this.context = transactionContext;
            this.registry = registry;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            try {
                return method.invoke(findMap(), args);
            } catch (final InvocationTargetException ite) {
                throw ite.getCause();
            }
        }

        private Map<Contextual<?>, BeanInstanceBag<?>> findMap() {
            final Object resource = registry.getResource(KEY);
            if (resource == null) {
                final Map<Contextual<?>, BeanInstanceBag<?>> map = new HashMap<>();
                registry.putResource(KEY, map);
                registry.registerInterposedSynchronization(context);
                return map;
            }
            return Map.class.cast(resource);
        }
    }

    private static final class BeanInstanceBag<T> {
        private final CreationalContext<T> beanCreationalContext;
        private T beanInstance;

        public BeanInstanceBag(CreationalContext<T> beanCreationalContext) {
            this.beanCreationalContext = beanCreationalContext;
        }

        public T create(Contextual<T> contextual) {
            if (beanInstance == null) {
                beanInstance = contextual.create(beanCreationalContext);
            }
            return beanInstance;
        }
    }
}
