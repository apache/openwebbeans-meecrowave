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
package org.apache.meecrowave.doc.generator;

import org.apache.commons.lang3.text.WordUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

// helper to generate the download table content
public class Downloads {
    private static final SAXParserFactory FACTORY = SAXParserFactory.newInstance();
    private static final String MVN_BASE = "https://repo.maven.apache.org/maven2/";

    private static final String DIST_RELEASE = "https://dist.apache.org/repos/dist/release/openwebbeans/meecrowave/";
    private static final String ARCHIVE_RELEASE = "https://archive.apache.org/dist/openwebbeans/meecrowave/";
    private static final String MIRROR_RELEASE = "http://www.apache.org/dyn/closer.lua/openwebbeans/meecrowave/";

    private static final long MEGA_RATIO = 1024 * 1024;
    private static final long KILO_RATIO = 1024;

    static {
        FACTORY.setNamespaceAware(false);
        FACTORY.setValidating(false);
    }

    private Downloads() {
        // no-op
    }

    public static void main(final String[] args) {
        doMain(System.out);
    }

    public static void doMain(final PrintStream stream) {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "32");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

        Stream.of(
                Stream.of("org/apache/meecrowave/meecrowave")
                        .flatMap(Downloads::toVersions)
                        .map(v -> v.extensions("zip"))
                        .map(v -> v.classifiers("source-release")),
                versionStream("meecrowave-core")
                        .map(v -> v.classifiers("", "runner"))
                        .map(v -> v.extensions("jar")))
              .flatMap(s -> s)
              .flatMap(Downloads::toDownloadable)
              .parallel()
              .map(Downloads::fillDownloadable)
              .filter(Objects::nonNull)
              .sorted((o1, o2) -> {
                    final int versionComp = o2.version.compareTo(o1.version);
                    if (versionComp != 0) {
                        if (o2.version.startsWith(o1.version) && o2.version.contains("-M")) { // milestone
                            return -1;
                        }
                        if (o1.version.startsWith(o2.version) && o1.version.contains("-M")) { // milestone
                            return 1;
                        }
                        return versionComp;
                    }


                    final int nameComp = o1.name.compareTo(o2.name);
                    if (nameComp != 0) {
                        return nameComp;
                    }

                    final long dateComp = o2.date.compareTo(o1.date);
                    if (dateComp != 0) {
                        return (int) dateComp;
                    }

                    return o1.url.compareTo(o2.url);
                })
              .collect(toList())
              .forEach(d ->
                        stream.println("" +
                                "|" + d.name + (d.classifier.isEmpty() ? "" : (" " + d.classifier)).replace("source-release", "Source Release") +
                                "|" + d.version +
                                "|" + dateFormatter.format(d.date) +
                                "|" + d.size +
                                "|" + d.format +
                                "| " + d.url + "[icon:download[] " + d.format + "] " +
                                (d.sha512 != null?  d.sha512 + "[icon:download[]&nbsp;sha512] " : d.sha1 + "[icon:download[]&nbsp;sha1] ") +
                                d.asc + "[icon:download[]&nbsp;asc]"));
    }

    private static Download fillDownloadable(final Download download) {
        try {
            final URL url = new URL(download.mavenCentralUrl);
            final HttpURLConnection connection = HttpURLConnection.class.cast(url.openConnection());
            connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(30));
            final int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                if (HttpURLConnection.HTTP_NOT_FOUND != responseCode) {
                    System.err.println("Got " + responseCode + " for " + download.url);
                }
                return null;
            }

            long lastMod = connection.getHeaderFieldDate("Last-Modified", 0);
            download.date = Instant.ofEpochMilli(lastMod);

            download.size = toSize(ofNullable(connection.getHeaderField("Content-Length"))
                    .map(Long::parseLong).orElse(0L), ofNullable(connection.getHeaderField("Accept-Ranges")).orElse("bytes"));

            connection.getInputStream().close();
        } catch (final IOException e) {
            e.printStackTrace();
            return null;
        }
        return download;
    }

    private static String toSize(final long length, final String bytes) {
        if (!"bytes".equalsIgnoreCase(bytes)) {
            throw new IllegalArgumentException("Not handled unit: " + bytes);
        }
        final long meg = length / MEGA_RATIO;
        final long kilo = (length - (meg * MEGA_RATIO)) / KILO_RATIO;
        return (meg > 0 ? meg + " MB " : "") + (kilo > 0 ? kilo + " kB" : "");
    }

    private static Stream<Version> versionStream(final String artifactId) {
        return Stream.of("org/apache/meecrowave/" + artifactId)
                .flatMap(Downloads::toVersions);
    }

    private static Stream<Download> toDownloadable(final Version version) {
        final String base = version.base;
        final String artifactId = base.substring(base.lastIndexOf('/') + 1, base.length());
        final String artifactBase = version.base + "/" + version.version + "/" + artifactId + "-" + version.version;
        return version.extensions.stream()
                .flatMap(e -> (version.classifiers.isEmpty() ? Stream.of(new ArtifactDescription("", e)) : version.classifiers.stream().map(c -> new ArtifactDescription(c, e))))
                .map(a -> toDownload(artifactId, a.classifier, version.version, a.extension, artifactBase + (a.classifier.isEmpty() ? '.' + a.extension : ('-' + a.classifier + '.' + a.extension))));
    }

    private static Download toDownload(final String artifactId, final String classifier, final String version, final String format, String artifactUrl) {
        String url = DIST_RELEASE + version + "/" + artifactId + "-" + version + "-" + classifier + "." + format;
        String downloadUrl;
        String sha512 = null;
        if (urlExists(url)) {
            // artifact exists on dist.a.o
            downloadUrl = MIRROR_RELEASE + version + "/" + artifactId + "-" + version + "-" + classifier + "." + format;
        }
        else {
            url = ARCHIVE_RELEASE + version + "/" + artifactId + "-" + version + "-" + classifier + "." + format;
            if (urlExists(url)) {
                // artifact exists on archive.a.o
                downloadUrl = url;
            }
            else {
                // falling back to Maven URL
                downloadUrl = artifactUrl;
                url = artifactUrl;
            }
        }

        if (urlExists(url + ".sha512")) {
            sha512 = url + ".sha512";
        }

        return new Download(
                WordUtils.capitalize(artifactId.replace('-', ' ')),
                classifier,
                version,
                format,
                downloadUrl,
                url + ".sha1",
                sha512,
                url + ".asc",
                artifactUrl);
    }

    private static boolean urlExists(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setUseCaches(false);
            return conn.getResponseCode() == HttpURLConnection.HTTP_OK;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<Version> toVersions(final String baseUrl) {
        final QuickMvnMetadataParser handler = new QuickMvnMetadataParser();
        final String base = MVN_BASE + baseUrl;
        try (final InputStream stream = new URL(base + "/maven-metadata.xml").openStream()) {
            final SAXParser parser = FACTORY.newSAXParser();
            parser.parse(stream, handler);
            return handler.foundVersions.stream().map(v -> new Version(base, v)).parallel();
        } catch (final Exception e) {
            e.printStackTrace();
            return Stream.empty();
        }
    }

    public static class Version {
        private final String base;
        private final String version;
        private final Collection<String> classifiers = new ArrayList<>();
        private final Collection<String> extensions = new ArrayList<>();

        public Version(final String base, final String version) {
            this.base = base;
            this.version = version;
        }

        private Version extensions(final String... values) {
            extensions.addAll(asList(values));
            return this;
        }

        private Version classifiers(final String... values) {
            classifiers.addAll(asList(values));
            return this;
        }
    }

    public static class ArtifactDescription {
        private final String classifier;
        private final String extension;

        private ArtifactDescription(final String classifier, final String extension) {
            this.classifier = classifier;
            this.extension = extension;
        }
    }

    public static class Download {
        private final String name;
        private final String classifier;
        private final String version;
        private final String format;
        private final String mavenCentralUrl;
        private final String url;
        private final String sha1;
        private final String sha512;
        private final String asc;
        private Instant date;
        private String size;

        public Download(final String name, final String classifier, final String version,
                        final String format, final String url, final String sha1, final String sha512,
                        final String asc, String mavenCentralUrl) {
            this.name = name;
            this.classifier = classifier;
            this.version = version;
            this.format = format;
            this.url = url;
            this.sha1 = sha1;
            this.sha512 = sha512;
            this.asc = asc;
            this.mavenCentralUrl = mavenCentralUrl;
        }
    }

    private static class QuickMvnMetadataParser extends DefaultHandler {
        private boolean versioning = false;
        private boolean versions = false;
        private StringBuilder version;
        private final Collection<String> foundVersions = new ArrayList<>();

        @Override
        public void startElement(final String uri, final String localName,
                                 final String name, final Attributes attributes) throws SAXException {
            if ("versioning".equalsIgnoreCase(name)) {
                versioning = true;
            } else if ("versions".equalsIgnoreCase(name)) {
                versions = true;
            } else if (versioning && versions && "version".equalsIgnoreCase(name)) {
                version = new StringBuilder();
            }
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            if (version != null) {
                version.append(new String(ch, start, length));
            }
        }

        public void endElement(final String uri, final String localName, final String name) throws SAXException {
            if ("versioning".equalsIgnoreCase(name)) {
                versioning = false;
            } else if ("versions".equalsIgnoreCase(name)) {
                versions = false;
            } else if ("version".equalsIgnoreCase(name)) {
                foundVersions.add(version.toString());
            }
        }
    }
}
