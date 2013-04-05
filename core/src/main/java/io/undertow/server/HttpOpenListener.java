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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.conduits.HttpRequestStreamSourceConduit;
import io.undertow.conduits.PipelingBufferingStreamSinkConduit;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

/**
 * Open listener for HTTP server.  XNIO should be set up to chain the accept handler to post-accept open
 * listeners to this listener which actually initiates HTTP parsing.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpOpenListener implements ChannelListener<StreamConnection>, OpenListener {

    private final Pool<ByteBuffer> bufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;

    public HttpOpenListener(final Pool<ByteBuffer> pool, final int bufferSize) {
        this(pool, OptionMap.EMPTY, bufferSize);
    }

    public HttpOpenListener(final Pool<ByteBuffer> pool, final OptionMap undertowOptions, final int bufferSize) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
        this.bufferSize = bufferSize;
    }

    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        HttpServerConnection connection = new HttpServerConnection(channel, bufferPool, rootHandler, undertowOptions, bufferSize);

        final List<ResetableConduit> resetableConduits = new ArrayList<>();
        StreamSinkConduit sinkConduit = channel.getSinkChannel().getConduit();
        StreamSourceConduit sourceConduit = channel.getSourceChannel().getConduit();
        //now we setup the conduits
        if(undertowOptions.get(UndertowOptions.BUFFER_PIPELINED_DATA, false)) {
            PipelingBufferingStreamSinkConduit bufferingStreamSinkConduit = new PipelingBufferingStreamSinkConduit(sinkConduit, connection.getBufferPool(), connection);
            connection.putAttachment(PipelingBufferingStreamSinkConduit.ATTACHMENT_KEY, bufferingStreamSinkConduit);
            sinkConduit = bufferingStreamSinkConduit;
            resetableConduits.add(bufferingStreamSinkConduit);
        }

        HttpResponseConduit httpResponseConduit = new HttpResponseConduit(sinkConduit, connection.getBufferPool());
        sinkConduit = httpResponseConduit;
        resetableConduits.add(httpResponseConduit);

        //setup the stream source conduits
        sourceConduit = new ReadDataStreamSourceConduit(sourceConduit, connection);
        HttpRequestStreamSourceConduit httpRequestStreamSourceConduit = new HttpRequestStreamSourceConduit(sourceConduit);
        resetableConduits.add(httpRequestStreamSourceConduit);
        sourceConduit = httpRequestStreamSourceConduit;


        channel.getSinkChannel().setConduit(sinkConduit);
        channel.getSourceChannel().setConduit(sourceConduit);

        HttpReadListener readListener = new HttpReadListener(resetableConduits, connection);
        readListener.startRequest();
    }

    @Override
    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    @Override
    public void setRootHandler(final HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
    }

    @Override
    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    @Override
    public void setUndertowOptions(final OptionMap undertowOptions) {
        if (undertowOptions == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("undertowOptions");
        }
        this.undertowOptions = undertowOptions;
    }
}
