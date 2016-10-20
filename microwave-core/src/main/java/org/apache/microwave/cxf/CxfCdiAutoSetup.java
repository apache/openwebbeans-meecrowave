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
package org.apache.microwave.cxf;

import org.apache.cxf.cdi.CXFCdiServlet;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.jaxrs.JAXRSServiceFactoryBean;
import org.apache.cxf.jaxrs.model.ApplicationInfo;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.MethodDispatcher;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;
import org.apache.cxf.transport.ChainInitiationObserver;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.servlet.ServletDestination;
import org.apache.johnzon.jaxrs.DelegateProvider;
import org.apache.johnzon.jaxrs.JohnzonProvider;
import org.apache.johnzon.jaxrs.JsrProvider;
import org.apache.microwave.Microwave;
import org.apache.microwave.logging.tomcat.LogFacade;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.ws.rs.core.Application;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

public class CxfCdiAutoSetup implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) throws ServletException {
        final Microwave.Builder builder = Microwave.Builder.class.cast(ctx.getAttribute("microwave.configuration"));
        final ServletRegistration.Dynamic jaxrs = ctx.addServlet("cxf-cdi", new CXFCdiServlet() {
            @Override
            protected void loadBus(final ServletConfig servletConfig) {
                super.loadBus(servletConfig);
                if (!builder.isJaxrsProviderSetup()) {
                    return;
                }

                final List<DelegateProvider<?>> providers = asList(new JohnzonProvider<>(), new JsrProvider());

                // client
                if (bus.getProperty("org.apache.cxf.jaxrs.bus.providers") == null) {
                    bus.setProperty("skip.default.json.provider.registration", "true");
                    bus.setProperty("org.apache.cxf.jaxrs.bus.providers", providers);
                }

                // server
                getDestinationRegistryFromBus().getDestinations()
                        .forEach(d -> {
                            final ChainInitiationObserver observer = ChainInitiationObserver.class.cast(d.getMessageObserver());
                            final ServerProviderFactory providerFactory = ServerProviderFactory.class.cast(observer.getEndpoint().get(ServerProviderFactory.class.getName()));
                            providerFactory.setUserProviders(providers);
                        });
            }

            @Override
            public void init(final ServletConfig sc) throws ServletException {
                super.init(sc);

                // just logging the endpoints
                final LogFacade log = new LogFacade(CxfCdiAutoSetup.class.getName());
                final DestinationRegistry registry = getDestinationRegistryFromBus();
                registry.getDestinations().stream()
                        .filter(ServletDestination.class::isInstance)
                        .map(ServletDestination.class::cast)
                        .forEach(sd -> {
                            final Endpoint endpoint = ChainInitiationObserver.class.cast(sd.getMessageObserver()).getEndpoint();
                            final ApplicationInfo app = ApplicationInfo.class.cast(endpoint.get(Application.class.getName()));
                            final JAXRSServiceFactoryBean sfb = JAXRSServiceFactoryBean.class.cast(endpoint.get(JAXRSServiceFactoryBean.class.getName()));

                            final List<Logs.LogResourceEndpointInfo> resourcesToLog = new ArrayList<>();
                            int classSize = 0;
                            int addressSize = 0;

                            final String base = sd.getEndpointInfo().getAddress();
                            final List<ClassResourceInfo> resources = sfb.getClassResourceInfo();
                            for (final ClassResourceInfo info : resources) {
                                if (info.getResourceClass() == null) { // possible?
                                    continue;
                                }

                                final String address = Logs.singleSlash(base, info.getURITemplate().getValue());

                                final String clazz = info.getResourceClass().getName();
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
                                    + ofNullable(app).map(ApplicationInfo::getResourceClass).map(Class::getName).orElse(""));

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
                        });
            }
        });
        jaxrs.setLoadOnStartup(1);
        jaxrs.setAsyncSupported(true);
        jaxrs.addMapping(builder.getJaxrsMapping());
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

}
