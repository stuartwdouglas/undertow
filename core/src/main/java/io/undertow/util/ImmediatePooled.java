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

package io.undertow.util;

import io.undertow.buffers.PooledBuffer;

import java.nio.ByteBuffer;

/**
 * Wrapper that allows you to use a non-pooed item as a pooled value
 *
 * @author Stuart Douglas
 */
public class ImmediatePooled implements PooledBuffer {

    private final ByteBuffer value;

    public ImmediatePooled(ByteBuffer value) {
        this.value = value;
    }

    @Override
    public ByteBuffer buffer() throws IllegalStateException {
        return value;
    }

    @Override
    public PooledBuffer duplicate() {
        return new ImmediatePooled(value.duplicate());
    }

    @Override
    public PooledBuffer reference() {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOpen() {
        return true;
    }
}
