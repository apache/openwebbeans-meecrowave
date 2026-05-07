/**
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

package org.apache.meecrowave.tests.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.Meecrowave.Builder;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.junit.Test;

/*
 * Creates the following connector
 * <Connector port="8443" protocol="HTTP/1.1" maxThreads="10" SSLEnabled="true" scheme="https" secure="true"
    sslDefaultHost="*meecrowave-localhost">
    <SSLHostConfig honorCipherOrder="false" hostName="localhost">
        <Certificate certificateKeystoreFile="meecrowave.jks"
                     certificateKeystorePassword="meecrowave"
                     certificateKeyAlias="meecrowave"
                     truststoreFile = "meecrowave.jks"
                     truststorePassword = "meecrowave" />
    </SSLHostConfig>
    <SSLHostConfig honorCipherOrder="false" hostName="meecrowave-locahost">
        <Certificate certificateKeystoreFile="meecrowave_second_host.jks"
                     certificateKeystorePassword="meecrowave"
                     certificateKeyAlias="meecrowave" />
    </SSLHostConfig>
    <SSLHostConfig honorCipherOrder="false" hostName="meecrowave.apacge.org">
        <Certificate certificateKeyFile="meecrowave.key.pem"
                     certificateFile="meecrowave.cert.pem"
                     certificateChainFile="ca-chain.cert.pem" />
    </SSLHostConfig>
</Connector>
 */

public class TlsVirtualHostPropertiesTest {
    private static final String keyStorePath1 = "meecrowave_first_host.jks";
    private static final String keyStorePath2 = "meecrowave_second_host.jks";

    static {
        System.setProperty("javax.net.ssl.trustStore", Paths.get("").toAbsolutePath() + "/target/classes/meecrowave_trust.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "meecrowave");
    }

    public static final Properties p = new Properties() {{
        setProperty("connector.attributes.maxThreads", "10");

        setProperty("connector.sslhostconfig.certificateKeystoreFile", keyStorePath1);
        setProperty("connector.sslhostconfig.certificateKeystoreType", "JKS");
        setProperty("connector.sslhostconfig.certificateKeystorePassword", "meecrowave");
        setProperty("connector.sslhostconfig.certificateKeyAlias", "meecrowave");
        setProperty("connector.sslhostconfig.hostName", "localhost");
        setProperty("connector.sslhostconfig.truststoreFile", "meecrowave_trust.jks");
        setProperty("connector.sslhostconfig.truststorePassword", "meecrowave");

        setProperty("connector.sslhostconfig.1.certificateKeystoreFile", keyStorePath2);
        setProperty("connector.sslhostconfig.1.certificateKeystoreType", "JKS");
        setProperty("connector.sslhostconfig.1.certificateKeystorePassword", "meecrowave");
        setProperty("connector.sslhostconfig.1.certificateKeyAlias", "meecrowave");
        setProperty("connector.sslhostconfig.1.protocols", "TLSv1.2,TLSv1.3");
        setProperty("connector.sslhostconfig.1.hostName", "meecrowave-localhost");

        setProperty("connector.sslhostconfig.2.hostName", "meecrowave.apache.org");
        setProperty("connector.sslhostconfig.2.certificateKeyFile", "meecrowave.key.pem");
        setProperty("connector.sslhostconfig.2.certificateFile", "meecrowave.cert.pem");
        setProperty("connector.sslhostconfig.2.certificateChainFile", "ca-chain.cert.pem");
        setProperty("connector.sslhostconfig.2.protocols", "TLSv1.3");

    }};

    @Test
    public void run() throws IOException {
        try (final Meecrowave CONTAINER = new Meecrowave(new Builder() {{
            randomHttpsPort();
            setSkipHttp(true);
            includePackages("org.apache.meecrowave.tests.ssl");
            setSsl(true);
            setDefaultSSLHostConfigName("localhost");
            setTomcatNoJmx(false);
            setProperties(p);
        }}).bake()) {

            final String confPath = CONTAINER.getBase().getCanonicalPath() + "/conf/";
            SSLHostConfig[] sslHostConfigs = CONTAINER.getTomcat().getService().findConnectors()[0].findSslHostConfigs();

            assertEquals(3, sslHostConfigs.length);

            // In Tomcat 11 sind Zertifikatsinformationen strikter in SSLHostConfigCertificate gekapselt
            // Wir greifen auf das jeweils erste Zertifikat des Hosts zu

            // Validierung Host 0
            SSLHostConfigCertificate cert0 = sslHostConfigs[0].getCertificates().iterator().next();
            assertEquals("localhost", sslHostConfigs[0].getHostName());
            assertTrue(isFilesSame(confPath + keyStorePath1, cert0.getCertificateKeystoreFile()));
            assertEquals("JKS", cert0.getCertificateKeystoreType());
            assertEquals("meecrowave", cert0.getCertificateKeystorePassword());
            assertTrue(isFilesSame(confPath + "meecrowave_trust.jks", sslHostConfigs[0].getTruststoreFile()));

            // Validierung Host 1
            SSLHostConfigCertificate cert1 = sslHostConfigs[1].getCertificates().iterator().next();
            assertEquals("meecrowave-localhost", sslHostConfigs[1].getHostName());
            assertTrue(isFilesSame(confPath + keyStorePath2, cert1.getCertificateKeystoreFile()));
            // Tomcat 11 entfernt veraltete Protokolle aus dem Set, daher prüfen wir auf die neuen Standards
            assertTrue(sslHostConfigs[1].getProtocols().contains("TLSv1.3"));

            // Validierung Host 2
            SSLHostConfigCertificate cert2 = sslHostConfigs[2].getCertificates().iterator().next();
            assertEquals("meecrowave.apache.org", sslHostConfigs[2].getHostName());
            assertTrue(isFilesSame(confPath + "meecrowave.key.pem", cert2.getCertificateKeyFile()));
            assertTrue(isFilesSame(confPath + "meecrowave.cert.pem", cert2.getCertificateFile()));
            assertTrue(sslHostConfigs[2].getProtocols().contains("TLSv1.3"));

            assertEquals("Hello", TestSetup.callJaxrsService(CONTAINER.getConfiguration().getHttpsPort()));
        }
    }

    boolean isFilesSame(final String input, final String output) throws IOException {
        if (input == null || output == null) return false;
        return Files.isSameFile(Paths.get(input), Paths.get(output));
    }
}