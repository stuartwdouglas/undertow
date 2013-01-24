package io.undertow.websockets.highlevel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketSessionHandler {

    /**
     * Is called once a new WebSocketSession is established and so the handshake complete
     */
    void onSession(WebSocketSession session);

    /**
     * Is called once a Ping was received from the remote peer. The implementation will automatically send pong
     * back to the other peer.
     */
    void onPing(WebSocketSession session);

    /**
     * Is called once a Close frame was received from the remote peer. The implementation will automatically send the
     * close frame back and close the connection once done.
     */
    void onClose(WebSocketSession session);

    /**
     * Is called if an error accours while handling websocket frames. Once this message was called the implementation
     * will automatically drop the connection as there is no way to recover.
     */
    void onError(WebSocketSession session, Throwable cause);
}
