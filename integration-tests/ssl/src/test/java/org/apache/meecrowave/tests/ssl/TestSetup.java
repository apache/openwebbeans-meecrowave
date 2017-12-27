package org.apache.meecrowave.tests.ssl;

import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;

public class TestSetup {
	protected static String callJaxrsService(int portNumber) {
		Client client = ClientBuilder.newBuilder()
	   			 .connectTimeout(5, TimeUnit.SECONDS)
	   			 .readTimeout(5, TimeUnit.SECONDS)
	   			 .build();
		HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
		TLSClientParameters params = conduit.getTlsClientParameters();
				//Accept self signed certificates
				params.setTrustManagers(new TrustManager[]{
				new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
				public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
				public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
				}}); 
		params.setDisableCNCheck(true);
		return client.target("https://localhost:" + portNumber + "/hello")
					 .request(MediaType.TEXT_PLAIN)
					 .get(String.class);
	}
}
