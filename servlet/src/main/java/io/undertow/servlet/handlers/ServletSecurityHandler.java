package io.undertow.servlet.handlers;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Handler that applies a specified role requirement to a
 *
 * @author Stuart Douglas
 */
public class ServletSecurityHandler implements HttpHandler {

    private final HttpHandler next;

    public ServletSecurityHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        int dispatcher = exchange.
    }
}
