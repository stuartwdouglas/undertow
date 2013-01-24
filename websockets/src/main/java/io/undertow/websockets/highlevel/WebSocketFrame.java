package io.undertow.websockets.highlevel;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketFrame {
    boolean isBinary();
    ByteBuffer getPayload();
    int getRsv();
    boolean isLastFragement();
}
