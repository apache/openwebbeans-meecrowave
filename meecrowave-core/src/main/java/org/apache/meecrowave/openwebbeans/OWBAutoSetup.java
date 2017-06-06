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
package org.apache.meecrowave.openwebbeans;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.cxf.JAXRSFieldInjectionInterceptor;
import org.apache.webbeans.annotation.AnyLiteral;
import org.apache.webbeans.annotation.DefaultLiteral;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.intercept.InterceptorsManager;
import org.apache.webbeans.servlet.WebBeansConfigurationListener;
import org.apache.webbeans.web.context.WebConversationFilter;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

public class OWBAutoSetup implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) throws ServletException {
        final Meecrowave.Builder builder = Meecrowave.Builder.class.cast(ctx.getAttribute("meecrowave.configuration"));
        if (builder.isCdiConversation()) {
            final FilterRegistration.Dynamic filter = ctx.addFilter("owb-conversation", WebConversationFilter.class);
            filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
        }

        // eager boot to let injections work in listeners
        final EagerBootListener bootListener = new EagerBootListener(builder);
        bootListener.doContextInitialized(new ServletContextEvent(ctx));
        ctx.addListener(bootListener);
    }

    public static class EagerBootListener extends WebBeansConfigurationListener implements Extension {
        private final Meecrowave.Builder config;

        private EagerBootListener(final Meecrowave.Builder builder) {
            this.config = builder;
        }

        @Override
        public void contextInitialized(final ServletContextEvent event) {
            // skip
        }

        private void doContextInitialized(final ServletContextEvent event) {
            try {
                final WebBeansContext instance = WebBeansContext.getInstance();
                customizeContext(instance);
            } catch (final IllegalStateException ise) {
                // lifecycle not supporting it
            }
            super.contextInitialized(event);
        }

        private void customizeContext(final WebBeansContext instance) {
            final BeanManagerImpl beanManager = instance.getBeanManagerImpl();
            final InterceptorsManager interceptorsManager = instance.getInterceptorsManager();

            beanManager.addInternalBean(new ConfigBean(config));

            interceptorsManager.addInterceptorBindingType(JAXRSFieldInjectionInterceptor.Binding.class);
            beanManager.addAdditionalAnnotatedType(this, beanManager.createAnnotatedType(JAXRSFieldInjectionInterceptor.class));
        }

        private static class ConfigBean implements Bean<Meecrowave.Builder> {
            private final Meecrowave.Builder value;
            private final Set<Type> types = new HashSet<>(asList(Meecrowave.Builder.class, Object.class));
            private final Set<Annotation> qualifiers = new HashSet<>(asList(DefaultLiteral.INSTANCE, AnyLiteral.INSTANCE));

            private ConfigBean(final Meecrowave.Builder config) {
                this.value = config;
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return Collections.emptySet();
            }

            @Override
            public Class<?> getBeanClass() {
                return Meecrowave.Builder.class;
            }

            @Override
            public boolean isNullable() {
                return false;
            }

            @Override
            public Meecrowave.Builder create(final CreationalContext<Meecrowave.Builder> context) {
                return value;
            }

            @Override
            public void destroy(final Meecrowave.Builder instance, final CreationalContext<Meecrowave.Builder> context) {

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
}
