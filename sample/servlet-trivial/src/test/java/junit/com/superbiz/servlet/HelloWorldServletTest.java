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
package junit.com.superbiz.servlet;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;


public class HelloWorldServletTest {

  @Rule // started once for the class, @Rule would be per method
  public final MeecrowaveRule rule = new MeecrowaveRule();

  @Test
  public void test001() throws IOException {

    OkHttpClient client = new OkHttpClient();

    Request request = new Request.Builder()
        .url("http://127.0.0.1:" + rule.getConfiguration().getHttpPort() + "/?value=HalloNase")
        .get()
        .build();
    Response response = client.newCall(request).execute();
    String   string   = response.body().string().trim();
    System.out.println("string = " + string);
    Assert.assertEquals("HALLONASE", string);
  }
}
