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
package org.apache.meecrowave.internal;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.config.WebBeansFinder;
import org.apache.webbeans.corespi.DefaultSingletonService;
import org.apache.webbeans.spi.SingletonService;

public final class ClassLoaderLock {
    public static final Lock LOCK = new ReentrantLock();

    public static ClassLoader getUsableContainerLoader() {
        ClassLoader currentCL = Thread.currentThread().getContextClassLoader();
        if (currentCL == null) {
            currentCL = ClassLoaderLock.class.getClassLoader();
        }
        if (Boolean.getBoolean("meecrowave.junit.classloaderlock.off")) { // safeguard for advanced cases
            return currentCL;
        }

        final SingletonService<WebBeansContext> singletonService = WebBeansFinder.getSingletonService();
        synchronized (singletonService) {
            try {
                if (singletonService instanceof DefaultSingletonService) {
                    synchronized (singletonService) {
                        ((DefaultSingletonService) singletonService).register(currentCL, null);
                        // all fine, it seems we do not have an OWB container for this ClassLoader yet

                        // let's reset it then ;
                        singletonService.clear(currentCL);
                    }
                    return currentCL;
                }
            }
            catch (IllegalArgumentException iae) {
                // whoops there is already an OWB container registered for this very ClassLoader
            }

            return new ClassLoader(currentCL) {};
        }
    }

    private ClassLoaderLock() {
        // no-op
    }
}
