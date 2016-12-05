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

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.logging.tomcat.LogFacade;
import org.apache.tomcat.JarScanFilter;
import org.apache.webbeans.web.scanner.WebScannerService;

import javax.servlet.ServletContext;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.apache.tomcat.JarScanType.PLUGGABILITY;

public class OWBTomcatWebScannerService extends WebScannerService {
    private final LogFacade logger = new LogFacade(OWBTomcatWebScannerService.class.getName());

    private JarScanFilter filter;
    private String jreBase;

    // just for logging (== temp)
    private final Set<String> urls = new HashSet<>();
    private String docBase;
    private String shared;

    @Override
    public void init(final Object context) {
        // no-op
    }

    @Override
    public void scan() {
        if (finder != null) {
            return;
        }
        super.scan();
        if (!urls.isEmpty()) {
            logger.info("OpenWebBeans scanning:");
            final String m2 = new File(System.getProperty("user.home", "."), ".m2/repository").getAbsolutePath();
            final String base = ofNullable(docBase).orElse("$$$");
            final String sharedBase = ofNullable(shared).orElse("$$$");
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
                }

                return shownValue;
            }).sorted().forEach(v -> logger.info("    " + v));
        }
        urls.clear(); // no more needed
        filter = null;
        docBase = null;
        shared = null;
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
                return filter.check(PLUGGABILITY, path.substring(lastSep + 1, path.length() - 2)) ?
                        -1 : (path.indexOf(".jar") - 1 /*should be lastIndexOf but filterExcludedJar logic would be broken*/);
            }
        }

        final int filenameIdx = path.replace(File.separatorChar, '/').replace("!/", "").lastIndexOf('/') + 1;
        if (filenameIdx < 0 || filenameIdx >= path.length()) { // unlikely
            return -1;
        }

        return filter.check(PLUGGABILITY, path.substring(filenameIdx)) ? -1 : (path.indexOf(".jar") - 1);
    }

    // replace init
    public void setFilter(final JarScanFilter filter, final ServletContext ctx) {
        this.filter = filter;

        super.init(ctx);
        if (this.filter == null) {
            final Meecrowave.Builder config = Meecrowave.Builder.class.cast(ServletContext.class.cast(ctx).getAttribute("meecrowave.configuration"));
            this.filter = new KnownJarsFilter(config);
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
}
