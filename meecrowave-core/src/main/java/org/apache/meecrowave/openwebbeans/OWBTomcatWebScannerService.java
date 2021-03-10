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
package org.apache.meecrowave.openwebbeans;

import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.tomcat.JarScanFilter;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.scanner.xbean.CdiArchive;
import org.apache.webbeans.corespi.scanner.xbean.OwbAnnotationFinder;
import org.apache.webbeans.spi.BDABeansXmlScanner;
import org.apache.webbeans.spi.BdaScannerService;
import org.apache.webbeans.spi.BeanArchiveService;
import org.apache.webbeans.util.WebBeansUtil;
import org.apache.webbeans.web.scanner.WebScannerService;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.filter.Filter;

import javax.servlet.ServletContext;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static org.apache.tomcat.JarScanType.PLUGGABILITY;

public class OWBTomcatWebScannerService extends WebScannerService {
    private final LogFacade logger = new LogFacade(OWBTomcatWebScannerService.class.getName());
    private final BdaScannerService delegate;
    private final Supplier<OwbAnnotationFinder> finderAccessor;

    protected JarScanFilter filter;
    private String jreBase;

    // just for logging (== temp)
    private final Set<String> urls = new HashSet<>();
    private String docBase;
    private String shared;
    private Consumer<File> fileVisitor;

    public OWBTomcatWebScannerService() {
        this(null, null);
    }


    public OWBTomcatWebScannerService(final BdaScannerService delegate, final Supplier<OwbAnnotationFinder> finderAccessor) {
        this.delegate = delegate;
        this.finderAccessor = finderAccessor;
    }

    @Override
    public void init(final Object context) {
        if (delegate != null) {
            delegate.init(context);
        }
    }

    @Override
    public Set<URL> getBeanXmls() {
        if (delegate == null) {
            return super.getBeanXmls();
        }
        return delegate.getBeanXmls();
    }

    @Override
    public boolean isBDABeansXmlScanningEnabled() {
        if (delegate == null) {
            return super.isBDABeansXmlScanningEnabled();
        }
        return delegate.isBDABeansXmlScanningEnabled();
    }

    @Override
    public BDABeansXmlScanner getBDABeansXmlScanner() {
        if (delegate == null) {
            return super.getBDABeansXmlScanner();
        }
        return delegate.getBDABeansXmlScanner();
    }

    @Override
    public OwbAnnotationFinder getFinder() {
        if (finderAccessor != null) {
            return finderAccessor.get();
        }
        return super.getFinder();
    }

    @Override
    public Map<BeanArchiveService.BeanArchiveInformation, Set<Class<?>>> getBeanClassesPerBda() {
        if (delegate != null) {
            return delegate.getBeanClassesPerBda();
        }
        return super.getBeanClassesPerBda();
    }

    @Override
    public void release() {
        if (delegate != null) {
            delegate.release();
        } else {
            super.release();
        }
    }

    @Override
    public Set<Class<?>> getBeanClasses() {
        if (delegate != null) {
            return delegate.getBeanClasses();
        }
        return super.getBeanClasses();
    }

    @Override
    public void scan() {
        if (delegate != null) {
            if (getFinder() == null) {
                delegate.scan();
            }
            if (finder == null) {
                finder = getFinder();
            }
        }

        if (finder != null) {
            return;
        }

        super.scan();
        scanGroovy(WebBeansUtil.getCurrentClassLoader());
        if (!urls.isEmpty()) {
            logger.info("OpenWebBeans scanning:");
            final String m2 = new File(System.getProperty("user.home", "."), ".m2/repository").getAbsolutePath();
            final String base = ofNullable(docBase).orElse("$$$");
            final String sharedBase = ofNullable(shared).orElse("$$$");
            final String runnerBase = ofNullable(System.getProperty("meecrowave.base")).orElse("$$$");
            final String runnerHome = ofNullable(System.getProperty("meecrowave.home")).orElse("$$$");
            urls.stream().map(u -> {
                String shownValue = u
                        // protocol
                        .replace("file://", "")
                        .replace("file:", "")
                        .replace("jar:", "")
                        // URL suffix
                        .replace("!/META-INF/beans.xml", "")
                        .replace("!/", "")
                        // beans.xml
                        .replace("META-INF/beans.xml", "");

                // try to make it shorter
                if (shownValue.startsWith(m2)) {
                    shownValue = "${maven}/" + shownValue.substring(shownValue.replace(File.separatorChar, '/').lastIndexOf('/') + 1);
                } else if (shownValue.startsWith(base)) {
                    shownValue = "${app}" + shownValue.replace(base, "");
                } else if (shownValue.startsWith(sharedBase)) {
                    shownValue = "${shared}" + shownValue.replace(sharedBase, "");
                } else if (shownValue.startsWith(runnerBase)) {
                    shownValue = "${base}" + shownValue.replace(runnerBase, "");
                } else if (shownValue.startsWith(runnerHome)) {
                    shownValue = "${home}" + shownValue.replace(runnerHome, "");
                }

                return shownValue;
            }).sorted().forEach(v -> logger.info("    " + v));

            if (fileVisitor != null) {
                urls.stream()
                        .filter(this::isFile)
                        .map(this::toFile)
                        .filter(File::isDirectory)
                        .forEach(fileVisitor);
            }
        }
        urls.clear(); // no more needed
        filter = null;
        docBase = null;
        shared = null;
    }

    private File toFile(final String url) {
        try {
            return new File(new URL(url).getFile());
        } catch (final MalformedURLException e) {
            return new File(url.substring("file://".length(), url.length()));
        }
    }

    private boolean isFile(final String url) {
        return url.startsWith("file:") && !url.endsWith("!/") && !url.endsWith("!/META-INF/beans.xml");
    }

    private void scanGroovy(final ClassLoader currentClassLoader) {
        if (currentClassLoader == null || !currentClassLoader.getClass().getName().equals("groovy.lang.GroovyClassLoader")) {
            return;
        }
        try {
            final Class<?>[] getLoadedClasses = Class[].class.cast(
                    currentClassLoader.getClass()
                            .getMethod("getLoadedClasses")
                            .invoke(currentClassLoader));
            addClassesToDefault(getLoadedClasses);
        } catch (final Exception e) {
            new LogFacade(OWBTomcatWebScannerService.class.getName()).warn(e.getMessage());
        }
    }

    private void addClassesToDefault(final Class<?>[] all) throws Exception {
        if (all == null || all.length == 0) {
            return;
        }

        final Field linking = AnnotationFinder.class.getDeclaredField("linking");
        final Method readClassDef = AnnotationFinder.class.getDeclaredMethod("readClassDef", Class.class);
        if (!readClassDef.isAccessible()) {
            readClassDef.setAccessible(true);
        }
        if (!linking.isAccessible()) {
            linking.setAccessible(true);
        }

        final URI uri = URI.create("jar:file://!/"); // we'll never find it during scanning and it avoids to create a custom handler
        final URL url = uri.toURL();
        final String key = uri.toASCIIString();
        CdiArchive.FoundClasses foundClasses = archive.classesByUrl().get(key);
        if (foundClasses == null) {
            final BeanArchiveService beanArchiveService = webBeansContext().getBeanArchiveService();
            foundClasses = new CdiArchive.FoundClasses(url, new HashSet<>(), beanArchiveService.getBeanArchiveInformation(url));
            archive.classesByUrl().put(key, foundClasses);
        }

        foundClasses.getClassNames().addAll(Stream.of(all).map(Class::getName).collect(toSet()));

        try {
            linking.set(finder, true);

            Stream.of(all).forEach(c -> { // populate classInfos map to support annotated mode which relies on ClassInfo
                try {
                    readClassDef.invoke(finder, c);
                } catch (final IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalStateException(e.getCause());
                }
            });
        } finally {
            try {
                linking.set(finder, false);
            } catch (final IllegalAccessException e) {
                // no-op
            }
        }
    }

    @Override
    protected void filterExcludedJars(final Set<URL> classPathUrls) {
        String jreBaseTmp;
        try {
            jreBaseTmp = new File(System.getProperty("java.home")).toURI().toURL().toExternalForm();
        } catch (MalformedURLException e) {
            jreBaseTmp = System.getProperty("java.home");
        }
        jreBase = jreBaseTmp;

        super.filterExcludedJars(classPathUrls);
    }

    @Override
    protected int isExcludedJar(final String path) {
        if (path.startsWith(jreBase) || (path.startsWith("jar:") && path.indexOf(jreBase) == 4)) {
            return jreBase.length();
        }

        // jar:file:spring-boot-cdi-launcher-1.0-SNAPSHOT.jar!/BOOT-INF/lib/x.jar!/
        if (path.startsWith("jar:file:") && path.endsWith(".jar!/")) {
            final int lastSep = path.substring(0, path.length() - 2).lastIndexOf('/');
            if (lastSep > 0) {
                return filter != null && filter.check(PLUGGABILITY, path.substring(lastSep + 1, path.length() - 2)) ?
                        -1 : (path.indexOf(".jar") - 1 /*should be lastIndexOf but filterExcludedJar logic would be broken*/);
            }
        }

        final int filenameIdx = path.replace(File.separatorChar, '/').replace("!/", "").lastIndexOf('/') + 1;
        if (filenameIdx < 0 || filenameIdx >= path.length()) { // unlikely
            return -1;
        }

        return filter!= null && filter.check(PLUGGABILITY, path.substring(filenameIdx)) ? -1 : (path.indexOf(".jar") - 1);
    }

    // replace init
    public void setFilter(final JarScanFilter filter, final ServletContext ctx) {
        this.filter = filter;

        super.init(ctx);
        final Configuration config = Configuration.class.cast(ServletContext.class.cast(ctx).getAttribute("meecrowave.configuration"));
        if (this.filter == null) {
            this.filter = new KnownJarsFilter(config);
        }

        final Filter userFilter = webBeansContext().getService(Filter.class);
        if (KnownClassesFilter.class.isInstance(userFilter)) {
            KnownClassesFilter.class.cast(userFilter).init(config);
        }
    }

    @Override
    protected void addWebBeansXmlLocation(final URL beanArchiveUrl) {
        final String url = beanArchiveUrl.toExternalForm();
        if (urls.add(of(url)
                .map(s -> s.startsWith("jar:") && s.endsWith("!/META-INF/beans.xml") ? s.substring("jar:".length(), s.length() - "!/META-INF/beans.xml".length()) : s)
                .get())) {
            super.doAddWebBeansXmlLocation(beanArchiveUrl);
        }
    }

    public void setShared(final String shared) {
        this.shared = ofNullable(shared).map(File::new).filter(File::isDirectory).map(File::getAbsolutePath).orElse(null);
    }

    public void setDocBase(final String docBase) {
        this.docBase = docBase;
    }

    public void setFileVisitor(final Consumer<File> fileVisitor) {
        this.fileVisitor = fileVisitor;
    }

    //don't rename this method - in case of owb2 it overrides the method defined in AbstractMetaDataDiscovery
    protected WebBeansContext webBeansContext() {
        return WebBeansContext.getInstance(); //only way to be compatible with owb 1.7.x and 2.x (without reflection)
    }
}
