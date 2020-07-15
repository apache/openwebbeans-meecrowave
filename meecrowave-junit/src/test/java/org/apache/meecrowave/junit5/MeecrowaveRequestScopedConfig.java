package org.apache.meecrowave.junit5;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.enterprise.context.RequestScoped;

import org.junit.platform.commons.annotation.Testable;

@Testable
@Retention(RetentionPolicy.RUNTIME)
@MeecrowaveConfig(scopes = RequestScoped.class)
public @interface MeecrowaveRequestScopedConfig {

}
