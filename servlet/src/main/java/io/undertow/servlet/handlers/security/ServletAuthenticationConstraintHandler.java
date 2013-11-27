/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.servlet.handlers.security;

import java.util.List;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.servlet.api.AuthorizationManager;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.SingleConstraintMatch;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * A simple handler that just sets the auth type to REQUIRED after iterating each of the {@link io.undertow.servlet.api.SingleConstraintMatch} instances
 * and identifying if any require authentication.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ServletAuthenticationConstraintHandler extends AuthenticationConstraintHandler {

    private final AuthorizationManager authorizationManager;
    private final Deployment deployment;


    public ServletAuthenticationConstraintHandler(final HttpHandler next, AuthorizationManager authorizationManager, Deployment deployment) {
        super(next);
        this.authorizationManager = authorizationManager;
        this.deployment = deployment;
    }

    @Override
    protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        List<SingleConstraintMatch> constraints = servletRequestContext.getRequiredConstrains();
        return authorizationManager.isAuthenticationRequired(constraints, servletRequestContext.getCurrentServlet().getManagedServlet().getServletInfo(), servletRequestContext.getOriginalRequest(), deployment);

    }

}
