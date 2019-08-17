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

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.ws.rs.Path;

import org.apache.meecrowave.cxf.Cxfs;
import org.apache.meecrowave.cxf.JAXRSFieldInjectionInterceptor;
import org.apache.meecrowave.cxf.MeecrowaveBus;
import org.apache.webbeans.container.AnnotatedTypeWrapper;
import org.apache.webbeans.portable.AnnotatedElementFactory;

public class MeecrowaveExtension implements Extension {

    void addBeansFromJava(@Observes final BeforeBeanDiscovery bbd, final BeanManager bm) {
        if (Cxfs.IS_PRESENT) {
            bbd.addInterceptorBinding(JAXRSFieldInjectionInterceptor.Binding.class);

            Stream.of(MeecrowaveBus.class, JAXRSFieldInjectionInterceptor.class)
                  .forEach(type -> bbd.addAnnotatedType(bm.createAnnotatedType(type)));
        }
    }

    void onPat(@Observes final ProcessAnnotatedType<?> pat, final BeanManager bm) {
        final AnnotatedType<?> at = pat.getAnnotatedType();
        if (isJaxRsEndpoint(bm, at)) {
            pat.setAnnotatedType(new JAXRSFIeldInjectionAT(this, at));
        } else if (isVetoedMeecrowaveCore(at.getJavaClass().getName())) {
            pat.veto();
        }
    }

    private boolean isJaxRsEndpoint(final BeanManager bm, final AnnotatedType<?> at) {
        return Cxfs.IS_PRESENT
                && at.isAnnotationPresent(Path.class)
                && !at.isAnnotationPresent(JAXRSFieldInjectionInterceptor.Binding.class)
                && at.getAnnotations().stream().anyMatch(a -> bm.isNormalScope(a.annotationType()));
    }

    // for fatjars
    private boolean isVetoedMeecrowaveCore(final String name) {
        return !"org.apache.meecrowave.cxf.MeecrowaveBus".equals(name)
                && !"org.apache.meecrowave.cxf.JAXRSFieldInjectionInterceptor".equals(name)
                && (name.startsWith("org.apache.meecrowave.api.")
                    || name.startsWith("org.apache.meecrowave.cdi.")
                    || name.startsWith("org.apache.meecrowave.configuration.")
                    || name.startsWith("org.apache.meecrowave.cxf.")
                    || name.startsWith("org.apache.meecrowave.io.")
                    || name.startsWith("org.apache.meecrowave.lang.")
                    || name.startsWith("org.apache.meecrowave.logging.")
                    || name.startsWith("org.apache.meecrowave.openwebbeans.")
                    || name.startsWith("org.apache.meecrowave.runner.")
                    || name.startsWith("org.apache.meecrowave.service.")
                    || name.startsWith("org.apache.meecrowave.tomcat.")
                    || name.startsWith("org.apache.meecrowave.watching.")
                    || name.equals("org.apache.meecrowave.Meecrowave"));
    }

    private static class JAXRSFIeldInjectionAT<T> extends AnnotatedTypeWrapper<T> {

        private final Set<Annotation> annotations;

        private JAXRSFIeldInjectionAT(final Extension extension, final AnnotatedType<T> original) {
            super(extension, original,
                    AnnotatedTypeWrapper.class.isInstance(original) ? AnnotatedTypeWrapper.class.cast(original).getId()
                            : getDefaultId(extension, original));
            this.annotations = new HashSet<>(original.getAnnotations().size() + 1);
            this.annotations.addAll(original.getAnnotations());
            this.annotations.add(JAXRSFieldInjectionInterceptor.Binding.INSTANCE);
        }

        @Override
        public Set<Annotation> getAnnotations() {
            return annotations;
        }

        @Override
        public <T1 extends Annotation> T1 getAnnotation(final Class<T1> t1Class) {
            return t1Class == JAXRSFieldInjectionInterceptor.Binding.class
                    ? t1Class.cast(JAXRSFieldInjectionInterceptor.Binding.INSTANCE)
                    : super.getAnnotation(t1Class);
        }

        @Override
        public boolean isAnnotationPresent(final Class<? extends Annotation> aClass) {
            return JAXRSFieldInjectionInterceptor.Binding.class == aClass || super.isAnnotationPresent(aClass);
        }

        private static <T> String getDefaultId(final Extension extension, final AnnotatedType<T> original) {
            return extension.getClass().getName() + original + AnnotatedElementFactory.OWB_DEFAULT_KEY;
        }
    }
}
