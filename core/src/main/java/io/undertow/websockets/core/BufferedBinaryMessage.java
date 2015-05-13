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

import org.xnio.ChannelListener;
import io.undertow.connector.PooledBuffer;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A buffered binary message.
 *
 * @author Stuart Douglas
 */
public class BufferedBinaryMessage implements Closeable {

    private final boolean bufferFullMessage;
    private List<PooledBuffer> data = new ArrayList<>(1);
    private PooledBuffer current;
    private final long maxMessageSize;
    private long currentSize;
    private boolean complete;
    private int frameCount;


    public BufferedBinaryMessage(long maxMessageSize, boolean bufferFullMessage) {
        this.bufferFullMessage = bufferFullMessage;
        this.maxMessageSize = maxMessageSize;
    }

    public BufferedBinaryMessage(boolean bufferFullMessage) {
        this(-1, bufferFullMessage);
    }

    public void readBlocking(StreamSourceFrameChannel channel) throws IOException {
        if (current == null) {
            current = channel.getWebSocketChannel().getBufferPool().allocate();
        }
        for (; ; ) {
            int res = channel.read(current.buffer());
            if (res == -1) {
                complete = true;
                return;
            } else if (res == 0) {
                channel.awaitReadable();
            }
            checkMaxSize(channel, res);
            if (bufferFullMessage) {
                dealWithFullBuffer(channel);
            } else if (!current.buffer().hasRemaining()) {
                return;
            }
        }
    }

    private void dealWithFullBuffer(StreamSourceFrameChannel channel) {
        if (!current.buffer().hasRemaining()) {
            current.buffer().flip();
            data.add(current);
            current = channel.getWebSocketChannel().getBufferPool().allocate();
        }
    }

    public void read(final StreamSourceFrameChannel channel, final WebSocketCallback<BufferedBinaryMessage> callback) {
        try {
            for (; ; ) {
                if (current == null) {
                    current = channel.getWebSocketChannel().getBufferPool().allocate();
                }
                int res = channel.read(current.buffer());
                if (res == -1) {
                    this.complete = true;
                    callback.complete(channel.getWebSocketChannel(), this);
                    return;
                } else if (res == 0) {
                    channel.getReadSetter().set(new ChannelListener<StreamSourceFrameChannel>() {
                        @Override
                        public void handleEvent(StreamSourceFrameChannel channel) {
                            if(complete ) {
                                return;
                            }
                            try {
                                for (; ; ) {
                                    if (current == null) {
                                        current = channel.getWebSocketChannel().getBufferPool().allocate();
                                    }
                                    int res = channel.read(current.buffer());
                                    if (res == -1) {
                                        complete = true;
                                        channel.suspendReads();
                                        callback.complete(channel.getWebSocketChannel(), BufferedBinaryMessage.this);
                                        return;
                                    } else if (res == 0) {
                                        return;
                                    }

                                    checkMaxSize(channel, res);
                                    if (bufferFullMessage) {
                                        dealWithFullBuffer(channel);
                                    } else if (!current.buffer().hasRemaining()) {
                                        callback.complete(channel.getWebSocketChannel(), BufferedBinaryMessage.this);
                                    } else {
                                        handleNewFrame(channel, callback);
                                    }
                                }
                            } catch (IOException e) {
                                channel.suspendReads();
                                callback.onError(channel.getWebSocketChannel(), BufferedBinaryMessage.this, e);
                            }
                        }
                    });
                    channel.resumeReads();
                    return;
                }

                checkMaxSize(channel, res);
                if (bufferFullMessage) {
                    dealWithFullBuffer(channel);
                } else if (!current.buffer().hasRemaining()) {
                    callback.complete(channel.getWebSocketChannel(), BufferedBinaryMessage.this);
                } else {
                    handleNewFrame(channel, callback);
                }
            }
        } catch (IOException e) {
            callback.onError(channel.getWebSocketChannel(), this, e);
        }
    }

    private void handleNewFrame(StreamSourceFrameChannel channel, final WebSocketCallback<BufferedBinaryMessage> callback) {
        //TODO: remove this crap
        //basically some bogus web sockets TCK tests assume that messages will be broken up into frames
        //even if we have the full message available.
        if(!bufferFullMessage) {
            if(channel.getWebSocketFrameCount() != frameCount && current != null && !channel.isFinalFragment()) {
                frameCount = channel.getWebSocketFrameCount();
                callback.complete(channel.getWebSocketChannel(), this);
            }
        }
    }

    private void checkMaxSize(StreamSourceFrameChannel channel, int res) throws IOException {
        currentSize += res;
        if (maxMessageSize > 0 && currentSize > maxMessageSize) {
            WebSockets.sendClose(new CloseMessage(CloseMessage.MSG_TOO_BIG, WebSocketMessages.MESSAGES.messageToBig(maxMessageSize)), channel.getWebSocketChannel(), null);
            throw new IOException(WebSocketMessages.MESSAGES.messageToBig(maxMessageSize));
        }
    }

    public PooledBuffer[] getData() {
        if (current == null) {
            return new PooledBuffer[]{};
        }
        if (data.isEmpty()) {
            final PooledBuffer current = this.current;
            current.buffer().flip();
            this.current = null;
            return new PooledBuffer[]{current};
        }
        current.buffer().flip();
        data.add(current);
        current = null;
        ByteBuffer[] ret = new ByteBuffer[data.size()];
        for (int i = 0; i < data.size(); ++i) {
            ret[i] = data.get(i).buffer();
        }
        List<PooledBuffer> data = this.data;
        this.data = new ArrayList<>();

        return data.toArray(new PooledBuffer[data.size()]);
    }

    public boolean isComplete() {
        return complete;
    }

    @Override
    public void close() {
        if(current != null) {
            current.close();
        }
        if(data != null) {
            for(PooledBuffer i : data) {
                i.close();
            }
        }
    }
}
