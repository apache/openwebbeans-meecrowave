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
package org.apache.meecrowave.testing;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.internal.ClassLoaderLock;

import java.io.File;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

public class MonoBase {
    private static final AtomicReference<Meecrowave> CONTAINER = new AtomicReference<>();
    private static final AtomicReference<Meecrowave.Builder> CONFIGURATION = new AtomicReference<>();

    public Meecrowave.Builder doBoot() {
        final Meecrowave.Builder configuration = new Meecrowave.Builder().randomHttpPort().noShutdownHook(/*the rule does*/);
        CONFIGURATION.compareAndSet(null, configuration);

        boolean unlocked = false;
        ClassLoaderLock.LOCK.lock();
        try {
            final ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
            ClassLoaderLock.LOCK.lock();
            final ClassLoader containerLoader = ClassLoaderLock.getUsableContainerLoader();

            final Meecrowave meecrowave = new Meecrowave(CONFIGURATION.get());
            if (CONTAINER.compareAndSet(null, meecrowave)) {
                final Configuration runnerConfig = StreamSupport.stream(ServiceLoader.load(Configuration.class)
                                                                                     .spliterator(), false)
                                                                .sorted(Comparator.comparingInt(Configuration::order))
                                                                .findFirst()
                                                                .orElseGet(() -> new Configuration() {});

                runnerConfig.beforeStarts();

                final File war = runnerConfig.application();
                final Thread thread = Thread.currentThread();
                if (containerLoader != originalCL) {
                    thread.setContextClassLoader(containerLoader);
                }
                try {
                    if (war == null) {
                        meecrowave.bake(runnerConfig.context());
                    } else {
                        meecrowave.deployWebapp(runnerConfig.context(), runnerConfig.application());
                    }
                } finally {
                    if (containerLoader != originalCL) {
                        thread.setContextClassLoader(originalCL);
                    }
                }
                ClassLoaderLock.LOCK.unlock();
                unlocked = true;

                runnerConfig.afterStarts();

                Runtime.getRuntime()
                       .addShutdownHook(new Thread() {
                           {
                               setName("Meecrowave-mono-rule-stopping");
                           }

                           @Override
                           public void run() {
                               try {
                                   runnerConfig.beforeStops();
                               } finally {
                                   try {
                                       meecrowave.close();
                                   } finally {
                                       runnerConfig.afterStops();
                                   }
                               }
                           }
                       });
            }
        } finally {
            if (!unlocked) {
                ClassLoaderLock.LOCK.unlock();
            }
        }
        return getConfiguration();
    }

    public Meecrowave.Builder getConfiguration() {
        return CONFIGURATION.get();
    }

    public Meecrowave.Builder startIfNeeded() {
        if (CONTAINER.get() == null) { // yes synchro could be simpler but it does the job, feel free to rewrite it
            synchronized (CONTAINER) {
                if (CONTAINER.get() == null) {
                    doBoot();
                }
            }
        }
        return getConfiguration();
    }



    public interface Configuration {
        default int order() {
            return 0;
        }

        default String context() {
            return "";
        }

        default File application() {
            return null;
        }

        default void beforeStarts() {
            // no-op
        }

        default void afterStarts() {
            // no-op
        }

        default void beforeStops() {
            // no-op
        }

        default void afterStops() {
            // no-op
        }
    }
}
