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
import org.xnio.Bits;

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
            if(buffer != null) {
                currentQueueLengthUpdater.decrementAndGet(this);
            }

        } else {
            threadLocalCache.set(local = new ThreadLocalData());
        }
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
                if (local.buffers.size() < threadLocalCacheSize) {
                    local.buffers.add(buffer);
                    return;
                }
            }
        }
        int size;
        do {
            size = currentQueueLength;
            if(size > maximumPoolSize) {
                return;
            }
        } while (!currentQueueLengthUpdater.compareAndSet(this, size, currentQueueLength + 1));
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
        private final LeakDetector leakDetector;
        private ByteBuffer buffer;

        /**
         * Mask that indicates that the primary buffer has been freed. As in most cases buffers are not duplicated
         * we do not introduce another volatile variable, instead we set a flag in the high bits of the reference
         * count to indicate that the the primary buffer has been freed.
         *
         * The protects against double freeing a primary buffer also freeing a duplicated buffer.
         */
        private static final int PRIMARY_BUFFER_CLOSED = 1 << 31;
        private static final int PRIMARY_BUFFER_CLOSED_MASK = ~PRIMARY_BUFFER_CLOSED;
        private volatile int referenceCount = 1;
        private static final AtomicIntegerFieldUpdater<DefaultPooledBuffer> referenceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultPooledBuffer.class, "referenceCount");



        public DefaultPooledBuffer(DefaultByteBufferPool pool, ByteBuffer buffer) {
            this.pool = pool;
            this.buffer = buffer;
            this.leakDetector = null;
        }

        @Override
        public ByteBuffer buffer() {
            if(Bits.anyAreSet(referenceCount, PRIMARY_BUFFER_CLOSED)) {
                throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
            }
            return buffer;
        }

        private PooledBuffer acquire() {
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
            if(Bits.anyAreSet(referenceCount, PRIMARY_BUFFER_CLOSED)) {
                throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
            }
            acquire();
            final ByteBuffer duplicate = buffer.duplicate();
            return new DuplicatedPooledBuffer(duplicate, this);
        }

        @Override
        public void close() {
            if(Bits.anyAreSet(referenceCount, PRIMARY_BUFFER_CLOSED)) {
                return;
            }
            int ref;
            do {
                ref = referenceCount;
                if (Bits.anyAreSet(ref, PRIMARY_BUFFER_CLOSED)) {
                    return;
                }
            } while (!referenceCountUpdater.compareAndSet(this, ref, (ref - 1) | PRIMARY_BUFFER_CLOSED));
            if (ref == 1) {
                if(leakDetector != null) {
                    leakDetector.closed = true;
                }
                pool.freeInternal(buffer);
                this.buffer = null;
            }
        }

        private void closeDuplicate() {
            int ref;
            int newRef;
            boolean close;
            do {
                ref = referenceCount;
                if(Bits.anyAreSet(ref, PRIMARY_BUFFER_CLOSED)) {
                    int count = (ref & PRIMARY_BUFFER_CLOSED_MASK) - 1;
                    close = (count == 0);
                    newRef = count | PRIMARY_BUFFER_CLOSED;
                } else {
                    newRef = ref - 1;
                    close = (newRef == 0);

                }
            } while (!referenceCountUpdater.compareAndSet(this, ref, newRef));
            if (close) {
                if(leakDetector != null) {
                    leakDetector.closed = true;
                }
                pool.freeInternal(buffer);
                this.buffer = null;
            }
        }

        @Override
        public boolean isOpen() {
            return Bits.anyAreClear(referenceCount, PRIMARY_BUFFER_CLOSED);
        }

        private static class DuplicatedPooledBuffer implements PooledBuffer {

            private static final AtomicIntegerFieldUpdater<DuplicatedPooledBuffer> freeUpdater = AtomicIntegerFieldUpdater.newUpdater(DuplicatedPooledBuffer.class, "free");
            private final ByteBuffer duplicate;
            private final DefaultPooledBuffer parent;
            private volatile int free = 0;

            public DuplicatedPooledBuffer(ByteBuffer duplicate, DefaultPooledBuffer parent) {
                this.duplicate = duplicate;
                this.parent = parent;
            }

            @Override
            public ByteBuffer buffer() {
                if(free == 1) {
                    throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
                }
                return duplicate;
            }

            @Override
            public PooledBuffer duplicate() {
                if(free == 1) {
                    throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
                }
                return parent.duplicate();
            }

            @Override
            public void close() {
                if(!freeUpdater.compareAndSet(this, 0, 1)) {
                    return;
                }
                parent.closeDuplicate();
            }

            @Override
            public boolean isOpen() {
                return free == 0;
            }
        }
    }

    private class ThreadLocalData {
        ArrayDeque<ByteBuffer> buffers = new ArrayDeque<>(threadLocalCacheSize);
        int allocationDepth = 0;
    }

    private static class LeakDetector {

        volatile boolean closed = false;
        private final Throwable allocationPoint;

        private LeakDetector() {
            this.allocationPoint = new Throwable("Buffer leak detected");
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            if(!closed) {
                allocationPoint.printStackTrace();
            }
        }
    }

}
