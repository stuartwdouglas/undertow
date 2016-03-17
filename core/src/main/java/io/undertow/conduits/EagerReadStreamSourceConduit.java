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

package io.undertow.conduits;

import io.undertow.UndertowLogger;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 *
 * A conduit that delays suspend/resume reads as much as possible.
 *
 * @author Stuart Douglas
 */
public class EagerReadStreamSourceConduit implements StreamSourceConduit {

    private final int MAX_QUEUE_SIZE = 5; //todo
    private final ByteBufferPool bufferPool;
    private final StreamSourceConduit next;
    private final ConcurrentLinkedDeque<PooledByteBuffer> queue = new ConcurrentLinkedDeque<>();
    private volatile boolean readResumed;
    private volatile IOException exception;
    private volatile boolean ended;
    private volatile Thread waiter;
    /**
     * Flag that indicates if the last buffer in the queue still has space. If so it may be re-used
     */
    private boolean lastInQueueHasSpace;

    public EagerReadStreamSourceConduit(StreamSourceConduit next, ConduitStreamSourceChannel channel, ByteBufferPool bufferPool) {
        this.next = next;
        this.bufferPool = bufferPool;
        readResumed = next.isReadResumed();
        setReadReadyHandler(new ReadReadyHandler.ChannelListenerHandler<>(channel));
        next.resumeReads();
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if(exception != null) {
            throw new IOException(exception);
        }
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if(exception != null) {
            throw new IOException(exception);
        }
        return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if(exception != null) {
            throw new IOException(exception);
        }
        final PooledByteBuffer pooled = queue.poll();
        if(pooled != null) {
            ByteBuffer buffer = pooled.getBuffer();
            int copied = Buffers.copy(dst, buffer);
            if (buffer.hasRemaining()) {
                queue.addFirst(pooled);
            } else {
                pooled.close();
                if(queue.isEmpty()) {
                    next.resumeReads();
                }
            }
            return copied;
        }
        if(Thread.currentThread() == getReadThread()) {
            int res = next.read(dst);
            if(res == -1) {
                ended = true;
            }
            return res;
        }
        return ended ? -1 : 0;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
        if(exception != null) {
            throw new IOException(exception);
        }
        final PooledByteBuffer pooled = queue.peek();
        if(pooled != null) {
            ByteBuffer buffer = pooled.getBuffer();
            int copied = Buffers.copy(dsts, offs, len, buffer);
            if (!buffer.hasRemaining()) {
                pooled.close();
                queue.poll();
                if(queue.isEmpty()) {
                    next.resumeReads();
                }
            }
            return copied;
        }
        if(Thread.currentThread() == getReadThread()) {
            long res = next.read(dsts, offs, len);
            if(res == -1) {
                ended = true;
            }
            return res;
        }
        return ended ? -1 : 0;
    }

    @Override
    public void terminateReads() throws IOException {
        close();
        next.terminateReads();
    }

    @Override
    public boolean isReadShutdown() {
        return next.isReadShutdown();
    }

    @Override
    public void resumeReads() {
        readResumed = true;
        if(exception != null || queue.size() > 0 || ended) {
            next.wakeupReads();
        } else {
            next.resumeReads();
        }
    }

    @Override
    public void suspendReads() {
        readResumed = false;
    }

    @Override
    public void wakeupReads() {
        readResumed = true;
        next.wakeupReads();
    }

    @Override
    public boolean isReadResumed() {
        return readResumed;
    }

    @Override
    public void awaitReadable() throws IOException {
        if(queue.isEmpty() && !ended && exception == null) {
            if(waiter != null) {
                throw new IllegalStateException(); //should never happen
            }
            waiter = Thread.currentThread();
            try {
                if (queue.isEmpty() && !ended && exception == null) {
                    LockSupport.park();
                }
            } finally {
                waiter = null;
            }
        }
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        if(queue.isEmpty() && !ended && exception == null) {
            waiter = Thread.currentThread();
            try {
                if (queue.isEmpty() && !ended && exception == null) {
                    LockSupport.parkNanos(timeUnit.toNanos(time));
                }
            } finally {
                waiter = null;
            }
        }
    }

    @Override
    public XnioIoThread getReadThread() {
        return next.getReadThread();
    }

    void doBufferedRead() {
        if(ended || exception != null) {
            return;
        }
        PooledByteBuffer pooled;
        if(lastInQueueHasSpace) {
            lastInQueueHasSpace = false;
            pooled = queue.pollLast();
            if(pooled != null) {
                pooled.getBuffer().compact();
            } else {
                pooled = bufferPool.allocate();
            }
        } else {
            pooled = bufferPool.allocate();
        }
        try {
            for (; ; ) {
                ByteBuffer buf = pooled.getBuffer();
                int res = next.read(buf);
                if(res == -1) {
                    buf.flip();
                    if(buf.hasRemaining()) {
                        addBuffer(pooled);
                    } else {
                        pooled.close();
                    }
                    ended = true;
                    if(waiter != null) {
                        LockSupport.unpark(waiter);
                    }
                    return;
                } else if(res == 0) {
                    boolean hasSpace = buf.hasRemaining();
                    buf.flip();
                    if(buf.hasRemaining()) {
                        addBuffer(pooled);
                        if(hasSpace) {
                            lastInQueueHasSpace = true;
                        }
                        if(queue.size() == MAX_QUEUE_SIZE) {
                            next.suspendReads();
                            if(queue.size() == 0) {
                                next.resumeReads();
                            }
                        }
                    } else {
                        pooled.close();
                    }
                    return;
                } else if(!buf.hasRemaining()) {
                    buf.flip();
                    addBuffer(pooled);
                    if(queue.size() == MAX_QUEUE_SIZE) {
                        next.suspendReads();
                        if(queue.size() == 0) {
                            next.resumeReads();
                        }
                    }
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            pooled.close();
            pooled = queue.poll();
            while (pooled != null) {
                pooled.close();
                pooled = queue.poll();
            }
            this.exception = new IOException(e);
            if(waiter != null) {
                LockSupport.unpark(waiter);
            }
            try {
                terminateReads();
            } catch (IOException e1) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e1);
            }
        }
    }

    private void addBuffer(PooledByteBuffer pooled) {
        queue.add(pooled);
        if(waiter != null) {
            LockSupport.unpark(waiter);
        }
    }

    @Override
    public void setReadReadyHandler(final ReadReadyHandler handler) {
        next.setReadReadyHandler(new ReadReadyHandler() {
            @Override
            public void readReady() {
                if(readResumed) {
                    do {
                        handler.readReady();
                    } while (!queue.isEmpty());
                } else {
                    doBufferedRead();
                    if(readResumed) {
                        do {
                            handler.readReady();
                        } while (!queue.isEmpty());
                    }
                }
            }

            @Override
            public void forceTermination() {
                ended = true;
                close();
                handler.readReady();
            }

            @Override
            public void terminated() {
                handler.readReady();
            }
        });
    }

    void close() {
        exception = new ClosedChannelException();
        getReadThread().execute(new Runnable() {
            @Override
            public void run() {
                PooledByteBuffer p = queue.poll();
                while(p != null) {
                    p.close();
                    p = queue.poll();
                }
            }
        });
    }

    @Override
    public XnioWorker getWorker() {
        return next.getWorker();
    }
}
