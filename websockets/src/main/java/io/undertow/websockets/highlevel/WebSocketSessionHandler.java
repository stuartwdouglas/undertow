package io.undertow.websockets.highlevel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketSessionHandler {

    /**
     * Is called once a new WebSocketSession is established and so the handshake complete
     */
    void onSession(WebSocketSession session);


}
