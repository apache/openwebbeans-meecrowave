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
package org.apache.meecrowave.lang;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SubstitutorTest {

    @Test
    public void noSubstitute() {
        assertEquals("${foo}", new Substitutor(emptyMap()).replace("${foo}"));
    }

    @Test
    public void substitute() {
        assertEquals("bar", new Substitutor(singletonMap("foo", "bar")).replace("${foo}"));
    }

    @Test
    public void defaultValue() {
        assertEquals("or", new Substitutor(emptyMap()).replace("${foo:-or}"));
    }

    @Test
    public void nested() {
        assertEquals("bar", new Substitutor(singletonMap("foo", "bar")).replace("${any:-${foo}}"));
    }

    @Test
    public void twiceNested() {
        assertEquals("bar", new Substitutor(singletonMap("other", "bar")).replace("${any:-${foo:-${other}}}"));
    }

    @Test
    public void composed() {
        assertEquals("pref-bar-suff", new Substitutor(singletonMap("foo", "bar")).replace("pref-${foo}-suff"));
    }
}
