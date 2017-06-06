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

import org.apache.meecrowave.cxf.JAXRSFieldInjectionInterceptor;
import org.apache.meecrowave.cxf.MeecrowaveBus;
import org.apache.webbeans.container.AnnotatedTypeWrapper;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.ws.rs.Path;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class MeecrowaveExtension implements Extension {
    void addBeansFromJava(@Observes final BeforeBeanDiscovery bbd, final BeanManager bm) {
        // stream not really needed but here for the pattern in case we need other beans
        Stream.of(MeecrowaveBus.class).forEach(type -> bbd.addAnnotatedType(bm.createAnnotatedType(type)));
    }

    void enableContextFieldInjectionWorks(@Observes final ProcessAnnotatedType<?> pat, final BeanManager bm) {
        final AnnotatedType<?> at = pat.getAnnotatedType();
        if (at.isAnnotationPresent(Path.class) && !at.isAnnotationPresent(JAXRSFieldInjectionInterceptor.Binding.class)
                && at.getAnnotations().stream().anyMatch(a -> bm.isNormalScope(a.annotationType()))) {
            pat.setAnnotatedType(new JAXRSFIeldInjectionAT(this, at));
        }
    }

    private static class JAXRSFIeldInjectionAT<T> extends AnnotatedTypeWrapper<T> {
        private final Set<Annotation> annotations;

        private JAXRSFIeldInjectionAT(final Extension extension, final AnnotatedType<T> original) {
            super(extension, original);
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
            return t1Class == JAXRSFieldInjectionInterceptor.Binding.class ? t1Class.cast(JAXRSFieldInjectionInterceptor.Binding.INSTANCE) : super.getAnnotation(t1Class);
        }

        @Override
        public boolean isAnnotationPresent(final Class<? extends Annotation> aClass) {
            return JAXRSFieldInjectionInterceptor.Binding.class == aClass || super.isAnnotationPresent(aClass);
        }
    }
}
