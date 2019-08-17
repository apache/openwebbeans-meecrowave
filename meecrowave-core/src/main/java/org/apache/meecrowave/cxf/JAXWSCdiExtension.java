/**
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
package org.apache.meecrowave.cxf;

import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.webbeans.component.OwbBean;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessBean;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// bus is handled by JAXRSCdiResourceExtension
//
// todo: support WebServiceProvider, maybe clients?
public class JAXWSCdiExtension implements Extension {
    private final Impl impl = new Impl();

    public <T> void collect(@Observes final ProcessBean<T> processBean) {
        impl.collect(processBean);
    }

    public void load(@Observes final AfterDeploymentValidation afterDeploymentValidation, final BeanManager beanManager) {
        impl.load(afterDeploymentValidation, beanManager);
    }

    public void release(@Observes final BeforeShutdown beforeShutdown) {
        impl.release(beforeShutdown);
    }

    private static class Impl implements Extension {
        private boolean active;
        private Class<? extends Annotation> marker;

        private final List<Bean<?>> serviceBeans = new ArrayList<>();
        private final Collection<Runnable> preShutdownTasks = new ArrayList<>();

        private Impl() {
            try {
                final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                loader.loadClass("org.apache.cxf.jaxws.JaxWsServerFactoryBean");
                loader.loadClass("org.apache.cxf.service.model.SchemaInfo");
                marker = (Class<? extends Annotation>) loader.loadClass("javax.jws.WebService");
                active = true;
            } catch (final NoClassDefFoundError | ClassNotFoundException e) {
                active = false;
            }
        }

        public <T> void collect(@Observes final ProcessBean<T> processBean) {
            if (active && processBean.getAnnotated().isAnnotationPresent(marker)) {
                serviceBeans.add(processBean.getBean());
            }
        }

        public void load(@Observes final AfterDeploymentValidation afterDeploymentValidation, final BeanManager beanManager) {
            if (!active || serviceBeans.isEmpty() ||
                    !Configuration.class.cast(beanManager.getReference(beanManager.resolve(beanManager.getBeans(Configuration.class)), Configuration.class, null))
                                             .isJaxwsSupportIfAvailable()) {
                return;
            }

            final Bean<?> busBean = beanManager.resolve(beanManager.getBeans("cxf"));
            final org.apache.cxf.Bus bus = org.apache.cxf.Bus.class.cast(beanManager.getReference(
                    busBean, org.apache.cxf.Bus.class, beanManager.createCreationalContext(busBean)));

            final Bean<?> mapperBean = beanManager.resolve(beanManager.getBeans(JAXWSAddressMapper.class));
            final JAXWSAddressMapper mapper;
            if (mapperBean == null) {
                mapper = type -> {
                    WsMapping wsMapping = type.getAnnotation(WsMapping.class);
                    return wsMapping != null
                            ? wsMapping.value()
                            : "/webservices/" + type.getSimpleName();
                };
            } else {
                mapper = JAXWSAddressMapper.class.cast(beanManager.getReference(mapperBean, JAXWSAddressMapper.class, beanManager.createCreationalContext(mapperBean)));
            }

            serviceBeans.forEach(bean -> {
                final Class<?> beanClass = OwbBean.class.isInstance(bean) ? OwbBean.class.cast(bean).getReturnType() : bean.getBeanClass();

                final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                try {
                    final org.apache.cxf.endpoint.AbstractEndpointFactory aef = org.apache.cxf.endpoint.AbstractEndpointFactory.class.cast(
                            loader.loadClass("org.apache.cxf.jaxws.JaxWsServerFactoryBean").getConstructor().newInstance());
                    aef.setBus(bus);
                    aef.setAddress(mapper.map(beanClass));

                    final Class<? extends org.apache.cxf.endpoint.AbstractEndpointFactory> factoryClass = aef.getClass();
                    factoryClass.getMethod("setStart", boolean.class).invoke(aef, true);
                    factoryClass.getMethod("setServiceClass", Class.class).invoke(aef, beanClass);

                    final CreationalContext<Object> creationalContext = beanManager.createCreationalContext(null);
                    if (!beanManager.isNormalScope(bean.getScope())) {
                        preShutdownTasks.add(creationalContext::release);
                    }
                    factoryClass.getMethod("setServiceBean", Object.class).invoke(aef, beanManager.getReference(bean, Object.class, creationalContext));

                    final Object server = factoryClass.getMethod("create").invoke(aef);

                    final Class<?> serverClass = server.getClass();
                    serverClass.getMethod("start").invoke(server);
                    preShutdownTasks.add(() -> {
                        try {
                            serverClass.getMethod("destroy").invoke(server);
                        } catch (final NoClassDefFoundError | NoSuchMethodException | IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        } catch (final InvocationTargetException e) {
                            throw new IllegalStateException(e.getCause());
                        }
                    });
                } catch (final NoClassDefFoundError | ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException e) {
                    throw new IllegalStateException(e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalStateException(e.getCause());
                }
            });
            serviceBeans.clear();
        }

        public void release(@Observes final BeforeShutdown beforeShutdown) {
            preShutdownTasks.stream().map(r -> (Runnable) () -> {
                try {
                    r.run();
                } catch (final RuntimeException re) {
                    new LogFacade(org.apache.meecrowave.cxf.JAXWSCdiExtension.class.getName()).warn(re.getMessage(), re);
                }
            }).forEach(Runnable::run);
            preShutdownTasks.clear();
        }
    }
}
