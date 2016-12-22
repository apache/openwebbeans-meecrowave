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

import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public class JtaExtension implements Extension {
    private TransactionContext context;
    private boolean hasManager;
    private boolean hasRegistry;
    private final JtaConfig config = new JtaConfig();

    void register(@Observes final BeforeBeanDiscovery beforeBeanDiscovery, final BeanManager beanManager) {
        Stream.of(
                MandatoryInterceptor.class, NeverInterceptor.class,
                NotSupportedInterceptor.class, RequiredInterceptor.class,
                RequiredNewInterceptor.class, SupportsInterceptor.class)
                .forEach(c -> beforeBeanDiscovery.addAnnotatedType(beanManager.createAnnotatedType(c)));
    }

    void findJtaComponents(@Observes final ProcessBean<?> bean) {
        if (!hasManager && bean.getBean().getTypes().contains(TransactionManager.class)) {
            hasManager = true;
        }
        if (!hasRegistry && bean.getBean().getTypes().contains(TransactionSynchronizationRegistry.class)) {
            hasRegistry = true;
        }
    }

    void addContextAndBeans(@Observes final AfterBeanDiscovery afterBeanDiscovery, final BeanManager bm) {
        context = new TransactionContext();
        afterBeanDiscovery.addContext(context);

        if (!hasManager && !hasRegistry) {
            try {
                final GeronimoTransactionManager mgr = new GeronimoTransactionManager();
                afterBeanDiscovery.addBean(new JtaBean(mgr));
            } catch (final XAException e) {
                throw new IllegalStateException(e);
            }
            hasManager = true;
            hasRegistry = true;
        }

        afterBeanDiscovery.addBean(new JtaConfigBean(config));
    }

    void init(@Observes final AfterDeploymentValidation afterDeploymentValidation, final BeanManager bm) {
        if (!hasRegistry && hasManager) {
            afterDeploymentValidation.addDeploymentProblem(new IllegalStateException("You should produce a TransactionManager and TransactionSynchronizationRegistry"));
            return;
        }
        final TransactionManager manager = TransactionManager.class.cast(
                bm.getReference(bm.resolve(bm.getBeans(TransactionManager.class)), TransactionManager.class, bm.createCreationalContext(null)));
        final TransactionSynchronizationRegistry registry = TransactionSynchronizationRegistry.class.isInstance(manager) ?
                TransactionSynchronizationRegistry.class.cast(manager) :
                TransactionSynchronizationRegistry.class.cast(bm.getReference(bm.resolve(bm.getBeans(TransactionSynchronizationRegistry.class)),
                        TransactionSynchronizationRegistry.class, bm.createCreationalContext(null)));
        context.init(manager, registry);

        try {
            final Class<?> builder = Thread.currentThread().getContextClassLoader().loadClass("org.apache.meecrowave.Meecrowave$Builder");
            final JtaConfig ext = JtaConfig.class.cast(builder.getMethod("getExtension", Class.class).invoke(
                    bm.getReference(bm.resolve(bm.getBeans(builder)), builder, bm.createCreationalContext(null)), JtaConfig.class));
            config.handleExceptionOnlyForClient = ext.handleExceptionOnlyForClient;
        } catch (final Exception e) {
            config.handleExceptionOnlyForClient = Boolean.getBoolean("meecrowave.jta.handleExceptionOnlyForClient");
        }
    }

    private static class JtaBean implements Bean<TransactionManager> {
        private final GeronimoTransactionManager manager;
        private final Set<Type> types = new HashSet<>(asList(TransactionManager.class, TransactionSynchronizationRegistry.class, Object.class));
        private final Set<Annotation> qualifiers = new HashSet<>(asList(DefaultLiteral.INSTANCE, AnyLiteral.INSTANCE));

        private JtaBean(final GeronimoTransactionManager mgr) {
            this.manager = mgr;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public Class<?> getBeanClass() {
            return GeronimoTransactionManager.class;
        }

        @Override
        public boolean isNullable() {
            return false;
        }

        @Override
        public TransactionManager create(final CreationalContext<TransactionManager> context) {
            return manager;
        }

        @Override
        public void destroy(final TransactionManager instance, final CreationalContext<TransactionManager> context) {
            // no-op
        }

        @Override
        public Set<Type> getTypes() {
            return types;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return ApplicationScoped.class;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }

    private static class JtaConfigBean implements Bean<JtaConfig> {
        private final JtaConfig config;
        private final Set<Type> types = new HashSet<>(asList(JtaConfig.class, Object.class));
        private final Set<Annotation> qualifiers = new HashSet<>(asList(DefaultLiteral.INSTANCE, AnyLiteral.INSTANCE));

        private JtaConfigBean(final JtaConfig value) {
            this.config = value;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public Class<?> getBeanClass() {
            return JtaConfig.class;
        }

        @Override
        public boolean isNullable() {
            return false;
        }

        @Override
        public JtaConfig create(final CreationalContext<JtaConfig> context) {
            return config;
        }

        @Override
        public void destroy(final JtaConfig instance, final CreationalContext<JtaConfig> context) {
            // no-op
        }

        @Override
        public Set<Type> getTypes() {
            return types;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return ApplicationScoped.class;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }
}
