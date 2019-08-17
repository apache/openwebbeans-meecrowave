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
package org.apache.meecrowave.cxf;

import org.apache.cxf.cdi.CXFCdiServlet;
import org.apache.cxf.common.util.ReflectionUtil;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.jaxrs.JAXRSServiceFactoryBean;
import org.apache.cxf.jaxrs.model.ApplicationInfo;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.MethodDispatcher;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.ChainInitiationObserver;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.servlet.ServletDestination;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.logging.tomcat.LogFacade;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.ws.rs.core.Application;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

// this look a bit complicated but it just:
// - wraps cxf in a filter to support plain resources when not conflicting with application path
// - logs resources
public class CxfCdiAutoSetup implements ServletContainerInitializer {
    private static final String NAME = "cxf-cdi";

    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) {
        final Configuration builder = Configuration.class.cast(ctx.getAttribute("meecrowave.configuration"));
        final MeecrowaveCXFCdiServlet delegate = new MeecrowaveCXFCdiServlet();
        final FilterRegistration.Dynamic jaxrs = ctx.addFilter(NAME, new Filter() {
            private final String servletPath = builder.getJaxrsMapping().endsWith("/*") ?
                    builder.getJaxrsMapping().substring(0, builder.getJaxrsMapping().length() - 2) : builder.getJaxrsMapping();

            @Override
            public void init(final FilterConfig filterConfig) throws ServletException {
                delegate.init(new ServletConfig() {
                    @Override
                    public String getServletName() {
                        return NAME;
                    }

                    @Override
                    public ServletContext getServletContext() {
                        return filterConfig.getServletContext();
                    }

                    @Override
                    public String getInitParameter(final String name) {
                        return filterConfig.getInitParameter(name);
                    }

                    @Override
                    public Enumeration<String> getInitParameterNames() {
                        return filterConfig.getInitParameterNames();
                    }
                });
            }

            @Override
            public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
                if (!HttpServletRequest.class.isInstance(request)) {
                    chain.doFilter(request, response);
                    return;
                }
                final HttpServletRequest http = HttpServletRequest.class.cast(request);
                final String path = http.getRequestURI().substring(http.getContextPath().length());
                final Optional<String> app = Stream.of(delegate.prefixes).filter(path::startsWith).findAny();
                if (app.isPresent()) {
                    delegate.service(new HttpServletRequestWrapper(http) { // fake servlet pathInfo and path
                        @Override
                        public String getPathInfo() {
                            return path;
                        }

                        @Override
                        public String getServletPath() {
                            return servletPath;
                        }
                    }, response);
                } else {
                    chain.doFilter(request, response);
                }
            }

            @Override
            public void destroy() {
                delegate.destroy();
            }
        });
        jaxrs.setAsyncSupported(true);
        jaxrs.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), true, builder.getJaxrsMapping());
        ofNullable(builder.getCxfServletParams()).ifPresent(m -> m.forEach(jaxrs::setInitParameter));
    }

    private static class Logs {
        private Logs() {
            // no-op
        }

        private static String forceLength(final String httpMethod, final int l, final boolean right) {
            final String http;
            if (httpMethod == null) { // subresourcelocator implies null http method
                http = "";
            } else {
                http = httpMethod;
            }

            final StringBuilder builder = new StringBuilder();
            if (!right) {
                for (int i = 0; i < l - http.length(); i++) {
                    builder.append(" ");
                }
            }
            builder.append(http);
            if (right) {
                for (int i = 0; i < l - http.length(); i++) {
                    builder.append(" ");
                }
            }
            return builder.toString();
        }

        private static String toSimpleString(final Method mtd) {
            try {
                final StringBuilder sb = new StringBuilder();
                final Type[] typeparms = mtd.getTypeParameters();
                if (typeparms.length > 0) {
                    boolean first = true;
                    sb.append("<");
                    for (Type typeparm : typeparms) {
                        if (!first) {
                            sb.append(",");
                        }
                        sb.append(name(typeparm));
                        first = false;
                    }
                    sb.append("> ");
                }

                final Type genRetType = mtd.getGenericReturnType();
                sb.append(name(genRetType)).append(" ");
                sb.append(mtd.getName()).append("(");
                final Type[] params = mtd.getGenericParameterTypes();
                for (int j = 0; j < params.length; j++) {
                    sb.append(name(params[j]));
                    if (j < (params.length - 1)) {
                        sb.append(", ");
                    }
                }
                sb.append(")");
                final Type[] exceptions = mtd.getGenericExceptionTypes();
                if (exceptions.length > 0) {
                    sb.append(" throws ");
                    for (int k = 0; k < exceptions.length; k++) {
                        sb.append(name(exceptions[k]));
                        if (k < (exceptions.length - 1)) {
                            sb.append(",");
                        }
                    }
                }
                return sb.toString();
            } catch (final Exception e) {
                return "<" + e + ">";
            }
        }

        private static String name(final Type type) {
            if (type instanceof Class<?>) {
                return ((Class) type).getSimpleName().replace("java.lang.", "").replace("java.util", "");
            } else if (type instanceof ParameterizedType) {
                final ParameterizedType pt = (ParameterizedType) type;
                final StringBuilder builder = new StringBuilder();
                builder.append(name(pt.getRawType()));
                final Type[] args = pt.getActualTypeArguments();
                if (args != null) {
                    builder.append("<");
                    for (int i = 0; i < args.length; i++) {
                        builder.append(name(args[i]));
                        if (i < args.length - 1) {
                            builder.append(", ");
                        }
                    }
                    builder.append(">");
                }
                return builder.toString();
            }
            return type.toString();
        }

        private static String singleSlash(final String address, final String value) {
            if (address.endsWith("/") && value.startsWith("/")) {
                return address + value.substring(1);
            }
            if (!address.endsWith("/") && !value.startsWith("/")) {
                return address + '/' + value;
            }
            if ("/".equals(value)) {
                return address;
            }
            return address + value;
        }

        private static class LogOperationEndpointInfo implements Comparable<LogOperationEndpointInfo> {
            private final String http;
            private final String address;
            private final String method;

            private LogOperationEndpointInfo(final String http, final String address, final String method) {
                this.address = address;
                this.method = method;

                if (http != null) {
                    this.http = http;
                } else { // can happen with subresource locators
                    this.http = "";
                }
            }

            @Override
            public int compareTo(final LogOperationEndpointInfo o) {
                int compare = http.compareTo(o.http);
                if (compare != 0) {
                    return compare;
                }

                compare = address.compareTo(o.address);
                if (compare != 0) {
                    return compare;
                }

                return method.compareTo(o.method);
            }
        }

        private static class LogResourceEndpointInfo implements Comparable<LogResourceEndpointInfo> {
            private final String address;
            private final String classname;
            private final List<LogOperationEndpointInfo> operations;
            private final int methodSize;
            private final int methodStrSize;

            private LogResourceEndpointInfo(final String address, final String classname,
                                            final List<LogOperationEndpointInfo> operations,
                                            final int methodSize, final int methodStrSize) {
                this.address = address;
                this.classname = classname;
                this.operations = operations;
                this.methodSize = methodSize;
                this.methodStrSize = methodStrSize;
            }

            @Override
            public int compareTo(final LogResourceEndpointInfo o) {
                final int compare = address.compareTo(o.address);
                if (compare != 0) {
                    return compare;
                }
                return classname.compareTo(o.classname);
            }
        }
    }

    private static class MeecrowaveCXFCdiServlet extends CXFCdiServlet {
        private String[] prefixes;

        @Override
        public void init(final ServletConfig sc) throws ServletException {
            super.init(sc);

            // just logging the endpoints
            final LogFacade log = new LogFacade(CxfCdiAutoSetup.class.getName());
            final String transportId = sc.getInitParameter(TRANSPORT_ID);
            final DestinationRegistry registry = getDestinationRegistryFromBusOrDefault(transportId);
            prefixes = registry.getDestinations().stream()
                    .filter(ServletDestination.class::isInstance)
                    .map(ServletDestination.class::cast)
                    .map(getServletDestinationPath(sc, log))
                    .filter(Objects::nonNull)
                    .toArray(String[]::new);
        }

        private Function<ServletDestination, String> getServletDestinationPath(ServletConfig sc, LogFacade log)
        {
            return sd -> {
                final Endpoint endpoint = ChainInitiationObserver.class.cast(sd.getMessageObserver()).getEndpoint();
                final ApplicationInfo app = ApplicationInfo.class.cast(endpoint.get(Application.class.getName()));
                final JAXRSServiceFactoryBean sfb = JAXRSServiceFactoryBean.class.cast(endpoint.get(JAXRSServiceFactoryBean.class.getName()));
                final String base = sd.getEndpointInfo().getAddress();

                if (sfb != null) {
                    final List<Logs.LogResourceEndpointInfo> resourcesToLog = new ArrayList<>();
                    int classSize = 0;
                    int addressSize = 0;

                    final List<ClassResourceInfo> resources = sfb.getClassResourceInfo();
                    for (final ClassResourceInfo info : resources) {
                        if (info.getResourceClass() == null) { // possible?
                            continue;
                        }

                        final String address = Logs.singleSlash(base, info.getURITemplate().getValue());

                        final String clazz = uproxyName(info.getResourceClass().getName());
                        classSize = Math.max(classSize, clazz.length());
                        addressSize = Math.max(addressSize, address.length());

                        int methodSize = 7;
                        int methodStrSize = 0;

                        final List<Logs.LogOperationEndpointInfo> toLog = new ArrayList<>();

                        final MethodDispatcher md = info.getMethodDispatcher();
                        for (final OperationResourceInfo ori : md.getOperationResourceInfos()) {
                            final String httpMethod = ori.getHttpMethod();
                            final String currentAddress = Logs.singleSlash(address, ori.getURITemplate().getValue());
                            final String methodToStr = Logs.toSimpleString(ori.getMethodToInvoke());
                            toLog.add(new Logs.LogOperationEndpointInfo(httpMethod, currentAddress, methodToStr));

                            if (httpMethod != null) {
                                methodSize = Math.max(methodSize, httpMethod.length());
                            }
                            addressSize = Math.max(addressSize, currentAddress.length());
                            methodStrSize = Math.max(methodStrSize, methodToStr.length());
                        }

                        Collections.sort(toLog);

                        resourcesToLog.add(new Logs.LogResourceEndpointInfo(address, clazz, toLog, methodSize, methodStrSize));
                    }

                    // effective logging
                    log.info("REST Application: " + endpoint.getEndpointInfo().getAddress() + " -> "
                            + ofNullable(app).map(ApplicationInfo::getResourceClass).map(Class::getName).map(CxfCdiAutoSetup::uproxyName).orElse(""));

                    Collections.sort(resourcesToLog);
                    final int fClassSize = classSize;
                    final int fAddressSize = addressSize;
                    resourcesToLog.forEach(resource -> {
                        log.info("     Service URI: "
                                + Logs.forceLength(resource.address, fAddressSize, true) + " -> "
                                + Logs.forceLength(resource.classname, fClassSize, true));

                        resource.operations.forEach(info -> {
                            log.info("          "
                                    + Logs.forceLength(info.http, resource.methodSize, false) + " "
                                    + Logs.forceLength(info.address, fAddressSize, true) + " ->      "
                                    + Logs.forceLength(info.method, resource.methodStrSize, true));
                        });
                        resource.operations.clear();
                    });
                    resourcesToLog.clear();

                    // log @Providers
                    if (Configuration.class.cast(sc.getServletContext().getAttribute("meecrowave.configuration")).isJaxrsLogProviders()) {
                        final ServerProviderFactory spf = ServerProviderFactory.class.cast(endpoint.get(ServerProviderFactory.class.getName()));
                        dump(log, spf, "MessageBodyReaders", "messageReaders");
                        dump(log, spf, "MessageBodyWriters", "messageWriters");
                    }
                } else {
                    final EndpointInfo endpointInfo = endpoint.getEndpointInfo();
                    if (endpointInfo.getName() != null) {
                        log.info("@WebService > " + endpointInfo.getName().toString() + " -> " + base);
                    }
                }

                return base;
            };
        }

        private void dump(final LogFacade log, final ServerProviderFactory spf, final String description, final String fieldName) {
            final Field field = ReflectionUtil.getDeclaredField(ProviderFactory.class, fieldName);
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            try {
                final Collection<ProviderInfo<?>> providers = Collection.class.cast(field.get(spf));
                log.info("     " + description);
                providers.stream().map(ProviderInfo::getProvider).forEach(o -> {
                    try {
                        log.info("       - " + o);
                    } catch (final RuntimeException re) {
                        // no-op: maybe cdi context is not active
                    }
                });
            } catch (IllegalAccessException e) {
                // ignore, not that a big deal
            }
        }
    }

    private static String uproxyName(final String clazz) {
        if (clazz.contains("$$")) {
            return clazz.substring(0, clazz.indexOf("$$"));
        }
        return clazz;
    }
}
