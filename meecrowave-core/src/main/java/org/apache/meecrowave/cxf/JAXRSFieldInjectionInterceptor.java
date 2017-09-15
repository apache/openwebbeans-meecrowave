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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Priority;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InterceptorBinding;
import javax.interceptor.InvocationContext;
import javax.ws.rs.core.Application;

import org.apache.cxf.jaxrs.model.ApplicationInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfoStack;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.webbeans.annotation.EmptyAnnotationLiteral;
import org.apache.webbeans.intercept.ConstructorInterceptorInvocationContext;

@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE)
@JAXRSFieldInjectionInterceptor.Binding
public class JAXRSFieldInjectionInterceptor implements Serializable {
    private final AtomicBoolean injected = new AtomicBoolean();

    @AroundConstruct
    public Object injectContexts(final InvocationContext ic) throws Exception {
        doInject(ic);
        return ic.proceed();
    }

    @AroundInvoke
    public Object lazyInjectContexts(final InvocationContext ic) throws Exception {
        if (!injected.get()) {
            doInject(ic);
        }
        return ic.proceed();
    }

    private void doInject(final InvocationContext ic) throws Exception {
        final Message current = JAXRSUtils.getCurrentMessage();
        if (current != null) {
            final OperationResourceInfoStack stack = OperationResourceInfoStack.class.cast(current.get(OperationResourceInfoStack.class.getName()));
            if (stack != null && !stack.isEmpty()) {
                final Object instance;
                if (ConstructorInterceptorInvocationContext.class.isInstance(ic)) {
                    final ConstructorInterceptorInvocationContext constructorInterceptorInvocationContext = ConstructorInterceptorInvocationContext.class.cast(ic);
                    constructorInterceptorInvocationContext.directProceed();
                    instance = constructorInterceptorInvocationContext.getNewInstance();
                } else {
                    instance = ic.getTarget();
                }
                Application application = null;
                final Object appInfo = current.getExchange().getEndpoint().get(Application.class.getName());
                if (ApplicationInfo.class.isInstance(appInfo)) {
                    application = ApplicationInfo.class.cast(appInfo).getProvider();
                }
                synchronized (this) {
                    if (injected.get()) {
                        return;
                    }
                    InjectionUtils.injectContextProxiesAndApplication(
                            stack.lastElement().getMethodInfo().getClassResourceInfo(),
                            instance,
                            application,
                            ProviderFactory.getInstance(current));
                    injected.compareAndSet(false, true);
                }
            }
        }
    }

    @Target(TYPE)
    @Retention(RUNTIME)
    @InterceptorBinding
    public  @interface Binding {
        Annotation INSTANCE = new EmptyAnnotationLiteral<Binding>() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Binding.class;
            }
        };
    }
}
