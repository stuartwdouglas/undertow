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

import io.undertow.buffers.PooledBuffer;
import io.undertow.connector.io.IOChannel;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public interface ReadChannel extends IOChannel<ReadChannel> {

    <D> void read(ReadCallback<ReadChannel, D> callback);

    PooledBuffer readBlocking() throws IOException;

    <D> void transferTo(long position, long count, FileChannel target, ReadCallback<ReadChannel, D> callback) throws IOException;

    @Override
    void close() throws IOException;
}
