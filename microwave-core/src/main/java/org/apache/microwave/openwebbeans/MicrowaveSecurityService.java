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
package org.apache.microwave.openwebbeans;

import org.apache.webbeans.corespi.security.ManagedSecurityService;
import org.apache.webbeans.spi.SecurityService;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.util.Properties;

public class MicrowaveSecurityService implements SecurityService {
    private final SecurityService securityService = new ManagedSecurityService();

    @Override // reason of that class
    public Principal getCurrentPrincipal() {
        return new MicrowavePrincipal();
    }

    @Override
    public <T> Constructor<T> doPrivilegedGetDeclaredConstructor(final Class<T> aClass, final Class<?>... classes) {
        return securityService.doPrivilegedGetDeclaredConstructor(aClass, classes);
    }

    @Override
    public <T> Constructor<T> doPrivilegedGetConstructor(final Class<T> aClass, final Class<?>... classes) {
        return securityService.doPrivilegedGetConstructor(aClass, classes);
    }

    @Override
    public <T> Constructor<?>[] doPrivilegedGetDeclaredConstructors(final Class<T> aClass) {
        return securityService.doPrivilegedGetDeclaredConstructors(aClass);
    }

    @Override
    public <T> Method doPrivilegedGetDeclaredMethod(final Class<T> aClass, final String s, final Class<?>... classes) {
        return securityService.doPrivilegedGetDeclaredMethod(aClass, s, classes);
    }

    @Override
    public <T> Method[] doPrivilegedGetDeclaredMethods(final Class<T> aClass) {
        return securityService.doPrivilegedGetDeclaredMethods(aClass);
    }

    @Override
    public <T> Field doPrivilegedGetDeclaredField(final Class<T> aClass, final String s) {
        return securityService.doPrivilegedGetDeclaredField(aClass, s);
    }

    @Override
    public <T> Field[] doPrivilegedGetDeclaredFields(final Class<T> aClass) {
        return securityService.doPrivilegedGetDeclaredFields(aClass);
    }

    @Override
    public void doPrivilegedSetAccessible(final AccessibleObject accessibleObject, final boolean b) {
        securityService.doPrivilegedSetAccessible(accessibleObject, b);
    }

    @Override
    public boolean doPrivilegedIsAccessible(final AccessibleObject accessibleObject) {
        return securityService.doPrivilegedIsAccessible(accessibleObject);
    }

    @Override
    public <T> T doPrivilegedObjectCreate(final Class<T> aClass) throws PrivilegedActionException, IllegalAccessException, InstantiationException {
        return securityService.doPrivilegedObjectCreate(aClass);
    }

    @Override
    public void doPrivilegedSetSystemProperty(final String s, final String s1) {
        securityService.doPrivilegedSetSystemProperty(s, s1);
    }

    @Override
    public String doPrivilegedGetSystemProperty(final String s, final String s1) {
        return securityService.doPrivilegedGetSystemProperty(s, s1);
    }

    @Override
    public Properties doPrivilegedGetSystemProperties() {
        return securityService.doPrivilegedGetSystemProperties();
    }

    // ensure it is contextual
    public static class MicrowavePrincipal implements Principal {
        @Override
        public String getName() {
            return unwrap().getName();
        }

        public /*ensure user can cast it to get the actual instance*/ Principal unwrap() {
            final BeanManager beanManager = CDI.current().getBeanManager();
            return HttpServletRequest.class.cast(
                    beanManager.getReference(
                            beanManager.resolve(beanManager.getBeans(HttpServletRequest.class)), HttpServletRequest.class,
                            beanManager.createCreationalContext(null)))
                    .getUserPrincipal();
        }
    }
}
