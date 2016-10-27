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
package org.apache.microwave.openwebbeans;

import org.apache.microwave.logging.tomcat.LogFacade;
import org.apache.tomcat.JarScanFilter;
import org.apache.webbeans.web.scanner.WebScannerService;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Optional.ofNullable;
import static org.apache.tomcat.JarScanType.PLUGGABILITY;

public class OWBTomcatWebScannerService extends WebScannerService {
    private final LogFacade logger = new LogFacade(OWBTomcatWebScannerService.class.getName());

    private JarScanFilter filter;
    private String jreBase;

    // just for logging (== temp)
    private final List<String> urls = new ArrayList<>(8);
    private String docBase;

    @Override
    public void scan() {
        if (finder != null) {
            return;
        }
        super.scan();
        if (!urls.isEmpty()) {
            Collections.sort(urls);
            logger.info("OpenWebBeans scanning:");
            final String m2 = new File(System.getProperty("user.home", "."), ".m2/repository").getAbsolutePath();
            final String base = ofNullable(docBase).orElse("$$$");
            urls.forEach(u -> {
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
                }

                // finally log
                logger.info("    " + shownValue);
            });
            urls.clear(); // no more needed
        }
        filter = null;
    }

    @Override
    protected void filterExcludedJars(final Set<URL> classPathUrls) {
        if (filter == null) {
            filter = new KnownJarsFilter();
        }

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

        final int filenameIdx = path.replace(File.separatorChar, '/').replace("!/", "").lastIndexOf('/') + 1;
        if (filenameIdx < 0 || filenameIdx >= path.length()) { // unlikely
            return -1;
        }

        return filter.check(PLUGGABILITY, path.substring(filenameIdx)) ? -1 : (path.indexOf(".jar") - 1);
    }

    public void setFilter(final JarScanFilter filter) {
        this.filter = filter;
    }

    @Override
    protected void addWebBeansXmlLocation(final URL beanArchiveUrl) {
        urls.add(beanArchiveUrl.toExternalForm());
        // we just customize the logging
        super.doAddWebBeansXmlLocation(beanArchiveUrl);
    }

    public void setDocBase(final String docBase) {
        this.docBase = docBase;
    }
}
