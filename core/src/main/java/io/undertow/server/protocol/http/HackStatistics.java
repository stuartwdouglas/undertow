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

package io.undertow.server.protocol.http;

/**
 * @author Stuart Douglas
 */
public class HackStatistics {

    private long requestStart = -1;
    private long requestParseComplete = -1;
    private long threadPoolDispatch = -1;
    private long threadPoolStart = -1;
    private long ioReadStart = -1;
    private long ioReadEnd = -1;
    private long ioWriteStart = -1;
    private long ioWriteEnd = -1;
    private long awaitReadableTime = 0;
    private long awaitWritableTime = 0;

    public long getRequestStart() {
        return requestStart;
    }

    public void setRequestStart(long requestStart) {
        this.requestStart = requestStart;
    }

    public long getThreadPoolDispatch() {
        return threadPoolDispatch;
    }

    public void setThreadPoolDispatch(long threadPoolDispatch) {
        this.threadPoolDispatch = threadPoolDispatch;
    }

    public long getThreadPoolStart() {
        return threadPoolStart;
    }

    public void setThreadPoolStart(long threadPoolStart) {
        this.threadPoolStart = threadPoolStart;
    }

    public long getIoReadStart() {
        return ioReadStart;
    }

    public void setIoReadStart(long ioReadStart) {
        this.ioReadStart = ioReadStart;
    }

    public long getIoReadEnd() {
        return ioReadEnd;
    }

    public void setIoReadEnd(long ioReadEnd) {
        this.ioReadEnd = ioReadEnd;
    }

    public long getIoWriteStart() {
        return ioWriteStart;
    }

    public void setIoWriteStart(long ioWriteStart) {
        this.ioWriteStart = ioWriteStart;
    }

    public long getIoWriteEnd() {
        return ioWriteEnd;
    }

    public void setIoWriteEnd(long ioWriteEnd) {
        this.ioWriteEnd = ioWriteEnd;
    }

    public long getRequestParseComplete() {
        return requestParseComplete;
    }

    public void setRequestParseComplete(long requestParseComplete) {
        this.requestParseComplete = requestParseComplete;
    }

    public long getAwaitReadableTime() {
        return awaitReadableTime;
    }

    public void setAwaitReadableTime(long awaitReadableTime) {
        this.awaitReadableTime = awaitReadableTime;
    }

    public long getAwaitWritableTime() {
        return awaitWritableTime;
    }

    public void setAwaitWritableTime(long awaitWritableTime) {
        this.awaitWritableTime = awaitWritableTime;
    }
}
