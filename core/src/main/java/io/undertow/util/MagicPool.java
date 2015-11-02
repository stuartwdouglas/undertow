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
import org.xnio.Xnio;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;

/**
 * An executor that has the following semantics:
 *
 * @author Stuart Douglas
 */
public class MagicPool implements Executor {

    private final ConcurrentLinkedDeque<MagicThread> threads = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();

    private final Xnio xnio;

    public MagicPool(int size, Xnio xnio) {
        this.xnio = xnio;
        for (int i = 0; i < size; ++i) {
            MagicThread magicThread = new MagicThread();
            magicThread.start();
        }
    }

    @Override
    public void execute(Runnable command) {
        //try and directly execute, avoiding the thread list
        do {
            MagicThread poll = threads.poll();
            if (poll != null) {
                //try and execute in this polled thread
                if (poll.execute(command)) {
                    return;
                }
            } else {
                break;
            }
        } while (true);
        //the direct execution failed, add it to the task list
        tasks.add(command);
        do {
            //now we need to try again, there could be available threads now
            MagicThread poll = threads.poll();
            if (poll == null) { //all threads are busy, task will be executed
                return;
            }
            if (poll.poke()) {
                //a thread was woken up to process the task queue
                return;
            }
        } while (true);
    }

    public class MagicThread extends Thread {
        private Runnable runnable;
        private IoWaitTask ioWaitTask = null;

        private boolean waitingOnSelector = false;
        private boolean waitingOnMonitor = false;

        public boolean execute(Runnable command) {
            synchronized (this) {
                if(threads.contains(this)) {
                    throw new IllegalStateException();
                }
                if (waitingOnMonitor) {
                    if (runnable != null) {
                        throw new IllegalStateException();
                    }
                    runnable = command;
                    notifyAll();
                } else if (waitingOnSelector) {
                    if (runnable != null) {
                        throw new IllegalStateException();
                    }
                    runnable = command;
                    interrupt();
                } else {
                    return false;
                }
            }
            return true;
        }

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
                            waitingOnMonitor = true;
                            try {
                                if (threads.contains(MagicThread.this)) {
                                    throw new IllegalStateException();
                                }
                                if(runnable != null) {
                                    throw new IllegalStateException();
                                }
                                threads.add(this);
                                while (runnable == null) {
                                    wait();
                                }
                            } finally {
                                threads.remove(this);
                                waitingOnMonitor = false;
                            }
                            if (runnable != null) {
                                r = runnable;
                                runnable = null;
                            }
                        }
                        if (r != null) {
                            try {
                                r.run();
                            } catch (Throwable t) {
                                UndertowLogger.ROOT_LOGGER.workerTaskFailed(t);
                            }
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
                        synchronized (this) {
                            if (runnable != null) {
                                r = runnable;
                                runnable = null;
                            }
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

        public boolean poke() {
            return execute(new Runnable() {
                @Override
                public void run() {

                }
            });
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
                    synchronized (MagicThread.this) {
                        if(runnable != null) {
                            throw new IllegalStateException();
                        }
                        waitingOnSelector = true;
                        if (threads.contains(MagicThread.this)) {
                            throw new IllegalStateException();
                        }
                        if(runnable != null) {
                            throw new IllegalStateException();
                        }
                        threads.add(MagicThread.this);
                    }
                    boolean handleEvent = false;
                    try {
                        if (channel.isOpen()) {
                            channel.awaitReadable();
                        }
                    } finally {
                        synchronized (MagicThread.this) {
                            threads.remove(MagicThread.this);
                            waitingOnSelector = false;
                            if (runnable == null) {
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
                threads.remove(MagicThread.this);
                channel.getReadSetter().set((ChannelListener) listener);
                channel.resumeReads();
            }
        }
    }

}
