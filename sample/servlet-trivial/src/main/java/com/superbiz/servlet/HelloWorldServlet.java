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
package com.superbiz.servlet;

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


/**
 * request with curl would look like
 *
 * http://localhost:8080/?value=HalloNase
 *
 */
@WebServlet("/*")
public class HelloWorldServlet extends HttpServlet {


  @Inject private UpperCaseService service;

  public void doGet(HttpServletRequest request,
                    HttpServletResponse response)
      throws IOException {
    response.setContentType("text/plain; charset=utf-8");

    String value = request.getParameter("value");

    response.getWriter().println(service.upperCase(value));
  }

}
