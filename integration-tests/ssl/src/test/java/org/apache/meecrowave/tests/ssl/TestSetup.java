package org.apache.meecrowave.tests.ssl;

import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

public class TestSetup {
    protected static String callJaxrsService(int portNumber) {
        Client client = ClientBuilder.newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build();
        return client.target("https://localhost:" + portNumber + "/hello")
                     .request(MediaType.TEXT_PLAIN)
                     .get(String.class);
    }
}
