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
package org.apache.meecrowave.maven;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.joining;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME_PLUS_SYSTEM;

@Mojo(name = "bundle", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class MeecrowaveBundleMojo extends AbstractMojo {
    private static final String ZIP = "zip";

	private static final String TAR_GZ = "tar.gz";

	private static final String DELETE_TEXT = "Just there to not loose the folder cause it is empty, you can safely delete.";

    @Parameter(property = "meecrowave.main", defaultValue = "org.apache.meecrowave.runner.Cli")
    private String main;

    @Parameter(property = "meecrowave.scopes", defaultValue = "compile,runtime")
    private Collection<String> scopes;

    @Parameter(property = "meecrowave.conf", defaultValue = "src/main/meecrowave/conf")
    private String conf;

    @Parameter(property = "meecrowave.libs")
    private Collection<String> libs;

    @Parameter(property = "meecrowave.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "meecrowave.formats", defaultValue = ZIP)
    private Collection<String> formats;

    @Parameter(property = "meecrowave.classifier")
    private String classifier;

    @Parameter(property = "meecrowave.attach", defaultValue = "true")
    private boolean attach;

    @Parameter(property = "meecrowave.no-root", defaultValue = "false")
    private boolean skipArchiveRootFolder;

    @Parameter(property = "meecrowave.keep-exploded-folder", defaultValue = "false")
    private boolean keepExplodedFolder;

    @Parameter(property = "meecrowave.root-name")
    private String rootName;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File buildDirectory;

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}", readonly = true)
    private File app;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().warn(getClass().getSimpleName() + " skipped");
            return;
        }

        final File distroFolder = new File(buildDirectory, rootName == null ? artifactId + "-distribution" : rootName);
        if (distroFolder.exists()) {
            delete(distroFolder);
        }

        Stream.of("bin", "conf", "logs", "lib").forEach(i -> new File(distroFolder, i).mkdirs());

        // TODO: add .bat support
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("bin/meecrowave.sh")))) {
        	
        	Map<String, String> mainMap = new HashMap();
        	mainMap.put("main", main);
        	
        	write(new File(distroFolder, "bin/meecrowave.sh"), StrSubstitutor.replace(reader.lines().collect(joining("\n")), mainMap));
            
        } catch (final IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        write(new File(distroFolder, "conf/log4j2.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Configuration status=\"INFO\">\n" +
                "  <Properties>\n" +
                "    <Property name=\"name\">" + artifactId + "</Property>\n" +
                "  </Properties>\n" +
                "  <Appenders>\n" +
                "    <Console name=\"Console\" target=\"SYSTEM_OUT\">\n" +
                "      <PatternLayout pattern=\"[%d{HH:mm:ss.SSS}][%highlight{%-5level}][%15.15t][%30.30logger] %msg%n\"/>\n" +
                "    </Console>" +
                "    <RollingFile name=\"DailyLogFile\" fileName=\"logs/meecrowave.log\"\n" +
                "                 filePattern=\"logs/${name}-%d{yyyy-MM-dd}-%i.log.gz\">\n" +
                "      <PatternLayout pattern=\"[%d{HH:mm:ss.SSS}][%-5level][%15.15t][%30.30logger] %msg%n\"/>\n" +
                "      <Policies>\n" +
                "        <TimeBasedTriggeringPolicy />\n" +
                "        <SizeBasedTriggeringPolicy size=\"50 MB\"/>\n" +
                "      </Policies>\n" +
                "    </RollingFile>\n" +
                "  </Appenders>\n" +
                "  <Loggers>\n" +
                "    <Root level=\"INFO\">\n" +
                "      <!--<AppenderRef ref=\"Console\"/>-->\n" +
                "      <AppenderRef ref=\"DailyLogFile\"/>\n" +
                "    </Root>\n" +
                "  </Loggers>\n" +
                "</Configuration>\n\n");
        write(new File(distroFolder, "conf/meecrowave.properties"), "# This file contains the meecrowave default configuration\n" +
                "# More on http://openwebbeans.apache.org/meecrowave/meecrowave-core/cli.html\n\n" +
                "tomcat-access-log-pattern = %h %l %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-Agent}i\"");
        write(new File(distroFolder, "logs/you_can_safely_delete.txt"), DELETE_TEXT);
        project.getArtifacts().stream()
                .filter(this::isIncluded)
                .map(Artifact::getFile)
                .forEach(f -> {
                    try {
                        Files.copy(f.toPath(), new File(distroFolder, "lib/" + f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
        if (app.exists()) {
            try {
                Files.copy(app.toPath(), new File(distroFolder, "lib/" + app.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        final Path prefix = skipArchiveRootFolder ? distroFolder.toPath() : distroFolder.getParentFile().toPath();
        for (final String format : formats) {
            getLog().info(format + "-ing Custom Meecrowave Distribution");

            final File output = new File(buildDirectory, artifactId + "-meecrowave-distribution." + format);

            switch (format.toLowerCase(ENGLISH)) {
                case TAR_GZ:
                    try (final TarArchiveOutputStream tarGz =
                                 new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(output)))) {
                        tarGz.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                        for (final String entry : distroFolder.list()) {
                            tarGz(tarGz, new File(distroFolder, entry), prefix);
                        }
                    } catch (final IOException e) {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                    break;
                case ZIP:
                    try (final ZipArchiveOutputStream zos =
                                 new ZipArchiveOutputStream(new FileOutputStream(output))) {
                        for (final String entry : distroFolder.list()) {
                            zip(zos, new File(distroFolder, entry), prefix);
                        }
                    } catch (final IOException e) {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException(format + " is not supported");
            }

            attach(format, output);
        }

        if (!keepExplodedFolder) {
            delete(distroFolder);
        }
    }

    private boolean isIncluded(final Artifact a) {
        return !((scopes == null && !(Artifact.SCOPE_COMPILE.equals(a.getScope()) || Artifact.SCOPE_RUNTIME.equals(a.getScope())))
                || (scopes != null && !scopes.contains(a.getScope())));
    }

    private void write(final File file, final String content) {
        try {
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void delete(final File distroFolder) { // not critical
        final Path rootPath = distroFolder.toPath();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (final IOException e) {
            getLog().warn(e);
        }
    }

    private void attach(final String ext, final File output) {
        if (attach) {
            getLog().info("Attaching Custom TomEE Distribution (" + ext + ")");
            if (classifier != null) {
                projectHelper.attachArtifact(project, ext, classifier, output);
            } else {
                projectHelper.attachArtifact(project, ext, output);
            }
        }
    }

    private void zip(final ZipArchiveOutputStream zip, final File f, final Path prefix) throws IOException {
        final String path = prefix.relativize(f.toPath()).toString().replace(File.separator, "/");
        final ZipArchiveEntry archiveEntry = new ZipArchiveEntry(f, path);
        if (isSh(path)) {
            archiveEntry.setUnixMode(0755);
        }
        zip.putArchiveEntry(archiveEntry);
        if (f.isDirectory()) {
            zip.closeArchiveEntry();
            final File[] files = f.listFiles();
            if (files != null) {
                for (final File child : files) {
                    zip(zip, child, prefix);
                }
            }
        } else {
            Files.copy(f.toPath(), zip);
            zip.closeArchiveEntry();
        }
    }

    private void tarGz(final TarArchiveOutputStream tarGz, final File f, final Path prefix) throws IOException {
        final String path = prefix.relativize(f.toPath()).toString().replace(File.separator, "/");
        final TarArchiveEntry archiveEntry = new TarArchiveEntry(f, path);
        if (isSh(path)) {
            archiveEntry.setMode(0755);
        }
        tarGz.putArchiveEntry(archiveEntry);
        if (f.isDirectory()) {
            tarGz.closeArchiveEntry();
            final File[] files = f.listFiles();
            if (files != null) {
                for (final File child : files) {
                    tarGz(tarGz, child, prefix);
                }
            }
        } else {
            Files.copy(f.toPath(), tarGz);
            tarGz.closeArchiveEntry();
        }
    }

    private boolean isSh(final String path) {
        return path.endsWith(".sh");
    }
}
