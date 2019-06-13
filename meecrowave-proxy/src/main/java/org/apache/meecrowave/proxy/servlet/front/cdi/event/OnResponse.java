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
package org.apache.meecrowave.proxy.servlet.front.cdi.event;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.apache.meecrowave.proxy.servlet.front.cdi.func.IOConsumer;

public class OnResponse extends BaseEvent {
    private final Response clientResponse;
    private final IOConsumer<Function<InputStream, InputStream>> delegate;
    private Function<InputStream, InputStream> payloadRewriter;
    private boolean proceeded;

    public OnResponse(final HttpServletRequest request, final HttpServletResponse response,
                      final Response clientResponse, final Function<InputStream, InputStream> payloadRewriter,
                      final IOConsumer<Function<InputStream, InputStream>> delegate) {
        super(request, response);
        this.clientResponse = clientResponse;
        this.delegate = delegate;
        this.payloadRewriter = payloadRewriter;
    }

    public Response getClientResponse() {
        return clientResponse;
    }

    public void setPayloadRewriter(final Function<InputStream, InputStream> payloadRewriter) {
        this.payloadRewriter = payloadRewriter;
    }

    public void proceed() throws IOException {
        delegate.accept(payloadRewriter);
        proceeded = true;
    }

    public boolean isProceeded() {
        return proceeded;
    }
}
