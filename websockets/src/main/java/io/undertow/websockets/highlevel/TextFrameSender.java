package io.undertow.websockets.highlevel;

import java.io.IOException;
import java.io.Writer;

/**
 * @author Stuart Douglas
 */
public interface TextFrameSender {

    /**
     * Send the a text websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload  The payload
     * @param callback The callback that is called when sending is done
     */
    void sendText(CharSequence payload, SendCallback callback);

    /**
     * Send the a text websocket frame and blocks until complete.
     * <p/>
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload The payload
     * @throws java.io.IOException If sending failed
     */
    void sendText(CharSequence payload) throws IOException;

    /**
     * Sends a text message using the resulting writer.
     * <p/>
     * This methods will block until the implementation is ready to actually send the message
     * (i.e. all previous messages in the queue have been sent).
     *
     * @param payloadSize The payload size
     * @return A writer that can be used to send a text message
     */
    Writer sendText(final long payloadSize) throws IOException;
}
