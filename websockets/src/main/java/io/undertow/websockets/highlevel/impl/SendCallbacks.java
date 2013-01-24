/*
 * Copyright 2013 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.highlevel.impl;

import io.undertow.websockets.highlevel.SendCallback;

/**
 * Wrapps a array of {@link SendCallback}s to execute on {@link #onCompletion()} or {@link #onError(Throwable)}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class SendCallbacks implements SendCallback {
    private final SendCallback[] callbacks;

    public SendCallbacks(SendCallback... callbacks) {
        if (callbacks == null || callbacks.length == 0) {
            throw new IllegalArgumentException("callbacks must at least one callback");
        }
        this.callbacks = callbacks;
    }

    @Override
    public void onCompletion() {
        try {
            for (SendCallback callback: callbacks) {
                callback.onCompletion();
            }
        } catch (Throwable cause) {
            // TODO: Add logging
            cause.printStackTrace();
        }
    }

    @Override
    public void onError(Throwable error) {
        try {
            for (SendCallback callback: callbacks) {
                callback.onError(error);
            }
        } catch (Throwable cause) {
            // TODO: Add logging
            cause.printStackTrace();
        }
    }
}
