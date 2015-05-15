/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.connector.xnio;

import io.undertow.UndertowOptions;
import io.undertow.buffers.DefaultByteBufferPool;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.ServerConfig;
import io.undertow.connector.UndertowServer;
import io.undertow.connector.xnio.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.connector.xnio.server.ajp.AjpOpenListener;
import io.undertow.connector.xnio.server.http.AlpnOpenListener;
import io.undertow.connector.xnio.server.http.HttpOpenListener;
import io.undertow.connector.xnio.server.http2.Http2OpenListener;
import io.undertow.connector.xnio.server.spdy.SpdyOpenListener;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.ssl.SslConnection;
import org.xnio.ssl.XnioSsl;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class XnioUndertowServer implements UndertowServer {

    private final ServerConfig config;

    private XnioWorker worker;
    private List<AcceptingChannel<? extends StreamConnection>> channels;
    private Xnio xnio;

    XnioUndertowServer(ServerConfig builder) {
        this.config = builder;
    }


    public synchronized void start() {
        xnio = Xnio.getInstance(XnioUndertowServer.class.getClassLoader());
        channels = new ArrayList<>();
        try {
            worker = xnio.createWorker(OptionMap.builder()
                    .set(Options.WORKER_IO_THREADS, config.getIoThreads())
                    .set(Options.CONNECTION_HIGH_WATER, 1000000)
                    .set(Options.CONNECTION_LOW_WATER, 1000000)
                    .set(Options.WORKER_TASK_CORE_THREADS, config.getWorkerThreads())
                    .set(Options.WORKER_TASK_MAX_THREADS, config.getWorkerThreads())
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.CORK, true)
                    .addAll(config.getWorkerOptions())
                    .getMap());

            OptionMap socketOptions = OptionMap.builder()
                    .set(Options.WORKER_IO_THREADS, config.getIoThreads())
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true)
                    .set(Options.BALANCING_TOKENS, 1)
                    .set(Options.BALANCING_CONNECTIONS, 2)
                    .set(Options.BACKLOG, 1000)
                    .addAll(this.config.getSocketOptions())
                    .getMap();


            ByteBufferPool buffers = new DefaultByteBufferPool(config.isDirectBuffers(), config.getBufferSize(), config.getBuffersPerRegion(), 3);

            for (ServerConfig.ListenerConfig listener : config.getListeners()) {
                final HttpHandler rootHandler = listener.getRootHandler() != null ? listener.getRootHandler() : this.config.getRootHandler();
                OptionMap undertowOptions = OptionMap.builder().set(UndertowOptions.BUFFER_PIPELINED_DATA, true).addAll(config.getServerOptions()).getMap();
                if (listener.getType().equals("ajp")) {
                    AjpOpenListener openListener = new AjpOpenListener(buffers, config.getServerOptions());
                    openListener.setRootHandler(rootHandler);
                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.getHost()), listener.getPort()), acceptListener, socketOptions);
                    server.resumeAccepts();
                    channels.add(server);
                } else if (listener.getType().equals("http")) {
                    HttpOpenListener openListener = new HttpOpenListener(buffers, undertowOptions);
                    openListener.setRootHandler(rootHandler);
                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.getHost()), listener.getPort()), acceptListener, socketOptions);
                    server.resumeAccepts();
                    channels.add(server);
                } else if (listener.getType().equals("https")) {
                    ChannelListener<StreamConnection> openListener;

                    HttpOpenListener httpOpenListener = new HttpOpenListener(buffers, undertowOptions);
                    httpOpenListener.setRootHandler(rootHandler);

                    boolean spdy = config.getServerOptions().get(UndertowOptions.ENABLE_SPDY, false);
                    boolean http2 = config.getServerOptions().get(UndertowOptions.ENABLE_HTTP2, false);
                    if (spdy || http2) {
                        AlpnOpenListener alpn = new AlpnOpenListener(buffers, undertowOptions, httpOpenListener);
                        if (spdy) {
                            SpdyOpenListener spdyListener = new SpdyOpenListener(buffers, new DefaultByteBufferPool(false, 1024, 100, 1), undertowOptions);
                            spdyListener.setRootHandler(rootHandler);
                            alpn.addProtocol(SpdyOpenListener.SPDY_3_1, spdyListener, 5);
                        }
                        if (http2) {
                            Http2OpenListener http2Listener = new Http2OpenListener(buffers, undertowOptions);
                            http2Listener.setRootHandler(rootHandler);
                            alpn.addProtocol(Http2OpenListener.HTTP2, http2Listener, 10);
                            alpn.addProtocol(Http2OpenListener.HTTP2_14, http2Listener, 7);
                        }
                        openListener = alpn;
                    } else {
                        openListener = httpOpenListener;
                    }
                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    XnioSsl xnioSsl;
                    if (listener.getSslContext() != null) {
                        xnioSsl = new UndertowXnioSsl(xnio, OptionMap.create(Options.USE_DIRECT_BUFFERS, true), listener.getSslContext());
                    } else {
                        xnioSsl = xnio.getSslProvider(listener.getKeyManagers(), listener.getTrustManagers(), OptionMap.create(Options.USE_DIRECT_BUFFERS, true));
                    }
                    AcceptingChannel<SslConnection> sslServer = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(Inet4Address.getByName(listener.getHost()), listener.getPort()), (ChannelListener) acceptListener, socketOptions);
                    sslServer.resumeAccepts();
                    channels.add(sslServer);

                } else {
                    throw new IllegalStateException("Unknown listener");
                }

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void stop() {
        for (AcceptingChannel<? extends StreamConnection> channel : channels) {
            IoUtils.safeClose(channel);
        }
        channels = null;
        worker.shutdownNow();
        worker = null;
        xnio = null;
    }

    @Override
    public void close() {
        stop();
    }

}
