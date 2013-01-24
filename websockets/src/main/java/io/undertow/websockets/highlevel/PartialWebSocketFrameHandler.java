package io.undertow.websockets.highlevel;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface PartialWebSocketFrameHandler {

    /**
     * Called once we start to process a new WebSocketFrame
     */
    void onStart(WebSocketSession session, long payloadSize, boolean lastFragment);

    /**
     * Called once some data is received that belongs to the payload for the WebSocketFrame
     */
    void onPartialPayload(WebSocketSession session, ByteBuffer data);

    /**
     * WebSocketFrame complete
     */
    void onCompletion(WebSocketSession session);
}
