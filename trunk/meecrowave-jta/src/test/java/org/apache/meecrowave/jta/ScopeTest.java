
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.meecrowave.jta;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScopeTest {
    @Rule
    public final MeecrowaveRule rule = new MeecrowaveRule(
            new Meecrowave.Builder().includePackages(ScopeTest.class.getName()), "")
            .inject(this);

    @Inject
    private Tx tx;

    @Inject
    private TransactionManager manager;

    @Test
    public void run() throws Exception {
        manager.begin();
        final AtomicBoolean ref = new AtomicBoolean();
        tx.handle(ref);
        Assert.assertFalse(ref.get());
        manager.commit();
        Assert.assertTrue(ref.get());
    }

    @TransactionScoped
    public static class Tx implements Serializable {
        private AtomicBoolean ref;

        @PreDestroy
        private void destroy() {
            ref.set(true);
        }

        public void handle(final AtomicBoolean ref) {
            this.ref = ref;
        }
    }
}
