package org.apache.meecrowave.tomcat.light;

import org.apache.tomcat.util.descriptor.LocalResolver;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;
import org.xml.sax.ext.EntityResolver2;

import static java.util.Collections.emptyMap;

// used to replace org.apache.tomcat.util.descriptor.DigesterFactory in some shades - see pom.xml
public final class LightDigester {
    private LightDigester() {
        // no-op
    }

    public static Digester newDigester(boolean xmlValidation,
                                       boolean xmlNamespaceAware,
                                       RuleSet rule,
                                       boolean blockExternal) {
        if (xmlValidation) {
            throw new IllegalArgumentException("Light distribution does not support xml validation");
        }
        final Digester digester = new Digester();
        digester.setNamespaceAware(xmlNamespaceAware);
        digester.setValidating(false);
        digester.setUseContextClassLoader(true);
        final EntityResolver2 resolver = new LocalResolver(emptyMap(), emptyMap(), blockExternal);
        digester.setEntityResolver(resolver);
        if (rule != null) {
            digester.addRuleSet(rule);
        }
        return digester;
    }
}
