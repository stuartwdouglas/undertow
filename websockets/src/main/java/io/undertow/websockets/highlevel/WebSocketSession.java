package io.undertow.websockets.highlevel;

import java.util.Set;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketSession {
    /**
     * Unique id for the session
     */
    String getId();

    /**
     * Send the {@link WebSocketFrame} and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     */
    void send(WebSocketFrame frame, SendCallback callback);

    /**
     * Return a {@link PartialWebSocketFrameHandler} which can be used to send a binary frame in chunks.
     * If a {@link PartialWebSocketFrameSender} was optained it is not valid to obtain another one till the
     * {@link SendCallback} of the last payload part was called. Trying so will result in an {@link IllegalStateException}
     */
    PartialWebSocketFrameSender sendBinary(long payloadSize);

    /**
     * Return a {@link PartialWebSocketTextFrameSender} which can be used to send a text frame in chunks.
     * If a {@link PartialWebSocketFrameSender} was optained it is not valid to obtain another one till the
     * {@link SendCallback} of the last payload part was called. Trying so will result in an {@link IllegalStateException}
     */
    PartialWebSocketTextFrameSender sendText(long payloadSize);

    /**
     * Close the session with a normal close code and no reason. The {@link SendCallback} will be notified once the close
     * frame was written to the remote peer.
     */
    void close(SendCallback callback);

    /**
     * Close the session with the provided code and reason. The {@link SendCallback} will be notified once the close
     * frame was written to the remote peer.
     */
    void close(CloseCode code, String reason, SendCallback callback);

    /**
     * Set a attribute on the session. When the value is {@code null} it will remove the attribute with the key.
     *
     */
    boolean setAttribute(String key, Object value);

    /**
     * Return the attribute for the key or {@code null} if non is stored for the key
     */
    Object getAttribute(String key);

    /**
     * Return {@code true} if this is a secure websocket connection
     */
    boolean isSecure();

    /**
     * Return the path for which the session was established
     */
    String getPath();

    /**
     * Set the {@link PartialWebSocketFrameHandler} which is used for text frames. If non is set all text frames will
     * just be discarded. Returns the {@link PartialWebSocketFrameHandler} which was set before.
     */
    PartialWebSocketFrameHandler setTextFrameHandler(PartialWebSocketFrameHandler handler);

    /**
     * Get the {@link PartialWebSocketFrameHandler} which is set for text frames.
     */
    PartialWebSocketFrameHandler getTextFrameHandler();

    /**
     * Remove the {@link PartialWebSocketFrameHandler} which is used for text frames.
     */
    PartialWebSocketFrameHandler removeTextFrameHandler();

    /**
     * Set the {@link PartialWebSocketFrameHandler} which is used for binary frames. If non is set all text frames will
     * just be discarded. Returns the {@link PartialWebSocketFrameHandler} which was set before.
     */
    void setBinaryFrameHandler(PartialWebSocketFrameHandler handler);

    /**
     * Get the {@link PartialWebSocketFrameHandler} which is set for binary frames.
     */
    PartialWebSocketFrameHandler getBinaryFrameHandler();

    /**
     * Remove the {@link PartialWebSocketFrameHandler} which is used for binary frames.
     */
    PartialWebSocketFrameHandler removeBinaryFrameHandler();

    /**
     * Return an unmodifiable {@link Set} of sub-protocols for which the {@link WebSocketSession} will be used. May
     * return an empty {@link Set}
     */
    Set<String> getSubProtocols();

}
