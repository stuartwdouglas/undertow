package io.undertow.server.protocol.ajp;

import io.undertow.server.protocol.framed.AbstractFramedChannel;
import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import org.xnio.Pooled;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class AjpStreamSourceChannel extends AbstractFramedStreamSourceChannel<AjpChannel, AjpStreamSourceChannel, AbstractAjpStreamSinkChannel> {

    public AjpStreamSourceChannel(AbstractFramedChannel<AjpChannel, AjpStreamSourceChannel, AbstractAjpStreamSinkChannel> framedChannel) {
        super(framedChannel);
    }

    public AjpStreamSourceChannel(AbstractFramedChannel<AjpChannel, AjpStreamSourceChannel, AbstractAjpStreamSinkChannel> framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining) {
        super(framedChannel, data, frameDataRemaining);
    }
}
