package io.undertow.server.protocol.ajp;

/**
 * @author Stuart Douglas
 */
public class AjpStreamSinkChannel extends AbstractAjpStreamSinkChannel {
    protected AjpStreamSinkChannel(AjpChannel channel) {
        super(channel);
    }

    @Override
    protected boolean isLastFrame() {
        return false;
    }
}
