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
package org.apache.meecrowave.junit5;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@ExtendWith(MeecrowaveExtension.class)
public @interface MeecrowaveConfig {
    // deployment related config
    String context() default "";

    // container config (note: ensure to keep type and default matching, see MeecrowaveExtension impl)
    int httpPort() default  -1;
    int httpsPort() default -1;
    int stopPort() default -1;
    String host() default "localhost";
    String dir() default "";
    String serverXml() default "";
    boolean keepServerXmlAsThis() default false;
    boolean quickSession() default true;
    boolean skipHttp() default false;
    boolean ssl() default false;
    String keystoreFile() default "";
    String keystorePass() default "";
    String keystoreType() default "JKS";
    String clientAuth() default "";
    String keyAlias() default "";
    String sslProtocol() default "";
    String webXml() default "";
    boolean http2() default false;
    String tempDir() default "";
    boolean webResourceCached() default false;
    String conf() default "";
    boolean deleteBaseOnStartup() default true;
    String jaxrsMapping() default "/*";
    boolean cdiConversation() default false;
    boolean jaxrsProviderSetup() default true;
    String jaxrsDefaultProviders() default "";
    boolean jaxrsLogProviders() default false;
    String jsonpBufferStrategy() default "QUEUE";
    int jsonpMaxStringLen() default 10 * 1024 * 1024;
    int jsonpMaxReadBufferLen() default 64 * 1024;
    int jsonpMaxWriteBufferLen() default 64 * 1024;
    boolean jsonpSupportsComment() default false;
    boolean jsonpPrettify() default false;
    String jsonbEncoding() default "UTF-8";
    boolean jsonbNulls() default false;
    boolean jsonbIJson() default false;
    boolean jsonbPrettify() default false;
    String jsonbBinaryStrategy() default "";
    String jsonbNamingStrategy() default "";
    String jsonbOrderStrategy() default "";
    boolean loggingGlobalSetup() default true;
    boolean tomcatScanning() default true;
    boolean tomcatAutoSetup() default true;
    boolean tomcatJspDevelopmentMode() default false;
    boolean useShutdownHook() default true;
    String tomcatFilter() default "";
    boolean useTomcatDefaults() default true;
    boolean tomcatWrapLoader() default false;
    String sharedLibraries() default "";
    boolean useLog4j2JulLogManager() default false;
    String scanningPackageIncludes() default "";
    String scanningPackageExcludes() default "";
    String scanningIncludes() default "";
    String scanningExcludes() default "";

    Class<? extends Annotation>[] scopes() default {};
}
