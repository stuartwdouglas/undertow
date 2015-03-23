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

package io.undertow.js.test;

import io.undertow.js.UndertowJS;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.script.ScriptException;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleJavascriptTestCase {

    @Test
    public void testSomeJsStuff() throws ScriptException, IOException {

        final TestHttpClient client = new TestHttpClient();
        try {
            UndertowJS js = UndertowJS.builder().build();
            js.start();
            DefaultServer.setRootHandler(js.getHandler(ResponseCodeHandler.HANDLE_404));


            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hi", HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
