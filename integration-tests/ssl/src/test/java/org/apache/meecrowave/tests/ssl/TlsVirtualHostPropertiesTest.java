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

import java.util.Properties;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.Meecrowave.Builder;
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
</Connector>
 */

public class TlsVirtualHostPropertiesTest {
	private static final String keyStorePath1 = "meecrowave.jks";
	private static final String keyStorePath2 = "meecrowave_second_host.jks";
	
	public static final Properties p = new Properties() {{
		setProperty("connector.attributes.maxThreads", "10");
		
		setProperty("connector.sslhostconfig.certificateKeystoreFile", keyStorePath1);
		setProperty("connector.sslhostconfig.certificateKeystoreType", "JKS");
		setProperty("connector.sslhostconfig.certificateKeystorePassword", "meecrowave");
		setProperty("connector.sslhostconfig.certificateKeyAlias", "meecrowave");
		setProperty("connector.sslhostconfig.hostName", "localhost");
		setProperty("connector.sslhostconfig.truststoreFile", keyStorePath1);
		setProperty("connector.sslhostconfig.truststorePassword", "meecrowave");
		
		setProperty("connector.sslhostconfig.1.certificateKeystoreFile", keyStorePath2);
		setProperty("connector.sslhostconfig.1.certificateKeystoreType", "JKS");
		setProperty("connector.sslhostconfig.1.certificateKeystorePassword", "meecrowave");
		setProperty("connector.sslhostconfig.1.certificateKeyAlias", "meecrowave");
		setProperty("connector.sslhostconfig.1.sslProtocol", "TLSv1.2");
		setProperty("connector.sslhostconfig.1.hostName", "meecrowave-localhost");
		
		setProperty("connector.sslhostconfig.2.hostName", "meecrowave-localhost2");
		setProperty("connector.sslhostconfig.2.certificateKeyFile", "meecrowave.key.pem");
		setProperty("connector.sslhostconfig.2.certificateFile", "meecrowave.cert.pem");
		setProperty("connector.sslhostconfig.2.certificateChainFile", "ca-chain.cert.pem");
		setProperty("connector.sslhostconfig.2.protocols", "TLSv1.2");
	
	}};
    
    @Test
    public void run() {
        try (final Meecrowave CONTAINER = new Meecrowave(new Builder() {{
			    	randomHttpsPort();
					setSkipHttp(true);
					includePackages("org.apache.meecrowave.tests.ssl");
					setSsl(true);
					setDefaultSSLHostConfigName("localhost");
					setTomcatNoJmx(false);
					setProperties(p);
				}}).bake()) {
    	assertEquals(3, CONTAINER.getTomcat().getService().findConnectors()[0].findSslHostConfigs().length);
    	assertEquals("Hello", TestSetup.callJaxrsService(CONTAINER.getConfiguration().getHttpsPort()));		
		}
    }
}
