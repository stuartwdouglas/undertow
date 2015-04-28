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

package io.undertow.buffers;

import io.undertow.UndertowMessages;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * A byte buffer pool that supports reference counted pools.
 *
 * @author Stuart Douglas
 */
public class DefaultByteBufferPool implements ByteBufferPool {

    private final ThreadLocal<ThreadLocalData> threadLocalCache = new ThreadLocal<>();
    private final ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();

    private final boolean direct;
    private final int bufferSize;
    private final int maximumPoolSize;
    private final int threadLocalCacheSize;

    private volatile int currentQueueLength = 0;
    private static final AtomicIntegerFieldUpdater<DefaultByteBufferPool> currentQueueLengthUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultByteBufferPool.class, "currentQueueLength");

    private volatile boolean closed;

    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     * @param maximumPoolSize      The maximum pool size, in number of buffers, it does not include buffers in thread local caches
     * @param threadLocalCacheSize The maximum number of buffers that can be stored in a thread local cache
     */
    public DefaultByteBufferPool(boolean direct, int bufferSize, int maximumPoolSize, int threadLocalCacheSize) {
        this.direct = direct;
        this.bufferSize = bufferSize;
        this.maximumPoolSize = maximumPoolSize;
        this.threadLocalCacheSize = threadLocalCacheSize;
    }

    @Override
    public int bufferSize() {
        return bufferSize;
    }

    @Override
    public PooledBuffer allocate() {
        if (closed) {
            throw UndertowMessages.MESSAGES.poolIsClosed();
        }
        ThreadLocalData local = threadLocalCache.get();
        ByteBuffer buffer = null;
        if (local != null) {
            buffer = local.buffers.poll();
        } else {
            threadLocalCache.set(local = new ThreadLocalData());
        }

        local.allocationDepth++;
        if (buffer == null) {
            buffer = queue.poll();
        }
        if (buffer == null) {
            if (direct) {
                buffer = ByteBuffer.allocateDirect(bufferSize);
            } else {
                buffer = ByteBuffer.allocate(bufferSize);
            }
        }

        local.allocationDepth++;
        buffer.clear();
        return new DefaultPooledBuffer(this, buffer);
    }

    private void freeInternal(ByteBuffer buffer) {
        if (closed) {
            return; //GC will take care of it
        }
        ThreadLocalData local = threadLocalCache.get();
        if(local != null) {
            if(local.allocationDepth > 0) {
                local.allocationDepth--;
                local.buffers.add(buffer);
                return;
            }
        }
        int size;
        do {
            size = currentQueueLength;
            if(size > maximumPoolSize) {
                return;
            }
        } while (!currentQueueLengthUpdater.compareAndSet(this, size, currentQueueLength));
        queue.add(buffer);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        queue.clear();
    }

    private static class DefaultPooledBuffer implements PooledBuffer {

        private final DefaultByteBufferPool pool;
        private ByteBuffer buffer;

        private volatile int referenceCount = 1;
        private static final AtomicIntegerFieldUpdater<DefaultPooledBuffer> referenceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultPooledBuffer.class, "referenceCount");



        public DefaultPooledBuffer(DefaultByteBufferPool pool, ByteBuffer buffer) {
            this.pool = pool;
            this.buffer = buffer;
        }

        @Override
        public ByteBuffer buffer() {
            if(referenceCount == 0) {
                throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
            }
            return buffer;
        }

        @Override
        public PooledBuffer aquire() {
            if(referenceCount == 0) {
                throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
            }
            int ref;
            do {
                ref = referenceCount;
                if (ref == 0) {
                    throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
                }
            } while (!referenceCountUpdater.compareAndSet(this, ref, ref + 1));
            return this;
        }

        @Override
        public PooledBuffer duplicate() {
            if(referenceCount == 0) {
                throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
            }
            aquire();
            final ByteBuffer duplicate = buffer.duplicate();
            return new PooledBuffer() {
                @Override
                public ByteBuffer buffer() {
                    return duplicate;
                }

                @Override
                public PooledBuffer aquire() {
                    return DefaultPooledBuffer.this.aquire();
                }

                @Override
                public PooledBuffer duplicate() {
                    return DefaultPooledBuffer.this.duplicate();
                }

                @Override
                public void close() {
                    DefaultPooledBuffer.this.close();
                }

                @Override
                public boolean isOpen() {
                    return DefaultPooledBuffer.this.isOpen();
                }
            };
        }

        @Override
        public void close() {
            int count = referenceCountUpdater.decrementAndGet(this);
            if (count == 0) {
                pool.freeInternal(buffer);
                this.buffer = null;
            }
        }

        @Override
        public boolean isOpen() {
            return referenceCount > 0;
        }
    }

    private class ThreadLocalData {
        ArrayDeque<ByteBuffer> buffers = new ArrayDeque<>(threadLocalCacheSize);
        int allocationDepth = 0;
    }

}
