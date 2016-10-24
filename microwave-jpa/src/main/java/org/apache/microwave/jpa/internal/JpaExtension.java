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
package org.apache.microwave.jpa.internal;

import org.apache.microwave.jpa.api.Unit;
import org.apache.microwave.jpa.api.PersistenceUnitInfoBuilder;

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
import javax.persistence.spi.PersistenceUnitInfo;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    private final Map<String, EntityManagerBean> entityManagerBeans = new HashMap<>();
    private final Collection<Bean<?>> unitBuilders = new ArrayList<>(2);

    void addInternals(@Observes final BeforeBeanDiscovery bbd, final BeanManager bm) {
        Stream.of(JpaTransactionInterceptor.class, JpaNoTransactionInterceptor.class)
                .forEach(interceptor -> bbd.addAnnotatedType(bm.createAnnotatedType(interceptor)));
    }

    void collectEntityManagerInjections(@Observes final ProcessBean<?> bean) {
        final Map<String, EntityManagerBean> beans = bean.getBean().getInjectionPoints().stream()
                .filter(i -> i.getAnnotated().isAnnotationPresent(Unit.class))
                .map(i -> i.getAnnotated().getAnnotation(Unit.class))
                .collect(toMap(Unit::name, unit -> new EntityManagerBean(entityManagerContext, unit.name(), unit.synchronization())));
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
                        return builder.toInfo();
                    } finally {
                        cc.release();
                    }
                }).collect(toMap(PersistenceUnitInfo::getPersistenceUnitName, identity()));

        entityManagerBeans.forEach((k, e) -> {
            final PersistenceUnitInfo info = infoIndex.get(k);
            if (info == null) {
                adv.addDeploymentProblem(new IllegalArgumentException("Didn't find any PersistenceUnitInfoBuilder for " + k));
            } else {
                e.init(bm, info);
            }
        });
    }

    public EntityManagerContext getEntityManagerContext() {
        return entityManagerContext;
    }
}
