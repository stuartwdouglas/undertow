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

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Represents the IO thread, that will perform all non
 * blocking operations on a channel.
 *
 * @author Stuart Douglas
 */
public interface IOThread extends Executor {

    /**
     * Schedules a future task
     *
     * @param task The task to schedule
     * @param time The delay before executing the task
     * @param timeUnit The time unit for the delay
     */
    void executeAfter(Runnable task, long time, TimeUnit timeUnit);

    /**
     * If this IOThread represents the current thread.
     * @return <code>true</code> if the current thread is the IO thread
     */
    boolean isCurrentThread();

    /**
     * A key that can be used to cancel and modify a scheduled task
     */
    interface ScheduledKey {

        /**
         * Attempts to cancel a scheduled task
         *
         * @return <code>true</code> if the task was cancelled
         */
        boolean cancel();

        /**
         * Modifies the execution time of a scheduled task. In general
         * this will be more efficient then cancelling and re-scheduling.
         *
         * Note that the time is calculated from the current time, not the
         * original scheduled time.
         *
         * @param time The new delay
         * @param timeUnit the delay time unit
         */
        void reSchedule(long time, TimeUnit timeUnit);

    }
}
