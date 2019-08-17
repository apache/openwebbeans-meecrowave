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
package org.apache.meecrowave.arquillian;

import static java.util.Optional.ofNullable;

import java.io.File;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.configuration.Configuration;
import org.apache.meecrowave.io.IO;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

public class MeecrowaveContainer implements DeployableContainer<MeecrowaveConfiguration> {
    private Configuration configuration;
    private Meecrowave container;
    private ProtocolDescription defaultProtocol;

    @Override
    public Class<MeecrowaveConfiguration> getConfigurationClass() {
        return MeecrowaveConfiguration.class;
    }

    @Override
    public void setup(final MeecrowaveConfiguration configuration) {
        this.configuration = configuration.toMeecrowaveConfiguration();
        this.defaultProtocol = new ProtocolDescription(configuration.getArquillianProtocol());
    }

    @Override
    public void start() {
        this.container = new Meecrowave(this.configuration);
        this.container.start();
    }

    @Override
    public void stop() {
        ofNullable(this.container).ifPresent(Meecrowave::close);
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return defaultProtocol;
    }

    @Override
    public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException {
        final File dump = toArchiveDump(archive);
        archive.as(ZipExporter.class).exportTo(dump, true);
        final String context = sanitizeName(archive);
        container.deployWebapp(context, dump);
        final int port = configuration.isSkipHttp() ? configuration.getHttpsPort() : configuration.getHttpPort();
        return new ProtocolMetaData()
                .addContext(new HTTPContext(configuration.getHost(), port)
                        .add(new Servlet("arquillian", context)));
    }

    @Override
    public void undeploy(final Archive<?> archive) throws DeploymentException {
        this.container.undeploy(sanitizeName(archive));
        final File dump = toArchiveDump(archive);
        if (dump.isFile()) {
            IO.delete(dump);
        }
        final File unpacked = new File(dump.getParentFile(), dump.getName().replace(".war", ""));
        if (unpacked.isDirectory()) {
            IO.delete(unpacked);
        }
    }

    @Override
    public void deploy(final Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void undeploy(final Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    private String sanitizeName(final Archive<?> archive) {
        final String root = archive.getName().replace(".war", "").replace("ROOT", "");
        return root.isEmpty() ? "" : ("/" + root);
    }

    private File toArchiveDump(final Archive<?> archive) {
        final File file = new File(this.configuration.getTempDir(), archive.getName());
        file.getParentFile().mkdirs();
        return file;
    }
}
