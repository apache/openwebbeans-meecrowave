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
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.Transactional;
import javax.transaction.TransactionalException;

@Interceptor
@Transactional(Transactional.TxType.REQUIRED)
@Priority(200)
public class RequiredInterceptor extends InterceptorBase {
    @AroundInvoke
    public Object intercept(final InvocationContext ic) throws Exception {
        return super.intercept(ic);
    }

    @Override
    protected boolean isNewTransaction(final State state) {
        return state.old == null;
    }

    @Override
    protected State start() {
        try {
            final Transaction transaction = transactionManager.getTransaction();
            final Transaction current;
            if (transaction == null) {
                transactionManager.begin();
                current = transactionManager.getTransaction();
            } else {
                current = transaction;
            }
            return new State(transaction, current);
        } catch (final SystemException | NotSupportedException se) {
            throw new TransactionalException(se.getMessage(), se);
        }
    }

    @Override
    protected void commit(final State state) {
        if (state.old != state.current) {
            try {
                state.current.commit();
            } catch (final HeuristicMixedException | HeuristicRollbackException | RollbackException | SystemException e) {
                throw new TransactionalException(e.getMessage(), e);
            }
        }
    }
}
