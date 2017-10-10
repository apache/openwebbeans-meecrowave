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
package org.apache.meecrowave.junit;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.CDI;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.testing.Injector;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class InjectRule implements TestRule {
    private final Object instance;

    public InjectRule(final Object instance) {
        this.instance = instance;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                CreationalContext<?> creationalContext = null;
                try {
                    creationalContext = Injector.inject(instance);
                    Injector.injectConfig(CDI.current().select(Meecrowave.Builder.class).get(), instance);
                    base.evaluate();
                } finally {
                    if (creationalContext != null) {
                        creationalContext.release();
                    }
                }
            }
        };
    }
}
