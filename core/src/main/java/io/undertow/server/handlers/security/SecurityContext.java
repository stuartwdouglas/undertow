package io.undertow.server.handlers.security;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.security.Principal;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public interface SecurityContext {

    AttachmentKey<SecurityContext> ATTACHMENT_KEY = AttachmentKey.create(SecurityContext.class);

    /**
     * Performs authentication on the request. If the auth succeeds then the next handler will be invoked, otherwise the
     * completion handler will be called.
     * <p/>
     * Invoking this method can result in worker handoff, once it has been invoked the current handler should not modify the
     * exchange.
     *
     * @param exchange The exchange
     * @param completionHandler The completion handler
     * @param nextHandler The next handler to invoke once auth succeeds
     */
    void authenticate(HttpServerExchange exchange, HttpCompletionHandler completionHandler,  HttpHandler nextHandler);

    /**
     * Authenticates the request using the provided credentials. The authentication is run using the default
     * callback handler and does not affect any configured mechanisms
     *
     * @param username The username to authenticate with
     * @param password The password to authenticate with
     */
    boolean authenticate(final String username, final String password);

    AuthenticationState getAuthenticationState();

    Principal getAuthenticatedPrincipal();

    void addAuthenticationMechanism(AuthenticationMechanism handler);

    List<AuthenticationMechanism> getAuthenticationMechanisms();
}
