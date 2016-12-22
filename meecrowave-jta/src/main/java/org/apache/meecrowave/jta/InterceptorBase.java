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

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.interceptor.InvocationContext;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionRolledbackException;
import javax.transaction.Transactional;
import javax.transaction.TransactionalException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Arrays.asList;

public abstract class InterceptorBase implements Serializable {
    private static final IllegalStateException ILLEGAL_STATE_EXCEPTION = new IllegalStateException("Can't use UserTransaction from @Transaction call");
    private static final ThreadLocal<RuntimeException> ERROR = new ThreadLocal<>();

    private transient volatile ConcurrentMap<Method, Boolean> rollback = new ConcurrentHashMap<>();

    @Inject
    private JtaConfig config;

    @Inject
    protected TransactionManager transactionManager;

    protected Object intercept(final InvocationContext ic) throws Exception {
        final boolean forbidsUt = doesForbidUtUsage();
        final RuntimeException oldEx;
        final IllegalStateException illegalStateException;
        if (forbidsUt) {
            illegalStateException = ILLEGAL_STATE_EXCEPTION;
            oldEx = error(illegalStateException);
        } else {
            illegalStateException = null;
            oldEx = null;
        }

        State state = null;
        try {
            state = start();
            final Object proceed = ic.proceed();
            commit(state); // force commit there to ensure we can catch synchro exceptions
            return proceed;
        } catch (final Exception e) {
            if (illegalStateException == e) {
                throw e;
            }

            Exception error = unwrap(e);
            if (error != null && (!config.isHandleExceptionOnlyForClient() || isNewTransaction(state))) {
                final Method method = ic.getMethod();
                if (rollback == null) {
                    synchronized (this) {
                        if (rollback == null) {
                            rollback = new ConcurrentHashMap<>();
                        }
                    }
                }
                Boolean doRollback = rollback.get(method);
                if (doRollback != null) {
                    if (doRollback && isTransactionActive(state.current)) {
                        setRollbackOnly();
                    }
                } else {
                    // computed lazily but we could cache it later for sure if that's really a normal case
                    final AnnotatedType<?> annotatedType = CDI.current().getBeanManager().createAnnotatedType(method.getDeclaringClass());
                    Transactional tx = null;
                    for (final AnnotatedMethod<?> m : annotatedType.getMethods()) {
                        if (method.equals(m.getJavaMember())) {
                            tx = m.getAnnotation(Transactional.class);
                            break;
                        }
                    }
                    if (tx == null) {
                        tx = annotatedType.getAnnotation(Transactional.class);
                    }
                    if (tx != null) {
                        doRollback = new ExceptionPriotiryRules(tx.rollbackOn(), tx.dontRollbackOn()).accept(error, method.getExceptionTypes());
                        rollback.putIfAbsent(method, doRollback);
                        if (doRollback && isTransactionActive(state.current)) {
                            setRollbackOnly();
                        }
                    }
                }
            }
            try {
                commit(state);
            } catch (final Exception ex) {
                // no-op: swallow to keep the right exception
                final Logger logger = Logger.getLogger(getClass().getName());
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Swallowing: " + ex.getMessage());
                }
            }

            throw new TransactionalException(e.getMessage(), error);
        } finally {
            if (forbidsUt) {
                resetError(oldEx);
            }
        }
    }

    protected abstract boolean isNewTransaction(final State state);
    protected abstract State start();
    protected abstract void commit(final State state);

    private void resetError(final RuntimeException oldEx) {
        ERROR.set(oldEx);
    }

    protected void setRollbackOnly() throws SystemException {
        transactionManager.setRollbackOnly();
    }

    public boolean isTransactionActive(final Transaction current) {
        if (current == null) {
            return false;
        }

        try {
            final int status = current.getStatus();
            return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
        } catch (final SystemException e) {
            return false;
        }
    }


    protected RuntimeException error(IllegalStateException illegalStateException) {
        final RuntimeException p = ERROR.get();
        ERROR.set(illegalStateException);
        return p;
    }

    private Exception unwrap(final Exception e) {
        Exception error = e;
        while (error != null && (SystemException.class.isInstance(error) || TransactionRolledbackException.class.isInstance(error))) {
            final Throwable cause = error.getCause();
            if (cause == error) {
                break;
            }
            error = Exception.class.isInstance(cause) ? Exception.class.cast(cause) : null;
        }
        if (RollbackException.class.isInstance(error) && Exception.class.isInstance(error.getCause())) {
            error = Exception.class.cast(error.getCause());
        }
        return error;
    }

    protected boolean doesForbidUtUsage() {
        return true;
    }

    private static final class ExceptionPriotiryRules {
        private final Class<?>[] includes;
        private final Class<?>[] excludes;

        private ExceptionPriotiryRules(final Class<?>[] includes, final Class<?>[] excludes) {
            this.includes = includes;
            this.excludes = excludes;
        }

        private boolean accept(final Exception e, final Class<?>[] exceptionTypes) {
            if (e == null) {
                return false;
            }

            final int includeScore = contains(includes, e);
            final int excludeScore = contains(excludes, e);

            if (excludeScore < 0) {
                return includeScore >= 0 || isNotChecked(e, exceptionTypes);
            }
            return includeScore - excludeScore >= 0;
        }

        private static int contains(final Class<?>[] list, final Exception e) {
            int score = -1;
            for (final Class<?> clazz : list) {
                if (clazz.isInstance(e)) {
                    final int thisScore = score(clazz, e.getClass());
                    if (score < 0) {
                        score = thisScore;
                    } else {
                        score = Math.min(thisScore, score);
                    }
                }
            }
            return score;
        }

        private static int score(final Class<?> config, final Class<?> ex) {
            int score = 0;
            Class<?> current = ex;
            while (current != null && !current.equals(config)) {
                score++;
                current = current.getSuperclass();
            }
            return score;
        }

        private static boolean isNotChecked(final Exception e, final Class<?>[] exceptionTypes) {
            return RuntimeException.class.isInstance(e) && (exceptionTypes.length == 0 || !asList(exceptionTypes).contains(e.getClass()));
        }
    }

    protected static class State {
        protected final Transaction old;
        protected final Transaction current;

        public State(final Transaction old, final Transaction current) {
            this.old = old;
            this.current = current;
        }
    }
}
