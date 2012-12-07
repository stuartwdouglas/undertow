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
package io.undertow.websockets.wrapper;

import io.undertow.websockets.ChannelFunction;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.List;

/**
 */
public class ChannelFunctionWritableByteChannel extends ChannelWrapper<WritableByteChannel> implements WritableByteChannel {

    private final List<ChannelFunction> functions;

    public ChannelFunctionWritableByteChannel(WritableByteChannel channel, List<ChannelFunction> functions) {
        super(channel);
        this.functions = functions;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        for(ChannelFunction function : functions) {
            function.beforeWrite(src);
        }
        return channel.write(src);
    }
}
