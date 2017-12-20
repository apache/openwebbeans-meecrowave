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

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.transaction.SystemException;
import javax.transaction.Transactional;
import javax.transaction.TransactionalException;

@Interceptor
@Transactional(Transactional.TxType.NEVER)
@Priority(200)
public class NeverInterceptor extends InterceptorBase {
    private static final State STATE = new State(null, null);

    @AroundInvoke
    public Object intercept(final InvocationContext ic) throws Exception {
        return super.intercept(ic);
    }

    @Override
    protected boolean isNewTransaction(final State state) {
        return false;
    }

    @Override
    protected State start() {
        try {
            if (transactionManager.getTransaction() != null) {
                throw new TransactionalException("@Transactional(NEVER) but a transaction is running", new IllegalStateException());
            }
            return STATE;
        } catch (final SystemException e) {
            throw new TransactionalException(e.getMessage(), e);
        }
    }

    @Override
    protected void commit(final State state) {
        // no-op
    }


    @Override
    protected boolean doesForbidUtUsage() {
        return false;
    }
}
