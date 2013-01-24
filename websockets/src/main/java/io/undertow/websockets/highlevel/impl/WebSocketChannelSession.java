/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.highlevel.impl;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketUtils;
import io.undertow.websockets.highlevel.CloseCode;
import io.undertow.websockets.highlevel.PartialWebSocketFrameHandler;
import io.undertow.websockets.highlevel.PartialWebSocketFrameSender;
import io.undertow.websockets.highlevel.PartialWebSocketTextFrameSender;
import io.undertow.websockets.highlevel.SendCallback;
import io.undertow.websockets.highlevel.WebSocketSession;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link WebSocketSession} implementation which wraps a {@link WebSocketChannel} and operate on its API.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class WebSocketChannelSession implements WebSocketSession {
    private final WebSocketChannel channel;
    private final String id;
    private final ConcurrentMap<String, Object> attrs = new ConcurrentHashMap<String, Object>();
    private final AtomicReference<PartialWebSocketFrameHandler> binaryFrameHandler =
            new AtomicReference<PartialWebSocketFrameHandler>();
    private final AtomicReference<PartialWebSocketFrameHandler> textFrameHandler =
            new AtomicReference<PartialWebSocketFrameHandler>();

    public WebSocketChannelSession(WebSocketChannel channel, String id) {
        this.channel = channel;
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void sendBinary(ByteBuffer payload, SendCallback callback) {
        send(WebSocketFrameType.BINARY, payload, callback);
    }

    @Override
    public void sendText(String payload, SendCallback callback) {
        send(WebSocketFrameType.BINARY, ByteBuffer.wrap(payload.getBytes(WebSocketUtils.UTF_8)), callback);
    }

    @Override
    public PartialWebSocketFrameSender sendBinary(final long payloadSize) {
        return new PartialWebSocketChannelFrameSender(channel, WebSocketFrameType.BINARY, payloadSize);
    }

    @Override
    public PartialWebSocketTextFrameSender sendText(long payloadSize) {
        return new PartialWebSocketChannelTextFrameSender(channel, payloadSize);
    }

    @Override
    public void close(SendCallback callback) {
        close(CloseCode.OK, null, callback);
    }

    @Override
    public void close(CloseCode code, String reason, final SendCallback callback) {
        final Pooled<ByteBuffer> pooled = channel.getBufferPool().allocate();
        SendCallback pooledCallback = new PooledSendCallback(new SendCallback() {
            @Override
            public void onCompletion() {
                callback.onCompletion();
                IoUtils.safeClose(channel);
            }

            @Override
            public void onError(Throwable cause) {
                callback.onError(cause);
                IoUtils.safeClose(channel);
            }
        }, pooled);
        ByteBuffer buffer = pooled.getResource();
        buffer.putShort((short) code.getCode());
        if (reason != null) {
            buffer.put(reason.getBytes(WebSocketUtils.UTF_8));
        }
        buffer.flip();

        send(WebSocketFrameType.CLOSE, buffer, pooledCallback);
    }

    private void send(WebSocketFrameType type, final ByteBuffer buffer, final SendCallback callback) {
        try {
            StreamSinkFrameChannel sink = channel.send(type, buffer.remaining());
            while (buffer.hasRemaining()) {
                if (sink.write(buffer) == 0) {
                    sink.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(StreamSinkChannel sink) {
                            try {
                                while (buffer.hasRemaining()) {
                                    if (sink.write(buffer) == 0) {
                                        sink.resumeWrites();
                                        return;
                                    }
                                }
                                StreamSinkUtils.shutdownAndFlush(sink, callback);
                            } catch (IOException e) {
                                callback.onError(e);
                            }
                        }
                    });
                    sink.resumeWrites();
                    return;
                }
            }
            StreamSinkUtils.shutdownAndFlush(sink, callback);
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    @Override
    public boolean setAttribute(String key, Object value) {
        if (value == null) {
            return attrs.remove(key) != null;
        } else {
            return attrs.putIfAbsent(key, value) == null;
        }
    }

    @Override
    public Object getAttribute(String key) {
        return attrs.get(key);
    }

    @Override
    public boolean isSecure() {
        return channel.isSecure();
    }

    @Override
    public String getPath() {
        return null;
    }

    @Override
    public PartialWebSocketFrameHandler setTextFrameHandler(PartialWebSocketFrameHandler handler) {
        return textFrameHandler.getAndSet(handler);
    }

    @Override
    public PartialWebSocketFrameHandler getTextFrameHandler() {
        return textFrameHandler.get();
    }

    @Override
    public PartialWebSocketFrameHandler removeTextFrameHandler() {
        return textFrameHandler.getAndSet(null);
    }

    @Override
    public PartialWebSocketFrameHandler setBinaryFrameHandler(PartialWebSocketFrameHandler handler) {
        return binaryFrameHandler.getAndSet(handler);
    }

    @Override
    public PartialWebSocketFrameHandler getBinaryFrameHandler() {
        return binaryFrameHandler.get();
    }

    @Override
    public PartialWebSocketFrameHandler removeBinaryFrameHandler() {
        return binaryFrameHandler.getAndSet(null);
    }

    @Override
    public Set<String> getSubProtocols() {
        return Collections.emptySet();
    }

}
