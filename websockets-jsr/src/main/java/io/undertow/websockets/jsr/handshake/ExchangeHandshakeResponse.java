/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.jsr.handshake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.HandshakeResponse;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * {@link HandshakeResponse} which wraps a {@link HttpServerExchange to act on it.
 * Once the processing of it is done {@link #update()} must be called to persist any changes
 * made.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class ExchangeHandshakeResponse implements HandshakeResponse {
    private final HttpServerExchange exchange;
    private Map<String, List<String>> headers;

    public ExchangeHandshakeResponse(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        if (headers == null) {
            headers = new HashMap<>();
            for(HttpString header : exchange.getResponseHeaderNames()) {
                headers.put(header.toString(), new ArrayList<String>(exchange.getResponseHeaders(header)));
            }
        }
        return headers;
    }

    /**
     * Persist all changes and update the wrapped {@link io.undertow.websockets.spi.WebSocketHttpExchange}.
     */
    void update() {
        if (headers != null) {
            exchange.clearResponseHeaders();
            for(Map.Entry<String, List<String>> entry : headers.entrySet()) {
                for(String val : entry.getValue()) {
                    exchange.addResponseHeader(HttpString.tryFromString(entry.getKey()), val);
                }
            }
        }
    }
}
