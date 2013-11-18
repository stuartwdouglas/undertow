package io.undertow.server.protocol.ajp;

import io.undertow.server.protocol.framed.AbstractFramedStreamSinkChannel;

/**
 * @author Stuart Douglas
 */
public abstract class AbstractAjpStreamSinkChannel extends AbstractFramedStreamSinkChannel<AjpChannel, AjpStreamSourceChannel, AbstractAjpStreamSinkChannel> {

    protected AbstractAjpStreamSinkChannel(AjpChannel channel) {
        super(channel);
    }

    boolean isActive() {
        return super.isActivated();
    }

}
