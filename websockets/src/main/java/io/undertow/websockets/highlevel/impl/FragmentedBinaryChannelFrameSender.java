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
import io.undertow.websockets.highlevel.FragmentedBinaryFrameSender;
import io.undertow.websockets.highlevel.SendCallback;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 * Default {@link io.undertow.websockets.highlevel.FragmentedBinaryFrameSender} implementation which use a {@link WebSocketChannel} for the I/O
 * operation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class FragmentedBinaryChannelFrameSender implements FragmentedBinaryFrameSender {
    private final WebSocketChannel channel;
    private final long payloadSize;
    private final WebSocketFrameType type;

    protected StreamSinkFrameChannel sink;

    FragmentedBinaryChannelFrameSender(WebSocketChannel channel, WebSocketFrameType type, long payloadSize) {
        this.channel = channel;
        this.payloadSize = payloadSize;
        this.type = type;
    }

    @Override
    public void sendPartialPayload(final ByteBuffer partialPayload, final boolean last, final SendCallback callback) {
        try {
            if (sink == null) {
                sink = channel.send(type, payloadSize);
            }
            while (partialPayload.hasRemaining()) {
                if (sink.write(partialPayload) == 0) {
                    sink.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(StreamSinkChannel sink) {
                            try {
                                while (partialPayload.hasRemaining()) {
                                    if (sink.write(partialPayload) == 0) {
                                        sink.resumeWrites();
                                        return;
                                    }
                                }
                                if (last) {
                                    StreamSinkUtils.shutdownAndFlush(sink, callback);
                                } else {
                                    callback.onCompletion();
                                }
                            } catch (IOException e) {
                                callback.onError(e);
                            }
                        }
                    });
                    sink.resumeWrites();
                    return;
                }
            }
            if (last) {
                StreamSinkUtils.shutdownAndFlush(sink, callback);
            } else {
                callback.onCompletion();
            }
        } catch (IOException e) {
            callback.onError(e);
        }
    }
}
