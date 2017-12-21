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
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.Meecrowave.Builder;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.ClassRule;
import org.junit.Test;

/*
 * Creates the following connector
 * <Connector port="8443" protocol="HTTP/1.1" SSLEnabled="true" scheme="https" secure="true"
	sslDefaultHost="localhost">
	<SSLHostConfig honorCipherOrder="false" hostName="localhost">
		<Certificate certificateKeystoreFile="meecrowave.jks" 
					 certificateKeystorePassword="meecrowave" 
					 certificateKeyAlias="meecrowave" 
					 truststoreFile = "meecrowave.jks"
					 truststorePassword = "meecrowave"/>
	</SSLHostConfig>
</Connector>
 */
public class SslHostConfigPropertiesTest {
	private static final String keyStorePath = System.getProperty("user.dir") + "/src/main/resources/meecrowave.jks";
	
	static {
		System.setProperty("javax.net.ssl.trustStore", keyStorePath); 
		System.setProperty("javax.net.ssl.trustStorePassword", "meecrowave");
	}
	
	public static final Properties p = new Properties() {{
		setProperty("connector.sslhostconfig.truststoreFile", "../../target/classes/meecrowave.jks");
		setProperty("connector.sslhostconfig.truststorePassword", "meecrowave");
		setProperty("connector.sslhostconfig.certificateKeystoreFile", keyStorePath);
		setProperty("connector.sslhostconfig.certificateKeystoreType", "JKS");
		setProperty("connector.sslhostconfig.certificateKeystorePassword", "meecrowave");
		setProperty("connector.sslhostconfig.certificateKeyAlias", "meecrowave");
		setProperty("connector.sslhostconfig.hostName", "localhost");
	}};
		
	@ClassRule
    public static final MeecrowaveRule CONTAINER = 
	    new MeecrowaveRule(new Builder() {{
	    	randomHttpsPort();
			setSkipHttp(true);
			includePackages("org.apache.meecrowave.tests.ssl");
			setSsl(true);
			setProperties(p);
		}}, "");
    
    @Test
    public void run() {
    	String response = 
    			ClientBuilder.newBuilder()
			    			 .connectTimeout(5, TimeUnit.SECONDS)
			    			 .readTimeout(5, TimeUnit.SECONDS)
			    			 .build()
    						 .target("https://localhost:" + CONTAINER.getConfiguration().getHttpsPort() + "/hello")
    						 .request(MediaType.TEXT_PLAIN)
							 .get(String.class);
		assertEquals("Hello", response);				 
										
    }
}
