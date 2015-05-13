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
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public interface WriteChannel extends IOChannel<WriteChannel> {

    <D> void write(WriteCallback<D> callback, D context);

    <D> void write(PooledBuffer buffer, WriteCallback<D> callback, D context);

    <D> void write(PooledBuffer[] buffers, WriteCallback<D> callback, D context);

    <D> void writeFinal(PooledBuffer buffer, WriteCallback<D> callback, D context);

    <D> void writeFinal(PooledBuffer[] buffers, WriteCallback<D> callback, D context);

    void writeBlocking(PooledBuffer buffer) throws IOException;

    void writeBlocking(PooledBuffer[] buffers) throws IOException;

    void writeFinalBlocking(PooledBuffer buffer) throws IOException;

    void writeFinalBlocking(PooledBuffer[] buffers) throws IOException;

    void transferFrom(FileChannel channel, long position, long count, WriteCallback callback);

    <D> void flush(WriteCallback<D> callback, D context);

    void shutdownWrites();

    /**
     * Forcibly close the channel, breaking the underlying connection.
     *
     * A normal close should consist of a call to {@link #shutdownWrites()} followed
     * by a call to one of the <code>flush()</code> calls.
     *
     */
    @Override
    void close();
}
