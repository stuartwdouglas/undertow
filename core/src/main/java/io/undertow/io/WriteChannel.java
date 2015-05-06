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

package io.undertow.io;

import io.undertow.buffers.PooledBuffer;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public interface WriteChannel extends Closeable {

    boolean write(PooledBuffer buffer);

    boolean write(PooledBuffer[] buffers);

    void writeBlocking(PooledBuffer buffer) throws IOException;

    void writeBlocking(PooledBuffer[] buffers) throws IOException;

    void transferFrom(FileChannel channel, long position, long count);

    boolean isReady();

    void resumeWrites();


}
