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

package io.undertow.servlet.test.abruptclose;

import java.io.IOException;
 
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SocketLeakServlet extends HttpServlet {
 
    private static final long serialVersionUID = 0L;
    private static StringBuffer responseData = new StringBuffer("Z");
    private static long requestCounter = 0;
 
    @Override
    public void init() {
        // Binary increase responseData: 2^i [bytes] required to fine tune race condition,
        // start with ~ 32kB (2^15)
        for (int i = 0; i < 19; i++) {
            responseData.append(responseData);
        }
 
        System.out.println("ResponseData: " + (responseData.length() / 1024) + "kB");
    }
 
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Delay response for 2mS in oder to prevent socket depletion (depending on OS) due to state TIME_WAIT.
        // OS used during tests: debian jessie with default out of the box settings
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
 
        resp.setStatus(HttpServletResponse.SC_OK);
 
        requestCounter++;
 
        System.out.println("ClientRequests served: " + requestCounter);
 
        // Two methods are possible to write responseData to client: resp.getWriter() or resp.getOutputstream().
        // Both methods result in Half Closed Sockets that can be observed with following linux command:
        // watch "lsof -p <wildfly_pid> | grep sock | grep protocol | wc -l"
 
        resp.getOutputStream().print(responseData.toString());
        try {
            Thread.sleep(1000);
            resp.getOutputStream().print(responseData.toString());
            Thread.sleep(1000);
            resp.getOutputStream().print(responseData.toString());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        //resp.getOutputStream().print(responseData.toString());
    }
 
}