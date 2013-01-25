package io.undertow.websockets.highlevel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public interface BinaryFrameSender {


    /**
     * Send the a binary websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload  The payload
     * @param callback The callback that is called when sending is done
     */
    void sendBinary(ByteBuffer payload, SendCallback callback);

    /**
     * Send the a binary websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload  The payload
     * @param callback The callback that is called when sending is done
     */
    void sendBinary(ByteBuffer[] payload, SendCallback callback);

    /**
     * Send the a binary websocket frame and blocks until complete.
     * <p/>
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload The payload
     * @throws java.io.IOException If sending failed
     */
    void sendBinary(ByteBuffer payload) throws IOException;


    /**
     * Send the a binary websocket frame and blocks until complete.
     * <p/>
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload The payload
     * @throws IOException If sending failed
     */
    void sendBinary(ByteBuffer[] payload) throws IOException;

    /**
     * Sends a binary message using the resulting output stream.
     * <p/>
     * This methods will block until the implementation is ready to actually send the message
     * (i.e. all previous messages in the queue have been sent).
     *
     * @param payloadSize The payload size
     * @return A stream that can be used to send a binary message
     */
    OutputStream sendBinary(final long payloadSize) throws IOException;
}
