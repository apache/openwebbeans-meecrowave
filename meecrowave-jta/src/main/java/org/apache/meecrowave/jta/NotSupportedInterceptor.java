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

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionalException;

@Interceptor
@Transactional(Transactional.TxType.NOT_SUPPORTED)
@Priority(200)
public class NotSupportedInterceptor extends InterceptorBase {
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
            final Transaction tx = transactionManager.getTransaction();
            return new State(tx, null);
        } catch (final SystemException e) {
            throw new TransactionalException(e.getMessage(), e);
        }
    }

    @Override
    protected void commit(final State state) {
        if (state.old != null) {
            try {
                transactionManager.resume(state.old);
            } catch (final InvalidTransactionException | SystemException e) {
                throw new TransactionalException(e.getMessage(), e);
            }
        }
    }

    @Override
    protected boolean doesForbidUtUsage() {
        return false;
    }
}
