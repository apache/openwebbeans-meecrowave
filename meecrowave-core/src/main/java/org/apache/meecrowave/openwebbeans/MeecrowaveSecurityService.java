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

import java.security.Principal;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.security.SimpleSecurityService;

public class MeecrowaveSecurityService extends SimpleSecurityService {
    private final boolean useWrapper;

    public MeecrowaveSecurityService(final WebBeansContext context) {
        useWrapper = "true".equalsIgnoreCase(context.getOpenWebBeansConfiguration()
                .getProperty("org.apache.webbeans.component.PrincipalBean.proxy", "true"));
    }
    @Override // reason of that class
    public Principal getCurrentPrincipal() {
        return useWrapper ? new MeecrowavePrincipal() : getUserPrincipal();
    }

    // ensure it is contextual
    private static class MeecrowavePrincipal implements Principal {
        @Override
        public String getName() {
            return unwrap().getName();
        }

        private Principal unwrap() {
            return getUserPrincipal();
        }
    }

    private static Principal getUserPrincipal() {
        final BeanManager beanManager = CDI.current().getBeanManager();
        final HttpServletRequest request = HttpServletRequest.class.cast(
                beanManager.getReference(
                        beanManager.resolve(beanManager.getBeans(HttpServletRequest.class)), HttpServletRequest.class,
                        beanManager.createCreationalContext(null)));
        final Object supplier = request.getAttribute(Principal.class.getName() + ".supplier");
        if (supplier != null) {
            return ((Supplier<Principal>) supplier).get();
        }
        return request.getUserPrincipal();
    }
}
