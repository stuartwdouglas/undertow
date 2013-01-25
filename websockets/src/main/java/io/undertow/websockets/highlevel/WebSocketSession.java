/*
 * Copyright 2013 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.highlevel;

import java.io.IOException;
import java.util.Set;

/**
 * Session for a WebSocket connection. For each new connection a {@link WebSocketSession} will be created.
 * This {@link WebSocketSession} can then be used to communicate with the remote peer.
 * <p/>
 * Implementations of the interface are expected to be thread-safe, however if multiple threads
 * are sending messages no guarantees are provided about the resulting message order.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface WebSocketSession extends BinaryFrameSender, TextFrameSender {
    /**
     * Unique id for the session
     */
    String getId();

    /**
     * Return a {@link FragmentedBinaryFrameSender} which can be used to send a binary frame in chunks.
     */
    FragmentedBinaryFrameSender sendFragmentedBinary();

    /**
     * Return a {@link FragmentedTextFrameSender} which can be used to send a text frame in chunks.
     */
    FragmentedTextFrameSender sendFragmentedText();


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
     * Close the session with a normal close code and no reason. This method will block until the close has been sent
     * and acknowledged.
     */
    void close() throws IOException;

    /**
     * Close the session with the provided code and reason. This method will block until the close has been sent
     * and acknowledged.
     */
    void close(CloseCode code, String reason) throws IOException;

    /**
     * Set a attribute on the session. When the value is {@code null} it will remove the attribute with the key.
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
     * If this is true then incoming fragments are buffered and automatically
     * assembled into a single message.
     *
     */
    boolean isAutomaticallyAssembleFragments();

    void setAutomaticallyAssembleFragments(boolean value);

    /**
     * Set the {@link FrameHandler} which is used for all frames. If non is set all frames will
     * just be discarded. Returns the {@link FrameHandler} which was set before.
     * <p/>
     * Be aware that if you set a new {@link FrameHandler} it will only be used for the next websocket
     * frame. In progress handling of a frame will continue with the old one.
     */
    FrameHandler setFrameHandler(FrameHandler handler);

    /**
     *
     * @return The current frame handler
     */
    FrameHandler getFrameHandler();

    /**
     * Return an unmodifiable {@link Set} of sub-protocols for which the {@link WebSocketSession} will be used. May
     * return an empty {@link Set}
     */
    Set<String> getSubProtocols();

}
