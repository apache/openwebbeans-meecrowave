package org.apache.meecrowave.letencrypt;

import static java.util.Optional.ofNullable;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.http11.AbstractHttp11Protocol;

public class LetsEncryptValve extends ValveBase {
    private final AbstractHttp11Protocol<?> protocol;
    private final LetsEncryptReloadLifecycle.LetsEncryptConfig config;
    private LetsEncryptReloadLifecycle support;

    private volatile Current current = new Current("/.well-known/acme-challenge/-", "none");

    public LetsEncryptValve(final AbstractHttp11Protocol<?> protocol,
                            final LetsEncryptReloadLifecycle.LetsEncryptConfig config) {
        super(true);
        this.protocol = protocol;
        this.config = config;
    }

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        support = new LetsEncryptReloadLifecycle(config, protocol, (e, c) -> current = new Current(e, c));
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        ofNullable(support).ifPresent(LetsEncryptReloadLifecycle::close);
    }

    @Override
    public void invoke(final Request request, final Response response) throws IOException, ServletException {
        if (request.getRequestURI().equals(current.endpoint)) {
            response.setHeader("Content-Type", "text/plain");
            response.getWriter().write(current.challenge);
            return;
        }
        getNext().invoke(request, response);
    }

    private static class Current {
        private final String endpoint;
        private final String challenge;

        private Current(final String endpoint, final String challenge) {
            this.endpoint = endpoint;
            this.challenge = challenge;
        }
    }
}
