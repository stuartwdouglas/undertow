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

package io.undertow.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.IoUtils.safeClose;

/**
 * Listener which reads requests and headers off of an HTTP stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpReadListener implements ChannelListener<StreamSourceChannel>, Runnable, ExchangeCompletionListener {

    private final List<ResetableConduit> resetableConduits;
    private final HttpServerConnection connection;
    private final int maxRequestSize;

    private ParseState state;
    private HttpServerExchange httpServerExchange;
    private int read = 0;

    HttpReadListener(final List<ResetableConduit> resetableConduits, final HttpServerConnection connection) {
        this.resetableConduits = resetableConduits;
        this.connection = connection;
        maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
    }

    public void handleEvent(final StreamSourceChannel channel) {

        Pooled<ByteBuffer> existing = connection.getExtraBytes();

        final Pooled<ByteBuffer> pooled = existing == null ? connection.getBufferPool().allocate() : existing;
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;

        try {
            int res;
            do {
                if (existing == null) {
                    buffer.clear();
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
                        }
                        safeClose(channel);
                        return;
                    }
                } else {
                    res = buffer.remaining();
                }

                if (res == 0) {
                    if (!channel.isReadResumed()) {
                        channel.getReadSetter().set(this);
                        channel.resumeReads();
                    }
                    return;
                } else if (res == -1) {
                    //EOF while reading a request, we just close the channel
                    IoUtils.safeClose(channel);
                    return;
                }
                //TODO: we need to handle parse errors
                if (existing != null) {
                    existing = null;
                    connection.setExtraBytes(null);
                } else {
                    buffer.flip();
                }
                HttpParser.INSTANCE.handle(buffer, state, httpServerExchange);
                if (buffer.hasRemaining()) {
                    free = false;
                    connection.setExtraBytes(pooled);
                } else {
                    int total = read + res;
                    read = total;
                    if (read > maxRequestSize) {
                        UndertowLogger.REQUEST_LOGGER.requestHeaderWasTooLarge(connection.getPeerAddress(), maxRequestSize);
                        IoUtils.safeClose(connection);
                        return;
                    }
                }
            } while (!state.isComplete());

            // we remove ourselves as the read listener from the channel;
            // if the http handler doesn't set any then reads will suspend, which is the right thing to do
            channel.getReadSetter().set(null);
            channel.suspendReads();

            final HttpServerExchange httpServerExchange = this.httpServerExchange;
            httpServerExchange.putAttachment(UndertowOptions.ATTACHMENT_KEY, connection.getUndertowOptions());
            try {
                httpServerExchange.setRequestScheme(connection.getSslSession() != null ? "https" : "http");
                state = null;
                HttpTransferEncoding.handleRequest(httpServerExchange, connection.getRootHandler());

            } catch (Throwable t) {
                //TODO: we should attempt to return a 500 status code in this situation
                UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
                IoUtils.safeClose(channel);
                IoUtils.safeClose(connection);
            }
        } catch (Exception e) {
            UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
            IoUtils.safeClose(connection.getChannel());
        } finally {
            if (free) pooled.free();
        }
    }

    public void startRequest() {

        final HttpServerExchange oldExchange = this.httpServerExchange;
        HttpServerConnection connection = this.connection;

        state = new ParseState();
        read = 0;
        HttpServerExchange exchange  = new HttpServerExchange(connection, connection.getChannel().getSourceChannel(), connection.getChannel().getSinkChannel());
        this.httpServerExchange = exchange;
        exchange.addExchangeCompleteListener(this);

        for (final ResetableConduit conduit : resetableConduits) {
            conduit.reset(exchange);
        }

        if(oldExchange == null) {
            //only on the initial request, we just run the read listener directly
            handleEvent(connection.getChannel().getSourceChannel());
        } else if (oldExchange.isPersistent() && !oldExchange.isUpgrade()) {
            final StreamSourceChannel channel = connection.getChannel().getSourceChannel();
            if (connection.getExtraBytes() == null) {
                //if we are not pipelining we just register a listener
                channel.getReadSetter().set(this);
                channel.resumeReads();
            } else {
                if (channel.isReadResumed()) {
                    channel.suspendReads();
                }
                if (oldExchange.isInIoThread()) {
                    channel.getIoThread().execute(this);
                } else {
                    Executor executor = oldExchange.getDispatchExecutor();
                    if (executor == null) {
                        executor = connection.getWorker();
                    }
                    executor.execute(this);
                }
            }
        }
    }

    /**
     * perform a read
     */
    @Override
    public void run() {
        handleEvent(connection.getChannel().getSourceChannel());
    }

    @Override
    public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
        startRequest();
        nextListener.proceed();
    }

}
