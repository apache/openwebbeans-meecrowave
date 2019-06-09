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
import java.util.concurrent.CompletionStage;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.apache.meecrowave.proxy.servlet.configuration.Routes;
import org.apache.meecrowave.proxy.servlet.front.cdi.func.IOFunction;

public class OnRequest extends BaseEvent {
    private Routes.Route route;
    private IOFunction<Routes.Route, CompletionStage<Response>> delegate;

    public OnRequest(final HttpServletRequest request, final HttpServletResponse response,
                     final Routes.Route defaultRoute,
                     final IOFunction<Routes.Route, CompletionStage<Response>> defaultImpl) {
        super(request, response);
        this.route = defaultRoute;
        this.delegate = defaultImpl;
    }

    public Routes.Route getRoute() {
        return route;
    }

    public CompletionStage<Response> proceed() throws IOException {
        return delegate.apply(route);
    }

    public void route(final Routes.Route route) {
        this.route = route;
    }

    public void provide(final IOFunction<Routes.Route, CompletionStage<Response>> impl) {
        this.delegate = impl;
    }

    public void provide(final CompletionStage<Response> promise) {
        provide(route -> promise);
    }
}
