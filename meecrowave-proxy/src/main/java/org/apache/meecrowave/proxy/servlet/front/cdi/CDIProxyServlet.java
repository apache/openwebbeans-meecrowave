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
package org.apache.meecrowave.proxy.servlet.front.cdi;

import static java.util.function.Function.identity;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.apache.meecrowave.proxy.servlet.configuration.Routes;
import org.apache.meecrowave.proxy.servlet.front.ProxyServlet;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.AfterResponse;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.BeforeRequest;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.OnRequest;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.OnResponse;
import org.apache.meecrowave.proxy.servlet.front.cdi.extension.SpyExtension;

// IMPORTANT: don't make this class depending on meecrowave, cxf or our internals, use setup class
public class CDIProxyServlet extends ProxyServlet {
    @Inject
    private Event<BeforeRequest> beforeRequestEvent;

    @Inject
    private Event<AfterResponse> afterResponseEvent;

    @Inject
    private Event<OnRequest> onRequestEvent;

    @Inject
    private Event<OnResponse> onResponseEvent;

    @Inject
    private SpyExtension spy;

    @Override
    protected CompletionStage<HttpServletResponse> doExecute(final Routes.Route route,
                                                             final HttpServletRequest req, final HttpServletResponse resp,
                                                             final String prefix) throws IOException {
        final CompletionStage<HttpServletResponse> stage;
        if (spy.isHasBeforeEvent()) {
            final BeforeRequest event = new BeforeRequest(req, resp);
            event.setRoute(route);
            event.setPrefix(prefix);
            beforeRequestEvent.fire(event);
            stage = super.doExecute(event.getRoute(), req, resp, event.getPrefix());
        } else {
            stage = super.doExecute(route, req, resp, prefix);
        }
        if (!spy.isHasAfterEvent()) {
            return stage;
        }
        return stage.handle((r, t) -> {
            afterResponseEvent.fire(new AfterResponse(req, resp));
            return r;
        });
    }

    @Override
    protected CompletionStage<Response> doRequest(final Routes.Route route,
                                                  final HttpServletRequest req,
                                                  final HttpServletResponse response,
                                                  final String prefix) throws IOException {
        if (spy.isHasOnRequestEvent()) {
            final OnRequest onRequest = new OnRequest(req, response, route, r -> super.doRequest(r, req, response, prefix));
            return onRequestEvent.fireAsync(onRequest, onRequest.getRoute().notificationOptions)
                    .thenCompose(it -> {
                        try {
                            return it.proceed();
                        } catch (final IOException e) {
                            final CompletableFuture<Response> future = new CompletableFuture<>();
                            future.completeExceptionally(e);
                            return future;
                        }
                    });
        }
        return super.doRequest(route, req, response, prefix);
    }

    @Override
    protected void forwardResponse(final Routes.Route route, final Response response,
                                   final HttpServletRequest request, final HttpServletResponse resp,
                                   final Function<InputStream, InputStream> responseRewriter) throws IOException {
        if (spy.isHasOnResponseEvent()) {
            final OnResponse onResponse = new OnResponse(request, resp, response, responseRewriter,
                    rewriter -> super.forwardResponse(route, response, request, resp, rewriter));
            onResponseEvent.fire(onResponse);
            if (!onResponse.isProceeded()) {
                onResponse.proceed();
            }
        } else {
            super.forwardResponse(route, response, request, resp, identity());
        }
    }
}
