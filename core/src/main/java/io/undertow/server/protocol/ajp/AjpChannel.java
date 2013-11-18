package io.undertow.server.protocol.ajp;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.ServerConnection;
import io.undertow.server.protocol.framed.AbstractFramedChannel;
import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class AjpChannel extends AbstractFramedChannel<AjpChannel, AjpStreamSourceChannel, AbstractAjpStreamSinkChannel> implements ServerConnection {

    private final Map<AttachmentKey<?>, Object> attachments = new IdentityHashMap<AttachmentKey<?>, Object>(0);
    private final OptionMap options;
    private final AjpRequestParser parser;
    private final int bufferSize;
    private AjpRequestParseState parseState;
    private HttpServerExchange exchange;
    private AjpStreamSourceChannel existing;
    private AjpStreamSinkChannel sinkChannel;
    private ConduitStreamSinkChannel conduitStreamSinkChannel;
    private ConduitStreamSourceChannel conduitStreamSourceChannel;

    /**
     * Create a new {@link io.undertow.server.protocol.framed.AbstractFramedChannel}
     * 8
     *
     * @param connectedStreamChannel The {@link org.xnio.channels.ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                               Be aware that it already must be "upgraded".
     * @param bufferPool             The {@link org.xnio.Pool} which will be used to acquire {@link java.nio.ByteBuffer}'s from.
     * @param options
     * @param bufferSize
     */
    protected AjpChannel(StreamConnection connectedStreamChannel, Pool<ByteBuffer> bufferPool, String encoding, boolean doDecode, OptionMap options, int bufferSize) {
        super(connectedStreamChannel, bufferPool, AjpFramePriority.INSTANCE);
        this.options = options;
        this.bufferSize = bufferSize;
        parser = new AjpRequestParser(encoding, doDecode);
    }

    @Override
    protected AjpStreamSourceChannel createChannel(FrameHeaderData frameHeaderData, Pooled<ByteBuffer> frameData) {
        AjpStreamSourceChannel channel = new AjpStreamSourceChannel(this, frameData, frameHeaderData.getFrameLength());
        existing = channel;
        return channel;
    }

    @Override
    protected FrameHeaderData parseFrame(ByteBuffer data) throws IOException {
        if(parseState == null) {
            parseState = new AjpRequestParseState();
        }
        if(existing == null) {
            exchange = new HttpServerExchange(null); //todo: connection impl
        }
        parser.parse(data, parseState, exchange);

        if(!parseState.isComplete()) {
            return null;
        } else {
            HttpServerExchange exchange = this.exchange;
            AjpRequestParseState parseState = this.parseState;
            this.parseState = null;
            this.exchange = null;

            AjpStreamSourceChannel existing;
            if(parseState.prefix == AjpRequestParser.) {
                existing = this.existing;
            } else {
                existing = null;
            }
            return new AjpFrameHeaderData(parseState, exchange, existing);
        }
    }

    @Override
    protected boolean isLastFrameReceived() {
        return false;
    }

    @Override
    protected boolean isLastFrameSent() {
        return false;
    }

    @Override
    protected void handleBrokenChannel() {
    }

    @Override
    public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OptionMap getUndertowOptions() {
        return options;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public SSLSessionInfo getSslSessionInfo() {
        return null;
    }

    @Override
    public void setSslSessionInfo(SSLSessionInfo sessionInfo) {
    }

    @Override
    public void addCloseListener(CloseListener listener) {
    }

    @Override
    public StreamConnection upgradeChannel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConduitStreamSinkChannel getSinkChannel() {
        return null;
    }

    @Override
    public ConduitStreamSourceChannel getSourceChannel() {
        return null;
    }

    private static final class AjpFrameHeaderData implements FrameHeaderData {

        private final AjpRequestParseState parseState;
        private final HttpServerExchange exchange;
        private final AjpStreamSourceChannel existing;

        private AjpFrameHeaderData(AjpRequestParseState parseState, HttpServerExchange exchange, AjpStreamSourceChannel existing) {
            this.parseState = parseState;
            this.exchange = exchange;
            this.existing = existing;
        }

        @Override
        public long getFrameLength() {
            return parseState.dataSize;
        }

        @Override
        public AbstractFramedStreamSourceChannel<?, ?, ?> getExistingChannel() {
            return existing;
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T getAttachment(final AttachmentKey<T> key) {
        if (key == null) {
            return null;
        }
        return key.cast(attachments.get(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key) {
        if (key == null) {
            return null;
        }
        List<T> list = key.cast(attachments.get(key));
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T putAttachment(final AttachmentKey<T> key, final T value) {
        if (key == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("key");
        }
        return key.cast(attachments.put(key, key.cast(value)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T removeAttachment(final AttachmentKey<T> key) {
        if (key == null) {
            return null;
        }
        return key.cast(attachments.remove(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void addToAttachmentList(final AttachmentKey<AttachmentList<T>> key, final T value) {
        if (key != null) {
            final Map<AttachmentKey<?>, Object> attachments = this.attachments;
            final AttachmentList<T> list = key.cast(attachments.get(key));
            if (list == null) {
                final AttachmentList<T> newList = new AttachmentList<T>(AttachmentKey.listValueClass(key));
                attachments.put(key, newList);
                newList.add(value);
            } else {
                list.add(value);
            }
        }
    }
}
