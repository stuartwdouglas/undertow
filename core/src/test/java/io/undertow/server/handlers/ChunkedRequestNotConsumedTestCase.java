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

package io.undertow.server.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ChunkedRequestNotConsumedTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws InterruptedException {
                exchange.setResponseContentLength("message".length());
                exchange.getResponseSender().send("message", new IoCallback() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable() {
                            @Override
                            public void run() {
                                exchange.getIoThread().executeAfter(new Runnable() {
                                    @Override
                                    public void run() {
                                        exchange.endExchange();
                                    }
                                }, 1000, TimeUnit.MILLISECONDS);
                            }
                        });
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        exchange.endExchange();
                    }
                });
            }
        });
    }

    @Test
    public void testChunkedRequestNotConsumed() throws IOException {
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            final Random random = new Random();
            final int seed = random.nextInt();
            System.out.print("Using Seed " + seed);
            random.setSeed(seed);


            for (int i = 0; i < 10; ++i) {
                generateMessage(10);
                post.setEntity(new StringEntity(message) {
                    @Override
                    public long getContentLength() {
                        return -1;
                    }

                    @Override
                    public boolean isChunked() {
                        return true;
                    }

                    @Override
                    public void writeTo(OutputStream outstream) throws IOException {
                        int l = 0;
                        int i = 0;
                        while (i <= message.length()) {
                            i += random.nextInt(10);
                            i = Math.min(i, message.length());
                            outstream.write(message.getBytes(), l, i - l);
                            l = i;
                            ++i;
                        }
                    }
                });
                HttpResponse result = client.execute(post);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                HttpClientUtils.readResponse(result);
            }
        } finally {

            client.getConnectionManager().shutdown();
        }
    }


    private static void generateMessage(int repetitions) {
        final StringBuilder builder = new StringBuilder(repetitions * MESSAGE.length());
        for (int i = 0; i < repetitions; ++i) {
            builder.append(MESSAGE);
        }
        message = builder.toString();
    }
}
