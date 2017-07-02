/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.meecrowave;

import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.junit.Test;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class OctetStreamMediaTypeTest {
    @Test
    public void fields() throws IOException {
        try (final Meecrowave meecrowave = new Meecrowave(new Meecrowave.Builder()
            .randomHttpPort()
            .includePackages(OctetStreamMediaTypeTest.class.getName())).bake()) {
            try (final InputStream stream = new URL(
                "http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/OctetStreamMediaTypeTest/response").openStream()) {
                assertEquals("resp", Streams.asString(stream, "UTF-8"));
            }
            try (final InputStream stream = new URL(
                "http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/OctetStreamMediaTypeTest/responseBytes").openStream()) {
                assertEquals("resp", Streams.asString(stream, "UTF-8"));
            }
            try (final InputStream stream = new URL(
                "http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/OctetStreamMediaTypeTest/streaming").openStream()) {
                assertEquals("stream", Streams.asString(stream, "UTF-8"));
            }
            try (final InputStream stream = new URL(
                "http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/OctetStreamMediaTypeTest/string").openStream()) {
                assertEquals("string", Streams.asString(stream, "UTF-8"));
            }
            try (final InputStream stream = new URL(
                    "http://localhost:" + meecrowave.getConfiguration().getHttpPort() + "/OctetStreamMediaTypeTest/bytes").openStream()) {
                assertEquals("bytes", Streams.asString(stream, "UTF-8"));
            }
        }
    }

    @Path("OctetStreamMediaTypeTest")
    @ApplicationScoped
    public static class App {
        @GET
        @Path("response")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        public Response getResponse() {
            return Response.ok("resp").build();
        }

        @GET
        @Path("responseBytes")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        public Response getResponseBytes() {
            return Response.ok("resp".getBytes()).build();
        }

        @GET
        @Path("streaming")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        public StreamingOutput getStreamingOutput() {
            return output -> output.write("stream".getBytes(StandardCharsets.UTF_8));
        }

        @GET
        @Path("string")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        public String getString() {
            return "string";
        }

        @GET
        @Path("bytes")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        public byte[] getBytes() {
            return "bytes".getBytes(StandardCharsets.UTF_8);
        }
    }
}
