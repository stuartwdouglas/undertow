package io.undertow.websockets.highlevel;


import java.nio.ByteBuffer;

public interface FrameHandler {

    /**
     *
     * @param session
     * @param frame
     * @param payload
     */
    void onTextFrame(final WebSocketSession session, WebSocketFrame frame, String payload);

    /**
     *
     * @param session
     * @param frame
     * @param payload
     */
    void onBinaryFrame(final WebSocketSession session, WebSocketFrame frame, ByteBuffer payload);


    /**
     *
     * @param session
     * @param frame
     * @param payload
     */
    void onCloseFrame(final WebSocketSession session, WebSocketFrame frame, ByteBuffer payload);
    /**
     *
     * @param session
     * @param frame
     * @param payload
     */
    void onPingFrame(final WebSocketSession session, WebSocketFrame frame, ByteBuffer payload);

    /**
     *
     * @param session
     * @param frame
     * @param payload
     */
    void onPongFrame(final WebSocketSession session, WebSocketFrame frame, ByteBuffer payload);

    /**
     * Is called if an error occurs while handling websocket frames. Once this message was called the implementation
     * will automatically drop the connection as there is no way to recover.
     */
    void onError(WebSocketSession session, Throwable cause);
}
