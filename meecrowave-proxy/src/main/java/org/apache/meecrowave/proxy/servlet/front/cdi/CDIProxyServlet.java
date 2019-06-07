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

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.meecrowave.proxy.servlet.configuration.Routes;
import org.apache.meecrowave.proxy.servlet.front.ProxyServlet;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.AfterResponse;
import org.apache.meecrowave.proxy.servlet.front.cdi.event.BeforeRequest;

// IMPORTANT: don't make this class depending on meecrowave, cxf or our internals, use setup class
public class CDIProxyServlet extends ProxyServlet {
    @Inject
    private Event<BeforeRequest> beforeRequestEvent;

    @Inject
    private Event<AfterResponse> afterResponseEvent;

    @Override
    protected CompletionStage<HttpServletResponse> doExecute(final Routes.Route route, final HttpServletRequest req, final HttpServletResponse resp,
                                                             final String prefix) throws IOException {
        final BeforeRequest event = new BeforeRequest(req, resp);
        event.setRoute(route);
        event.setPrefix(prefix);
        beforeRequestEvent.fire(event);
        return super.doExecute(event.getRoute(), req, resp, event.getPrefix())
            .handle((r, t) -> {
                afterResponseEvent.fire(new AfterResponse(req, resp));
                return r;
            });
    }
}
