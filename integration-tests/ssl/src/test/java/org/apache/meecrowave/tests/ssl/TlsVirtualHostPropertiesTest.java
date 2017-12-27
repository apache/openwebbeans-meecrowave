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
        setProperty("connector.sslhostconfig.1.protocols", "TLSv1.1,TLSv1.2");
        setProperty("connector.sslhostconfig.1.hostName", "meecrowave-localhost");
        
        setProperty("connector.sslhostconfig.2.hostName", "meecrowave.apache.org");
        setProperty("connector.sslhostconfig.2.certificateKeyFile", "meecrowave.key.pem");
        setProperty("connector.sslhostconfig.2.certificateFile", "meecrowave.cert.pem");
        setProperty("connector.sslhostconfig.2.certificateChainFile", "ca-chain.cert.pem");
        setProperty("connector.sslhostconfig.2.protocols", "TLSv1.2");
    
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
        assertTrue(isFilesSame(confPath + keyStorePath1, sslHostConfigs[0].getCertificateKeystoreFile()));
        assertEquals("JKS", sslHostConfigs[0].getCertificateKeystoreType());
        assertEquals("meecrowave", sslHostConfigs[0].getCertificateKeystorePassword());
        assertEquals("meecrowave", sslHostConfigs[0].getCertificateKeyAlias());
        assertEquals("localhost", sslHostConfigs[0].getHostName());
        assertTrue(isFilesSame(confPath + "meecrowave_trust.jks", sslHostConfigs[0].getTruststoreFile()));
        assertEquals("meecrowave", sslHostConfigs[0].getTruststorePassword());
        
        assertTrue(isFilesSame(confPath + keyStorePath2, sslHostConfigs[1].getCertificateKeystoreFile()));
        assertEquals("JKS", sslHostConfigs[1].getCertificateKeystoreType());
        assertEquals("meecrowave", sslHostConfigs[1].getCertificateKeystorePassword());
        assertEquals("meecrowave", sslHostConfigs[1].getCertificateKeyAlias());
        assertEquals("meecrowave-localhost", sslHostConfigs[1].getHostName());
        assertEquals(2, sslHostConfigs[1].getProtocols().size());
        
        assertEquals("meecrowave.apache.org", sslHostConfigs[2].getHostName());
        assertTrue(isFilesSame(confPath + "meecrowave.key.pem", sslHostConfigs[2].getCertificateKeyFile()));
        assertTrue(isFilesSame(confPath + "meecrowave.cert.pem", sslHostConfigs[2].getCertificateFile()));
        assertTrue(isFilesSame(confPath + "ca-chain.cert.pem", sslHostConfigs[2].getCertificateChainFile()));
        assertEquals("TLSv1.2", sslHostConfigs[2].getProtocols().toArray()[0]);
        
        assertEquals("Hello", TestSetup.callJaxrsService(CONTAINER.getConfiguration().getHttpsPort()));        
        }
    }
    
    boolean isFilesSame(final String input, final String output) throws IOException {
        return Files.isSameFile(Paths.get(input), Paths.get(output));
    }
}
