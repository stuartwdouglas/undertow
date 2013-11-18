package io.undertow.server.protocol.ajp;

import io.undertow.util.ImmediatePooled;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public class AjpReadBodyChunkStreamSinkChannel extends AbstractAjpStreamSinkChannel {

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

    protected AjpReadBodyChunkStreamSinkChannel(AjpChannel channel) {
        super(channel);
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Pooled<ByteBuffer> createFrameHeader() {
        return new ImmediatePooled<ByteBuffer>(READ_BODY_CHUNK.duplicate());
    }

    @Override
    protected boolean isLastFrame() {
        return true;
    }
}
