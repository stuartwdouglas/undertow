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
var PredicateParser = Java.type("io.undertow.predicate.PredicateParser");

var createHandlerFunction = function (userHandler) {
    var handler = userHandler;
    var params = []
    if(userHandler.constructor === Array) {
        params = userHandler.slice(1);
    }

    return new HttpHandler({
        handleRequest: function(underlyingExchange) {

            var $exchange  = {
                $underlying: underlyingExchange,

                requestHeaders: function(name, value) {
                    if(arguments.length >= 2) {
                        underlyingExchange.requestHeaders.put(new HttpString(name), value);
                    } else if(arguments.length == 1) {
                        return underlyingExchange.requestHeaders.getFirst(name);
                    } else {
                        //TODO: return some kind of headers object
                    }
                },


                responseHeaders: function() {
                    if(arguments.length >= 2) {
                        underlyingExchange.responseHeaders.put(new HttpString(arguments[0]), arguments[1]);
                    } else if(arguments.length == 1) {
                        return underlyingExchange.responseHeaders.getFirst(arguments[1]);
                    } else {
                        //TODO: return some kind of headers object
                    }
                },

                send: function(val) {
                    underlyingExchange.responseSender.send(val);
                },

                sendRedirect: function(location) {
                    $exchange.responseHeaders("Location", location);
                    $exchange.status(302);
                    $exchange.endExchange();
                },

                status: function() {
                    if(arguments.length > 0) {
                        underlyingExchange.setResponseCode(arguments[0]);
                    } else {
                        return underlyingExchange.responseCode;
                    }
                },

                endExchange: function() {
                    underlyingExchange.endExchange();
                },

                param: function(name) {
                    var paramList = underlyingExchange.queryParameters.get(name);
                    if(paramList == null) {
                        return null;
                    }
                    return paramList.getFirst();
                },

                params: function(name) {
                    var params = underlyingExchange.queryParameters.get(name);
                    if(params == null) {
                        return null;
                    }
                    var it = params.iterator();
                    var ret = [];
                    while(it.hasNext()) {
                        ret.push(it.next());
                    }
                    return ret;
                }

            }

            var paramList = [];
            paramList.push($exchange);
            for(var i = 0; i < params.length; ++i) {
                paramList.push($undertow_injection_resolver.resolve(params[i]));
            }

            handler.apply(null, paramList);
        }
    });
};

$undertow = {
    onGet: function(route) {
        if(arguments.length > 2) {
            $undertow_routing_handler.get(route, PredicateParser.parse(arguments[1], $undertow_class_loader), createHandlerFunction(arguments[2]));
        } else {
            $undertow_routing_handler.get(route, createHandlerFunction(arguments[1]));
        }
        return $undertow;
    },

    onPost: function(route, handler) {
        $undertow_routing_handler.post(route, createHandlerFunction(handler));
        return $undertow;
    },

    onPut: function(route, handler) {
        $undertow_routing_handler.put(route, createHandlerFunction(handler));
        return $undertow;
    },

    onDelete: function(route, handler) {
        $undertow_routing_handler.delete(route, createHandlerFunction(handler));
        return $undertow;
    },

    onRequest: function(method, route, handler) {
        $undertow_routing_handler.add(method, route, createHandlerFunction(handler));
        return $undertow;
    }

};