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
package org.apache.microwave.junit;

import org.apache.microwave.Microwave;
import org.junit.rules.MethodRule;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

// a MicrowaveRule starting a single container, very awesome for forkCount=1, reuseForks=true
public class MonoMicrowave {
    private static final AtomicReference<AutoCloseable> CONTAINER = new AtomicReference<>();
    private static final AtomicReference<Microwave.Builder> CONFIGURATION = new AtomicReference<>();
    private static final AutoCloseable NOOP_CLOSEABLE = () -> {
    };

    public static class Runner extends BlockJUnit4ClassRunner {
        public Runner(final Class<?> klass) throws InitializationError {
            super(klass);
        }

        @Override
        protected List<MethodRule> rules(final Object test) {
            final List<MethodRule> rules = super.rules(test);
            rules.add((base, method, target) -> new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    doBoot();
                    configInjection(test.getClass(), test);
                    base.evaluate();
                }

                private void configInjection(final Class<?> aClass, final Object test) {
                    Stream.of(aClass.getDeclaredFields())
                            .filter(f -> f.isAnnotationPresent(ConfigurationInject.class))
                            .forEach(f -> {
                                if (!f.isAccessible()) {
                                    f.setAccessible(true);
                                }
                                try {
                                    f.set(test, CONFIGURATION.get());
                                } catch (final IllegalAccessException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
                    final Class<?> parent = aClass.getSuperclass();
                    if (parent != null && parent != Object.class) {
                        configInjection(parent, true);
                    }
                }
            });
            return rules;
        }

        /**
         * Only working with the runner
         */
        @Target(FIELD)
        @Retention(RUNTIME)
        public @interface ConfigurationInject {
        }
    }

    public static class Rule extends MicrowaveRuleBase<Rule> {
        @Override
        public Microwave.Builder getConfiguration() {
            return CONFIGURATION.get();
        }

        @Override
        protected AutoCloseable onStart() {
            if (CONTAINER.get() == null) { // yes synchro could be simpler but it does the job, feel free to rewrite it
                synchronized (CONTAINER) {
                    if (CONTAINER.get() == null) {
                        doBoot();
                    }
                }
            }
            return NOOP_CLOSEABLE;
        }
    }

    private static void doBoot() {
        final Microwave.Builder configuration = new Microwave.Builder().randomHttpPort().noShutdownHook(/*the rule does*/);
        StreamSupport.stream(ServiceLoader.load(Microwave.ConfigurationCustomizer.class).spliterator(), false)
                .forEach(c -> c.accept(configuration));
        CONFIGURATION.compareAndSet(null, configuration);

        final Microwave microwave = new Microwave(CONFIGURATION.get());
        if (CONTAINER.compareAndSet(null, microwave)) {
            final Configuration runnerConfig = StreamSupport.stream(ServiceLoader.load(Configuration.class).spliterator(), false)
                    .findAny()
                    .orElseGet(() -> new Configuration() {
                    });

            final File war = runnerConfig.application();
            if (war == null) {
                microwave.bake(runnerConfig.context());
            } else {
                microwave.deployWebapp(runnerConfig.context(), runnerConfig.application());
            }
            Runtime.getRuntime().addShutdownHook(new Thread() {
                {
                    setName("Microwave-mono-rue-stopping");
                }

                @Override
                public void run() {
                    microwave.close();
                }
            });
        }
    }

    public interface Configuration {
        default String context() {
            return "";
        }

        default File application() {
            return null;
        }
    }
}
