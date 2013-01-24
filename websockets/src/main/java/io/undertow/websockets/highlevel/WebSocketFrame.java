package io.undertow.websockets.highlevel;

import io.undertow.websockets.WebSocketFrameType;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketFrame {
    /**
     * TODO: Maybe just use isBinary() here and return true for binary and false for text frames
     */
    WebSocketFrameType getType();
    ByteBuffer getPayload();
    int getRsv();
    boolean isLastFragement();
}
