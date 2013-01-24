package io.undertow.websockets.highlevel;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocketFrameHandler implements PartialWebSocketFrameHandler {

    @Override
    public final void onStart(WebSocketSession session, long payloadSize, boolean lastFragment) {
        // init frame here to the right type
    }

    @Override
    public final void onPartialPayload(WebSocketSession session, ByteBuffer data) {
        // add data to the frame
    }

    @Override
    public final void onCompletion(WebSocketSession session) {
        onFrame(session, null); // pass the frame to the onFrame method
    }

    /**
     * Called once a complete WebSocketFrame is received
     */
    public abstract void onFrame(WebSocketSession session, WebSocketFrame frame);
}
