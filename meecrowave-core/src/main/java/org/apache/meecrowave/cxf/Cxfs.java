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
package org.apache.meecrowave.cxf;

public class Cxfs {
    public static final boolean IS_PRESENT;

    static {
        boolean present;
        try {
            Cxfs.class.getClassLoader().loadClass("org.apache.cxf.BusFactory");
            present = true;
        } catch (ClassNotFoundException e) {
            present = false;
        }
        IS_PRESENT = present;
    }

    private Cxfs() {
        // no-op
    }

    public static boolean hasDefaultBus() {
        return org.apache.cxf.BusFactory.getDefaultBus(false) != null;
    }

    public static void resetDefaultBusIfEquals(final ConfigurableBus clientBus) {
        if (clientBus != null && org.apache.cxf.BusFactory.getDefaultBus(false) == clientBus) {
            org.apache.cxf.BusFactory.setDefaultBus(null);
        }
    }
}
