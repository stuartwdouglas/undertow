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

/**
 * Interceptor that can be installed to modify read data.
 *
 *
 * @author Stuart Douglas
 */
public interface ReadInterceptor {

    /**
     * Interceptor that is invoked when data is read from the underlying channel.
     *
     * This method can return one of the following values:
     *
     * <code>null</code> - no data is returned to the user, and invocation of the read listener will be suppressed
     * <code>PooledBuffer</code> - The buffer to be returned to the user
     * <code>PooledBuffer[]</code> - The contents of the buffer array are returned to the user
     *
     * @param channel The channel
     * @param data The data that was read from the channel
     * @return The data to return to the user
     */
    Object dataRead(ReadChannel channel, PooledBuffer data);

    /**
     * Interceptor that is invoked when there is no more data to return from the underlying channel
     *
     * This method can return one of the following values:
     *
     * <code>null</code> - no data is returned to the user, and invocation of the read listener will be suppressed
     * <code>PooledBuffer</code> - The buffer to be returned to the user
     * <code>PooledBuffer[]</code> - The contents of the buffer array are returned to the user
     *
     * @param channel The channel
     * @return The data to return to the user
     */
    Object readComplete(ReadChannel channel);

    /**
     * Invoked when the underlying channel is forcibly closed.
     */
    void closed();

}
