package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResetableConduit;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Methods;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * A buffer that is used when processing pipelined requests, that allows the server to
 * buffer multiple responses into a single write() call.
 * <p/>
 * This can improve performance when pipelining requests.
 *
 * @author Stuart Douglas
 */
public class PipelingBufferingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> implements ResetableConduit {

    public static final AttachmentKey<PipelingBufferingStreamSinkConduit> ATTACHMENT_KEY = AttachmentKey.create(PipelingBufferingStreamSinkConduit.class);

    /**
     * If this channel is shutdown
     */
    private static final int SHUTDOWN = 1;
    private static final int DELEGATE_SHUTDOWN = 1 << 1;
    private static final int FLUSHING = 1 << 2;
    private static final int ACTIVE = 1 << 3;
    private static final int NEW_REQUEST = 1 << 4;

    private final HttpServerConnection connection;
    private int state;
    private HttpServerExchange exchange;

    private final Pool<ByteBuffer> pool;
    private Pooled<ByteBuffer> buffer;

    private final ExchangeCompletionListener completionListener = new ExchangeCompletionListener() {
        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
            //if we ever fail to read then we flush the pipeline buffer
            //this relies on us always doing an eager read when starting a request,
            //rather than waiting to be notified of data being available
            final HttpServerConnection connection = exchange.getConnection();
            if (connection.getExtraBytes() == null || exchange.isUpgrade()) {
                try {
                    if (!flushPipelinedData()) {
                        final StreamConnection channel = connection.getChannel();
                        channel.getSinkChannel().setWriteListener(new ChannelListener<Channel>() {
                            @Override
                            public void handleEvent(Channel c) {
                                try {
                                    if (flushPipelinedData()) {
                                        channel.getSinkChannel().setWriteListener(null);
                                        channel.getSinkChannel().suspendWrites();
                                        nextListener.proceed();
                                    }
                                } catch (IOException e) {
                                    UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
                                    IoUtils.safeClose(channel);
                                }
                            }
                        });
                        channel.getSinkChannel().resumeWrites();
                        return;
                    } else {
                        nextListener.proceed();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                nextListener.proceed();
            }
        }
    };

    private boolean enabled() {
        int state = this.state;
        if (anyAreSet(state, ACTIVE)) {
            return true;
        }
        if (anyAreSet(state, NEW_REQUEST)) {
            if (exchange.getRequestMethod().equals(Methods.GET) && connection.getExtraBytes() != null) {
                this.state |= ACTIVE;
                exchange.addExchangeCompleteListener(completionListener);
                return true;
            }
            this.state &= ~NEW_REQUEST;
        }
        return false;
    }

    public PipelingBufferingStreamSinkConduit(StreamSinkConduit next, final Pool<ByteBuffer> pool, final HttpServerConnection connection) {
        super(next);
        this.pool = pool;
        this.connection = connection;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if (!enabled()) {
            return next.transferFrom(src, position, count);
        }
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        if (!enabled()) {
           return next.transferFrom(source, count, throughBuffer);
        }
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (!enabled()) {
            return next.write(srcs, offset, length);
        }
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (anyAreSet(state, FLUSHING)) {
            boolean res = flushBuffer();
            if (!res) {
                return 0;
            }
        }
        Pooled<ByteBuffer> pooled = this.buffer;
        if (pooled == null) {
            this.buffer = pooled = pool.allocate();
        }
        final ByteBuffer buffer = pooled.getResource();

        int total = 0;
        for (int i = offset; i < offset + length; ++i) {
            total += srcs[i].remaining();
        }

        if (buffer.remaining() > total) {
            int put = total;
            Buffers.copy(buffer, srcs, offset, length);
            return put;
        } else {
            int put = buffer.remaining();
            Buffers.copy(put, buffer, srcs, offset, length);
            flushBuffer();
            return put;
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!enabled()) {
            return next.write(src);
        }
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (anyAreSet(state, FLUSHING)) {
            boolean res = flushBuffer();
            if (!res) {
                return 0;
            }
        }
        Pooled<ByteBuffer> pooled = this.buffer;
        if (pooled == null) {
            this.buffer = pooled = pool.allocate();
        }
        final ByteBuffer buffer = pooled.getResource();
        if (buffer.remaining() > src.remaining()) {
            int put = src.remaining();
            buffer.put(src);
            return put;
        } else {
            int put = buffer.remaining();
            int old = src.limit();
            src.limit(src.position() + put);
            buffer.put(src);
            src.limit(old);
            flushBuffer();
            return put;
        }
    }

    /**
     * Flushes the cached data.
     * <p/>
     * This should be called when a read thread fails to read any more request data, to make sure that any
     * buffered data is flushed after the last pipelined request.
     * <p/>
     * If this returns false the read thread should suspend reads and resume writes
     *
     * @return <code>true</code> If the flush suceeded, false otherwise
     * @throws IOException
     */
    public boolean flushPipelinedData() throws IOException {
        if (buffer == null || (buffer.getResource().position() == 0 && allAreClear(state, FLUSHING))) {
            return true;
        }
        return flushBuffer();
    }


    private boolean flushBuffer() throws IOException {
        if (buffer == null) {
            return true;
        }
        final ByteBuffer byteBuffer = buffer.getResource();
        if (!anyAreSet(state, FLUSHING)) {
            state |= FLUSHING;
            byteBuffer.flip();
        }
        int res = 0;
        do {
            res = next.write(byteBuffer);
            if (res == 0) {
                return false;
            }
        } while (byteBuffer.hasRemaining());
        if (!next.flush()) {
            return false;
        }
        buffer.free();
        this.buffer = null;
        state &= ~FLUSHING;
        return true;
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (!enabled()) {
            next.awaitWritable(time, timeUnit);
            return;
        }
        if (buffer != null) {
            if (buffer.getResource().hasRemaining()) {
                return;
            }
        }
        next.awaitWritable(time, timeUnit);
    }

    @Override
    public void awaitWritable() throws IOException {
        if (!enabled()) {
            next.awaitWritable();
            return;
        }
        if (buffer != null) {
            if (buffer.getResource().hasRemaining()) {
                return;
            }
            next.awaitWritable();
        }
    }

    @Override
    public boolean flush() throws IOException {
        if (!enabled()) {
            return next.flush();
        }
        if (anyAreSet(state, SHUTDOWN)) {
            if (!flushBuffer()) {
                return false;
            }
            if (anyAreSet(state, SHUTDOWN) &&
                    anyAreClear(state, DELEGATE_SHUTDOWN)) {
                state |= DELEGATE_SHUTDOWN;
                next.terminateWrites();
            }
            return next.flush();
        }
        return true;
    }

    @Override
    public void terminateWrites() throws IOException {
        if (!enabled()) {
            next.terminateWrites();
            return;
        }
        state |= SHUTDOWN;
        if (buffer == null) {
            state |= DELEGATE_SHUTDOWN;
            next.terminateWrites();
        }
    }

    public void truncateWrites() throws IOException {
        if (!enabled()) {
            next.truncateWrites();
            return;
        }
        try {
            next.truncateWrites();
        } finally {
            if (buffer != null) {
                buffer.free();
            }
        }
    }

    @Override
    public void reset(final HttpServerExchange newExchange) {
        this.exchange = newExchange;
        state |= NEW_REQUEST;
        if(anyAreSet(state, ACTIVE)) {
            exchange.addExchangeCompleteListener(completionListener);
        }
    }
}

