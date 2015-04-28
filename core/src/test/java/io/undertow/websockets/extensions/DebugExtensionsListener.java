/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.websockets.extensions;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.undertow.buffers.PooledBuffer;
import io.undertow.buffers.PooledBuffers;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.WebSockets;

/**
 * A {@link AbstractReceiveListener} implementation used as echo server in Autobahn tests.
 *
 * @author Lucas Ponce
 */
public class DebugExtensionsListener extends AbstractReceiveListener {

    private int binMsgs = 0;
    private int txtMsgs = 0;

    @Override
    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
        txtMsgs++;
        String data = message.getData();
        WebSocketLogger.EXTENSION_LOGGER.info("#" + txtMsgs + " onFullTextMessage() - Received: " + data.getBytes().length + " bytes. ");
        for (WebSocketChannel peerChannel : channel.getPeerConnections()) {
            WebSockets.sendText(data, peerChannel, null);
        }
    }

    @Override
    protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        binMsgs++;
        ByteBuffer[] data = PooledBuffers.toBufferArray(message.getData());
        int total = 0;
        for (int i =0; i < data.length; i++) {
            total += data[i].remaining();
        }
        StringBuilder received = new StringBuilder();
        received.append("# " + binMsgs + " onFullBinaryMessage() - Received: ").append(total).append(" length.").append("\n");

        WebSocketLogger.EXTENSION_LOGGER.info(received.toString());
        for (WebSocketChannel peerChannel : channel.getPeerConnections()) {
            WebSockets.sendBinary(data, peerChannel, null);
        }
    }

    @Override
    protected void onFullPingMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        WebSocketLogger.EXTENSION_LOGGER.info("onFullPingMessage() ");
        PooledBuffer[] data = message.getData();
        for (WebSocketChannel peerChannel : channel.getPeerConnections()) {
            WebSockets.sendPong(PooledBuffers.toBufferArray(data), peerChannel, null);
        }
    }

    @Override
    protected void onFullPongMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        WebSocketLogger.EXTENSION_LOGGER.info("onFullPongMessage() ");
    }

    @Override
    protected void onFullCloseMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        WebSocketLogger.EXTENSION_LOGGER.info("onFullCloseMessage() ");
        PooledBuffer[] pooled = message.getData();
        try {
            ByteBuffer[] data = PooledBuffers.toBufferArray(pooled);
        /*
            Empty messages should be closed as NORMAL_CLOSURE.
         */
            if (data.length == 1 || !data[0].hasRemaining()) {
                for (WebSocketChannel peerChannel : channel.getPeerConnections()) {
                    WebSockets.sendClose(CloseMessage.NORMAL_CLOSURE, "", peerChannel, null);
                }
            } else {
                for (WebSocketChannel peerChannel : channel.getPeerConnections()) {
                    WebSockets.sendClose(data, peerChannel, null);
                }
            }
        } finally {
            PooledBuffers.close(pooled);
        }
    }

    @Override
    protected void onError(WebSocketChannel channel, Throwable error) {
        WebSocketLogger.EXTENSION_LOGGER.info("onError(): " + error.getMessage());
    }
}
