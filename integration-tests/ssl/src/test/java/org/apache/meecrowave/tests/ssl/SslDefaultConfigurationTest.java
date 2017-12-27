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

import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.meecrowave.Meecrowave.Builder;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.ClassRule;
import org.junit.Test;

/*
 * Creates the following connector
 * <Connector port="8443" protocol="HTTP/1.1" SSLEnabled="true" scheme="https" secure="true"
	sslDefaultHost="_default_">
	<SSLHostConfig honorCipherOrder="false" hostName="_default_">
		<Certificate certificateKeystoreFile="meecrowave.jks" 
					 certificateKeystorePassword="meecrowave" 
					 certificateKeyAlias="meecrowave" 
					 truststoreFile = "meecrowave.jks"
					 truststorePassword = "meecrowave"/>
	</SSLHostConfig>
</Connector>
 */

public class SslDefaultConfigurationTest {
	private static final String keyStorePath = "meecrowave.jks";
	
	public static final Properties p = new Properties() {{
		setProperty("connector.sslhostconfig.truststoreFile", keyStorePath);
		setProperty("connector.sslhostconfig.truststorePassword", "meecrowave");
	}};
		
	@ClassRule
    public static final MeecrowaveRule CONTAINER = 
	    new MeecrowaveRule(new Builder() {{
	    	randomHttpsPort();
			setSkipHttp(true);
			includePackages("org.apache.meecrowave.tests.ssl");
			setSsl(true);
			setKeystoreFile(keyStorePath);
			setKeyAlias("meecrowave");
			setKeystorePass("meecrowave");
			setProperties(p);
		}}, "");

    @Test
    public void run() {
		assertEquals("Hello", TestSetup.callJaxrsService(CONTAINER.getConfiguration().getHttpsPort()));				 								
    }
}
