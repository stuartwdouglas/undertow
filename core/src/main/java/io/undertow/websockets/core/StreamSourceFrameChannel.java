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

package io.undertow.websockets.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import io.undertow.websockets.core.protocol.version07.Masker;
import io.undertow.websockets.core.protocol.version07.UTF8Checker;
import io.undertow.websockets.extensions.ExtensionByteBuffer;
import io.undertow.websockets.extensions.ExtensionFunction;
import org.xnio.Pooled;

import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;

/**
 * Base class for processes Frame bases StreamSourceChannels.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class StreamSourceFrameChannel extends AbstractFramedStreamSourceChannel<WebSocketChannel, StreamSourceFrameChannel, StreamSinkFrameChannel> {

    protected final WebSocketFrameType type;
    private final Masker masker;
    private final UTF8Checker checker;

    private boolean finalFragment;
    private final int rsv;
    private final List<ExtensionFunction> extensions;
    private ExtensionByteBuffer extensionResult;

    protected StreamSourceFrameChannel(WebSocketChannel wsChannel, WebSocketFrameType type,  long frameLength) {
        this(wsChannel, type, 0, true, frameLength, null, null);
    }

    protected StreamSourceFrameChannel(WebSocketChannel wsChannel, WebSocketFrameType type, int rsv, boolean finalFragment, long frameLength, Masker masker, UTF8Checker checker) {
        super(wsChannel, null, frameLength);
        this.type = type;
        this.finalFragment = finalFragment;
        this.rsv = rsv;
        this.masker = masker;
        this.checker = checker;
        if (wsChannel.areExtensionsSupported() && wsChannel.getExtensions() != null && !wsChannel.getExtensions().isEmpty()) {
            extensions = wsChannel.getExtensions();
        } else {
            extensions = null;
        }
        this.extensionResult = null;
    }



    /**
     * Return the {@link WebSocketFrameType} or {@code null} if its not known at the calling time.
     */
    public WebSocketFrameType getType() {
        return type;
    }

    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    public boolean isFinalFragment() {
        return finalFragment;
    }

    /**
     * Return the rsv which is used for extensions.
     */
    public int getRsv() {
        return rsv;
    }

    int getWebSocketFrameCount() {
        return getReadFrameCount();
    }

    @Override
    protected WebSocketChannel getFramedChannel() {
        return super.getFramedChannel();
    }

    public WebSocketChannel getWebSocketChannel() {
        return getFramedChannel();
    }

    public void finalFrame() {
        this.lastFrame();
        this.finalFragment = true;
    }

    //TODO: reduce visibility
    public void firstFrameData(Pooled<ByteBuffer> data) {
        dataReady(null, data);
    }

    @Override
    protected void dataReady(FrameHeaderData headerData, Pooled<ByteBuffer> frameData) {
        if(headerData != null) {
            if (masker != null) {
                masker.newFrame(headerData);
            }
            if (checker != null) {
                checker.newFrame(headerData);
            }
        }
        if(frameData != null) {
            List<Pooled<ByteBuffer>> expanded = handleBuffer(frameData);
            super.dataReady(headerData, frameData);
            if(expanded != null) {
                for(Pooled<ByteBuffer> d : expanded) {
                    super.dataReady(null, d);
                }
            }
        } else {
            super.dataReady(headerData, frameData);
        }
    }

    protected void complete() throws IOException {
        try {
            if (checker != null) {
                checker.complete();
            }
            if (masker != null) {
                masker.complete();
            }
        } catch (IOException e) {
            getFramedChannel().markReadsBroken(e);
            throw e;
        }
    }


    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        if(headerData != null) {
            if (((WebSocketFrame) headerData).isFinalFragment()) {
                finalFrame();
            }
        }
    }

    private List<Pooled<ByteBuffer>> handleBuffer(Pooled<ByteBuffer> frameData) {
        ByteBuffer resource = frameData.getResource();
        if(masker != null) {
            masker.afterRead(resource, resource.position(), resource.remaining());
        }


        if(checker != null) {
            try {
                checker.afterRead(resource, resource.position(), resource.remaining());
            } catch (IOException e) {
                getFramedChannel().markReadsBroken(e);
            }
        }
        return null;
    }

    /**
     * Process Extensions chain after a read operation.
     * <p>
     * An extension can modify original content beyond {@code ByteBuffer} capacity,then original buffer is wrapped with
     * {@link ExtensionByteBuffer} class. {@code ExtensionByteBuffer} stores extra buffer to manage overflow of original
     * {@code ByteBuffer} .
     *
     * @param buffer    the buffer to operate on
     * @param position  the index in the buffer to start from
     * @param length    the number of bytes to operate on
     * @return          a {@link ExtensionByteBuffer} instance as a wrapper of original buffer with extra buffers;
     *                  {@code null} if no extra buffers needed
     * @throws IOException
     */
    protected ExtensionByteBuffer applyExtensions(final ByteBuffer buffer, final int position, final int length) throws IOException {
        ExtensionByteBuffer extBuffer = new ExtensionByteBuffer(getWebSocketChannel(), buffer, position);
        int newLength = length;
        if (extensions != null) {
            for (ExtensionFunction ext : extensions) {
                ext.afterRead(this, extBuffer, position, newLength);
                if (extBuffer.getFilled() == 0) {
                    buffer.position(position);
                    newLength = 0;
                } else if (extBuffer.getFilled() != newLength) {
                    newLength = extBuffer.getFilled();
                }
            }
        }
        if (!extBuffer.hasExtra()) {
            return null;
        }
        return extBuffer;
    }

    private static class Bounds {
        final int position;
        final int limit;

        Bounds(int position, int limit) {
            this.position = position;
            this.limit = limit;
        }
    }
}
