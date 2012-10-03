/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package io.undertow.test.handlers.file;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.file.FileHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FileHandlerTestCase {


    final ByteBuffer buf = ByteBuffer.allocateDirect(11);
    {
    buf.put("hello world".getBytes());
        buf.flip();
    }

    @Test
    public void testFileIsServed() throws IOException, InterruptedException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            final FileHandler handler = new FileHandler(new File(getClass().getResource("page.html").getFile()).getParentFile());
            handler.setDirectoryListingEnabled(true);
            final PathHandler path = new PathHandler();
            path.addPath("/path", handler);
            final CanonicalPathHandler root = new CanonicalPathHandler();
            root.setNext(path);
            DefaultServer.setRootHandler(new HttpHandler() {
                @Override
                public void handleRequest(HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "11");
                    final StreamSinkChannel streamSinkChannel = exchange.getResponseChannelFactory().create();
                    final ByteBuffer duplicate = buf.duplicate();
                    try {
                        int res = 0;
                        do{
                            res = streamSinkChannel.write(duplicate);
                            if(res == 0) {
                                streamSinkChannel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {

                                    @Override
                                    public void handleEvent(StreamSinkChannel channel) {
                                        int a = 0;
                                        do {
                                            try {
                                                a = streamSinkChannel.write(duplicate);
                                            } catch (IOException e) {
                                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                            }
                                            if(a == 0) {
                                                return;
                                            }
                                        } while (duplicate.hasRemaining());
                                        completionHandler.handleComplete();
                                    }
                                });
                                return;
                            }
                        } while (duplicate.hasRemaining());
                        completionHandler.handleComplete();
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            });

            Thread.sleep(100000000);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response, response.contains("A web page"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
