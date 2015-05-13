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

package io.undertow.connector;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * A reference counted pooled buffer
 *
 * @author Stuart Douglas
 */
public interface PooledBuffer extends Closeable, AutoCloseable {

    ByteBuffer buffer();

    /**
     * Returns a duplicate of this buffer, that shares the same underling storage, however
     * with a different ByteBuffer instance. This is equivalent to {@link java.nio.ByteBuffer#duplicate()}.
     *
     * The new PooledBuffer must be released using close(), as calling this method increases the reference count.
     *
     * @return a duplicate of this pooled buffer
     */
    PooledBuffer duplicate();

    /**
     * Increases this buffers reference count.
     *
     * @return this buffer
     */
    PooledBuffer reference();

    /**
     * Reduces the buffers reference count by one. If the reference count hits zero then the buffer will be freed.
     */
    void close();

    /**
     *
     * @return true if this buffer has not been freed
     */
    boolean isOpen();
}
