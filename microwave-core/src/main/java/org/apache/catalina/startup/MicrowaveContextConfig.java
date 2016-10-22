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
package org.apache.catalina.startup;

import org.apache.catalina.WebResource;
import org.apache.microwave.Microwave;
import org.apache.microwave.logging.tomcat.LogFacade;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.scanner.xbean.CdiArchive;
import org.apache.webbeans.corespi.scanner.xbean.OwbAnnotationFinder;
import org.apache.webbeans.web.scanner.WebScannerService;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebListener;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class MicrowaveContextConfig extends ContextConfig {
    private final Microwave.Builder configuration;
    private final Map<String, Collection<Class<?>>> webClasses = new HashMap<>();
    private OwbAnnotationFinder finder;

    public MicrowaveContextConfig(final Microwave.Builder configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void webConfig() {
        if (!configuration.isTomcatScanning()) {
            super.webConfig();
            return;
        }

        // eagerly start CDI to scan only once and not twice (tomcat+CDI)
        final ClassLoader loader = context.getLoader().getClassLoader(); // should already be started at that point
        final Thread thread = Thread.currentThread();
        final ClassLoader old = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            final WebScannerService scannerService = WebScannerService.class.cast(WebBeansContext.getInstance().getScannerService());
            scannerService.scan();
            finder = scannerService.getFinder();
            finder.link();
            final CdiArchive archive = CdiArchive.class.cast(finder.getArchive());
            Stream.of(WebServlet.class, WebFilter.class, WebListener.class)
                    .forEach(marker -> finder.findAnnotatedClasses(marker).stream()
                            .filter(c -> !Modifier.isAbstract(c.getModifiers()) && Modifier.isPublic(c.getModifiers()))
                            .forEach(webComponent -> webClasses.computeIfAbsent(
                                    archive.classesByUrl().entrySet().stream()
                                            .filter(e -> e.getValue().getClassNames().contains(webComponent.getName()))
                                            .findFirst().get().getKey(), k -> new HashSet<>())
                                    .add(webComponent)));
        } finally {
            thread.setContextClassLoader(old);
        }
        try {
            super.webConfig();
        } finally {
            webClasses.clear();
            finder = null;
        }
    }

    @Override
    protected void processAnnotationsWebResource(final WebResource webResource, final WebXml fragment,
                                                 final boolean handlesTypesOnly,
                                                 final Map<String, JavaClassCacheEntry> javaClassCache) {
        if (configuration.isTomcatScanning()) { // TODO: use our finder
            super.processAnnotationsWebResource(webResource, fragment, handlesTypesOnly, javaClassCache);
        }
    }

    @Override
    protected void processAnnotationsUrl(final URL url, final WebXml fragment, final boolean handlesTypesOnly,
                                         final Map<String, JavaClassCacheEntry> javaClassCache) { // use our finder
        if (!configuration.isTomcatScanning()) {
            return;
        }

        final Collection<Class<?>> classes = webClasses.remove(url.toExternalForm());
        if (classes != null && !classes.isEmpty()) {
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            classes.forEach(c -> {
                try (final InputStream stream = loader.getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
                    super.processAnnotationsStream(stream, fragment, handlesTypesOnly, javaClassCache);
                } catch (final IOException e) {
                    new LogFacade(MicrowaveContextConfig.class.getName()).error("Can't parse " + c);
                }
            });
        }
    }

    @Override
    protected void processServletContainerInitializers() { // use our finder
        if (!configuration.isTomcatScanning()) {
            return;
        }

        try {
            new WebappServiceLoader<ServletContainerInitializer>(context).load(ServletContainerInitializer.class).forEach(sci -> {
                final Set<Class<?>> classes = new HashSet<>();
                initializerClassMap.put(sci, classes);

                final HandlesTypes ht;
                try {
                    ht = sci.getClass().getAnnotation(HandlesTypes.class);
                } catch (final Exception | NoClassDefFoundError e) {
                    return;
                }
                if (ht == null) {
                    return;
                }
                Stream.of(ht.value()).forEach(t -> {
                    if (t.isAnnotation()) {
                        final Class<? extends Annotation> annotation = Class.class.cast(t);
                        finder.findAnnotatedClasses(annotation).forEach(classes::add);
                    } else if (t.isInterface()) {
                        finder.findImplementations(t).forEach(classes::add);
                    } else {
                        finder.findSubclasses(t).forEach(classes::add);
                    }
                });
            });
        } catch (final IOException e) {
            ok = false;
        }
    }
}
