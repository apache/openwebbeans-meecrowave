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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.meecrowave.lang.Substitutor;
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

import static java.util.Arrays.asList;
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

    /**
     * A directory with bin/ files like setenv.sh.
     */
    @Parameter(property = "meecrowave.bin", defaultValue = "src/main/meecrowave/bin")
    private String bin;

    /**
     * A directory with configuration which should be put into the final bundle.
     */
    @Parameter(property = "meecrowave.conf", defaultValue = "src/main/meecrowave/conf")
    private String conf;

    @Parameter(property = "meecrowave.libs")
    private Collection<String> libs;

    @Parameter(property = "meecrowave.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "meecrowave.fakeTomcatScripts", defaultValue = "false")
    private boolean fakeTomcatScripts;

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

    @Parameter(property = "meecrowave.webapp", defaultValue = "${project.basedir}/src/main/webapp")
    private File webapp;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private ArtifactResolver resolver;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ProjectDependenciesResolver dependenciesResolver;

    @Component
    private DependencyGraphBuilder graphBuilder;

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

        copyProvidedFiles(distroFolder);

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
        if (webapp != null && webapp.isDirectory()) {
            try {
                final Path rootSrc = webapp.toPath().toAbsolutePath();
                final Path rootTarget = distroFolder.toPath().toAbsolutePath().resolve("docBase");
                Files.walkFileTree(rootSrc, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        final Path target = rootTarget.resolve(rootSrc.relativize(file));
                        target.toFile().getParentFile().mkdirs();
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                        return super.visitFile(file, attrs);
                    }
                });
            } catch (final IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
        if (enforceCommonsCli && !includedArtifacts.contains("commons-cli")) {
            addLib(distroFolder, resolve("commons-cli", "commons-cli", "1.4", ""));
        }
        if (libs != null) {
            libs.forEach(l -> {
                final boolean transitive = l.endsWith("?transitive");
                final String coords = transitive ? l.substring(0, l.length() - "?transitive".length()) : l;
                final String[] c = coords.split(":");
                if (c.length < 3 || c.length > 5) {
                    throw new IllegalArgumentException("libs syntax is groupId:artifactId:version[:classifier][:type[?transitive]]");
                }
                if (!transitive) {
                    addLib(distroFolder, resolve(c[0], c[1], c[2], c.length == 4 ? c[3] : ""));
                } else {
                    addTransitiveDependencies(distroFolder, includedArtifacts, new Dependency() {{
                        setGroupId(c[0]);
                        setArtifactId(c[1]);
                        setVersion(c[2]);
                        if (c.length == 4 && !"-".equals(c[3])) {
                            setClassifier(c[3]);
                        }
                        if (c.length == 5) {
                            setType(c[4]);
                        }
                    }});
                }
            });
        }
        if (enforceMeecrowave && !includedArtifacts.contains("meecrowave-core")) {
            addTransitiveDependencies(distroFolder, includedArtifacts, new Dependency() {{
                setGroupId("org.apache.meecrowave");
                setArtifactId("meecrowave-core");
                setVersion(findVersion());
            }});
        }

        for (final String ext : asList("sh", "bat")) {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Thread.currentThread().getContextClassLoader().getResourceAsStream("bin/meecrowave." + ext)))) {
                final File target = new File(distroFolder, "bin/meecrowave." + ext);
                if (!target.exists()) {
                    write(target, new Substitutor(new HashMap<String, String>() {{
                        put("main", main);
                        put("logManager", hasLog4j(distroFolder) ?
                                "org.apache.logging.log4j.jul.LogManager" : "org.apache.juli.ClassLoaderLogManager");
                    }}).replace(reader.lines().collect(joining("\n"))));
                }
            } catch (final IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
        if (fakeTomcatScripts) {
            Stream.of("catalina.sh", "shutdown.sh", "startup.sh").forEach(script -> {
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
                        Thread.currentThread().getContextClassLoader().getResourceAsStream("bin/" + script)))) {
                    final File target = new File(distroFolder, "bin/" + script);
                    if (!target.exists()) {
                        write(target, reader.lines().collect(joining("\n")));
                    }
                } catch (final IOException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            });
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

    private boolean hasLog4j(final File distroFolder) {
        try {
            return Files.list(distroFolder.toPath().resolve("lib"))
                    .anyMatch(it -> it.getFileName().toString().startsWith("log4j-jul"));
        } catch (final IOException e) {
            return true;
        }
    }

    private void addTransitiveDependencies(final File distroFolder, final Collection<String> includedArtifacts, final Dependency dependency) {
        final DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject(new MavenProject() {{
            getDependencies().add(dependency);
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
            throw new IllegalStateException(e.getMessage(), e);
        }
    }


    /**
     * Copy over all files from src/main/meecrowave/*
     * TODO!
     * The following files get added with default content if not found there:
     * <ul>
     *     <li>conf/log4j2.xml</li>
     *     <li>conf/meecrowave.properties</li>
     * </ul>
     * @param distroFolder
     */
    private void copyProvidedFiles(final File distroFolder) throws MojoExecutionException
    {
        boolean customLog4jConfig = false;
        boolean customMwProperties = false;
        final Log log = getLog();

        File srcConf = new File(conf);
        if (!srcConf.isAbsolute()) {
            srcConf = new File(project.getBasedir(), conf);
        }
        if (srcConf.isDirectory()) {
            final File targetConf = new File(distroFolder, "conf");
            targetConf.mkdirs();

            for (final File file : srcConf.listFiles()) {
                final String fileName = file.getName();
                if ("log4j2.xml".equals(fileName)) {
                    customLog4jConfig = true;
                } else if ("meecrowave.properties".equals(fileName)) {
                    customMwProperties = true;
                } else if (fileName.startsWith(".")) {
                    // hidden file -> ignore
                    continue;
                }

                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Copying file from " + file + " to " + targetConf);
                    }
                    Files.copy(file.toPath(), new File(targetConf, fileName).toPath());
                } catch (final IOException e) {
                    throw new MojoExecutionException("Could not copy file " + file.getAbsolutePath(), e);
                }
            }
        }
        if (!customLog4jConfig)
        {
            writeLog4jConfig(distroFolder);
        }

        if (!customMwProperties)
        {
            writeMeecrowaveProperties(distroFolder);
        }

        final File srcBin = new File(project.getBasedir(), bin);
        if (srcBin.exists() && srcBin.isDirectory()) {
            final File targetRoot = new File(distroFolder, "bin");
            targetRoot.mkdirs();
            Stream.of(srcBin.listFiles())
                  .filter(f -> !f.isDirectory()) // not nested for now
                  .forEach(f -> {
                      try {
                          if (log.isDebugEnabled()) {
                              log.debug("Copying file from " + f + " to " + targetRoot);
                          }
                          final File target = new File(targetRoot, f.getName());
                          Files.copy(f.toPath(), target.toPath());
                          if (target.getName().endsWith(".sh")) {
                              target.setExecutable(true);
                          }
                      }
                      catch (final IOException e) {
                          throw new IllegalArgumentException("Could not copy file " + f.getAbsolutePath(), e);
                      }
                  });
        }
    }

    private void writeMeecrowaveProperties(File distroFolder)
    {
        write(new File(distroFolder, "conf/meecrowave.properties"), "# This file contains the meecrowave default configuration\n" +
                "# More on http://openwebbeans.apache.org/meecrowave/meecrowave-core/cli.html\n\n" +
                "tomcat-access-log-pattern = %h %l %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-Agent}i\"");
    }

    private void writeLog4jConfig(File distroFolder)
    {
        write(new File(distroFolder, "conf/log4j2.xml"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Configuration status=\"INFO\">\n" +
                "  <Properties>\n" +
                "    <Property name=\"name\">" + artifactId + "</Property>\n" +
                "  </Properties>\n" +
                "  <Appenders>\n" +
                "    <Console name=\"Console\" target=\"SYSTEM_OUT\">\n" +
                "      <PatternLayout pattern=\"[%d{HH:mm:ss.SSS}][%highlight{%-5level}][%15.15t][%30.30logger] %msg%n\"/>\n" +
                "    </Console>\n" +
                "    <RollingFile name=\"DailyLogFile\" fileName=\"${sys:meecrowave.base}/logs/${name}.log\"\n" +
                "                 filePattern=\"${sys:meecrowave.base}/logs/${name}-%d{yyyy-MM-dd}-%i.log.gz\">\n" +
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
            getLog().info("Attaching Custom Meecrowave Distribution (" + ext + ")");
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
