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
package org.apache.microwave.maven;

import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.apache.ziplock.JarLocation.jarLocation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MicrowaveRunMojoTest {
    @Rule
    public final MojoRule mojo = new MojoRule();

    @Test
    public void run() throws Exception {
        final File moduleBase = jarLocation(MicrowaveRunMojoTest.class).getParentFile().getParentFile();
        final File basedir = new File(moduleBase, "src/test/resources/" + getClass().getSimpleName());
        final File pom = new File(basedir, "pom.xml");
        final MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(basedir);
        final ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
        final DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory()
                .newInstance(repositorySession, new LocalRepository(new File(moduleBase, "target/fake"), "")));
        configuration.setRepositorySession(repositorySession);
        final MavenProject project = mojo.lookup(ProjectBuilder.class).build(pom, configuration).getProject();
        final MavenSession session = mojo.newMavenSession(project);
        final int port;
        try (final ServerSocket serverSocket = new ServerSocket(0)) {
            port = serverSocket.getLocalPort();
        }
        final MojoExecution execution = mojo.newMojoExecution("run");
        execution.getConfiguration().addChild(new Xpp3Dom("httpPort") {{
            setValue(Integer.toString(port));
        }});
        final InputStream in = System.in;
        final CountDownLatch latch = new CountDownLatch(1);
        System.setIn(new InputStream() {
            private int val = 2; // just to not return nothing

            @Override
            public int read() throws IOException {
                try {
                    latch.await();
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                    fail(e.getMessage());
                }
                return val--;
            }
        });
        final Thread runner = new Thread() {
            @Override
            public void run() {
                try {
                    mojo.executeMojo(session, project, execution);
                } catch (final Exception e) {
                    fail(e.getMessage());
                }
            }
        };
        try {
            runner.start();
            for (int i = 0; i < 120; i++) {
                try {
                    assertEquals("simple", IOUtils.toString(new URL("http://localhost:" + port + "/api/test")));
                    latch.countDown();
                    break;
                } catch (final Exception | AssertionError e) {
                    Thread.sleep(500);
                }
            }
        } finally {
            runner.join(TimeUnit.MINUTES.toMillis(1));
            System.setIn(in);
            if (runner.isAlive()) {
                runner.interrupt();
                fail("Runner didn't terminate properly");
            }
        }
    }
}
