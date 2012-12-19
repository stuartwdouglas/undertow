package io.undertow.ajp;

import io.undertow.channels.DelegatingStreamSourceChannel;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * Underlying AJP request channel.
 *
 * @author Stuart Douglas
 */
public class AjpRequestChannel extends DelegatingStreamSourceChannel<AjpRequestChannel> {

    private static final ByteBuffer READ_BODY_CHUNK;

    static {
        ByteBuffer readBody = ByteBuffer.allocateDirect(7);
        readBody.put((byte) 'A');
        readBody.put((byte) 'B');
        readBody.put((byte) 0);
        readBody.put((byte) 3);
        readBody.put((byte) 6);
        readBody.put((byte) 0x1F);
        readBody.put((byte) 0xFA);
        readBody.flip();
        READ_BODY_CHUNK = readBody;

    }

    private final AjpResponseChannel ajpResponseChannel;

    /**
     * The size of the incoming request. A size of 0 indicates that the request is using chunked encoding
     */
    private final Integer size;

    /**
     * byte buffer that is used to hold header data
     */
    private final ByteBuffer headerBuffer = ByteBuffer.allocateDirect(4);

    private long state;

    /**
     * There is a packet coming from apache.
     */
    private static final long STATE_READING = 1L << 63L;
    /**
     * There is no packet coming, as we need to set a GET_BODY_CHUNK message
     */
    private static final long STATE_SEND_REQUIRED = 1L << 62L;
    /**
     * We are in the process of sending a GET_BODY_CHUNK message
     */
    private static final long STATE_SENDING = 1L << 61L;

    /**
     * read is done
     */
    private static final long STATE_FINISHED = 1L << 60L;

    /**
     * The remaining bits are used to store the remaining chunk size.
     */
    private static final long STATE_MASK = longBitMask(0, 55);

    private volatile ByteBuffer readBody;

    public AjpRequestChannel(final StreamSourceChannel delegate, AjpResponseChannel ajpResponseChannel, Integer size) {
        super(delegate);
        this.ajpResponseChannel = ajpResponseChannel;
        this.size = size;
        if (size == null) {
            state = STATE_SEND_REQUIRED;
        } else if (size == 0) {
            state = STATE_FINISHED;
        } else {
            state = STATE_READING;
        }
    }

    boolean writeRequestBodyChunkMessage() throws IOException {
        ByteBuffer readBody = this.readBody;
        if (readBody == null) {
            return true;
        }
        ajpResponseChannel.directWrite(readBody);

        if (!readBody.hasRemaining()) {
            this.readBody = null;
            state = STATE_READING;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        return target.transferFrom(this, position, count);
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(this, count, throughBuffer, target);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < length; ++i) {
            while (dsts[i].hasRemaining()) {
                int r = read(dsts[i]);
                if (r <= 0 && total > 0) {
                    return total;
                } else if (r <= 0) {
                    return r;
                } else {
                    total += r;
                }
            }
        }
        return total;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        do {
            long state = getState();
            if (anyAreSet(state, STATE_SEND_REQUIRED)) {
                readBody = READ_BODY_CHUNK.duplicate();
                if (!ajpResponseChannel.beginGetRequestBodyChunk(this)) {
                    setState(STATE_SENDING);
                    return 0;
                }
            } else if (anyAreSet(state, STATE_SENDING)) {
                if (!ajpResponseChannel.continueSendRequestBodyChunk()) {
                    return 0;
                }
                setState(STATE_READING);
            } else if (anyAreSet(state, STATE_READING)) {
                return doRead(dst);
            }
        } while (state != STATE_FINISHED);
        return -1;
    }

    private int doRead(final ByteBuffer dst) throws IOException {
        ByteBuffer headerBuffer = this.headerBuffer;
        long headerRead = headerBuffer.remaining();
        long remaining;
        if (headerRead < 4) {
            int read = delegate.read(headerBuffer);
            if (read == -1) {
                return read;
            } else if (headerBuffer.hasRemaining()) {
                return 0;
            } else {
                byte b1 = headerBuffer.get(); //0x12
                byte b2 = headerBuffer.get(); //0x34
                assert b1 == 0x12;
                assert b2 == 0x34;
                b1 = headerBuffer.get();
                b2 = headerBuffer.get();
                remaining = ((b1 & 0xFF) << 8) & (b2 & 0xFF);
            }
        } else {
            remaining = this.state & ~STATE_MASK;
        }
        int limit = dst.limit();
        try {
            if(limit > remaining) {
                dst.limit((int) (dst.position() + remaining));
            }
            return delegate.read(dst);
        } finally {
            dst.limit(limit);
            state = (state & STATE_MASK) | remaining;
        }
    }

    @Override
    public void awaitReadable() throws IOException {
        if (state == STATE_FINISHED ||
                state == STATE_READING) {
            super.awaitReadable();
        } else {

        }
    }

    private long getState() {
        return state & STATE_MASK;
    }

    private void setState(long newState) {
        this.state = (this.state & ~STATE_MASK) | newState;
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {

    }


    public void doWriteFromListener() {

    }
}
