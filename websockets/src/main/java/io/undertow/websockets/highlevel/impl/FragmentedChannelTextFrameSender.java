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
package io.undertow.websockets.highlevel.impl;

import java.nio.ByteBuffer;

import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketUtils;
import io.undertow.websockets.highlevel.FragmentedBinaryTextFrameSender;
import io.undertow.websockets.highlevel.FragmentedTextFrameSender;
import io.undertow.websockets.highlevel.SendCallback;

/**
 *
 * Default {@link io.undertow.websockets.highlevel.FragmentedBinaryFrameSender} implementation which use a {@link WebSocketChannel} for the I/O
 * operation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class FragmentedChannelTextFrameSender extends FragmentedBinaryChannelFrameSender implements FragmentedTextFrameSender {
    public FragmentedChannelTextFrameSender(WebSocketChannel channel, long payloadSize) {
        super(channel, WebSocketFrameType.TEXT, payloadSize);
    }

    @Override
    public void sendPartialPayload(String partialPayload, boolean last, SendCallback callback) {
        sendPartialPayload(ByteBuffer.wrap(partialPayload.getBytes(WebSocketUtils.UTF_8)), last, callback);
    }
}
