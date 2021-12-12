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

import static java.util.Optional.ofNullable;
import static org.apache.ziplock.JarLocation.jarLocation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.enterprise.inject.Model;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.helpers.FileUtils;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.meecrowave.io.IO;
import org.app.Endpoint;
import org.app.Injectable;
import org.app.RsApp;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class MeecrowaveRunMojoTest {

	private static final int RETRY_COUNT = 1000;
	private static final int RETRY_WAIT_PERIOD = 500;

	private static byte[] additionalEndpointClass;

	@Rule
    public final MojoRule mojo = new MojoRule();

	private MavenProject project;
    private MavenSession session;
    private int port;
    private MojoExecution execution;

    @BeforeClass
    public static void removeAdditionalEndpointClass() throws Exception {
        File additionalEndpointClassFile = getAdditionalEndpointClass();
        try (InputStream classStream = new FileInputStream(additionalEndpointClassFile)) {
            additionalEndpointClass = IOUtils.toByteArray(classStream);
        }
        assumeTrue(additionalEndpointClassFile.delete());
    }

    @AfterClass
    public static void restoreAdditionalEndpointClass() throws Exception {
        IOUtils.write(additionalEndpointClass, new FileOutputStream(getAdditionalEndpointClass()));
    }

    private static File getAdditionalEndpointClass() throws URISyntaxException {
        return new File(new File(MeecrowaveRunMojoTest.class.getResource("/").toURI()), "org/app/AdditionalEndpoint.class");
    }

    @Before
	public void setupMojoExecution() throws Exception {
        final File moduleBase = jarLocation(MeecrowaveRunMojoTest.class).getParentFile().getParentFile();
        final File basedir = new File(moduleBase, "src/test/resources/" + getClass().getSimpleName());
        final File pom = new File(basedir, "pom.xml");
        final MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(basedir);
        final ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
        final DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory()
                .newInstance(repositorySession, new LocalRepository(new File(moduleBase, "target/fake"), "")));
        configuration.setRepositorySession(repositorySession);
        project = mojo.lookup(ProjectBuilder.class).build(pom, configuration).getProject();
        session = mojo.newMavenSession(project);
        try (final ServerSocket serverSocket = new ServerSocket(0)) {
            port = serverSocket.getLocalPort();
        }
        execution = mojo.newMojoExecution("run");
	}

	@Test
    public void classpathDeployment() throws Exception {
        execution.getConfiguration().getChild("httpPort").setValue(Integer.toString(port));
        final Runnable quitCommand = quitCommand();
        List<Exception> mojoFailure = new CopyOnWriteArrayList<>();
        final Thread mojoExecutor = mojoExecutor(mojoFailure);
        try {
            mojoExecutor.start();
            retry(() -> {
                assertEquals("simple", IOUtils.toString(new URL("http://localhost:" + port + "/api/test")));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/test/model")).contains("first_name"));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/test/model")).contains("last_name"));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/test/model")).contains("firstname"));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/test/model")).contains("null"));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/sub/index.html")).contains("<h1>yes</h1>"));
                assertNotAvailable(new URL("http://localhost:" + port + "/api/additional"));
                quitCommand.run();
            }, mojoFailure);
        } finally {
            mojoExecutor.join(TimeUnit.MINUTES.toMillis(1));
            if (mojoExecutor.isAlive()) {
                mojoExecutor.interrupt();
                fail("Runner didn't terminate properly");
            }
        }
    }

    @Test
    public void webappDeployment() throws Exception {
        File target = new File(MeecrowaveRunMojoTest.class.getResource("/").toURI()).getParentFile();
        File webappDirectory = new File(target, MeecrowaveRunMojoTest.class.getSimpleName());
        webappDirectory.mkdir();
        assertTrue(webappDirectory.exists());
        setupWebapp(webappDirectory);
        execution.getConfiguration().getChild("httpPort").setValue(Integer.toString(port));
        execution.getConfiguration().getChild("useClasspathDeployment").setValue("false");
        execution.getConfiguration().getChild("webapp").setValue(webappDirectory.getAbsolutePath());
        final Runnable quitCommand = quitCommand();
        List<Exception> mojoFailure = new CopyOnWriteArrayList<>();
        final Thread mojoExecutor = mojoExecutor(mojoFailure);
        try {
            mojoExecutor.start();
            retry(() -> {
                assertEquals("simple", IOUtils.toString(new URL("http://localhost:" + port + "/api/test")));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/test/model")).contains("first_name"));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/test/model")).contains("last_name"));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/test/model")).contains("firstname"));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/test/model")).contains("null"));
                assertTrue(IOUtils.toString(new URL("http://localhost:" + port + "/api/additional")).contains("available"));
                assertNotAvailable(new URL("http://localhost:" + port + "/sub/index.html"));
                quitCommand.run();
            }, mojoFailure);
        } finally {
            mojoExecutor.join(TimeUnit.MINUTES.toMillis(1));
            if (mojoExecutor.isAlive()) {
                mojoExecutor.interrupt();
                fail("Runner didn't terminate properly");
            }
        }
    }

    @Test
    public void autoreloadWithClasspathDeployment() throws Exception {
        File additionalEndpointFile = getAdditionalEndpointClass();
        execution.getConfiguration().getChild("httpPort").setValue(Integer.toString(port));
        execution.getConfiguration().getChild("watcherBouncing").setValue("1");
        Runnable quitCommand = quitCommand();
        List<Exception> mojoFailure = new CopyOnWriteArrayList<>();
        final Thread mojoExecutor = mojoExecutor(mojoFailure);
        try {
            mojoExecutor.start();
            retry(() -> {
                assertEquals("simple", IOUtils.toString(new URL("http://localhost:" + port + "/api/test")));
                assertNotAvailable(new URL("http://localhost:" + port + "/api/additional"));
            }, mojoFailure);
            File folder = additionalEndpointFile.getParentFile();
            folder.mkdirs();
            assertTrue(folder.exists());
            IOUtils.write(additionalEndpointClass, new FileOutputStream(additionalEndpointFile));
            retry(() -> assertEquals("available", IOUtils.toString(new URL("http://localhost:" + port + "/api/additional"))), mojoFailure);
            retry(() -> assertEquals("simple", IOUtils.toString(new URL("http://localhost:" + port + "/api/test"))), mojoFailure);
            quitCommand.run();
        } finally {
            additionalEndpointFile.delete();
            assertFalse(additionalEndpointFile.exists());
            mojoExecutor.join(TimeUnit.MINUTES.toMillis(1));
            if (mojoExecutor.isAlive()) {
                mojoExecutor.interrupt();
                fail("Runner didn't terminate properly");
            }
        }
    }

    private void setupWebapp(File webappDirectory) throws Exception {
        Stream.of(Endpoint.class, RsApp.class, Injectable.class, Model.class).forEach(type -> {
            final String target = type.getName().replace(".", "/");
            File targetFile = new File(webappDirectory, "WEB-INF/classes/" + target + ".class");
            FileUtils.mkDir(targetFile.getParentFile());
            try (final InputStream from = Thread.currentThread().getContextClassLoader().getResourceAsStream(target + ".class");
                 final OutputStream to = new FileOutputStream(targetFile)) {
                IO.copy(from, to);
            } catch (final IOException e) {
                fail(e.getMessage());
            }
        });
        IOUtils.write(additionalEndpointClass, new FileOutputStream(new File(webappDirectory, "WEB-INF/classes/org/app/AdditionalEndpoint.class")));
    }

    private Runnable quitCommand() {
        final InputStream in = System.in;
        final CountDownLatch latch = new CountDownLatch(1);
        System.setIn(new InputStream() {
            private final InputStream delegate = new ByteArrayInputStream("quit".getBytes(StandardCharsets.UTF_8));

            @Override
            public int read() throws IOException {
                try {
                    latch.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail(e.getMessage());
                }
                if (delegate.available() > 0) {
                    return delegate.read();
                } else {
                    System.setIn(in);
                    return -1;
                }
            }
        });
        return latch::countDown;
    }

    private Thread mojoExecutor(List<Exception> mojoFailure) {
        return new Thread() {
            @Override
            public void run() {
                try {
                    mojo.executeMojo(session, project, execution);
                } catch (final Exception e) {
                    if (mojoFailure != null) {
                        mojoFailure.add(e);
                    }

                    fail(e.getMessage());
                }
            }
        };
    }

    private void assertNotAvailable(final URL url) {
        try {
            URLConnection connection = url.openConnection();
            connection.setReadTimeout(500);
            connection.getInputStream();
            fail(url.toString() + " is available");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e instanceof IOException);
        }
    }

    private void retry(RetryTemplate retryTemplate, List<Exception> mojoFailure) throws InterruptedException {
        Throwable error = null;
        for (int i = 0; i < RETRY_COUNT && mojoFailure.isEmpty(); i++) {
            try {
                retryTemplate.retry();
                return;
            } catch (Exception | AssertionError e) {
                error = e;
                Thread.sleep(RETRY_WAIT_PERIOD);
            }
        }
        if (!mojoFailure.isEmpty()) {
            mojoFailure.get(0).printStackTrace();
            fail("Error while starting Meecrowave");
        }
        fail(ofNullable(error).map(Throwable::getMessage).orElse("retry failes"));
    }

    interface RetryTemplate {
        void retry() throws Exception;
    }
}
