package io.undertow.websockets.highlevel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketSessionPathMapper {

    /**
     * Register the {@link WebSocketSessionHandler} for the path. Once a request on the path is made the handshake
     * will be issued and once complete the {@link WebSocketSessionHandler#onSession(WebSocketSession)} method will be
     * called.
     */
    void register(String path, WebSocketSessionHandler handler);

    /**
     * Remove the {@link WebSocketSessionHandler} for the given path and return it.
     */
    WebSocketSessionHandler remove(String path);
}
