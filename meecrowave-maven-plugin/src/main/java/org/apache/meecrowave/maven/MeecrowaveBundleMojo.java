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
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME_PLUS_SYSTEM;

@Mojo(name = "bundle", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class MeecrowaveBundleMojo extends AbstractMojo {
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

    @Parameter(property = "meecrowave.formats", defaultValue = "zip")
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

    @Parameter(property = "meecrowave.enforce-commons_cli", defaultValue = "true")
    private boolean enforceCommonsCli; // set to false if you use a packaged version of commons-cli not named commons-cli*.jar

    @Parameter(property = "meecrowave.enforce-meecrowave", defaultValue = "true")
    private boolean enforceMeecrowave; // set to false if you package meecrowave runner

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private ArtifactResolver resolver;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ProjectDependenciesResolver dependenciesResolver;

    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession session;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    private List<RemoteRepository> remoteRepositories;

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
            write(new File(distroFolder, "bin/meecrowave.sh"), StrSubstitutor.replace(reader.lines().collect(joining("\n")), new HashMap<String, String>() {{
                put("main", main);
            }}));
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
        final Collection<String> includedArtifacts = project.getArtifacts().stream()
                .filter(this::isIncluded)
                .map(a -> {
                    addLib(distroFolder, a.getFile());
                    return a.getArtifactId();
                }).collect(toList());
        if (app.exists()) {
            addLib(distroFolder, app);
        }
        if (enforceCommonsCli && !includedArtifacts.contains("commons-cli")) {
            addLib(distroFolder, resolve("commons-cli", "commons-cli", "1.3.1", ""));
        }
        if (libs != null) {
            libs.forEach(l -> {
                final String[] c = l.split(":");
                if (c.length != 3 && c.length != 4) {
                    throw new IllegalArgumentException("libs syntax is groupId:artifactId:version[:classifier]");
                }
                addLib(distroFolder, resolve(c[0], c[1], c[2], c.length == 4 ? c[3] : ""));
            });
        }
        if (enforceMeecrowave && !includedArtifacts.contains("meecrowave-core")) {
            final DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
            request.setMavenProject(new MavenProject() {{
                getDependencies().add(new Dependency() {{
                    setGroupId("org.apache.meecrowave");
                    setArtifactId("meecrowave-core");
                    setVersion(findVersion());
                }});
            }});
            request.setRepositorySession(session);
            try {
                dependenciesResolver.resolve(request).getDependencyGraph().accept(new DependencyVisitor() {
                    @Override
                    public boolean visitEnter(final DependencyNode node) {
                        return true;
                    }

                    @Override
                    public boolean visitLeave(final DependencyNode node) {
                        final org.eclipse.aether.artifact.Artifact artifact = node.getArtifact();
                        if (artifact != null && !includedArtifacts.contains(artifact.getArtifactId())) {
                            addLib(distroFolder, artifact.getFile());
                        }
                        return true;
                    }
                });
            } catch (final DependencyResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

        final Path prefix = skipArchiveRootFolder ? distroFolder.toPath() : distroFolder.getParentFile().toPath();
        for (final String format : formats) {
            getLog().info(format + "-ing Custom Meecrowave Distribution");

            final File output = new File(buildDirectory, artifactId + "-meecrowave-distribution." + format);

            switch (format.toLowerCase(ENGLISH)) {
                case "tar.gz":
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
                case "zip":
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

    private void addLib(final File distroFolder, final File cc) {
        try {
            Files.copy(cc.toPath(), new File(distroFolder, "lib/" + cc.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private String findVersion() {
        return new Properties() {{
            try (final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/maven/org.apache.meecrowave/meecrowave-core/pom.properties")) {
                if (is != null) {
                    load(is);
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }}.getProperty("version", "0.3.2");
    }

    private File resolve(final String group, final String artifact, final String version, final String classifier) {
        final DefaultArtifact art = new DefaultArtifact(group, artifact, classifier, "jar", version);
        final ArtifactRequest artifactRequest = new ArtifactRequest().setArtifact(art).setRepositories(remoteRepositories);

        final LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        art.setFile(new File(lrm.getRepository().getBasedir(), lrm.getPathForLocalArtifact(artifactRequest.getArtifact())));

        try {
            final ArtifactResult result = repositorySystem.resolveArtifact(session, artifactRequest);
            if (result.isMissing()) {
                throw new IllegalStateException("Can't find commons-cli, please add it to the pom.");
            }
            return result.getArtifact().getFile();
        } catch (final ArtifactResolutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
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
