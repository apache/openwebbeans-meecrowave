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

import jakarta.transaction.TransactionalException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.Providers;

@Provider
public class TransactionExceptionMapper implements ExceptionMapper<TransactionalException> {
    @Context
    private Providers providers;

    @Override
    public Response toResponse(final TransactionalException ejbException) {
        final Throwable cause = ejbException.getCause();
        if (cause != null) {
            final Class causeClass = cause.getClass();
            final ExceptionMapper exceptionMapper = providers.getExceptionMapper(causeClass);
            if (exceptionMapper == null) {
                return defaultResponse(cause);
            }
            return exceptionMapper.toResponse(cause);
        }
        return defaultResponse(ejbException);
    }

    private Response defaultResponse(final Throwable cause) {
        return Response.serverError().type(MediaType.TEXT_PLAIN_TYPE).entity(cause.getMessage()).build();
    }
}
