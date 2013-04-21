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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.undertow.io.Sender;
import io.undertow.util.Attachable;
import io.undertow.util.HttpString;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * @author Stuart Douglas
 */
public interface HttpServerExchange extends Attachable {

    //basic properties of the request, such as path, method protocol etc

    /**
     * Get the request protocol string.  Normally this is one of the strings listed in {@link io.undertow.util.Protocols}.
     *
     * @return the request protocol string
     */
    HttpString getProtocol();

    /**
     * Sets the http protocol
     *
     * @param protocol
     */
    void setProtocol(HttpString protocol);

    /**
     * Determine whether this request conforms to HTTP 0.9.
     *
     * @return {@code true} if the request protocol is equal to {@link io.undertow.util.Protocols#HTTP_0_9}, {@code false} otherwise
     */
    boolean isHttp09();

    /**
     * Determine whether this request conforms to HTTP 1.0.
     *
     * @return {@code true} if the request protocol is equal to {@link io.undertow.util.Protocols#HTTP_1_0}, {@code false} otherwise
     */
    boolean isHttp10();

    /**
     * Determine whether this request conforms to HTTP 1.1.
     *
     * @return {@code true} if the request protocol is equal to {@link io.undertow.util.Protocols#HTTP_1_1}, {@code false} otherwise
     */
    boolean isHttp11();

    /**
     * Get the HTTP request method.  Normally this is one of the strings listed in {@link io.undertow.util.Methods}.
     *
     * @return the HTTP request method
     */
    HttpString getRequestMethod();

    /**
     * Set the HTTP request method.
     *
     * @param requestMethod the HTTP request method
     */
    void setRequestMethod(HttpString requestMethod);

    /**
     * Get the request URI scheme.  Normally this is one of {@code http} or {@code https}.
     *
     * @return the request URI scheme
     */
    String getRequestScheme();

    /**
     * Set the request URI scheme.
     *
     * @param requestScheme the request URI scheme
     */
    void setRequestScheme(String requestScheme);

    /**
     * Gets the request URI, including hostname, protocol etc if specified by the client.
     * <p/>
     * In most cases this will be equal to {@link #requestPath}
     *
     * @return The request URI
     */
    String getRequestURI();

    /**
     * Sets the request URI
     *
     * @param requestURI The new request URI
     */
    void setRequestURI(String requestURI);

    /**
     * Get the request URI path.  This is the whole original request path.
     *
     * @return the request URI path
     */
    String getRequestPath();

    /**
     * Set the request URI path.
     *
     * @param requestPath the request URI path
     */
    void setRequestPath(String requestPath);

    /**
     * Get the request relative path.  This is the path which should be evaluated by the current handler.
     * <p/>
     * If the {@link io.undertow.server.handlers.CanonicalPathHandler} is installed in the current chain
     * then this path with be canonicalized
     *
     * @return the request relative path
     */
    String getRelativePath();

    /**
     * Set the request relative path.
     *
     * @param relativePath the request relative path
     */
    void setRelativePath(String relativePath);

    /**
     * Get the resolved path.
     *
     * @return the resolved path
     */
    String getResolvedPath();

    /**
     * Set the resolved path.
     *
     * @param resolvedPath the resolved path
     */
    void setResolvedPath(String resolvedPath);

    /**
     * Get the canonical path.
     *
     * @return the canonical path
     */
    String getCanonicalPath();

    /**
     * Set the canonical path.
     *
     * @param canonicalPath the canonical path
     */
    void setCanonicalPath(String canonicalPath);

    /**
     * Returns the query string
     *
     * @return The request query string
     */
    String getQueryString();

    /**
     * Sets the query string part of the request
     *
     * @param queryString The new query string
     */
    void setQueryString(String queryString);

    /**
     * Reconstructs the complete URL as seen by the user. This includes scheme, host name etc,
     * but does not include query string.
     */
    String getRequestURL();


    /**
     * Get the source address of the HTTP request.
     *
     * @return the source address of the HTTP request
     */
    InetSocketAddress getSourceAddress();

    /**
     * Get the destination address of the HTTP request.
     *
     * @return the destination address of the HTTP request
     */
    InetSocketAddress getDestinationAddress();

    /**
     * Returns a mutable map of very parameters.
     *
     * @return The query parameters
     */
    Map<String, Deque<String>> getQueryParameters();

    void addQueryParam(String name, String param);

    /**
     * @return <code>true</code> If this request is an upgrade request, and the connection will be upgraded once this request is done
     */
    boolean isUpgrade();


    /**
     * Change the response code for this response.  If not specified, the code will be a {@code 200}.  Setting
     * the response code after the response headers have been transmitted has no effect.
     *
     * @param responseCode the new code
     * @throws IllegalStateException if a response or upgrade was already sent
     */
    void setResponseCode(int responseCode);

    /**
     * Get the response code.
     *
     * @return the response code
     */
    int getResponseCode();

    //header methods
    //request headers

    /**
     * Returns the first request header value for the given header
     *
     * @param headerName The header name
     * @return The first header value, or <code>null</code> if none was present
     */
    String getRequestHeader(final HttpString headerName);

    /**
     * Returns the last request header value for the given header. In most cases
     * there will only be a single header value.
     *
     * @param headerName The header name
     * @return The last header value, or <code>null</code> if none was present
     */
    String getLastRequestHeader(final HttpString headerName);

    /**
     * Returns all request header value for the given header.
     *
     * @param headerName The header name
     * @return The header values, or <code>null</code> if none was present
     */
    List<String> getRequestHeaders(final HttpString headerName);

    /**
     * Sets the request header.
     *
     * @param headerName The header name
     * @param value      The new header value
     */
    void setRequestHeader(final HttpString headerName, String value);

    /**
     * Adds a new request header
     *
     * @param headerName The header name
     * @param value      The header value
     */
    void addRequestHeader(final HttpString headerName, String value);

    /**
     * Clears a request header
     *
     * @param headerName The header name
     */
    void removeRequestHeader(final HttpString headerName);

    /**
     * Returns <code>true</code> if a given request header is present.
     *
     * @param headerName The header name
     * @return <code>true</code> if the header is present
     */
    boolean isRequestHeaderPresent(final HttpString headerName);

    /**
     * @return A set of all request header names
     */
    Collection<HttpString> getRequestHeaderNames();

    /**
     * Clears all request headers.
     */
    void clearRequestHeaders();

    //response headers


    /**
     * Returns the first response header value for the given header
     *
     * @param headerName The header name
     * @return The first header value, or <code>null</code> if none was present
     */
    String getResponseHeader(final HttpString headerName);

    /**
     * Returns the last response header value for the given header. In most cases
     * there will only be a single header value.
     *
     * @param headerName The header name
     * @return The last header value, or <code>null</code> if none was present
     */
    String getLastResponseHeader(final HttpString headerName);

    /**
     * Returns all response header value for the given header.
     *
     * @param headerName The header name
     * @return The header values, or <code>null</code> if none was present
     */
    List<String> getResponseHeaders(final HttpString headerName);

    /**
     * Sets the response header.
     *
     * @param headerName The header name
     * @param value      The new header value
     */
    void setResponseHeader(final HttpString headerName, String value);

    /**
     * Adds a new response header
     *
     * @param headerName The header name
     * @param value      The header value
     */
    void addResponseHeader(final HttpString headerName, String value);

    /**
     * Clears a response header
     *
     * @param headerName The header name
     */
    void removeResponseHeader(final HttpString headerName);

    /**
     * Returns <code>true</code> if a given response header is present.
     *
     * @param headerName The header name
     * @return <code>true</code> if the header is present
     */
    boolean isResponseHeaderPresent(final HttpString headerName);

    /**
     * @return A set of all response header names
     */
    Collection<HttpString> getResponseHeaderNames();

    /**
     * Clears all response headers.
     */
    void clearResponseHeaders();

    //details about the state of the request.

    /**
     * Get the underlying HTTP connection.
     *
     * @return the underlying HTTP connection
     */
    HttpServerConnection getConnection();

    /**
     * Returns <code>true</code> if this is a persistent request, and the connection will be re-used after
     * the current exchange has completed (assuming nothing fails hard and breaks the connection).
     *
     * @return <code>true</code> if this is a persistent request
     */
    boolean isPersistent();

    /**
     * @return <code>true</code> If this exchange is currently being executed by an IO thread
     */
    boolean isInIoThread();

    /**
     * @return <code>true</code> If this request is dispatched, and will continue after the current call stack ends.
     */
    boolean isDispatched();


    /**
     * Returns true if the completion handler for this exchange has been invoked, and the request is considered
     * finished. This will return true once the request and response channels have both been closed.
     */
    boolean isComplete();

    /**
     * Dispatches this request to the XNIO worker thread pool. Once the call stack returns
     * the given runnable will be submitted to the executor.
     * <p/>
     * In general handlers should first check the value of {@link #isInIoThread()} before
     * calling this method, and only dispatch if the request is actually running in the IO
     * thread.
     *
     * @param runnable The task to run
     * @throws IllegalStateException If this exchange has already been dispatched
     */
    void dispatch(Runnable runnable);

    /**
     * Dispatches this request to the given executor. Once the call stack returns
     * the given runnable will be submitted to the executor.
     * <p/>
     * In general handlers should first check the value of {@link #isInIoThread()} before
     * calling this method, and only dispatch if the request is actually running in the IO
     * thread.
     *
     * @param runnable The task to run
     * @throws IllegalStateException If this exchange has already been dispatched
     */
    void dispatch(Executor executor, Runnable runnable);

    void dispatch(HttpHandler handler);

    void dispatch(Executor executor, HttpHandler handler);

    /**
     * Sets the executor that is used for dispatch operations where no executor is specified.
     *
     * @param executor The executor to use
     */
    void setDispatchExecutor(Executor executor);

    /**
     * Gets the current executor that is used for dispatch operations. This may be null
     *
     * @return The current dispatch executor
     */
    Executor getDispatchExecutor();

    /**
     * Upgrade the channel to a raw socket. This method set the response code to 101, and then marks both the
     * request and response as terminated, which means that once the current request is completed the raw channel
     * can be obtained from {@link HttpServerConnection#getChannel()}
     *
     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
     *                               read
     */
    void upgradeChannel(ExchangeCompletionListener upgradeCompleteListener);

    /**
     * Upgrade the channel to a raw socket. This method set the response code to 101, and then marks both the
     * request and response as terminated, which means that once the current request is completed the raw channel
     * can be obtained from {@link HttpServerConnection#getChannel()}
     *
     * @param productName the product name to report to the client
     * @throws IllegalStateException if a response or upgrade was already sent, or if the request body is already being
     *                               read
     */
    void upgradeChannel(String productName, ExchangeCompletionListener upgradeCompleteListener);

    /**
     * Adds an exchange completion listener. This listener will be notified when the exchange is complete
     *
     * @param listener The completion listener
     */
    void addExchangeCompleteListener(ExchangeCompletionListener listener);

    /**
     * Adds a default response listener. If {@link #endExchange()} is called with no response being send
     * these listeners have a chance to generate some default content such as an error page
     *
     * @param listener The response listener
     */
    void addDefaultResponseListener(DefaultResponseListener listener);

    //IO releated methods

    /**
     * @return <code>true</code> If the response has already been started
     */
    boolean isResponseStarted();

    /**
     * Get the inbound request.  If there is no request body, calling this method
     * may cause the next request to immediately be processed.  The {@link org.xnio.channels.StreamSourceChannel#close()} or {@link org.xnio.channels.StreamSourceChannel#shutdownReads()}
     * method must be called at some point after the request is processed to prevent resource leakage and to allow
     * the next request to proceed.  Any unread content will be discarded.
     *
     * @return the channel for the inbound request, or {@code null} if another party already acquired the channel
     */
    StreamSourceChannel getRequestChannel();

    /**
     * @return <code>true</code> if it is possible to obtain the request channel.
     */
    boolean isRequestChannelAvailable();

    /**
     * Get the response channel. The channel must be closed and fully flushed before the next response can be started.
     * In order to close the channel you must first call {@link org.xnio.channels.StreamSinkChannel#shutdownWrites()},
     * and then call {@link org.xnio.channels.StreamSinkChannel#flush()} until it returns true. Alternativly you can
     * call {@link #endExchange()}, which will close the channel as part of its cleanup.
     * <p/>
     * Closing a fixed-length response before the corresponding number of bytes has been written will cause the connection
     * to be reset and subsequent requests to fail; thus it is important to ensure that the proper content length is
     * delivered when one is specified.  The response channel may not be writable until after the response headers have
     * been sent.
     * <p/>
     * If this method is not called then an empty or default response body will be used, depending on the response code set.
     * <p/>
     * The returned channel will begin to write out headers when the first write request is initiated, or when
     * {@link org.xnio.channels.StreamSinkChannel#shutdownWrites()} is called on the channel with no content being written.
     * Once the channel is acquired, however, the response code and headers may not be modified.
     * <p/>
     * Note that if you call {@link #getResponseSender()} first this method will return null
     *
     * @return the response channel, or {@code null} if another party already acquired the channel
     */
    StreamSinkChannel getResponseChannel();

    /**
     * @return <code>true</code> if {@link #getResponseChannel()} has not been called
     */
    boolean isResponseChannelAvailable();

    /**
     * Get the response sender.
     *
     * If {@link #startBlocking()} has not been called then this will provide a sender wrapper around the
     * response channel, so all the semantics of {@link #getResponseChannel()} apply, namely that if
     * {@link #getResponseChannel()} has already been called this call will return null.
     *
     * If {@link #startBlocking()} has already been called then the underlying sender will write to the
     * output stream in a (potentially) blocking manner, and will always return a usable sender. If the
     * given output stream supports async IO then this sender may use the async IO features of the stream.
     *
     * This method provides a means to write content for both blocking and non-blocking requests without regard
     * for the semantics.
     *
     * @return the response sender, or {@code null} if another party already acquired the channel or the sender
     * @see #getResponseChannel()
     */
    Sender getResponseSender();

    /**
     * Calling this method puts the exchange in blocking mode, and creates a
     * {@link io.undertow.server.BlockingHttpExchange} object to store the streams.
     * <p/>
     * When an exchange is in blocking mode the input stream methods become
     * available, other than that there is presently no major difference
     * between blocking an non-blocking modes.
     *
     * @return The new blocking exchange
     */
    BlockingHttpExchange startBlocking();

    /**
     * Calling this method puts the exchange in blocking mode, using the given
     * blocking exchange as the source of the streams.
     * <p/>
     * When an exchange is in blocking mode the input stream methods become
     * available, other than that there is presently no major difference
     * between blocking an non-blocking modes.
     * <p/>
     * Note that this method may be called multiple times with different
     * exchange objects, to allow handlers to modify the streams
     * that are being used.
     *
     * @return The existing blocking exchange, if any
     */
    BlockingHttpExchange startBlocking(BlockingHttpExchange httpExchange);

    /**
     *
     * @return The input stream
     * @throws IllegalStateException if {@link #startBlocking()} has not been called
     */
    InputStream getInputStream();

    /**
     * @return The output stream
     * @throws IllegalStateException if {@link #startBlocking()} has not been called
     */
    OutputStream getOutputStream();


    /**
     * Ends the exchange by fully draining the request channel, and flushing the response channel.
     * <p/>
     * This can result in handoff to an XNIO worker, so after this method is called the exchange should
     * not be modified by the caller.
     * <p/>
     * If the exchange is already complete this method is a noop
     */
    void endExchange();

    XnioExecutor getIoThread();

    /**
     * Adds a {@link io.undertow.server.ConduitWrapper} to the request wrapper chain.
     *
     * @param wrapper the wrapper
     */
    void addRequestWrapper(ConduitWrapper<StreamSourceConduit> wrapper);

    /**
     * Adds a {@link io.undertow.server.ConduitWrapper} to the response wrapper chain.
     *
     * @param wrapper the wrapper
     */
    void addResponseWrapper(ConduitWrapper<StreamSinkConduit> wrapper);
}
