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

package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResetableConduit;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.EmptyStreamSourceConduit;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSourceConduit;

/**
 * @author Stuart Douglas
 */
public class HttpRequestStreamSourceConduit implements StreamSourceConduit, ResetableConduit {

    private final StreamSourceConduit next;

    private HttpServerExchange exchange;
    private StreamSourceConduit selected;
    private ServerChunkedStreamSourceConduit chunked;
    private ServerFixedLengthStreamSourceConduit fixedLength;
    private EmptyStreamSourceConduit empty;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    public HttpRequestStreamSourceConduit(final StreamSourceConduit next) {
        this.next = next;
    }

    @Override
    public void reset(final HttpServerExchange newExchange) {
        if (selected != null) {
            if (chunked == selected) {
                chunked.reset(newExchange);
            } else if (fixedLength == selected) {
                fixedLength.reset(newExchange);
            }
        }
        selected = null;
        this.exchange = newExchange;
        select();
    }

    private void select() {
        if (selected == null) {
            if (exchange.getRequestMethod().equals(Methods.GET)) {
                // no content - immediately start the next request, returning an empty stream for this one
                exchange.terminateRequest();
                if (empty == null) {
                    empty = new EmptyStreamSourceConduit(exchange.getConnection().getIoThread());
                }
                selected = empty;
            } else {
                handleRequestEncoding();
            }
        }
    }


    private void handleRequestEncoding() {
        final String contentLengthHeader = exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            final long contentLength;
            contentLength = Long.parseLong(contentLengthHeader);
            if (contentLength == 0L) {
                // no content - immediately start the next request, returning an empty stream for this one
                exchange.terminateRequest();
                if (empty == null) {
                    empty = new EmptyStreamSourceConduit(exchange.getConnection().getIoThread());
                }
                selected = empty;
            } else {
                // fixed-length content - add a wrapper for a fixed-length stream
                if (fixedLength == null) {
                    fixedLength = new ServerFixedLengthStreamSourceConduit(next);
                }
                fixedLength.reset(exchange);
                fixedLength.setLength(contentLength);
                selected = fixedLength;
            }
        } else {
            String transferCodingHeader = exchange.getRequestHeaders().getLast(Headers.TRANSFER_ENCODING);
            if (transferCodingHeader != null) {
                final HttpString transferCoding = new HttpString(transferCodingHeader);
                if (transferCoding.equals(Headers.CHUNKED)) {
                    if (chunked == null) {
                        chunked = new ServerChunkedStreamSourceConduit(next, exchange.getConnection(), exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE));
                    }
                    chunked.reset(exchange);
                    selected = chunked;
                } else {
                    exchange.setPersistent(false);
                    selected = next;
                }
            } else {
                exchange.setPersistent(false);
                selected = next;
            }

        }

    }


    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return selected.transferTo(position, count, target);
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return selected.transferTo(count, throughBuffer, target);
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        return selected.read(dst);
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offs, final int len) throws IOException {
        return selected.read(dsts, offs, len);
    }

    @Override
    public void terminateReads() throws IOException {
        selected.terminateReads();
    }

    @Override
    public boolean isReadShutdown() {
        return selected.isReadShutdown();
    }

    @Override
    public void resumeReads() {
        selected.resumeReads();
    }

    @Override
    public void suspendReads() {
        selected.suspendReads();
    }

    @Override
    public void wakeupReads() {
        selected.wakeupReads();
    }

    @Override
    public boolean isReadResumed() {
        return selected.isReadResumed();
    }

    @Override
    public void awaitReadable() throws IOException {
        selected.awaitReadable();
    }

    @Override
    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        selected.awaitReadable(time, timeUnit);
    }

    @Override
    public XnioIoThread getReadThread() {
        return selected.getReadThread();
    }

    @Override
    public void setReadReadyHandler(final ReadReadyHandler handler) {
        selected.setReadReadyHandler(handler);
    }

    @Override
    public XnioWorker getWorker() {
        return exchange.getConnection().getWorker();
    }
}
