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
package org.apache.meecrowave.cxf;

import java.lang.reflect.Field;
import java.util.List;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessBean;
import javax.ws.rs.Path;

public class Cxfs {
    public static final boolean IS_PRESENT;

    static {
        boolean present;
        try {
            Cxfs.class.getClassLoader().loadClass("org.apache.cxf.BusFactory");
            present = true;
        } catch (ClassNotFoundException e) {
            present = false;
        }
        IS_PRESENT = present;
    }

    private Cxfs() {
        // no-op
    }

    public static boolean hasDefaultBus() {
        return org.apache.cxf.BusFactory.getDefaultBus(false) != null;
    }

    public static void resetDefaultBusIfEquals(final ConfigurableBus clientBus) {
        if (clientBus != null && org.apache.cxf.BusFactory.getDefaultBus(false) == clientBus) {
            org.apache.cxf.BusFactory.setDefaultBus(null);
        }
    }

    public static Extension mapCdiExtensionIfNeeded(final Extension extension) {
        if ("org.apache.cxf.cdi.JAXRSCdiResourceExtension".equals(extension.getClass().getName())) {
            final Field serviceBeans;
            try {
                serviceBeans = org.apache.cxf.cdi.JAXRSCdiResourceExtension.class
                        .getDeclaredField("serviceBeans");
            } catch (final NoSuchFieldException e) {
                new org.apache.meecrowave.logging.tomcat.LogFacade(Cxfs.class.getName()).warn(e.getMessage(), e);
                return extension;
            }
            if (!serviceBeans.isAccessible()) {
                serviceBeans.setAccessible(true);
            }
            return new ContractFriendlyJAXRSCdiResourceExtension(serviceBeans);
        }
        return extension;
    }

    // to drop when we will have a cxf version with https://issues.apache.org/jira/browse/CXF-7921
    private static class ContractFriendlyJAXRSCdiResourceExtension extends org.apache.cxf.cdi.JAXRSCdiResourceExtension {
        private final Field serviceBeans;

        private ContractFriendlyJAXRSCdiResourceExtension(final Field serviceBeans) {
            this.serviceBeans = serviceBeans;
        }

        @Override
        public <T> void collect(@Observes final ProcessBean<T> event) {
            if (!event.getAnnotated().isAnnotationPresent(Path.class) && AnnotatedType.class.isInstance(event.getAnnotated())) {
                final AnnotatedType<?> type = AnnotatedType.class.cast(event.getAnnotated());
                // note: should we use Annotated for interfaces as well?
                if (type.getTypeClosure().stream()
                        .filter(it -> Class.class.isInstance(it) && Class.class.cast(it).isInterface())
                        .map(Class.class::cast)
                        .anyMatch(c -> c.isAnnotationPresent(Path.class))) {
                    try {
                        List.class.cast(serviceBeans.get(this)).add(event.getBean());
                        return;
                    } catch (final IllegalAccessException e) {
                        new org.apache.meecrowave.logging.tomcat.LogFacade(Cxfs.class.getName())
                                .error(e.getMessage(), e);
                    }
                }
            }
            super.collect(event);
        }
    }
}
