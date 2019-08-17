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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;

import org.apache.cxf.Bus;
import org.apache.cxf.common.util.ClassUnwrapper;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.configuration.Configuration;

@Named("cxf")
@Typed({MeecrowaveBus.class, Bus.class})
@ApplicationScoped
public class MeecrowaveBus implements Bus, ClassUnwrapper {
    private final ConfigurableBus delegate;

    protected MeecrowaveBus() {
        delegate= null;
    }

    @Inject
    public MeecrowaveBus(final ServletContext context) {
        final ClassLoader appLoader = context.getClassLoader();

        final Meecrowave meecrowave = Meecrowave.class.cast(context.getAttribute("meecrowave.instance"));
        final Configuration builder = Configuration.class.cast(context.getAttribute("meecrowave.configuration"));
        if (meecrowave.getClientBus() == null) {
            delegate = new ConfigurableBus();
            if (builder != null && builder.isJaxrsProviderSetup()) {
                delegate.initProviders(builder, appLoader);
            }
        } else {
            delegate = meecrowave.getClientBus();
            if (builder != null && !builder.isInitializeClientBus() && builder.isJaxrsProviderSetup()) {
                delegate.initProviders(builder, appLoader);
            }
        }

        setProperty(ClassUnwrapper.class.getName(), this);
        setExtension(appLoader, ClassLoader.class); // ServletController locks on the classloader otherwise
    }


    /**
     * Unwrap all proxies and get the real underlying class
     * for detecting annotations, etc.
     * @param o the instance to analyze.
     * @return the class to introspect.
     */
    @Override
    public Class<?> getRealClass(Object o) {
        return getRealClassFromClass(o.getClass());
    }

    @Override
    public Class<?> getRealClassFromClass(final Class<?> aClass) {
        if (aClass.getName().contains("$$")) {
            Class realClass = aClass.getSuperclass();
            if (realClass == Object.class || realClass.isInterface()) {
                // we have to dig deeper as we might have a producer method for an interface
                Class<?>[] interfaces = aClass.getInterfaces();
                if (interfaces.length > 0) {
                    Class<?> rootInterface = interfaces[0];
                    for (Class<?> anInterface : interfaces) {
                        if (rootInterface.isAssignableFrom(anInterface)) {
                            rootInterface = anInterface;
                        }
                    }
                    return rootInterface;
                }
            }
            return realClass;
        }
        return aClass;
    }

    @Override
    public <T> T getExtension(final Class<T> extensionType) {
        return delegate.getExtension(extensionType);
    }

    @Override
    public <T> void setExtension(final T extension, final Class<T> extensionType) {
        delegate.setExtension(extension, extensionType);
    }

    @Override
    public boolean hasExtensionByName(final String name) {
        return delegate.hasExtensionByName(name);
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public void setId(final String i) {
        delegate.setId(i);
    }

    @Override
    public void shutdown(final boolean wait) {
        delegate.shutdown(wait);
    }

    @Override
    public void setProperty(final String s, final Object o) {
        delegate.setProperty(s, o);
    }

    @Override
    public Object getProperty(final String s) {
        return delegate.getProperty(s);
    }

    @Override
    public void setProperties(final Map<String, Object> properties) {
        delegate.setProperties(properties);
    }

    @Override
    public Map<String, Object> getProperties() {
        return delegate.getProperties();
    }

    @Override
    public Collection<Feature> getFeatures() {
        return delegate.getFeatures();
    }

    @Override
    public void setFeatures(final Collection<? extends Feature> features) {
        delegate.setFeatures(features);
    }

    @Override
    public BusState getState() {
        return delegate.getState();
    }

    @Override
    public List<Interceptor<? extends Message>> getInInterceptors() {
        return delegate.getInInterceptors();
    }

    @Override
    public List<Interceptor<? extends Message>> getOutInterceptors() {
        return delegate.getOutInterceptors();
    }

    @Override
    public List<Interceptor<? extends Message>> getInFaultInterceptors() {
        return delegate.getInFaultInterceptors();
    }

    @Override
    public List<Interceptor<? extends Message>> getOutFaultInterceptors() {
        return delegate.getOutFaultInterceptors();
    }
}
