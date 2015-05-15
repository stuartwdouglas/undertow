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

package io.undertow.connector;

import io.undertow.server.HttpHandler;
import org.xnio.OptionMap;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class ServerConfig {


    private final int bufferSize;
    private final int buffersPerRegion;
    private final int ioThreads;
    private final int workerThreads;
    private final boolean directBuffers;
    private final List<ListenerConfig> listeners;
    private final HttpHandler rootHandler;
    private final OptionMap workerOptions;
    private final OptionMap socketOptions;
    private final OptionMap serverOptions;

    public ServerConfig(int bufferSize, int buffersPerRegion, int ioThreads, int workerThreads, boolean directBuffers, List<ListenerConfig> listeners, HttpHandler rootHandler, OptionMap workerOptions, OptionMap socketOptions, OptionMap serverOptions) {
        this.bufferSize = bufferSize;
        this.buffersPerRegion = buffersPerRegion;
        this.ioThreads = ioThreads;
        this.workerThreads = workerThreads;
        this.directBuffers = directBuffers;
        this.listeners = listeners;
        this.rootHandler = rootHandler;
        this.workerOptions = workerOptions;
        this.socketOptions = socketOptions;
        this.serverOptions = serverOptions;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getBuffersPerRegion() {
        return buffersPerRegion;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public boolean isDirectBuffers() {
        return directBuffers;
    }

    public List<ListenerConfig> getListeners() {
        return listeners;
    }

    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    public OptionMap getWorkerOptions() {
        return workerOptions;
    }

    public OptionMap getSocketOptions() {
        return socketOptions;
    }

    public OptionMap getServerOptions() {
        return serverOptions;
    }

    public static class ListenerConfig {

        private final String type;
        private final int port;
        private final String host;
        private final KeyManager[] keyManagers;
        private final TrustManager[] trustManagers;
        private final SSLContext sslContext;
        private final HttpHandler rootHandler;

        public ListenerConfig(String type, int port, String host, KeyManager[] keyManagers, TrustManager[] trustManagers, SSLContext sslContext, HttpHandler rootHandler) {
            this.type = type;
            this.port = port;
            this.host = host;
            this.keyManagers = keyManagers;
            this.trustManagers = trustManagers;
            this.sslContext = sslContext;
            this.rootHandler = rootHandler;
        }

        public String getType() {
            return type;
        }

        public int getPort() {
            return port;
        }

        public String getHost() {
            return host;
        }

        public KeyManager[] getKeyManagers() {
            return keyManagers;
        }

        public TrustManager[] getTrustManagers() {
            return trustManagers;
        }

        public SSLContext getSslContext() {
            return sslContext;
        }

        public HttpHandler getRootHandler() {
            return rootHandler;
        }
    }

}
