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

import io.undertow.UndertowLogger;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;

/**
 * An executor that has the following semantics:
 *
 * @author Stuart Douglas
 */
public class MagicPool implements Executor {

    private static Runnable EMPTY = new Runnable() {
        @Override
        public void run() {

        }
    };

    private final ConcurrentLinkedDeque<MagicThread> threads = new ConcurrentLinkedDeque<>();
    private final LinkedHashSet<MagicThread> blockingIoThreads = new LinkedHashSet<>();
    private final ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();

    public MagicPool(int size) {
        for (int i = 0; i < size; ++i) {
            MagicThread magicThread = new MagicThread();
            magicThread.start();
        }
    }

    @Override
    public void execute(Runnable command) {
        execute(command, true);
    }

    private void execute(Runnable command, boolean queueTask) {
        //try and directly execute, avoiding the thread list

        MagicThread poll = threads.poll();
        if (poll != null) {
            //execute in this waiting thread
            poll.executeWaiter(command);
            return;
        }
        //no free threads
        //look for IO blocked threads
        synchronized (this) {
            if(!blockingIoThreads.isEmpty()) {
                Iterator<MagicThread> iterator = blockingIoThreads.iterator();
                poll = iterator.next();
                iterator.remove();
                if (poll != null) {
                    poll.executeInIOBlockedThread(command);
                    return;
                }
            }
        }
        //the direct execution failed, add it to the task list
        if (queueTask) {
            tasks.add(command);
            //we need to retry execution, as all threads could now be waiting
            execute(EMPTY, false);
        }
    }

    public class MagicThread extends Thread {
        private volatile Runnable runnable;
        private IoWaitTask ioWaitTask = null;

        @Override
        public void run() {

            while (true) {
                try {
                    //start of the loop, look for tasks from the queue
                    Runnable task = tasks.poll();
                    if (task != null) {
                        //if there is a wait task we need to cancel it, as we are busy
                        if (ioWaitTask != null) {
                            ioWaitTask.cancel();
                            ioWaitTask = null;
                        }
                        try {
                            task.run();
                        } catch (Throwable t) {
                            UndertowLogger.ROOT_LOGGER.workerTaskFailed(t);
                        }
                    } else if (ioWaitTask == null) {
                        //no task, and no IO task, lets wait on the monitor for a task
                        Runnable r = null;
                        synchronized (this) {
                            if (threads.contains(MagicThread.this)) {
                                throw new IllegalStateException();
                            }
                            if (runnable != null) {
                                throw new IllegalStateException();
                            }
                            threads.add(this);
                            while (runnable == null) {
                                wait();
                            }

                            r = runnable;
                            runnable = null;
                        }
                        try {
                            r.run();
                        } catch (Throwable t) {
                            UndertowLogger.ROOT_LOGGER.workerTaskFailed(t);
                        }
                    } else {
                        //we have an IO wait task, and nothing has been queued
                        try {
                            IoWaitTask w = ioWaitTask;
                            ioWaitTask = null;
                            w.run();
                        } catch (Throwable t) {
                            UndertowLogger.ROOT_LOGGER.workerTaskFailed(t);
                        }
                        Runnable r = null;
                        if (runnable != null) {
                            r = runnable;
                            runnable = null;
                        }
                        if (r != null) {
                            try {
                                r.run();
                            } catch (Throwable t) {
                                UndertowLogger.ROOT_LOGGER.workerTaskFailed(t);
                            }
                        }
                    }

                } catch (Throwable t) {
                    UndertowLogger.ROOT_LOGGER.workerTaskFailed(t);
                }
            }

        }

        //perform a read,
        public void executeReadTask(final StreamSourceChannel channel, final ChannelListener<StreamSourceChannel> listener) {
            if (Thread.currentThread() != this) {
                throw new IllegalStateException();
            }
            ioWaitTask = new IoWaitTask(channel, listener);
        }

        private void executeWaiter(Runnable command) {
            synchronized (MagicThread.this) {
                if (runnable != null) {
                    throw new IllegalStateException();
                }
                runnable = command;
                notifyAll();
            }
        }

        private void executeInIOBlockedThread(Runnable command) {
            assert Thread.holdsLock(MagicPool.this);
            if (runnable != null) {
                throw new IllegalStateException();
            }
            runnable = command;
            interrupt();
        }

        class IoWaitTask {

            final StreamSourceChannel channel;
            final ChannelListener<StreamSourceChannel> listener;

            IoWaitTask(StreamSourceChannel channel, ChannelListener<StreamSourceChannel> listener) {
                this.channel = channel;
                this.listener = listener;
            }

            public void run() {
                try {
                    synchronized (MagicPool.this) {
                        if (runnable != null) {
                            throw new IllegalStateException();
                        }
                        if (runnable != null) {
                            throw new IllegalStateException();
                        }
                        blockingIoThreads.add(MagicThread.this);
                    }
                    boolean handleEvent = false;
                    try {
                        if (channel.isOpen()) {
                            channel.awaitReadable();
                        }
                    } finally {
                        synchronized (MagicPool.this) {
                            if (runnable == null) {
                                blockingIoThreads.remove(MagicThread.this);
                                handleEvent = true;
                            }
                            interrupted();
                        }
                    }
                    if (handleEvent) {
                        listener.handleEvent(channel);
                    } else {
                        channel.getReadSetter().set((ChannelListener) listener);
                        channel.resumeReads();
                    }
                } catch (InterruptedIOException e) {
                    channel.getReadSetter().set((ChannelListener) listener);
                    channel.resumeReads();
                    interrupted();
                } catch (IOException e) {
                    channel.getReadSetter().set((ChannelListener) listener);
                    channel.wakeupReads();
                }
            }

            void cancel() {
                channel.getReadSetter().set((ChannelListener) listener);
                channel.resumeReads();
            }
        }
    }

}
