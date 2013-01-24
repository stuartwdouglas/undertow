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

import io.undertow.websockets.highlevel.SendCallback;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.channels.Channel;

/**
 * Utility class for internal usage.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class StreamSinkUtils {

    /**
     * Shutdown and flush the given {@link StreamSinkChannel} and notify the given {@link SendCallback}.
     */
    public static void shutdownAndFlush(StreamSinkChannel sink, final SendCallback callback) {
        try {
            sink.shutdownWrites();
            if (!sink.flush()) {
                sink.getWriteSetter().set(
                        ChannelListeners.flushingChannelListener(
                                new ChannelListener<StreamSinkChannel>() {
                                    @Override
                                    public void handleEvent(StreamSinkChannel sink) {
                                        try {
                                            sink.close();
                                        } catch (IOException e) {
                                            callback.onError(e);
                                        }
                                    }
                                }, new ChannelExceptionHandler<Channel>() {
                                    @Override
                                    public void handleException(Channel channel, IOException e) {
                                        callback.onError(e);
                                    }
                                }
                        ));
                sink.resumeWrites();
                return;
            }
            sink.close();
            callback.onCompletion();
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    private StreamSinkUtils() {
        // utility
    }
}
