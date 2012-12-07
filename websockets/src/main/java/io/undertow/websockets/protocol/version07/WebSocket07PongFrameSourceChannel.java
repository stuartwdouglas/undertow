/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package io.undertow.websockets.protocol.version07;

import io.undertow.websockets.ChannelFunction;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.wrapper.Masker;
import io.undertow.websockets.protocol.WebSocketFixedPayloadFrameSourceChannel;
import org.xnio.channels.StreamSourceChannel;

import java.util.Collections;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07PongFrameSourceChannel extends WebSocketFixedPayloadFrameSourceChannel {
    public WebSocket07PongFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, long payloadSize, int rsv, final boolean masked, final int mask) {
        // can not be fragmented
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.PONG, payloadSize, rsv, true,  masked ? Collections.<ChannelFunction>singletonList(new Masker(mask)) : Collections.<ChannelFunction>emptyList());
    }
}
