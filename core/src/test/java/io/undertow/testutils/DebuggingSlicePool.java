package io.undertow.testutils;

import io.undertow.buffers.ByteBufferPool;
import io.undertow.buffers.PooledBuffer;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Stuart Douglas
 */
public class DebuggingSlicePool implements ByteBufferPool{

    /**
     * context that can be added to allocations to give more information about buffer leaks, useful when debugging buffer leaks
     */
    private static final ThreadLocal<String> ALLOCATION_CONTEXT = new ThreadLocal<>();

    static final Set<DebuggingBuffer> BUFFERS = Collections.newSetFromMap(new ConcurrentHashMap<DebuggingBuffer, Boolean>());
    static volatile String currentLabel;

    private final ByteBufferPool delegate;

    public DebuggingSlicePool(ByteBufferPool delegate) {
        this.delegate = delegate;
    }

    public static void addContext(String context) {
        ALLOCATION_CONTEXT.set(context);
    }

    @Override
    public int bufferSize() {
        return delegate.bufferSize();
    }

    @Override
    public PooledBuffer allocate() {
        final PooledBuffer delegate = this.delegate.allocate();
        return new DebuggingBuffer(delegate, currentLabel);
    }

    @Override
    public void close() {
        delegate.close();
    }

    static class DebuggingBuffer implements PooledBuffer {

        private static final AtomicInteger allocationCount = new AtomicInteger();
        private final RuntimeException allocationPoint;
        private final PooledBuffer delegate;
        private final String label;
        private final int no;
        private volatile boolean free = false;
        private RuntimeException freePoint;
        private final AtomicInteger referenceCount = new AtomicInteger(1);

        public DebuggingBuffer(PooledBuffer delegate, String label) {
            this.delegate = delegate;
            this.label = label;
            this.no = allocationCount.getAndIncrement();
            String ctx = ALLOCATION_CONTEXT.get();
            ALLOCATION_CONTEXT.remove();
            allocationPoint = new RuntimeException(delegate.buffer()  + " NO: " + no + " " + (ctx == null ? "[NO_CONTEXT]" : ctx));
            BUFFERS.add(this);
        }

        @Override
        public void close() {
            int ref;
            do {
                ref = referenceCount.get();
                if(ref == 0) {
                    return;
                }
            } while (!referenceCount.compareAndSet(ref, ref - 1));
            if(ref == 1) {
                freePoint = new RuntimeException("FREE POINT");
                free = true;
                BUFFERS.remove(this);
                delegate.close();
            }
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public ByteBuffer buffer() throws IllegalStateException {
            if(free) {
                throw new IllegalStateException("Buffer already freed, free point: ", freePoint);
            }
            return delegate.buffer();
        }

        @Override
        public PooledBuffer aquire() {
            referenceCount.incrementAndGet();
            delegate.aquire();
            return this;
        }

        @Override
        public PooledBuffer duplicate() {
            referenceCount.incrementAndGet();
            final PooledBuffer duplicate = delegate.duplicate();
            return new PooledBuffer() {
                @Override
                public ByteBuffer buffer() {
                    return duplicate.buffer();
                }

                @Override
                public PooledBuffer aquire() {
                    return duplicate.aquire();
                }

                @Override
                public PooledBuffer duplicate() {
                    return DebuggingBuffer.this.duplicate();
                }

                @Override
                public void close() {
                    delegate.close();
                }

                @Override
                public boolean isOpen() {
                    return DebuggingBuffer.this.isOpen();
                }
            };
        }

        RuntimeException getAllocationPoint() {
            return allocationPoint;
        }

        String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return "[debug]" + delegate.toString();
        }
    }
}
