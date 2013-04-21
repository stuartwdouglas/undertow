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

package io.undertow.servlet.websockets;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.io.Sender;
import io.undertow.server.BlockingHttpExchange;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.HttpString;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * A servlet backed implementation of HttpServerExchange.
 *
 *
 * @author Stuart Douglas
 */
public class ServletHttpExchange implements HttpServerExchange {

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public ServletHttpExchange(final HttpServletRequest request, final HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }


    @Override
    public <T> T putAttachment(final AttachmentKey<T> key, final T value) {
        return HttpServletRequestImpl.getRequestImpl(request).getExchange().putAttachment(key, value);
    }

    @Override
    public <T> T removeAttachment(final AttachmentKey<T> key) {
        return null;
    }

    @Override
    public <T> void addToAttachmentList(final AttachmentKey<AttachmentList<T>> key, final T value) {

    }

    @Override
    public <T> T getAttachment(final AttachmentKey<T> key) {
        return HttpServletRequestImpl.getRequestImpl(request).getExchange().getAttachment(key);
    }

    @Override
    public <T> List<T> getAttachmentList(final AttachmentKey<? extends List<T>> key) {
        return null;
    }

    @Override
    public HttpString getProtocol() {
        return null;
    }

    @Override
    public void setProtocol(final HttpString protocol) {

    }

    @Override
    public boolean isHttp09() {
        return false;
    }

    @Override
    public boolean isHttp10() {
        return false;
    }

    @Override
    public boolean isHttp11() {
        return false;
    }

    @Override
    public HttpString getRequestMethod() {
        return null;
    }

    @Override
    public void setRequestMethod(final HttpString requestMethod) {

    }

    @Override
    public String getRequestScheme() {
        return null;
    }

    @Override
    public void setRequestScheme(final String requestScheme) {

    }

    @Override
    public String getRequestURI() {
        return null;
    }

    @Override
    public void setRequestURI(final String requestURI) {

    }

    @Override
    public String getRequestPath() {
        return null;
    }

    @Override
    public void setRequestPath(final String requestPath) {

    }

    @Override
    public String getRelativePath() {
        return null;
    }

    @Override
    public void setRelativePath(final String relativePath) {

    }

    @Override
    public String getResolvedPath() {
        return null;
    }

    @Override
    public void setResolvedPath(final String resolvedPath) {

    }

    @Override
    public String getCanonicalPath() {
        return null;
    }

    @Override
    public void setCanonicalPath(final String canonicalPath) {

    }

    @Override
    public String getQueryString() {
        return null;
    }

    @Override
    public void setQueryString(final String queryString) {

    }

    @Override
    public String getRequestURL() {
        return null;
    }

    @Override
    public InetSocketAddress getSourceAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getDestinationAddress() {
        return null;
    }

    @Override
    public Map<String, Deque<String>> getQueryParameters() {
        return null;
    }

    @Override
    public void addQueryParam(final String name, final String param) {

    }

    @Override
    public boolean isUpgrade() {
        return false;
    }

    @Override
    public void setResponseCode(final int responseCode) {

    }

    @Override
    public int getResponseCode() {
        return 0;
    }

    @Override
    public String getRequestHeader(final HttpString headerName) {
        return request.getHeader(headerName.toString());
    }

    @Override
    public String getLastRequestHeader(final HttpString headerName) {
        return null;
    }

    @Override
    public List<String> getRequestHeaders(final HttpString headerName) {
        return null;
    }

    @Override
    public void setRequestHeader(final HttpString headerName, final String value) {

    }

    @Override
    public void addRequestHeader(final HttpString headerName, final String value) {

    }

    @Override
    public void removeRequestHeader(final HttpString headerName) {

    }

    @Override
    public boolean isRequestHeaderPresent(final HttpString headerName) {
        return false;
    }

    @Override
    public Collection<HttpString> getRequestHeaderNames() {
        return null;
    }

    @Override
    public void clearRequestHeaders() {

    }

    @Override
    public String getResponseHeader(final HttpString headerName) {
        return null;
    }

    @Override
    public String getLastResponseHeader(final HttpString headerName) {
        return null;
    }

    @Override
    public List<String> getResponseHeaders(final HttpString headerName) {
        return null;
    }

    @Override
    public void setResponseHeader(final HttpString headerName, final String value) {

    }

    @Override
    public void addResponseHeader(final HttpString headerName, final String value) {

    }

    @Override
    public void removeResponseHeader(final HttpString headerName) {

    }

    @Override
    public boolean isResponseHeaderPresent(final HttpString headerName) {
        return false;
    }

    @Override
    public Collection<HttpString> getResponseHeaderNames() {
        return null;
    }

    @Override
    public void clearResponseHeaders() {

    }

    @Override
    public HttpServerConnection getConnection() {
        return null;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public boolean isInIoThread() {
        return false;
    }

    @Override
    public boolean isDispatched() {
        return false;
    }

    @Override
    public boolean isComplete() {
        return false;
    }

    @Override
    public void dispatch(final Runnable runnable) {

    }

    @Override
    public void dispatch(final Executor executor, final Runnable runnable) {

    }

    @Override
    public void dispatch(final HttpHandler handler) {

    }

    @Override
    public void dispatch(final Executor executor, final HttpHandler handler) {

    }

    @Override
    public void setDispatchExecutor(final Executor executor) {

    }

    @Override
    public Executor getDispatchExecutor() {
        return null;
    }


    @Override
    public void upgradeChannel(final ExchangeCompletionListener upgradeCallback) {
        HttpServletRequestImpl impl = HttpServletRequestImpl.getRequestImpl(request);
        HttpServerExchange exchange = impl.getExchange();
        exchange.upgradeChannel(upgradeCallback);
    }

    @Override
    public void upgradeChannel(final String productName, final ExchangeCompletionListener upgradeCompleteListener) {

    }

    @Override
    public void addExchangeCompleteListener(final ExchangeCompletionListener listener) {

    }

    @Override
    public void addDefaultResponseListener(final DefaultResponseListener listener) {

    }

    @Override
    public boolean isResponseStarted() {
        return false;
    }

    @Override
    public StreamSourceChannel getRequestChannel() {
        return null;
    }

    @Override
    public boolean isRequestChannelAvailable() {
        return false;
    }

    @Override
    public StreamSinkChannel getResponseChannel() {
        return null;
    }

    @Override
    public boolean isResponseChannelAvailable() {
        return false;
    }

    @Override
    public Sender getResponseSender() {
        return null;
    }

    @Override
    public BlockingHttpExchange startBlocking() {
        return null;
    }

    @Override
    public BlockingHttpExchange startBlocking(final BlockingHttpExchange httpExchange) {
        return null;
    }

    @Override
    public InputStream getInputStream() {
        return null;
    }

    @Override
    public OutputStream getOutputStream() {
        return null;
    }

    @Override
    public void endExchange() {

    }

    @Override
    public XnioExecutor getIoThread() {
        return null;
    }

    @Override
    public void addRequestWrapper(final ConduitWrapper<StreamSourceConduit> wrapper) {

    }

    @Override
    public void addResponseWrapper(final ConduitWrapper<StreamSinkConduit> wrapper) {

    }

}
