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

/**
 * Undertow scripts that provide core javascript functionality
 */
var HttpHandler = Java.type("io.undertow.server.HttpHandler");
var HttpString = Java.type("io.undertow.util.HttpString");

var createHandlerFunction = function (userHandler) {
    var handler = userHandler;
    if(userHandler.constructor === Array) {
        for(var i = 0; i < userHandler.length; ++i) {

        }
    }

    return new HttpHandler({
        handleRequest: function(underlyingExchange) {

            var $exchange  = {
                $underlying: underlyingExchange,

                requestHeaders: function(name, value) {
                    if(value && name) {
                        underlyingExchange.requestHeaders.put(new HttpString(name), value);
                    } else if(name) {
                        return underlyingExchange.requestHeaders.getFirst(name);
                    } else {
                        //TODO: return some kind of headers object
                    }
                },


                responseHeaders: function(name, value) {
                    if(value && name) {
                        underlyingExchange.responseHeaders.put(new HttpString(name), value);
                    } else if(name) {
                        return underlyingExchange.responseHeaders.getFirst(name);
                    } else {
                        //TODO: return some kind of headers object
                    }
                },

                send: function(val) {
                    underlyingExchange.responseSender.send(val);
                },

                read: function(callback) {

                }

            }

            handler($exchange)
        }
    });
};

$undertow = {
    get: function(route, handler) {
        $undertow_routing_handler.get(route, createHandlerFunction(handler));
        return $undertow;
    },

    post: function(route, handler) {
        $undertow_routing_handler.post(route, createHandlerFunction(handler));
        return $undertow;
    },

    put: function(route, handler) {
        $undertow_routing_handler.put(route, createHandlerFunction(handler));
        return $undertow;
    },

    delete: function(route, handler) {
        $undertow_routing_handler.delete(route, createHandlerFunction(handler));
        return $undertow;
    },

    route: function(method, route, handler) {
        $undertow_routing_handler.add(method, route, createHandlerFunction(handler));
        return $undertow;
    },
};