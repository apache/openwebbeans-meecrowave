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
import org.apache.meecrowave.configuration.Configuration;
import org.apache.webbeans.annotation.DefaultLiteral;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.configurator.BeanConfiguratorImpl;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.servlet.WebBeansConfigurationListener;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

public class OWBAutoSetup implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) {
        final Configuration builder = Configuration.class.cast(ctx.getAttribute("meecrowave.configuration"));
        final Meecrowave instance = Meecrowave.class.cast(ctx.getAttribute("meecrowave.instance"));
        if (builder.isCdiConversation()) {
            try {
                final Class<? extends Filter> clazz = (Class<? extends Filter>) OWBAutoSetup.class.getClassLoader()
                      .loadClass("org.apache.webbeans.web.context.WebConversationFilter");
                final FilterRegistration.Dynamic filter = ctx.addFilter("owb-conversation", clazz);
                filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
            } catch (final Exception e) {
                // no-op
            }
        }

        // eager boot to let injections work in listeners
        final EagerBootListener bootListener = new EagerBootListener(instance);
        bootListener.doContextInitialized(new ServletContextEvent(ctx));
        ctx.addListener(bootListener);
    }

    public static class EagerBootListener extends WebBeansConfigurationListener implements Extension {
        private final Meecrowave meecrowave;

        private EagerBootListener(final Meecrowave meecrowave) {
            this.meecrowave = meecrowave;
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

            beanManager.addInternalBean(newBean(instance, configurator ->
                    configurator.beanClass(Meecrowave.Builder.class)
                            .scope(Dependent.class)
                            .qualifiers(DefaultLiteral.INSTANCE)
                            .types(Configuration.class, Meecrowave.Builder.class, Object.class)
                            .createWith(cc -> meecrowave.getConfiguration())));

            beanManager.addInternalBean(newBean(instance, configurator ->
                    configurator.beanClass(Meecrowave.class)
                            .scope(Dependent.class)
                            .qualifiers(DefaultLiteral.INSTANCE)
                            .types(Meecrowave.class, AutoCloseable.class, Object.class)
                            .createWith(cc -> meecrowave)));
        }

        private <T> Bean<?> newBean(final WebBeansContext instance, final Consumer<BeanConfigurator<T>> configurer) {
            final BeanConfiguratorImpl<T> meecrowaveBeanBuilder = new BeanConfiguratorImpl<>(instance);
            configurer.accept(meecrowaveBeanBuilder);
            return meecrowaveBeanBuilder.getBean();
        }
    }
}
