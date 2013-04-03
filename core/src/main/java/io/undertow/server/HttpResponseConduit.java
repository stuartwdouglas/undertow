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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.List;

import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpResponseConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final Pool<ByteBuffer> pool;

    private int state = STATE_START;

    private Pooled<ByteBuffer> pooledBuffer;
    private final HttpServerExchange exchange;

    private static final int STATE_BODY = 0; // Message body, normal pass-through operation
    private static final int STATE_START = 1; // No headers written yet
    private static final int STATE_BUF_FLUSH = 10; // flush the buffer and go to writing body

    private static final int MASK_STATE = 0x0000000F;
    private static final int FLAG_SHUTDOWN = 0x00000010;

    public static final ConduitWrapper<StreamSinkConduit> WRAPPER = new ConduitWrapper<StreamSinkConduit>() {
        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
            final StreamSinkConduit channel = factory.create();
            return new HttpResponseConduit(channel, exchange.getConnection().getBufferPool(), exchange);
        }
    };

    HttpResponseConduit(final StreamSinkConduit next, final Pool<ByteBuffer> pool, final HttpServerExchange exchange) {
        super(next);
        this.pool = pool;
        this.exchange = exchange;
    }

    /**
     * Handles writing out the header data. It can also take a byte buffer of user
     * data, to enable both user data and headers to be written out in a single operation,
     * which has a noticable performance impact.
     * <p/>
     * It is up to the caller to note the current position of this buffer before and after they
     * call this method, and use this to figure out how many bytes (if any) have been written.
     *
     * @param state
     * @param userData
     * @return
     * @throws IOException
     */
    private int processWrite(int state, final ByteBuffer userData) throws IOException {
        if (state == STATE_BUF_FLUSH) {
            final ByteBuffer byteBuffer = pooledBuffer.getResource();
            do {
                long res = 0;
                ByteBuffer[] data;
                if (userData == null) {
                    data = new ByteBuffer[]{byteBuffer};
                } else {
                    data = new ByteBuffer[]{byteBuffer, userData};
                }
                res = next.write(data, 0, data.length);
                if (res == 0) {
                    return STATE_BUF_FLUSH;
                }
            } while (byteBuffer.hasRemaining());
            pooledBuffer.free();
            return STATE_BODY;
        }
        pooledBuffer = pool.allocate();
        ByteBuffer buffer = pooledBuffer.getResource();
        Iterator<HttpString> nameIterator;
        Iterator<String> valueIterator;

        assert buffer.remaining() >= 0x100;
        exchange.getProtocol().appendTo(buffer);
        buffer.put((byte) ' ');
        int code = exchange.getResponseCode();
        assert 999 >= code && code >= 100;
        buffer.put((byte) (code / 100 + '0'));
        buffer.put((byte) (code / 10 % 10 + '0'));
        buffer.put((byte) (code % 10 + '0'));
        buffer.put((byte) ' ');
        String string = StatusCodes.getReason(code);
        writeString(buffer, string);
        buffer.put((byte) '\r').put((byte) '\n');
        HeaderMap headers = exchange.getResponseHeaders();
        nameIterator = headers.iterator();
        while (nameIterator.hasNext()) {
            HttpString header = nameIterator.next();
            List<String> values = headers.get(header);
            for (final String value : values) {
                header.appendTo(buffer);
                buffer.put((byte) ':').put((byte) ' ');
                writeString(buffer, value);
                buffer.put((byte) '\r').put((byte) '\n');
            }
        }
        buffer.put((byte) '\r').put((byte) '\n');
        buffer.flip();
        do {
            long res = 0;
            ByteBuffer[] data;
            if (userData == null) {
                data = new ByteBuffer[]{buffer};
            } else {
                data = new ByteBuffer[]{buffer, userData};
            }
            res = next.write(data, 0, data.length);
            if (res == 0) {
                return STATE_BUF_FLUSH;
            }
        } while (buffer.hasRemaining());
        pooledBuffer.free();
        return STATE_BODY;
    }

    private static void writeString(ByteBuffer buffer, String string) {
        int length = string.length();
        for (int charIndex = 0; charIndex < length; charIndex++) {
            buffer.put((byte) string.charAt(charIndex));
        }
    }

    private boolean flushHeaderBuffer(ByteBuffer buffer) throws IOException {
        int res;
        buffer.flip();
        do {
            res = next.write(buffer);
            if (res == 0) {
                return true;
            }
        } while (buffer.hasRemaining());
        buffer.clear();
        return false;
    }

    public int write(final ByteBuffer src) throws IOException {
        int oldState = this.state;
        int state = oldState & MASK_STATE;
        int alreadyWritten = 0;
        int originalRemaining = -1;
        try {
            if (state != 0) {
                originalRemaining = src.remaining();
                state = processWrite(state, src);
                if (state != 0) {
                    return 0;
                }
                alreadyWritten = originalRemaining - src.remaining();
                if (allAreSet(oldState, FLAG_SHUTDOWN)) {
                    next.terminateWrites();
                    throw new ClosedChannelException();
                }
            }
            if (alreadyWritten != originalRemaining) {
                return next.write(src) + alreadyWritten;
            }
            return alreadyWritten;
        } finally {
            this.state = oldState & ~MASK_STATE | state;
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0L;
        }
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                //todo: use gathering write here
                state = processWrite(state, null);
                if (state != 0) {
                    return 0;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    next.terminateWrites();
                    throw new ClosedChannelException();
                }
            }
            return length == 1 ? next.write(srcs[offset]) : next.write(srcs, offset, length);
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (count == 0L) {
            return 0L;
        }
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state, null);
                if (state != 0) {
                    return 0;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    next.terminateWrites();
                    throw new ClosedChannelException();
                }
            }
            return next.transferFrom(src, position, count);
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (count == 0) {
            throughBuffer.clear().limit(0);
            return 0L;
        }
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state, null);
                if (state != 0) {
                    return 0;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    next.terminateWrites();
                    throw new ClosedChannelException();
                }
            }
            return next.transferFrom(source, count, throughBuffer);
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }

    public boolean flush() throws IOException {
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state, null);
                if (state != 0) {
                    return false;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    next.terminateWrites();
                    // fall out to the flush
                }
            }
            return next.flush();
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }


    public void terminateWrites() throws IOException {
        int oldVal = this.state;
        if (allAreClear(oldVal, MASK_STATE)) {
            next.terminateWrites();
            return;
        }
        this.state = oldVal | FLAG_SHUTDOWN;
    }

    public void truncateWrites() throws IOException {
        int oldVal = this.state;
        if (allAreClear(oldVal, MASK_STATE)) {
            try {
                next.truncateWrites();
            } finally {
                if (pooledBuffer != null) {
                    pooledBuffer.free();
                    pooledBuffer = null;
                }
            }
            return;
        }
        this.state = oldVal & ~MASK_STATE | FLAG_SHUTDOWN | STATE_BODY;
        throw new TruncatedResponseException();
    }

    public XnioWorker getWorker() {
        return next.getWorker();
    }
}
