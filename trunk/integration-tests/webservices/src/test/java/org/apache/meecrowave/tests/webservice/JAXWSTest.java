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
package org.apache.meecrowave.tests.webservice;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.apache.meecrowave.tests.webservices.Contract1;
import org.apache.meecrowave.tests.webservices.Contract2;
import org.junit.ClassRule;
import org.junit.Test;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class JAXWSTest {
    @ClassRule
    public static final MeecrowaveRule CONTAINER = new MeecrowaveRule(new Meecrowave.Builder()
            .randomHttpPort()
            .includePackages(Contract1.class.getPackage().getName()), "");

    @Test
    public void run() throws MalformedURLException {
        assertEquals("foo", Service.create(
                new URL(String.format("http://localhost:%d/webservices/Endpoint1?wsdl", CONTAINER.getConfiguration().getHttpPort())),
                new QName("http://webservices.tests.meecrowave.apache.org/", "Endpoint1Service"))
                .getPort(Contract1.class)
                .foo());
        assertEquals("bar", Service.create(
                new URL(String.format("http://localhost:%d/webservices/Endpoint2?wsdl", CONTAINER.getConfiguration().getHttpPort())),
                new QName("http://webservices.tests.meecrowave.apache.org/", "Endpoint2Service"))
                .getPort(Contract2.class)
                .bar());
    }
}
