package org.slf4j.bridge;

// dep of org.jbake.app.Oven but we don't want the bridge there
public class SLF4JBridgeHandler {
    public static void removeHandlersForRootLogger(){}
    public static void install() {}
}
