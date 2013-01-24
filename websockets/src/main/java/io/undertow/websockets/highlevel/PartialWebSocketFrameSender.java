package io.undertow.websockets.highlevel;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface PartialWebSocketFrameSender {

    /**
     * Send the partial payload and notify the callback once it is done. Once it was the last part of the payload
     * any attempt to call this method again will result in an {@link IllegalStateException}.
     */
    SendCallback sendPartialPayload(ByteBuffer partialPayload, boolean last);
}
