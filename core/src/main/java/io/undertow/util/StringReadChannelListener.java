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

package io.undertow.util;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Simple utility class for reading a string
 * <p/>
 * TODO: support different character encodings
 *
 * @author Stuart Douglas
 */
public abstract class StringReadChannelListener implements ChannelListener<StreamSourceChannel> {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final Pool<ByteBuffer> bufferPool;

    private final String encoding;

    public StringReadChannelListener(final Pool<ByteBuffer> bufferPool) {
        this.bufferPool = bufferPool;
        this.encoding = "UTF-8";
    }
    public StringReadChannelListener(String encoding, final Pool<ByteBuffer> bufferPool) {
        this.bufferPool = bufferPool;
        this.encoding = encoding;
    }

    public void setup(final StreamSourceChannel channel) {
        Pooled<ByteBuffer> resource = bufferPool.allocate();
        ByteBuffer buffer = resource.getResource();
        try {
            int r = 0;
            do {
                r = channel.read(buffer);
                if (r == 0) {
                    channel.getReadSetter().set(this);
                    channel.resumeReads();
                } else if (r == -1) {
                    stringDone(bytes.toString(encoding));
                    IoUtils.safeClose(channel);
                } else {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        bytes.write(buffer.get());
                    }
                }
            } while (r > 0);
        } catch (IOException e) {
            error(e);
        } finally {
            resource.free();
        }
    }

    @Override
    public void handleEvent(final StreamSourceChannel channel) {
        Pooled<ByteBuffer> resource = bufferPool.allocate();
        ByteBuffer buffer = resource.getResource();
        try {
            int r = 0;
            do {
                r = channel.read(buffer);
                if (r == 0) {
                    return;
                } else if (r == -1) {
                    stringDone(bytes.toString(encoding));
                    IoUtils.safeClose(channel);
                } else {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        bytes.write(buffer.get());
                    }
                }
            } while (r > 0);
        } catch (IOException e) {
            error(e);
        } finally {
            resource.free();
        }
    }

    protected abstract void stringDone(String string);

    protected abstract void error(IOException e);
}
