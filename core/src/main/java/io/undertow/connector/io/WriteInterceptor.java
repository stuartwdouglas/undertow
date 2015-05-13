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
 * An interceptor that can modify data as it is written.
 *
 * @author Stuart Douglas
 */
public interface WriteInterceptor {

    /**
     * Interceptor that is invoked when data is written to the underlying channel
     *
     * This method can return one of the following values:
     *
     * <code>null</code> - no data is written to the underlying channel
     * <code>PooledBuffer</code> - The contents of this buffer are written to the underlying channel
     * <code>PooledBuffer[]</code> - The contents of the buffer array are written to the underlying channel
     *
     * Note that it is possible to re-use the
     *
     * @param channel The channel
     * @param data The data that is being written
     * @return the data to write
     */
    Object dataWritten(WriteChannel channel, PooledBuffer data);

    /**
     * Interceptor that is invoked when flush is invoked. This callback allows any buffered data
     * in this interceptor to be written out before the flush is processed.
     *
     * This method can return one of the following values:
     *
     * <code>null</code> - no data is written to the underlying channel
     * <code>PooledBuffer</code> - The contents of this buffer are written to the underlying channel
     * <code>PooledBuffer[]</code> - The contents of the buffer array are written to the underlying channel
     *
     * @param channel The channel
     * @return the data to write
     */
    Object flush(WriteChannel channel);

    /**
     * Interceptor that is invoked when the channel is closed. This callback allows any buffered data
     * in this interceptor to be written out before the close is processed.
     *
     * This method can return one of the following values:
     *
     * <code>null</code> - no data is written to the underlying channel
     * <code>PooledBuffer</code> - The contents of this buffer are written to the underlying channel
     * <code>PooledBuffer[]</code> - The contents of the buffer array are written to the underlying channel
     *
     * @param channel The channel
     * @return the data to write
     */
    Object writeComplete(ReadChannel channel);

    /**
     * Invoked when the underlying channel is forcibly closed.
     */
    void closed();
}
