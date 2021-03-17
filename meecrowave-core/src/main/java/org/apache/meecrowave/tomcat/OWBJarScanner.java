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

import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.scan.Constants;
import org.apache.tomcat.util.scan.JarFactory;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.corespi.scanner.xbean.CdiArchive;
import org.apache.webbeans.corespi.scanner.xbean.OwbAnnotationFinder;
import org.apache.webbeans.spi.ScannerService;
import org.apache.webbeans.web.scanner.WebScannerService;
import org.apache.xbean.finder.util.Files;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class OWBJarScanner implements JarScanner {
    private JarScanFilter filter; // not yet used

    @Override
    public void scan(final JarScanType jarScanType, final ServletContext servletContext, final JarScannerCallback callback) {
        switch (jarScanType) {
            case PLUGGABILITY:
                final WebBeansContext owb = WebBeansContext.getInstance();
                final ScannerService scannerService = owb.getScannerService();
                if (!WebScannerService.class.isInstance(scannerService)) {
                    return;
                }
                final OwbAnnotationFinder finder = WebScannerService.class.cast(scannerService).getFinder();
                if (finder == null) {
                    return;
                }
                CdiArchive.class.cast(finder.getArchive())
                        .classesByUrl().keySet().stream()
                        .filter(u -> !"jar:file://!/".equals(u)) // not a fake in memory url
                        .forEach(u -> {
                            try {
                                final URL url = new URL(u);
                                final File asFile = Files.toFile(url);
                                if (!asFile.exists()) {
                                    return;
                                }
                                if (filter != null && !filter.check(jarScanType, asFile.getName())) {
                                    return;
                                }

                                if (asFile.getName().endsWith(Constants.JAR_EXT)) {
                                    try (final Jar jar = JarFactory.newInstance(asFile.toURI().toURL())) {
                                        callback.scan(jar, u, true);
                                    }
                                } else if (asFile.isDirectory()) {
                                    callback.scan(asFile, asFile.getAbsolutePath(), true);
                                }
                            } catch (final MalformedURLException e) {
                                // skip
                            } catch (final IOException ioe) {
                                throw new IllegalArgumentException(ioe);
                            }
                        });
                return;

            case TLD:
            default:
        }
    }

    @Override
    public JarScanFilter getJarScanFilter() {
        return filter;
    }

    @Override
    public void setJarScanFilter(final JarScanFilter jarScanFilter) {
        this.filter = jarScanFilter;
    }
}
