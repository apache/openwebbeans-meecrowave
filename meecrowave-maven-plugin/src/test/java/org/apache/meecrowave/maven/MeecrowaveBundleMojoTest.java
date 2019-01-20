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

import static org.apache.ziplock.JarLocation.jarLocation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
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

public class MeecrowaveBundleMojoTest {
    @Rule
    public final MojoRule mojo = new MojoRule();

    @Test
    public void bundle() throws Exception {
        final File moduleBase = jarLocation(MeecrowaveBundleMojoTest.class).getParentFile().getParentFile();
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
        final Build build = new Build();
        final File buildDir = new File("target/" + getClass().getName() + "/build");
        build.setDirectory(buildDir.getAbsolutePath());
        project.setBuild(build);
        final MavenSession session = mojo.newMavenSession(project);
        final MojoExecution execution = mojo.newMojoExecution("bundle");
        execution.getConfiguration().addChild(new Xpp3Dom("enforceMeecrowave") {{
            setValue(Boolean.FALSE.toString());
        }});
        execution.getConfiguration().addChild(new Xpp3Dom("enforceCommonsCli") {{
            setValue(Boolean.FALSE.toString());
        }});
        execution.getConfiguration().addChild(new Xpp3Dom("conf") {{
            setValue("src/main/meecrowave/conf");
        }});
        execution.getConfiguration().addChild(new Xpp3Dom("webapp") {{
            setValue("src/main/webapp");
        }});
        mojo.executeMojo(session, project, execution);
        assertTrue(buildDir.exists());
        try (final ZipFile zip = new ZipFile(new File(buildDir, "test-meecrowave-distribution.zip"))) {
            assertTrue(zip.getEntry("test-distribution/docBase/sub/index.html") != null);
        }
    }
}
