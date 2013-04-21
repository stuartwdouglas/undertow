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

package io.undertow.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.io.Sender;
import io.undertow.io.UndertowInputStream;
import io.undertow.io.UndertowOutputStream;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.SecureHashMap;
import io.undertow.util.WrapperConduitFactory;
import org.jboss.logging.Logger;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.intBitMask;

/**
 * An HTTP server request/response exchange.  An instance of this class is constructed as soon as the request headers are
 * fully parsed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpServerExchangeImpl extends AbstractAttachable implements HttpServerExchange {
    // immutable state

    /**
     * The executor that is to be used to dispatch the {@link #DISPATCH_TASK}. Note that this is not cleared
     * between dispatches, so once a request has been dispatched once then all subsequent dispatches will use
     * the same executor.
     */
    public static final AttachmentKey<Executor> DISPATCH_EXECUTOR = AttachmentKey.create(Executor.class);

    /**
     * When the call stack return this task will be executed by the executor specified in {@link #DISPATCH_EXECUTOR}.
     * If the executor is null then it will be executed by the XNIO worker.
     */
    public static final AttachmentKey<Runnable> DISPATCH_TASK = AttachmentKey.create(Runnable.class);

    private static final Logger log = Logger.getLogger(HttpServerExchangeImpl.class);

    private final HttpServerConnection connection;
    private final HeaderMap requestHeaders = new HeaderMap();
    private final HeaderMap responseHeaders = new HeaderMap();

    private int exchangeCompletionListenersCount = 0;
    private ExchangeCompletionListener[] exchangeCompleteListeners = new ExchangeCompletionListener[2];
    private final Deque<DefaultResponseListener> defaultResponseListeners = new ArrayDeque<DefaultResponseListener>(1);

    private Map<String, Deque<String>> queryParameters;

    /**
     * The actual response channel. May be null if it has not been created yet.
     */
    private StreamSinkChannel responseChannel;
    /**
     * The actual request channel. May be null if it has not been created yet.
     */
    private StreamSourceChannel requestChannel;

    private BlockingHttpExchange blockingHttpExchange;

    private HttpString protocol;

    // mutable state

    private int state = 200;
    private HttpString requestMethod;
    private String requestScheme;
    /**
     * The original request URI. This will include the host name if it was specified by the client
     */
    private String requestURI;
    /**
     * The original request path.
     */
    private String requestPath;
    /**
     * The canonical version of the original path.
     */
    private String canonicalPath;
    /**
     * The remaining unresolved portion of the canonical path.
     */
    private String relativePath;

    /**
     * The resolved part of the canonical path.
     */
    private String resolvedPath = "";

    /**
     * the query string
     */
    private String queryString = "";

    private int requestWrapperCount = 0;
    private ConduitWrapper<StreamSourceConduit>[] requestWrappers = new ConduitWrapper[2];

    private int responseWrapperCount = 0;
    private ConduitWrapper<StreamSinkConduit>[] responseWrappers = new ConduitWrapper[4];

    private static final int MASK_RESPONSE_CODE = intBitMask(0, 9);
    private static final int FLAG_RESPONSE_SENT = 1 << 10;
    private static final int FLAG_RESPONSE_TERMINATED = 1 << 11;
    private static final int FLAG_REQUEST_TERMINATED = 1 << 12;
    private static final int FLAG_PERSISTENT = 1 << 14;

    /**
     * If this flag is set it means that the request has been dispatched,
     * and will not be ending when the call stack returns. This could be because
     * it is being dispatched to a worker thread from an IO thread, or because
     * resume(Reads/Writes) has been called.
     */
    private static final int FLAG_DISPATCHED = 1 << 15;

    /**
     * If this flag is set the request is in an IO thread.
     */
    private static final int FLAG_IN_IO_THREAD = 1 << 16;

    /**
     * If this flag is set then the request is current being processed.
     */
    private static final int FLAG_IN_CALL = 1 << 17;

    public HttpServerExchangeImpl(final HttpServerConnection connection) {
        this.connection = connection;
    }

    @Override
    public HttpString getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(final HttpString protocol) {
        this.protocol = protocol;
    }

    @Override
    public boolean isHttp09() {
        return protocol.equals(Protocols.HTTP_0_9);
    }

    @Override
    public boolean isHttp10() {
        return protocol.equals(Protocols.HTTP_1_0);
    }

    @Override
    public boolean isHttp11() {
        return protocol.equals(Protocols.HTTP_1_1);
    }

    @Override
    public HttpString getRequestMethod() {
        return requestMethod;
    }

    @Override
    public void setRequestMethod(final HttpString requestMethod) {
        this.requestMethod = requestMethod;
    }

    @Override
    public String getRequestScheme() {
        return requestScheme;
    }

    @Override
    public void setRequestScheme(final String requestScheme) {
        this.requestScheme = requestScheme;
    }

    @Override
    public String getRequestURI() {
        return requestURI;
    }

    @Override
    public void setRequestURI(final String requestURI) {
        this.requestURI = requestURI;
    }

    @Override
    public String getRequestPath() {
        return requestPath;
    }

    @Override
    public void setRequestPath(final String requestPath) {
        this.requestPath = requestPath;
    }

    @Override
    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public void setRelativePath(final String relativePath) {
        this.relativePath = relativePath;
    }

    /**
     * internal method used by the parser to set both the request and relative
     * path fields
     */
    public void setParsedRequestPath(final String requestPath) {
        this.relativePath = requestPath;
        this.requestPath = requestPath;
    }

    @Override
    public String getResolvedPath() {
        return resolvedPath;
    }

    @Override
    public void setResolvedPath(final String resolvedPath) {
        this.resolvedPath = resolvedPath;
    }

    @Override
    public String getCanonicalPath() {
        return canonicalPath;
    }

    @Override
    public void setCanonicalPath(final String canonicalPath) {
        this.canonicalPath = canonicalPath;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public void setQueryString(final String queryString) {
        this.queryString = queryString;
    }

    @Override
    public String getRequestURL() {
        String host = getRequestHeaders().getFirst(Headers.HOST);
        if (host == null) {
            host = getDestinationAddress().getAddress().getHostAddress();
        }
        return getRequestScheme() + "://" + host + getRequestURI();
    }

    @Override
    public HttpServerConnection getConnection() {
        return connection;
    }

    @Override
    public boolean isPersistent() {
        return anyAreSet(state, FLAG_PERSISTENT);
    }

    void setInIoThread(final boolean inIoThread) {
        if (inIoThread) {
            state |= FLAG_IN_IO_THREAD;
        } else {
            state &= ~FLAG_IN_IO_THREAD;
        }
    }

    @Override
    public boolean isInIoThread() {
        return anyAreSet(state, FLAG_IN_IO_THREAD);
    }

    @Override
    public boolean isUpgrade() {
        return getResponseCode() == 101;
    }

    public void setPersistent(final boolean persistent) {
        if (persistent) {
            this.state = this.state | FLAG_PERSISTENT;
        } else {
            this.state = this.state & ~FLAG_PERSISTENT;
        }
    }

    @Override
    public boolean isDispatched() {
        return anyAreSet(state, FLAG_DISPATCHED);
    }

    public void unDispatch() {
        state &= ~FLAG_DISPATCHED;
        removeAttachment(DISPATCH_EXECUTOR);
        removeAttachment(DISPATCH_TASK);
    }

    /**
     *
     */
    public void dispatch() {
        state |= FLAG_DISPATCHED;
    }

    @Override
    public void dispatch(final Runnable runnable) {
        dispatch(null, runnable);
    }

    @Override
    public void dispatch(final Executor executor, final Runnable runnable) {
        if (isInCall()) {
            state |= FLAG_DISPATCHED;
            if (executor != null) {
                putAttachment(DISPATCH_EXECUTOR, executor);
            }
            putAttachment(DISPATCH_TASK, runnable);
        } else {
            if (executor == null) {
                getConnection().getWorker().execute(runnable);
            } else {
                executor.execute(runnable);
            }
        }
    }

    @Override
    public void dispatch(final HttpHandler handler) {
        dispatch(null, handler);
    }

    @Override
    public void dispatch(final Executor executor, final HttpHandler handler) {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                HttpHandlers.executeRootHandler(handler, HttpServerExchangeImpl.this, false);
            }
        };
        dispatch(executor, runnable);
    }

    @Override
    public void setDispatchExecutor(final Executor executor) {
        if (executor == null) {
            removeAttachment(DISPATCH_EXECUTOR);
        } else {
            putAttachment(DISPATCH_EXECUTOR, executor);
        }
    }

    @Override
    public Executor getDispatchExecutor() {
        return getAttachment(DISPATCH_EXECUTOR);
    }

    boolean isInCall() {
        return anyAreSet(state, FLAG_IN_CALL);
    }

    void setInCall(boolean value) {
        if (value) {
            state |= FLAG_IN_CALL;
        } else {
            state &= ~FLAG_IN_CALL;
        }
    }


    @Override
    public void upgradeChannel(final ExchangeCompletionListener upgradeCompleteListener) {
        setResponseCode(101);
        final int exchangeCompletionListenersCount = this.exchangeCompletionListenersCount++;
        ExchangeCompletionListener[] exchangeCompleteListeners = this.exchangeCompleteListeners;
        if(exchangeCompleteListeners.length == exchangeCompletionListenersCount) {
            ExchangeCompletionListener[] old = exchangeCompleteListeners;
            this.exchangeCompleteListeners = exchangeCompleteListeners = new ExchangeCompletionListener[exchangeCompletionListenersCount + 2];
            System.arraycopy(old, 0, exchangeCompleteListeners, 1, exchangeCompletionListenersCount);
            exchangeCompleteListeners[0] = upgradeCompleteListener;
        } else {
            for(int i = exchangeCompletionListenersCount - 1; i >=0; --i) {
                exchangeCompleteListeners[i+1] = exchangeCompleteListeners[i];
            }
            exchangeCompleteListeners[0] = upgradeCompleteListener;
        }
    }

    @Override
    public void upgradeChannel(String productName, final ExchangeCompletionListener upgradeCompleteListener) {
        setResponseCode(101);
        final HeaderMap headers = getResponseHeaders();
        headers.add(Headers.UPGRADE, productName);
        headers.add(Headers.CONNECTION, Headers.UPGRADE_STRING);
        final int exchangeCompletionListenersCount = this.exchangeCompletionListenersCount++;
        ExchangeCompletionListener[] exchangeCompleteListeners = this.exchangeCompleteListeners;
        if(exchangeCompleteListeners.length == exchangeCompletionListenersCount) {
            ExchangeCompletionListener[] old = exchangeCompleteListeners;
            this.exchangeCompleteListeners = exchangeCompleteListeners = new ExchangeCompletionListener[exchangeCompletionListenersCount + 2];
            System.arraycopy(old, 0, exchangeCompleteListeners, 1, exchangeCompletionListenersCount);
            exchangeCompleteListeners[0] = upgradeCompleteListener;
        } else {
            for(int i = exchangeCompletionListenersCount - 1; i >=0; --i) {
                exchangeCompleteListeners[i+1] = exchangeCompleteListeners[i];
            }
            exchangeCompleteListeners[0] = upgradeCompleteListener;
        }
    }

    @Override
    public void addExchangeCompleteListener(final ExchangeCompletionListener listener) {
        final int exchangeCompletionListenersCount = this.exchangeCompletionListenersCount++;
        ExchangeCompletionListener[] exchangeCompleteListeners = this.exchangeCompleteListeners;
        if(exchangeCompleteListeners.length == exchangeCompletionListenersCount) {
            ExchangeCompletionListener[] old = exchangeCompleteListeners;
            this.exchangeCompleteListeners = exchangeCompleteListeners = new ExchangeCompletionListener[exchangeCompletionListenersCount + 2];
            System.arraycopy(old, 0, exchangeCompleteListeners, 0, exchangeCompletionListenersCount);
        }
        exchangeCompleteListeners[exchangeCompletionListenersCount] = listener;
    }

    @Override
    public void addDefaultResponseListener(final DefaultResponseListener listener) {
        defaultResponseListeners.add(listener);
    }

    @Override
    public InetSocketAddress getSourceAddress() {
        return connection.getPeerAddress(InetSocketAddress.class);
    }

    @Override
    public InetSocketAddress getDestinationAddress() {
        return connection.getLocalAddress(InetSocketAddress.class);
    }

    /**
     * Get the request headers.
     *
     * @return the request headers
     */
    public HeaderMap getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * Get the response headers.
     *
     * @return the response headers
     */
    public HeaderMap getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public Map<String, Deque<String>> getQueryParameters() {
        if (queryParameters == null) {
            queryParameters = new SecureHashMap<>(0);
        }
        return queryParameters;
    }

    @Override
    public void addQueryParam(final String name, final String param) {
        if (queryParameters == null) {
            queryParameters = new TreeMap<>();
        }
        Deque<String> list = queryParameters.get(name);
        if (list == null) {
            queryParameters.put(name, list = new ArrayDeque<String>());
        }
        list.add(param);
    }

    @Override
    public boolean isResponseStarted() {
        return allAreSet(state, FLAG_RESPONSE_SENT);
    }

    @Override
    public StreamSourceChannel getRequestChannel() {
        final ConduitWrapper<StreamSourceConduit>[] wrappers = this.requestWrappers;
        this.requestWrappers = null;
        if (wrappers == null) {
            return null;
        }
        final ConduitStreamSourceChannel sourceChannel = connection.getChannel().getSourceChannel();
        final WrapperConduitFactory<StreamSourceConduit> factory = new WrapperConduitFactory<>(wrappers, requestWrapperCount, sourceChannel.getConduit(), this);
        sourceChannel.setConduit(factory.create());
        return requestChannel = new ReadDispatchChannel(sourceChannel);
    }

    @Override
    public boolean isRequestChannelAvailable() {
        return requestWrappers != null;
    }

    @Override
    public boolean isComplete() {
        return allAreSet(state, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED);
    }

    /**
     * Force the codec to treat the request as fully read.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    public void terminateRequest() {
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
            // idempotent
            return;
        }
        this.state = oldVal | FLAG_REQUEST_TERMINATED;
        if (anyAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
            invokeExchangeCompleteListeners();
        }
    }

    private void invokeExchangeCompleteListeners() {
        if (exchangeCompletionListenersCount > 0) {
            int i = exchangeCompletionListenersCount- 1;
            ExchangeCompletionListener next = exchangeCompleteListeners[i];
            next.exchangeEvent(this, new ExchangeCompleteNextListener(exchangeCompleteListeners, this, i));
        }
    }

    /**
     * Pushes back the given data. This should only be used by transfer coding handlers that have read past
     * the end of the request when handling pipelined requests
     *
     * @param unget The buffer to push back
     */
    public void ungetRequestBytes(final Pooled<ByteBuffer> unget) {
        if (connection.getExtraBytes() == null) {
            connection.setExtraBytes(unget);
        } else {
            Pooled<ByteBuffer> eb = connection.getExtraBytes();
            ByteBuffer buf = eb.getResource();
            final ByteBuffer ugBuffer = unget.getResource();

            if (ugBuffer.limit() - ugBuffer.remaining() > buf.remaining()) {
                //stuff the existing data after the data we are ungetting
                ugBuffer.compact();
                ugBuffer.put(buf);
                ugBuffer.flip();
                eb.free();
                connection.setExtraBytes(unget);
            } else {
                //TODO: this is horrible, but should not happen often
                final byte[] data = new byte[ugBuffer.remaining() + buf.remaining()];
                int first = ugBuffer.remaining();
                ugBuffer.get(data);
                buf.get(data, first, buf.remaining());
                eb.free();
                unget.free();
                final ByteBuffer newBuffer = ByteBuffer.wrap(data);
                connection.setExtraBytes(new Pooled<ByteBuffer>() {
                    @Override
                    public void discard() {

                    }

                    @Override
                    public void free() {

                    }

                    @Override
                    public ByteBuffer getResource() throws IllegalStateException {
                        return newBuffer;
                    }
                });
            }
        }
    }

    @Override
    public StreamSinkChannel getResponseChannel() {
        final ConduitWrapper<StreamSinkConduit>[] wrappers = responseWrappers;
        this.responseWrappers = null;
        if (wrappers == null) {
            return null;
        }
        final ConduitStreamSinkChannel sinkChannel = connection.getChannel().getSinkChannel();
        final WrapperConduitFactory<StreamSinkConduit> factory = new WrapperConduitFactory<>(wrappers, responseWrapperCount, sinkChannel.getConduit(), this);
        sinkChannel.setConduit(factory.create());
        this.responseChannel = new WriteDispatchChannel(sinkChannel);
        this.startResponse();
        return responseChannel;
    }

    @Override
    public Sender getResponseSender() {
        StreamSinkChannel channel = getResponseChannel();
        if (channel == null) {
            return null;
        }
        return new SenderImpl(channel, this);
    }

    @Override
    public boolean isResponseChannelAvailable() {
        return responseWrappers != null;
    }

    @Override
    public void setResponseCode(final int responseCode) {
        if (responseCode < 0 || responseCode > 999) {
            throw new IllegalArgumentException("Invalid response code");
        }
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
        this.state = oldVal & ~MASK_RESPONSE_CODE | responseCode & MASK_RESPONSE_CODE;
    }

    @Override
    public void addRequestWrapper(final ConduitWrapper<StreamSourceConduit> wrapper) {
        ConduitWrapper<StreamSourceConduit>[] wrappers = requestWrappers;
        if (wrappers == null) {
            throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
        }
        if (wrappers.length == requestWrapperCount) {
            requestWrappers = new ConduitWrapper[wrappers.length + 2];
            System.arraycopy(wrappers, 0, requestWrappers, 0, wrappers.length);
            wrappers = requestWrappers;
        }
        wrappers[requestWrapperCount++] = wrapper;
    }

    @Override
    public void addResponseWrapper(final ConduitWrapper<StreamSinkConduit> wrapper) {
        ConduitWrapper<StreamSinkConduit>[] wrappers = responseWrappers;
        if (wrappers == null) {
            throw UndertowMessages.MESSAGES.requestChannelAlreadyProvided();
        }
        if (wrappers.length == responseWrapperCount) {
            responseWrappers = new ConduitWrapper[wrappers.length + 2];
            System.arraycopy(wrappers, 0, responseWrappers, 0, wrappers.length);
            wrappers = responseWrappers;
        }
        wrappers[responseWrapperCount++] = wrapper;
    }

    @Override
    public BlockingHttpExchange startBlocking() {
        final BlockingHttpExchange old = this.blockingHttpExchange;
        blockingHttpExchange = new DefaultBlockingHttpExchange(this);
        return old;
    }

    @Override
    public BlockingHttpExchange startBlocking(final BlockingHttpExchange httpExchange) {
        final BlockingHttpExchange old = this.blockingHttpExchange;
        blockingHttpExchange = httpExchange;
        return old;
    }


    @Override
    public InputStream getInputStream() {
        if (blockingHttpExchange == null) {
            throw UndertowMessages.MESSAGES.startBlockingHasNotBeenCalled();
        }
        return blockingHttpExchange.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        if (blockingHttpExchange == null) {
            throw UndertowMessages.MESSAGES.startBlockingHasNotBeenCalled();
        }
        return blockingHttpExchange.getOutputStream();
    }

    @Override
    public int getResponseCode() {
        return state & MASK_RESPONSE_CODE;
    }

    @Override
    public String getRequestHeader(final HttpString headerName) {
        return requestHeaders.getFirst(headerName);
    }

    @Override
    public String getLastRequestHeader(final HttpString headerName) {
        return requestHeaders.getLast(headerName);
    }

    @Override
    public List<String> getRequestHeaders(final HttpString headerName) {
        return requestHeaders.get(headerName);
    }

    @Override
    public void setRequestHeader(final HttpString headerName, final String value) {
        requestHeaders.put(headerName, value);
    }

    @Override
    public void addRequestHeader(final HttpString headerName, final String value) {
        requestHeaders.add(headerName, value);
    }

    @Override
    public void removeRequestHeader(final HttpString headerName) {
        requestHeaders.remove(headerName);
    }

    @Override
    public boolean isRequestHeaderPresent(final HttpString headerName) {
        return requestHeaders.contains(headerName);
    }

    @Override
    public Collection<HttpString> getRequestHeaderNames() {
        return requestHeaders.getHeaderNames();
    }

    public void clearRequestHeaders() {
        requestHeaders.clear();
    }

    @Override
    public String getResponseHeader(final HttpString headerName) {
        return responseHeaders.getFirst(headerName);
    }

    @Override
    public String getLastResponseHeader(final HttpString headerName) {
        return responseHeaders.getLast(headerName);
    }

    @Override
    public List<String> getResponseHeaders(final HttpString headerName) {
        return responseHeaders.get(headerName);
    }

    @Override
    public void setResponseHeader(final HttpString headerName, final String value) {
        responseHeaders.put(headerName, value);
    }

    @Override
    public void addResponseHeader(final HttpString headerName, final String value) {
        responseHeaders.add(headerName, value);
    }

    @Override
    public void removeResponseHeader(final HttpString headerName) {
        responseHeaders.remove(headerName);
    }

    @Override
    public boolean isResponseHeaderPresent(final HttpString headerName) {
        return responseHeaders.contains(headerName);
    }

    @Override
    public void clearResponseHeaders() {
        responseHeaders.clear();
    }

    @Override
    public Set<HttpString> getResponseHeaderNames() {
        return null;
    }

    /**
     * Force the codec to treat the response as fully written.  Should only be invoked by handlers which downgrade
     * the socket or implement a transfer coding.
     */
    public void terminateResponse() {
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_TERMINATED)) {
            // idempotent
            return;
        }
        this.state = oldVal | FLAG_RESPONSE_TERMINATED;
        if (anyAreSet(oldVal, FLAG_REQUEST_TERMINATED)) {
            invokeExchangeCompleteListeners();
        }
    }

    @Override
    public void endExchange() {
        final int state = this.state;
        if (allAreSet(state, FLAG_REQUEST_TERMINATED | FLAG_RESPONSE_TERMINATED)) {
            return;
        }
        while (!defaultResponseListeners.isEmpty()) {
            DefaultResponseListener listener = defaultResponseListeners.poll();
            try {
                if (listener.handleDefaultResponse(this)) {
                    return;
                }
            } catch (Exception e) {
                UndertowLogger.REQUEST_LOGGER.debug("Exception running default response listener", e);
            }
        }

        //417 means that we are rejecting the request
        //so the client should not actually send any data
        //TODO: how
        if (anyAreClear(state, FLAG_REQUEST_TERMINATED)) {
            //not really sure what the best thing to do here is
            //for now we are just going to drain the channel
            if (requestChannel == null) {
                getRequestChannel();
            }
            int totalRead = 0;
            for (; ; ) {
                try {
                    long read = Channels.drain(requestChannel, Long.MAX_VALUE);
                    totalRead += read;
                    if (read == 0) {
                        //if the response code is 417 this is a rejected continuation request.
                        //however there is a chance the client could have sent the data anyway
                        //so we attempt to drain, and if we have not drained anything then we
                        //assume the server has not sent any data

                        if (getResponseCode() != 417 || totalRead > 0) {
                            requestChannel.getReadSetter().set(ChannelListeners.drainListener(Long.MAX_VALUE,
                                    new ChannelListener<StreamSourceChannel>() {
                                        @Override
                                        public void handleEvent(final StreamSourceChannel channel) {
                                            if (anyAreClear(state, FLAG_RESPONSE_TERMINATED)) {
                                                closeAndFlushResponse();
                                            }
                                        }
                                    }, new ChannelExceptionHandler<StreamSourceChannel>() {
                                        @Override
                                        public void handleException(final StreamSourceChannel channel, final IOException e) {
                                            UndertowLogger.REQUEST_LOGGER.debug("Exception draining request stream", e);
                                            IoUtils.safeClose(connection.getChannel());
                                        }
                                    }
                            ));
                            requestChannel.resumeReads();
                            return;
                        } else {
                            break;
                        }
                    } else if (read == -1) {
                        break;
                    }
                } catch (IOException e) {
                    UndertowLogger.REQUEST_LOGGER.debug("Exception draining request stream", e);
                    IoUtils.safeClose(connection.getChannel());
                    break;
                }

            }
        }
        if (anyAreClear(state, FLAG_RESPONSE_TERMINATED)) {
            closeAndFlushResponse();
        }
    }

    private void closeAndFlushResponse() {
        try {
            if (isResponseChannelAvailable()) {
                getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                getResponseChannel();
            }
            responseChannel.shutdownWrites();
            if (!responseChannel.flush()) {
                responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                        new ChannelListener<StreamSinkChannel>() {
                            @Override
                            public void handleEvent(final StreamSinkChannel channel) {
                                channel.suspendWrites();
                                channel.getWriteSetter().set(null);
                            }
                        }, new ChannelExceptionHandler<Channel>() {
                            @Override
                            public void handleException(final Channel channel, final IOException exception) {
                                UndertowLogger.REQUEST_LOGGER.debug("Exception ending request", exception);
                                IoUtils.safeClose(connection.getChannel());
                            }
                        }
                ));
                responseChannel.resumeWrites();
            }
        } catch (IOException e) {
            UndertowLogger.REQUEST_LOGGER.debug("Exception ending request", e);
            IoUtils.safeClose(connection.getChannel());
        }
    }

    /**
     * Transmit the response headers. After this method successfully returns,
     * the response channel may become writable.
     * <p/>
     * If this method fails the request and response channels will be closed.
     * <p/>
     * This method runs asynchronously. If the channel is writable it will
     * attempt to write as much of the response header as possible, and then
     * queue the rest in a listener and return.
     * <p/>
     * If future handlers in the chain attempt to write before this is finished
     * XNIO will just magically sort it out so it works. This is not actually
     * implemented yet, so we just terminate the connection straight away at
     * the moment.
     * <p/>
     * TODO: make this work properly
     *
     * @throws IllegalStateException if the response headers were already sent
     */
    void startResponse() throws IllegalStateException {
        int oldVal = state;
        if (allAreSet(oldVal, FLAG_RESPONSE_SENT)) {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
        this.state = oldVal | FLAG_RESPONSE_SENT;

        log.tracef("Starting to write response for %s", this);
    }

    @Override
    public XnioExecutor getIoThread() {
        return connection.getIoThread();
    }

    private static class ExchangeCompleteNextListener implements ExchangeCompletionListener.NextListener {
        private final ExchangeCompletionListener[] list;
        private final HttpServerExchangeImpl exchange;
        private int i;

        public ExchangeCompleteNextListener(final ExchangeCompletionListener[] list, final HttpServerExchangeImpl exchange, int i) {
            this.list = list;
            this.exchange = exchange;
            this.i = i;
        }

        @Override
        public void proceed() {
            if (--i >= 0) {
                final ExchangeCompletionListener next = list[i];
                next.exchangeEvent(exchange, this);
            }
        }
    }

    private static class DefaultBlockingHttpExchange implements BlockingHttpExchange {

        private InputStream inputStream;
        private OutputStream outputStream;
        private final HttpServerExchangeImpl exchange;

        DefaultBlockingHttpExchange(final HttpServerExchangeImpl exchange) {
            this.exchange = exchange;
        }

        public InputStream getInputStream() {
            if (inputStream == null) {
                inputStream = new UndertowInputStream(exchange);
            }
            return inputStream;
        }

        public OutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new UndertowOutputStream(exchange);
            }
            return outputStream;
        }
    }

    /**
     * Channel implementation that is actually provided to clients of the exchange.
     *
     * We do not provide the underlying conduit channel, as this is shared between requests, so we need to make sure that after this request
     * is done the the channel cannot affect the next request.
     *
     * It also delays a wakeup/resumesWrites calls until the current call stack has returned, thus ensuring that only 1 thread is
     * active in the exchange at any one time.
     *
     */
    private class WriteDispatchChannel implements StreamSinkChannel, Runnable {

        protected final StreamSinkChannel delegate;
        protected final ChannelListener.SimpleSetter<WriteDispatchChannel> writeSetter = new ChannelListener.SimpleSetter<WriteDispatchChannel>();
        protected final ChannelListener.SimpleSetter<WriteDispatchChannel> closeSetter = new ChannelListener.SimpleSetter<WriteDispatchChannel>();
        private boolean wakeup;

        public WriteDispatchChannel(final StreamSinkChannel delegate) {
            this.delegate = delegate;
            delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener(this, writeSetter));
            delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
        }


        @Override
        public void suspendWrites() {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return;
            }
            delegate.suspendWrites();
        }


        @Override
        public boolean isWriteResumed() {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return false;
            }
            return delegate.isWriteResumed();
        }

        @Override
        public void shutdownWrites() throws IOException {
            if(allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return;
            }
            delegate.shutdownWrites();
        }

        @Override
        public void awaitWritable() throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            delegate.awaitWritable();
        }

        @Override
        public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            delegate.awaitWritable(time, timeUnit);
        }

        @Override
        public XnioExecutor getWriteThread() {
            return delegate.getWriteThread();
        }

        @Override
        public boolean isOpen() {
            return !allAreSet(state, FLAG_RESPONSE_TERMINATED) && delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) return;
            delegate.close();
        }

        @Override
        public boolean flush() throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return true;
            }
            return delegate.flush();
        }

        @Override
        public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.transferFrom(src, position, count);
        }

        @Override
        public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.transferFrom(source, count, throughBuffer);
        }

        @Override
        public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
            return writeSetter;
        }

        @Override
        public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
            return closeSetter;
        }

        @Override
        public XnioWorker getWorker() {
            return delegate.getWorker();
        }

        @Override
        public XnioIoThread getIoThread() {
            return delegate.getIoThread();
        }

        @Override
        public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.write(srcs, offset, length);
        }

        @Override
        public long write(final ByteBuffer[] srcs) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.write(srcs);
        }

        @Override
        public boolean supportsOption(final Option<?> option) {
            return delegate.supportsOption(option);
        }

        @Override
        public <T> T getOption(final Option<T> option) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.getOption(option);
        }

        @Override
        public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.setOption(option, value);
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.write(src);
        }

        @Override
        public void resumeWrites() {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return;
            }
            if (isInCall()) {
                wakeup = false;
                dispatch(SameThreadExecutor.INSTANCE, this);
            } else {
                delegate.resumeWrites();
            }
        }

        @Override
        public void wakeupWrites() {
            if (allAreSet(state, FLAG_RESPONSE_TERMINATED)) {
                return;
            }
            if (isInCall()) {
                wakeup = true;
                dispatch(SameThreadExecutor.INSTANCE, this);
            } else {
                delegate.wakeupWrites();
            }
        }

        @Override
        public void run() {
            if (wakeup) {
                delegate.wakeupWrites();
            } else {
                delegate.resumeWrites();
            }
        }
    }

    /**
     * Channel implementation that is actually provided to clients of the exchange. We do not provide the underlying
     * conduit channel, as this will become the next requests conduit channel, so if a thread is still hanging onto this
     * exchange it can result in problems.
     *
     * It also delays a readResume call until the current call stack has returned, thus ensuring that only 1 thread is
     * active in the exchange at any one time.
     *
     */
    private final class ReadDispatchChannel implements StreamSourceChannel, Runnable {

        private final StreamSourceChannel delegate;

        protected final ChannelListener.SimpleSetter<ReadDispatchChannel> readSetter = new ChannelListener.SimpleSetter<ReadDispatchChannel>();
        protected final ChannelListener.SimpleSetter<ReadDispatchChannel> closeSetter = new ChannelListener.SimpleSetter<ReadDispatchChannel>();

        public ReadDispatchChannel(final StreamSourceChannel delegate) {
            this.delegate = delegate;
            delegate.getReadSetter().set(ChannelListeners.delegatingChannelListener(this, readSetter));
            delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
        }

        @Override
        public void resumeReads() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            if (isInCall()) {
                dispatch(SameThreadExecutor.INSTANCE, this);
            } else {
                delegate.resumeReads();
            }
        }


        @Override
        public void run() {
            if (!allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                delegate.resumeReads();
            }
        }


        public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.transferTo(position, count, target);
        }

        public void awaitReadable() throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            delegate.awaitReadable();
        }

        public void suspendReads() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.suspendReads();
        }

        public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {

            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.transferTo(count, throughBuffer, target);
        }

        public XnioWorker getWorker() {
            return delegate.getWorker();
        }

        public boolean isReadResumed() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return false;
            }
            return delegate.isReadResumed();
        }

        public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {

            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            return delegate.setOption(option, value);
        }

        public boolean supportsOption(final Option<?> option) {
            return delegate.supportsOption(option);
        }

        public void shutdownReads() throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.shutdownReads();
        }

        public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
            return readSetter;
        }

        public boolean isOpen() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return false;
            }
            return delegate.isOpen();
        }

        public long read(final ByteBuffer[] dsts) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.read(dsts);
        }

        public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.read(dsts, offset, length);
        }

        public void wakeupReads() {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.wakeupReads();
        }

        public XnioExecutor getReadThread() {
            return delegate.getReadThread();
        }

        public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                throw UndertowMessages.MESSAGES.channelIsClosed();
            }
            delegate.awaitReadable(time, timeUnit);
        }

        public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
            return closeSetter;
        }

        public void close() throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return;
            }
            delegate.close();
        }

        public <T> T getOption(final Option<T> option) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                throw UndertowMessages.MESSAGES.streamIsClosed();
            }
            return delegate.getOption(option);
        }

        public int read(final ByteBuffer dst) throws IOException {
            if (allAreSet(state, FLAG_REQUEST_TERMINATED)) {
                return -1;
            }
            return delegate.read(dst);
        }

        @Override
        public XnioIoThread getIoThread() {
            return delegate.getIoThread();
        }
    }
}
