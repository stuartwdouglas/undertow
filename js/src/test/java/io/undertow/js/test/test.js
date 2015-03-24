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

$undertow
    .get("/testResponseSender", function ($exchange) {
        $exchange.send("Response Sender");
    })
    .get("/testRequestHeaders", function ($exchange) {
        $exchange.send($exchange.requestHeaders("my-header"));
    })
    .get("/testResponseHeaders", function ($exchange) {
        $exchange.responseHeaders("my-header", "my-header-value");
    })
    .get("/testArrayParam", ['$entity:json', 'jndi:java:datasources/DefaultDS', function($exchange, $next, json, ds) {
        $exchange.send("Array Param");
    }])
    .get("/testSendRedirect", function($exchange) {
        $exchange.sendRedirect("/testResponseSender");
    });

