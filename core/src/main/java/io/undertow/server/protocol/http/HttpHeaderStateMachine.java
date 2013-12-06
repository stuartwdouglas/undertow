package io.undertow.server.protocol.http;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
public class HttpHeaderStateMachine extends AbstractParsingStateMachine {

    public HttpHeaderStateMachine() {
        super(':', ' ');
    }

    @Override
    protected void handleResult(HttpString httpString, ParseState currentState, HttpServerExchange builder) {
        currentState.nextHeader = httpString;
        currentState.state++;
        currentState.parseState = 0;
    }
}
