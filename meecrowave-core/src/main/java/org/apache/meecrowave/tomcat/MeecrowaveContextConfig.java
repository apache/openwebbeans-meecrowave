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
package org.apache.meecrowave.tomcat;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Optional.ofNullable;

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
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebListener;
import javax.servlet.annotation.WebServlet;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.WebappServiceLoader;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.meecrowave.openwebbeans.OWBTomcatWebScannerService;
import org.apache.meecrowave.watching.ReloadOnChangeController;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.bcel.classfile.ClassParser;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.scanner.xbean.CdiArchive;
import org.apache.webbeans.corespi.scanner.xbean.OwbAnnotationFinder;
import org.apache.webbeans.spi.ScannerService;
import org.xml.sax.InputSource;

public class MeecrowaveContextConfig extends ContextConfig {
    private static final byte[] DEFAULT_WEB_XML = "<web-app version=\"3.1\" />".getBytes(StandardCharsets.UTF_8);

    private final Configuration configuration;
    private final Map<String, Collection<Class<?>>> webClasses = new HashMap<>();
    private final boolean fixDocBase;
    private final ServletContainerInitializer intializer;
	private final Consumer<Context> redeployCallback;
    private OwbAnnotationFinder finder;
    private ReloadOnChangeController watcher;

    public MeecrowaveContextConfig(final Configuration configuration, final boolean fixDocBase, final ServletContainerInitializer intializer, final Consumer<Context> redeployCallback) {
        this.configuration = configuration;
        this.fixDocBase = fixDocBase;
        this.intializer= intializer;
        this.redeployCallback = redeployCallback;
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
            context.getServletContext().setAttribute("meecrowave.configuration",
                    Meecrowave.Builder.class.isInstance(configuration) ? configuration : new Meecrowave.Builder(configuration));
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
            final ScannerService service = WebBeansContext.getInstance().getScannerService();
            if (OWBTomcatWebScannerService.class.isInstance(service)) {
                final OWBTomcatWebScannerService scannerService = OWBTomcatWebScannerService.class.cast(service);
                scannerService.setFilter(ofNullable(context.getJarScanner()).map(JarScanner::getJarScanFilter).orElse(null), context.getServletContext());
                scannerService.setDocBase(context.getDocBase());
                scannerService.setShared(configuration.getSharedLibraries());
                if (configuration.getWatcherBouncing() > 0) { // note that caching should be disabled with this config in most of the times
                    watcher = new ReloadOnChangeController(context, configuration.getWatcherBouncing(), redeployCallback);
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
            }

            super.webConfig();
        } finally {
            thread.setContextClassLoader(old);
            webClasses.clear();
            finder = null;
        }
    }

    @Override
    protected void processClasses(final WebXml webXml, final Set<WebXml> orderedFragments) {
        final ClassLoader loader = context.getLoader().getClassLoader();
        orderedFragments.forEach(fragment -> {
            final WebXml annotations = new WebXml();
            annotations.setDistributable(true);
            final URL url = fragment.getURL();
            String urlString = url.toExternalForm();
            Collection<Class<?>> classes = webClasses.get(urlString);
            if (classes == null) { // mainly java 11, no need on java 8
                if (urlString.startsWith("file:") && urlString.endsWith("jar")) {
                    urlString = "jar:" + urlString + "!/";
                } else {
                    return;
                }
                classes = webClasses.get(urlString);
                if (classes == null) {
                    return;
                }
            }
            classes.forEach(clazz -> {
                try (final InputStream stream = loader.getResourceAsStream(clazz.getName().replace('.', '/') + ".class")) {
                    processClass(annotations, new ClassParser(stream).parse());
                } catch (final IOException e) {
                    new LogFacade(MeecrowaveContextConfig.class.getName()).error("Can't parse " + clazz);
                }
            });
            fragment.merge(singleton(annotations));
        });
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
                        classes.addAll(finder.findAnnotatedClasses(annotation));
                    } else if (t.isInterface()) {
                        classes.addAll(finder.findImplementations(t));
                    } else {
                        classes.addAll(finder.findSubclasses(t));
                    }
                });
            });
        } catch (final IOException e) {
            ok = false;
        }
    }
}
