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

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.jpa.api.Jpa;
import org.apache.meecrowave.jpa.api.PersistenceUnitInfoBuilder;
import org.apache.meecrowave.jpa.api.Unit;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import javax.persistence.SynchronizationType;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.servlet.ServletContext;
import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * This extension is responsible to create entitymanagers, entitymanagerfactories
 * and link it to CDI named units.
 * <p>
 * Note: we don't reuse @DataSourceDefinition which is all but defined (pooling, datasource configs are a mess).
 */
public class JpaExtension implements Extension {
    private final EntityManagerContext entityManagerContext = new EntityManagerContext();
    private final List<String> jpaClasses = new ArrayList<>();
    private final Map<UnitKey, EntityManagerBean> entityManagerBeans = new HashMap<>();
    private final Collection<Bean<?>> unitBuilders = new ArrayList<>(2);

    void addInternals(@Observes final BeforeBeanDiscovery bbd, final BeanManager bm) {
        Stream.of(JpaTransactionInterceptor.class, JpaNoTransactionInterceptor.class)
                .forEach(interceptor -> bbd.addAnnotatedType(bm.createAnnotatedType(interceptor)));
    }

    <T> void addJpaToEmConsumers(@Observes @WithAnnotations(Unit.class) final ProcessAnnotatedType<T> pat) {
        if (pat.getAnnotatedType().isAnnotationPresent(Jpa.class)) {
            return;
        }
        pat.setAnnotatedType(new AutoJpaAnnotationType<T>(pat.getAnnotatedType()));
    }

    void collectEntityManagerInjections(@Observes final ProcessBean<?> bean) {
        final Map<UnitKey, EntityManagerBean> beans = bean.getBean().getInjectionPoints().stream()
                .filter(i -> i.getAnnotated().isAnnotationPresent(Unit.class))
                .map(i -> i.getAnnotated().getAnnotation(Unit.class))
                .collect(toMap(u -> new UnitKey(u.name(), u.synchronization()), unit -> new EntityManagerBean(entityManagerContext, unit.name(), unit.synchronization())));
        entityManagerBeans.putAll(beans);
    }

    void collectEntityManagers(@Observes final ProcessBean<?> bean) {
        if (bean.getBean().getTypes().contains(PersistenceUnitInfoBuilder.class)) {
            unitBuilders.add(bean.getBean());
        }
    }

    void collectEntities(@Observes @WithAnnotations({Entity.class, MappedSuperclass.class, Embeddable.class}) final ProcessAnnotatedType<?> jpa) {
        jpaClasses.add(jpa.getAnnotatedType().getJavaClass().getName());
    }

    void addBeans(@Observes final AfterBeanDiscovery afb, final BeanManager bm) {
        afb.addContext(entityManagerContext);
        entityManagerBeans.forEach((n, b) -> afb.addBean(b));
    }

    void initBeans(@Observes final AfterDeploymentValidation adv, final BeanManager bm) {
        if (entityManagerBeans.isEmpty()) {
            return;
        }

        // only not portable part is this config read, could be optional
        final ServletContext sc = ServletContext.class.cast(bm.getReference(bm.resolve(bm.getBeans(ServletContext.class)), ServletContext.class, bm.createCreationalContext(null)));
        final Meecrowave.Builder config = Meecrowave.Builder.class.cast(sc.getAttribute("meecrowave.configuration"));
        final Map<String, String> props = new HashMap<>();
        if (config != null) {
            ofNullable(config.getProperties()).ifPresent(p -> p.stringPropertyNames().stream()
                    .filter(k -> k.startsWith("jpa.property."))
                    .forEach(k -> props.put(k.substring("jpa.property.".length()), p.getProperty(k))));
        }

        final Map<String, PersistenceUnitInfo> infoIndex = unitBuilders.stream()
                .map(bean -> {
                    final CreationalContext<?> cc = bm.createCreationalContext(null);
                    try {
                        final Bean<?> resolvedBean = bm.resolve(bm.getBeans(
                                PersistenceUnitInfoBuilder.class,
                                bean.getQualifiers().toArray(new Annotation[bean.getQualifiers().size()])));
                        final PersistenceUnitInfoBuilder builder = PersistenceUnitInfoBuilder.class.cast(
                                bm.getReference(resolvedBean, PersistenceUnitInfoBuilder.class, cc));
                        if (builder.getManagedClasses().isEmpty()) {
                            builder.setManagedClassNames(jpaClasses).setExcludeUnlistedClasses(true);
                        }
                        props.forEach(builder::addProperty);
                        return builder.toInfo();
                    } finally {
                        cc.release();
                    }
                }).collect(toMap(PersistenceUnitInfo::getPersistenceUnitName, identity()));

        entityManagerBeans.forEach((k, e) -> {
            PersistenceUnitInfo info = infoIndex.get(k.unitName);
            if (info == null) {
                info = tryCreateDefaultPersistenceUnit(k.unitName, bm, props);
            }
            if (info == null) { // not valid
                adv.addDeploymentProblem(new IllegalArgumentException("Didn't find any PersistenceUnitInfoBuilder for " + k));
            } else {
                e.init(info, bm);
            }
        });
    }

    private PersistenceUnitInfo tryCreateDefaultPersistenceUnit(final String unitName, final BeanManager bm, final Map<String, String> props) {
        final Set<Bean<?>> beans = bm.getBeans(DataSource.class);
        final Bean<?> bean = bm.resolve(beans);
        if (bean == null || !bm.isNormalScope(bean.getScope())) {
            return null;
        }

        final DataSource ds = DataSource.class.cast(bm.getReference(bean, DataSource.class, bm.createCreationalContext(null)));

        final PersistenceUnitInfoBuilder builder = new PersistenceUnitInfoBuilder()
                .setManagedClassNames(jpaClasses)
                .setExcludeUnlistedClasses(true)
                .setUnitName(unitName)
                .setDataSource(ds);

        props.forEach(builder::addProperty);

        return builder.toInfo();
    }

    public EntityManagerContext getEntityManagerContext() {
        return entityManagerContext;
    }

    private static class UnitKey {
        private final String unitName;
        private final SynchronizationType synchronizationType;
        private final int hash;

        private UnitKey(final String unitName, final SynchronizationType synchronizationType) {
            this.unitName = unitName;
            this.synchronizationType = synchronizationType;
            this.hash = 31 * unitName.hashCode() + synchronizationType.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final UnitKey unitKey = UnitKey.class.cast(o);
            return unitName.equals(unitKey.unitName) && synchronizationType == unitKey.synchronizationType;

        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "UnitKey{" +
                    "unitName='" + unitName + '\'' +
                    ", synchronizationType=" + synchronizationType +
                    '}';
        }
    }
}
