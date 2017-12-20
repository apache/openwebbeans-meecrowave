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

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.WebResource;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.meecrowave.openwebbeans.OWBTomcatWebScannerService;
import org.apache.meecrowave.watching.ReloadOnChangeController;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.scanner.xbean.CdiArchive;
import org.apache.webbeans.corespi.scanner.xbean.OwbAnnotationFinder;
import org.xml.sax.InputSource;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebListener;
import javax.servlet.annotation.WebServlet;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;

public class MeecrowaveContextConfig extends ContextConfig {
    private static final byte[] DEFAULT_WEB_XML = "<web-app version=\"3.1\" />".getBytes(StandardCharsets.UTF_8);

    private final Meecrowave.Builder configuration;
    private final Map<String, Collection<Class<?>>> webClasses = new HashMap<>();
    private final boolean fixDocBase;
    private final ServletContainerInitializer intializer;
    private OwbAnnotationFinder finder;
    private ReloadOnChangeController watcher;

    public MeecrowaveContextConfig(final Meecrowave.Builder configuration, final boolean fixDocBase, final ServletContainerInitializer intializer) {
        this.configuration = configuration;
        this.fixDocBase = fixDocBase;
        this.intializer= intializer;
    }

    @Override
    protected void fixDocBase() throws IOException {
        if (!fixDocBase) {
            return;
        }
        super.fixDocBase();
    }

    @Override
    protected void webConfig() {
        if (context.getServletContext().getAttribute("meecrowave.configuration") == null) { // redeploy
            context.getServletContext().setAttribute("meecrowave.configuration", configuration);
            context.addServletContainerInitializer(intializer, emptySet());
        }

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
            final OWBTomcatWebScannerService scannerService = OWBTomcatWebScannerService.class.cast(WebBeansContext.getInstance().getScannerService());
            scannerService.setFilter(ofNullable(context.getJarScanner()).map(JarScanner::getJarScanFilter).orElse(null), context.getServletContext());
            scannerService.setDocBase(context.getDocBase());
            scannerService.setShared(configuration.getSharedLibraries());
            if (configuration.getWatcherBouncing() > 0) { // note that caching should be disabled with this config in most of the times
                watcher = new ReloadOnChangeController(context, configuration.getWatcherBouncing());
                scannerService.setFileVisitor(f -> watcher.register(f));
            }
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
    public void lifecycleEvent(final LifecycleEvent event) {
        super.lifecycleEvent(event);
        if (watcher != null && watcher.shouldRun() && Context.class.cast(event.getLifecycle()) == context) {
            if (Lifecycle.AFTER_START_EVENT.equals(event.getType())) {
                watcher.start();
            } else if (Lifecycle.BEFORE_STOP_EVENT.equals(event.getType())) {
                watcher.close();
            }
        }
    }

    @Override  // just to avoid an info log pretty useless for us
    protected InputSource getGlobalWebXmlSource() {
        return ofNullable(super.getGlobalWebXmlSource()).orElse(new InputSource(new ByteArrayInputStream(DEFAULT_WEB_XML)));
    }

    @Override
    protected void processAnnotationsWebResource(final WebResource webResource, final WebXml fragment,
                                                 final boolean handlesTypesOnly,
                                                 final Map<String, JavaClassCacheEntry> javaClassCache) {
        if (configuration.isTomcatScanning()) {
            webClasses.keySet().stream().filter(k -> k.endsWith("/WEB-INF/classes"))
                    .forEach(k -> processClasses(fragment, handlesTypesOnly, javaClassCache, k));
        }
    }

    @Override
    protected void processAnnotationsUrl(final URL url, final WebXml fragment, final boolean handlesTypesOnly,
                                         final Map<String, JavaClassCacheEntry> javaClassCache) { // use our finder
        if (!configuration.isTomcatScanning()) {
            return;
        }
        processClasses(fragment, handlesTypesOnly, javaClassCache, url.toExternalForm());
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

    private void processClasses(final WebXml fragment, final boolean handlesTypesOnly,
                                final Map<String, JavaClassCacheEntry> javaClassCache, final String key) {
        Collection<Class<?>> classes = webClasses.remove(key);
        if (classes == null && key.endsWith(".jar") && key.startsWith("file:")) { // xbean vs o.a.tomcat.u.scan.JarFileUrlJar
            classes = webClasses.remove("jar:" + key + "!/");
        }
        if (classes != null && !classes.isEmpty()) {
            final ClassLoader loader = context.getLoader().getClassLoader();
            classes.forEach(c -> {
                try (final InputStream stream = loader.getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
                    super.processAnnotationsStream(stream, fragment, handlesTypesOnly, javaClassCache);
                } catch (final IOException e) {
                    new LogFacade(MeecrowaveContextConfig.class.getName()).error("Can't parse " + c);
                }
            });
        }
    }
}
