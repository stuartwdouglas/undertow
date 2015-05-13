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

package io.undertow.server.protocol.framed;

import io.undertow.connector.PooledBuffer;

/**
 * @author Stuart Douglas
 */
public class SendFrameHeader {

    private final int reminingInBuffer;
    private final PooledBuffer byteBuffer;
    private final boolean anotherFrameRequired;

    public SendFrameHeader(int reminingInBuffer, PooledBuffer byteBuffer, boolean anotherFrameRequired) {
        this.byteBuffer = byteBuffer;
        this.reminingInBuffer = reminingInBuffer;
        this.anotherFrameRequired = anotherFrameRequired;
    }

    public SendFrameHeader(int reminingInBuffer, PooledBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
        this.reminingInBuffer = reminingInBuffer;
        this.anotherFrameRequired = false;
    }

    public SendFrameHeader(PooledBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
        this.reminingInBuffer = 0;
        this.anotherFrameRequired = false;
    }

    /**
     *
     * @return The header byte buffer
     */
    public PooledBuffer getByteBuffer() {
        return byteBuffer;
    }

    /**
     *
     * @return
     */
    public int getRemainingInBuffer() {
        return reminingInBuffer;
    }

    /**
     * Returns true if another frame is required after this one. Note that returning false
     * does not mean that this is the last frame. This is used for protocols that require a trailing packet
     * after all data has been written.
     */
    public boolean isAnotherFrameRequired() {
        return anotherFrameRequired;
    }
}
