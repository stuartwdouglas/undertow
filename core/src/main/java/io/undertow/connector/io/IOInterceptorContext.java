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

/**
 * Context that is passed to an IO callback. This allows
 * the callback to suspend or resume the underlying IO mechanism.
 *
 * @author Stuart Douglas
 */
public interface IOInterceptorContext<C extends IOChannel<C>> {

    /**
     *
     * @return true if this is a blocking operation
     */
    boolean isBlocking();

    /**
     * This can only be invoked for non blocking operations. This tells
     * the channel that the interceptor is not finished yet, and may have more data
     * later.
     *
     * Any data that is returned from the interceptor will be processed as normal, however
     * after that no further callback processing will be done until resume() is called.
     *
     * If this is called during a flush() then when resume() is called the flush() method
     * will be invoked again.
     */
    void pause();

    /**
     * Resumes IO
     */
    void resume();

    /**
     *
     * @return The channel
     */
    C getChannel();

}
