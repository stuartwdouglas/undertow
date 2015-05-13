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
 * 
 * Represents a connection. This connection will generally have a limited scope (i.e. a single HTTP request).
 * Using the connection outside of this scope will have undefined results.
 *
 * 
 * @author Stuart Douglas
 */
public interface IOConnection extends IOChannel<IOConnection> {

    /**
     * Adds an interceptor that allows the data that is being written to be
     * modified.
     *
     * @param interceptor The interceptor
     */
    void addWriteInterceptor(WriteInterceptor interceptor);

    /**
     * Adds an interceptor that allows data that has been read to be modified.
     *
     * @param interceptor The interceptor
     */
    void addReadInterceptor(ReadInterceptor interceptor);
}
