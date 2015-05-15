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

package io.undertow;

import io.undertow.connector.ServerConfig;
import io.undertow.connector.ServerConfig.ListenerConfig;
import io.undertow.connector.UndertowConnector;
import io.undertow.connector.UndertowServer;
import io.undertow.server.HttpHandler;
import org.xnio.Option;
import org.xnio.OptionMap;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Convenience class used to build an Undertow server.
 * <p>
 *
 * @author Stuart Douglas
 */
public class Undertow {

    public static final String HTTP = "http";
    public static final String HTTPS = "https";
    public static final String AJP = "ajp";

    private final UndertowServer undertowServer;

    public Undertow(UndertowServer server) {
        this.undertowServer = server;
    }

    public void start() {
        undertowServer.start();
    }

    public void stop() {
        undertowServer.stop();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int bufferSize;
        private int buffersPerRegion;
        private int ioThreads;
        private int workerThreads;
        private boolean directBuffers;
        private final List<ListenerConfig> listeners = new ArrayList<>();
        private HttpHandler handler;

        private final OptionMap.Builder workerOptions = OptionMap.builder();
        private final OptionMap.Builder socketOptions = OptionMap.builder();
        private final OptionMap.Builder serverOptions = OptionMap.builder();

        private Builder() {
            ioThreads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
            workerThreads = ioThreads * 8;
            long maxMemory = Runtime.getRuntime().maxMemory();
            //smaller than 64mb of ram we use 512b buffers
            if (maxMemory < 64 * 1024 * 1024) {
                //use 512b buffers
                directBuffers = false;
                bufferSize = 512;
                buffersPerRegion = 10;
            } else if (maxMemory < 128 * 1024 * 1024) {
                //use 1k buffers
                directBuffers = true;
                bufferSize = 1024;
                buffersPerRegion = 10;
            } else {
                //use 16k buffers for best performance
                //as 16k is generally the max amount of data that can be sent in a single write() call
                directBuffers = true;
                bufferSize = 1024 * 16;
                buffersPerRegion = 20;
            }

        }

        public Undertow build() {
            ServiceLoader<UndertowConnector> connectors = ServiceLoader.load(UndertowConnector.class);
            ServerConfig config = new ServerConfig(bufferSize, buffersPerRegion, ioThreads, workerThreads, directBuffers, listeners, handler, workerOptions.getMap(), socketOptions.getMap(), serverOptions.getMap());
            return new Undertow(connectors.iterator().next().createServer(config));

        }

        @Deprecated
        public Builder addListener(int port, String host) {
            listeners.add(new ListenerConfig(HTTP, port, host, null, null, null, null));
            return this;
        }

        @Deprecated
        public Builder addListener(int port, String host, String listenerType) {
            listeners.add(new ListenerConfig(listenerType, port, host, null, null, null, null));
            return this;
        }

        public Builder addHttpListener(int port, String host) {
            listeners.add(new ListenerConfig(HTTP, port, host, null, null, null, null));
            return this;
        }

        public Builder addHttpsListener(int port, String host, KeyManager[] keyManagers, TrustManager[] trustManagers) {
            listeners.add(new ListenerConfig(HTTPS, port, host, keyManagers, trustManagers, null, null));
            return this;
        }

        public Builder addHttpsListener(int port, String host, SSLContext sslContext) {
            listeners.add(new ListenerConfig(HTTPS, port, host, null, null, sslContext, null));
            return this;
        }

        public Builder addAjpListener(int port, String host) {
            listeners.add(new ListenerConfig(AJP, port, host, null, null, null, null));
            return this;
        }

        public Builder addHttpListener(int port, String host, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(HTTP, port, host, null, null, null, rootHandler));
            return this;
        }

        public Builder addHttpsListener(int port, String host, KeyManager[] keyManagers, TrustManager[] trustManagers, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(HTTPS, port, host, keyManagers, trustManagers, null, rootHandler));
            return this;
        }

        public Builder addHttpsListener(int port, String host, SSLContext sslContext, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(HTTPS, port, host, null, null, sslContext, rootHandler));
            return this;
        }

        public Builder addAjpListener(int port, String host, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(AJP, port, host, null, null, null, rootHandler));
            return this;
        }
        public Builder setBufferSize(final int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder setBuffersPerRegion(final int buffersPerRegion) {
            this.buffersPerRegion = buffersPerRegion;
            return this;
        }

        public Builder setIoThreads(final int ioThreads) {
            this.ioThreads = ioThreads;
            return this;
        }

        public Builder setWorkerThreads(final int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }

        public Builder setDirectBuffers(final boolean directBuffers) {
            this.directBuffers = directBuffers;
            return this;
        }

        public Builder setHandler(final HttpHandler handler) {
            this.handler = handler;
            return this;
        }

        public <T> Builder setServerOption(final Option<T> option, final T value) {
            serverOptions.set(option, value);
            return this;
        }

        public <T> Builder setSocketOption(final Option<T> option, final T value) {
            socketOptions.set(option, value);
            return this;
        }

        public <T> Builder setWorkerOption(final Option<T> option, final T value) {
            workerOptions.set(option, value);
            return this;
        }
    }

}
