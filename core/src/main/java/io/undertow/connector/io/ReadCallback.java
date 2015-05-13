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

package io.undertow.connector.io;

import io.undertow.connector.PooledBuffer;

import java.io.IOException;

/**
 * Callback that is invoked when a channel is readable
 *
 * @author Stuart Douglas
 */
public interface ReadCallback<C, D> {

    /**
     *
     * Read
     *
     * @param data The read data
     * @param channel The underlying channel
     * @param context Context data that was passed into the callback
     */
    void dataReady(PooledBuffer data, IOInterceptorContext<ReadChannel> channel, D context);

    void complete(IOInterceptorContext<ReadChannel> channel, D context);

    void error(IOException exception, C channel, D context);
}
