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
package org.apache.meecrowave.jpa.internal;

import org.apache.meecrowave.jpa.api.EntityManagerScoped;

import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;

public class EntityManagerContext implements AlterableContext {
    private final ThreadLocal<ThreadContext> context = new ThreadLocal<>();

    public boolean enter(final boolean transactional) {
        if (context.get() != null) {
            return false;
        }
        final ThreadContext value = new ThreadContext();
        value.transactional = transactional;
        context.set(value);
        return true;
    }

    public void failed() {
        context.get().failed = true;
    }

    public void exit(final boolean created) {
        if (created) {
            try {
                context.get().exit();
            } finally {
                context.remove();
            }
        }
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return EntityManagerScoped.class;
    }

    @Override
    public void destroy(final Contextual<?> contextual) {
        context.get().destroy(contextual);
    }

    @Override
    public <T> T get(final Contextual<T> component, final CreationalContext<T> creationalContext) {
        return context.get().get(component, creationalContext);
    }

    @Override
    public <T> T get(final Contextual<T> component) {
        return context.get().get(component);
    }

    @Override
    public boolean isActive() {
        final boolean active = context.get() != null;
        if (!active) {
            context.remove();
        }
        return active;
    }

    public boolean hasFailed() {
        return context.get().failed;
    }

    public boolean isTransactional() {
        return context.get().transactional;
    }

    private static class ThreadContext implements AlterableContext {
        private final Map<Contextual<?>, BeanInstanceBag<?>> components = new HashMap<>();
        private boolean failed;
        private boolean transactional;

        @Override
        public Class<? extends Annotation> getScope() {
            return EntityManagerScoped.class;
        }

        @Override
        public <T> T get(final Contextual<T> component, final CreationalContext<T> creationalContext) {
            BeanInstanceBag<T> bag = (BeanInstanceBag<T>) components.get(component);
            if (bag == null) {
                bag = new BeanInstanceBag<>(creationalContext);
                components.put(component, bag);
            }
            if (bag.beanInstance == null) {
                bag.beanInstance = component.create(creationalContext);
            }
            return bag.beanInstance;
        }

        @Override
        public <T> T get(final Contextual<T> component) {
            final BeanInstanceBag<?> bag = components.get(component);
            return bag == null ? null : (T) bag.beanInstance;
        }

        @Override
        public void destroy(final Contextual<?> contextual) {
            final BeanInstanceBag remove = components.remove(contextual);
            ofNullable(remove).ifPresent(b -> doDestroy(contextual, b));
        }

        private <T> void doDestroy(final Contextual<T> contextual, final BeanInstanceBag<T> bag) {
            if ( bag.beanInstance !=null ) {
                // check null here because in case of missconfiguration, this can raise an NPE and hide the original exception
                contextual.destroy(bag.beanInstance, bag.beanCreationalContext);
            }
            bag.beanCreationalContext.release();
        }

        @Override
        public boolean isActive() {
            return true;
        }

        private void exit() {
            components.forEach((k, bag) -> doDestroy(k, BeanInstanceBag.class.cast(bag)));
        }
    }

    private static class BeanInstanceBag<T> implements Serializable {
        private final CreationalContext<T> beanCreationalContext;
        private T beanInstance;

        private BeanInstanceBag(final CreationalContext<T> beanCreationalContext) {
            this.beanCreationalContext = beanCreationalContext;
        }
    }
}
