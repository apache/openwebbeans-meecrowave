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
package org.apache.meecrowave.proxy.servlet.front;

import static java.util.Collections.list;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.CompletionStageRxInvoker;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import org.apache.meecrowave.proxy.servlet.configuration.Routes;
import org.apache.meecrowave.proxy.servlet.service.ConfigurationLoader;

// IMPORTANT: don't make this class depending on meecrowave, cxf or our internals, use setup class
public class ProxyServlet extends HttpServlet {
    protected Routes routes;
    protected int prefixLength;

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final String prefix = req.getRequestURI().substring(prefixLength);
        final Optional<Routes.Route> matchedRoute = findRoute(req, prefix);
        if (!matchedRoute.isPresent()) {
            super.service(req, resp);
        } else {
            doExecute(matchedRoute.orElseThrow(IllegalArgumentException::new), req, resp, prefix);
        }
    }

    protected CompletionStage<HttpServletResponse> doExecute(final Routes.Route route,
                                                             final HttpServletRequest req, final HttpServletResponse resp,
                                                             final String prefix) throws IOException {
        final AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(route.clientConfiguration.timeouts.execution);

        return doRequest(route, req, resp, prefix)
                .thenAccept(response -> {
                    try {
                        forwardResponse(route, response, req, resp, identity());
                    } catch (final IOException e) {
                        onError(route, req, resp, e);
                    }
        }).exceptionally(error -> onError(route, req, resp, error)).whenComplete((a, b) -> asyncContext.complete())
                .thenApply(i -> resp);
    }

    protected CompletionStage<Response> doRequest(final Routes.Route route,
                                                  final HttpServletRequest req, final HttpServletResponse response,
                                                  final String prefix) throws IOException {
        WebTarget target = route.client.target(route.responseConfiguration.target);
        target = target.path(prefix);

        final Map<String, String> queryParams = ofNullable(req.getQueryString())
                .filter(it -> !it.isEmpty())
                .map(queries -> Stream.of(queries.split("&"))
                        .map(it -> {
                            final int eq = it.indexOf('=');
                            if (eq > 0) {
                                return new AbstractMap.SimpleEntry<>(it.substring(0, eq), it.substring(eq + 1));
                            }
                            return new AbstractMap.SimpleEntry<>(it, "");
                        })
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .orElseGet(Collections::emptyMap);
        for (final Map.Entry<String, String> q : queryParams.entrySet()) {
            target = target.queryParam(q.getKey(), q.getValue());
        }

        final String type = route.requestConfiguration.addedHeaders != null ?
                route.requestConfiguration.addedHeaders.getOrDefault("Content-Type", req.getContentType()) :
                req.getContentType();
        Invocation.Builder request = type != null ? target.request(type) : target.request();

        final Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            final String name = headerNames.nextElement();
            if (!filterHeader(route.requestConfiguration.skippedHeaders, name)) {
                request = request.header(name, list(req.getHeaders(name)));
            }
        }

        if (route.requestConfiguration.addedHeaders != null) {
            for (final Map.Entry<String, String> header: route.requestConfiguration.addedHeaders.entrySet()) {
                request = request.header(header.getKey(), header.getValue());
            }
        }

        final Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (final Cookie cookie : cookies) {
                if (filterCookie(route.requestConfiguration.skippedCookies, cookie.getName(), cookie.getValue())) {
                    continue;
                }
                request = request.cookie(
                        new javax.ws.rs.core.Cookie(cookie.getName(), cookie.getValue(), cookie.getPath(), cookie.getDomain(), cookie.getVersion()));
            }
        }

        final CompletionStageRxInvoker rx = request.rx();
        final CompletionStage<Response> result;
        if (isWrite(req)) {
            result = rx.method(req.getMethod(), Entity.entity(req.getInputStream(), ofNullable(req.getContentType()).orElse(MediaType.WILDCARD)));
        } else {
            result = rx.method(req.getMethod());
        }
        return result;
    }

    protected boolean isWrite(final HttpServletRequest req) {
        return !HttpMethod.HEAD.equalsIgnoreCase(req.getMethod()) && !HttpMethod.GET.equalsIgnoreCase(req.getMethod());
    }

    protected Void onError(final Routes.Route route,
                           final HttpServletRequest request, final HttpServletResponse resp,
                           final Throwable error) {
        try {
            if (WebApplicationException.class.isInstance(error)) {
                final WebApplicationException wae = WebApplicationException.class.cast(error);
                if (wae.getResponse() != null) {
                    forwardResponse(route, wae.getResponse(), request, resp, identity());
                    return null;
                }
            }
            onDefaultError(resp, error);
        } catch (final IOException ioe) {
            getServletContext().log(ioe.getMessage(), ioe);
            throw new IllegalStateException(ioe);
        }
        return null;
    }

    protected void onDefaultError(HttpServletResponse resp, Throwable error) throws IOException {
        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        error.printStackTrace(new PrintWriter(resp.getOutputStream()));
    }

    protected void forwardResponse(final Routes.Route route, final Response response,
                                   final HttpServletRequest request, final HttpServletResponse resp,
                                   final Function<InputStream, InputStream> responseRewriter) throws IOException {
        final int status = response.getStatus();
        resp.setStatus(status);
        forwardHeaders(route, response, resp);
        if (status == HttpServletResponse.SC_NOT_MODIFIED && resp.getHeader(HttpHeaders.CONTENT_LENGTH) == null) {
            resp.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
        }
        forwardCookies(route, response, resp);
        writeOutput(resp, responseRewriter.apply(response.readEntity(InputStream.class)));
    }

    protected void forwardCookies(final Routes.Route route, final Response response, final HttpServletResponse resp) {
        response.getCookies().entrySet().stream()
                .filter(cookie -> filterCookie(route.responseConfiguration.skippedCookies, cookie.getKey(), cookie.getValue().getValue()))
                .forEach(cookie -> addCookie(resp, cookie));
    }

    protected void addCookie(final HttpServletResponse resp, final Map.Entry<String, NewCookie> cookie) {
        final NewCookie nc = cookie.getValue();
        final Cookie servletCookie = new Cookie(cookie.getKey(), nc.getValue());
        servletCookie.setComment(nc.getComment());
        if (nc.getDomain() != null) {
            servletCookie.setDomain(nc.getDomain());
        }
        servletCookie.setHttpOnly(nc.isHttpOnly());
        servletCookie.setSecure(nc.isSecure());
        servletCookie.setMaxAge(nc.getMaxAge());
        servletCookie.setPath(nc.getPath());
        servletCookie.setVersion(nc.getVersion());
        resp.addCookie(servletCookie);
    }

    protected void forwardHeaders(final Routes.Route route, final Response response, final HttpServletResponse resp) {
        response.getHeaders().entrySet().stream()
                .filter(header -> filterHeader(route.responseConfiguration.skippedHeaders, header.getKey()))
                .flatMap(entry -> entry.getValue().stream().map(value -> new AbstractMap.SimpleEntry<>(entry.getKey(), String.valueOf(value))))
                .forEach(header -> resp.addHeader(header.getKey(), header.getValue()));
    }

    protected boolean filterCookie(final Collection<String> blacklist, final String name, final String value) {
        return value != null && (blacklist == null || blacklist.stream().anyMatch(it -> it.equalsIgnoreCase(name)));
    }

    protected boolean filterHeader(final Collection<String> blacklist, final String name) {
        return  blacklist == null || blacklist.stream().anyMatch(it -> it.equalsIgnoreCase(name));
    }

    private void writeOutput(final HttpServletResponse resp, final InputStream stream) throws IOException {
        final int bufferSize = Math.max(128, Math.min(8192, stream.available()));
        final byte[] buffer = new byte[bufferSize]; // todo: reusable (copier?)
        final ServletOutputStream outputStream = resp.getOutputStream();
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            if (read > 0) {
                outputStream.write(buffer, 0, read);
            }
        }
    }

    protected Optional<Routes.Route> findRoute(final HttpServletRequest req, final String prefix) {
        return routes == null ? empty() : routes.routes.stream()
                .filter(it -> it.requestConfiguration.method == null || it.requestConfiguration.method.equalsIgnoreCase(req.getMethod()))
                .filter(it -> it.requestConfiguration.prefix == null || it.requestConfiguration.prefix.equalsIgnoreCase(prefix))
                .findFirst();
    }

    protected Optional<Routes> loadConfiguration() {
        return get("configuration").flatMap(path -> new ConfigurationLoader(path) {
            @Override
            protected void log(final String message) {
                getServletContext().log(message);
            }
        }.load());
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        prefixLength = get("mapping")
                .map(it -> it.endsWith("/*") ? it.substring(0, it.length() - "/*".length()) : it)
                .orElse("").length() + config.getServletContext().getContextPath().length();

        final Optional<Routes> configuration = loadConfiguration();
        if (!configuration.isPresent()) {
            return;
        }
        routes = configuration.orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public void destroy() {
        if (routes != null && routes.routes != null) {
            routes.routes.forEach(it -> {
                if (it.executor != null) {
                    it.executor.shutdown();
                    try {
                        if (!it.executor.awaitTermination(it.clientConfiguration.executor.shutdownTimeout, MILLISECONDS)) {
                            getServletContext().log("Can't shutdown the client executor in " + it.clientConfiguration.executor.shutdownTimeout + "ms");
                        }
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (it.client != null) {
                    it.client.close();
                }
            });
        }
        super.destroy();
    }

    private Optional<String> get(final String key) {
        return ofNullable(getServletConfig().getInitParameter(key));
    }
}
