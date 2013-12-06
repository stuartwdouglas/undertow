package io.undertow.server.protocol.http;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
public class HttpVersionStateMachine extends AbstractParsingStateMachine {

    public HttpVersionStateMachine() {
        super('\n', '\r');
    }

    @Override
    protected void handleResult(HttpString httpString, ParseState currentState, HttpServerExchange builder) {
        builder.setProtocol(httpString);
        currentState.state++;
        currentState.parseState = 0;
    }
}
