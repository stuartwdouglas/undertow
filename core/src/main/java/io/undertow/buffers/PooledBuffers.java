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

import io.undertow.connector.PooledBuffer;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class PooledBuffers {

    public static ByteBuffer[] toBufferArray(PooledBuffer... bufs) {
        ByteBuffer[] ret = new ByteBuffer[bufs.length];
        for(int i = 0; i < ret.length; ++i) {
            ret[i] = bufs[i].buffer();
        }
        return ret;
    }

    public static void close(PooledBuffer... bufs) {
        for(PooledBuffer i : bufs) {
            i.close();
        }
    }

    private PooledBuffers() {}
}
