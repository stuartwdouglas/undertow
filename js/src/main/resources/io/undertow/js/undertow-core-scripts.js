"use strict";
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

var $undertow = {

    Exchange: function (underlyingExchange) {

        this.$underlying = underlyingExchange;

        this.requestHeaders = function (name, value) {
            if (arguments.length >= 2) {
                underlyingExchange.requestHeaders.put(new HttpString(name), value);
            } else if (arguments.length == 1) {
                return underlyingExchange.requestHeaders.getFirst(name);
            } else {
                //TODO: return some kind of headers object
            }
        };


        this.responseHeaders = function () {
            if (arguments.length >= 2) {
                underlyingExchange.responseHeaders.put(new HttpString(arguments[0]), arguments[1]);
            } else if (arguments.length == 1) {
                return underlyingExchange.responseHeaders.getFirst(arguments[1]);
            } else {
                //TODO: return some kind of headers object
            }
        };

        this.send = function (val) {
            if (val == null) {
                underlyingExchange.responseSender.send("");
            } else {
                underlyingExchange.responseSender.send(val);
            }
        };

        this.sendRedirect = function (location) {
            this.responseHeaders("Location", location);
            this.status(302);
            this.endExchange();
        };

        this.status = function () {
            if (arguments.length > 0) {
                underlyingExchange.setResponseCode(arguments[0]);
            } else {
                return underlyingExchange.responseCode;
            }
        };

        this.endExchange = function () {
            underlyingExchange.endExchange();
        };

        this.param = function (name) {
            var paramList = underlyingExchange.queryParameters.get(name);
            if (paramList == null) {
                return null;
            }
            return paramList.getFirst();
        };

        this.params = function (name) {
            var params = underlyingExchange.queryParameters.get(name);
            if (params == null) {
                return null;
            }
            var it = params.iterator();
            var ret = [];
            while (it.hasNext()) {
                ret.push(it.next());
            }
            return ret;
        };
    },

    injection_aliases: {},
    entity_parsers: {},

    _create_injection_function: function (p) {
        var index = p.indexOf(":");
        if (index < 0) {
            //no prefix, it has to be an alias
            //we just use the alias function directly
            return $undertow.injection_aliases[p];
        } else {
            var prefix = p.substr(0, index);
            var suffix = p.substr(index);
            if (prefix == '$entity') {

            } else {
                var provider = $undertow_injection_providers[prefix];
                if (provider == null) {
                    return function () {
                        return null;
                    };
                } else {
                    return function () {
                        return provider.getObject(suffix);
                    };
                }
            }
        }
    },

    _create_handler_function: function (userHandler) {
        if (userHandler == null) {
            throw "handler function cannot be null";
        }
        var handler = userHandler;
        var params = []
        if (userHandler.constructor === Array) {
            handler = userHandler[userHandler.length - 1];
            for (var i = 0; i < userHandler.length - 1; ++i) {
                params.push($undertow._create_injection_function(userHandler[i]));
            }
        }

        return new HttpHandler({
            handleRequest: function (underlyingExchange) {

                var $exchange = new $undertow.Exchange(underlyingExchange)

                var paramList = [];
                paramList.push($exchange);
                for (var i = 0; i < params.length; ++i) {
                    var param = params[i];
                    if (param == null) {
                        paramList.push(null);
                    } else {
                        paramList.push(param());
                    }
                }

                handler.apply(null, paramList);
            }
        });
    },


    onGet: function () {
        var args = ["GET"];
        for (var i = 0; i < arguments.length; ++i) {
            args.push(arguments[i]);
        }
        $undertow.onRequest.apply(null, args);
        return $undertow;
    },

    onPost: function (route, handler) {
        var args = ["POST"];
        for (var i = 0; i < arguments.length; ++i) {
            args.push(arguments[i]);
        }
        $undertow.onRequest.apply(null, args);
        return $undertow;
    },

    onPut: function (route, handler) {
        var args = ["PUT"];
        for (var i = 0; i < arguments.length; ++i) {
            args.push(arguments[i]);
        }
        $undertow.onRequest.apply(null, args);
        return $undertow;
    },

    onDelete: function (route, handler) {
        var args = ["DELETE"];
        for (var i = 0; i < arguments.length; ++i) {
            args.push(arguments[i]);
        }
        $undertow.onRequest.apply(null, args);
        return $undertow;
    },

    onRequest: function (method, route) {
        if (arguments.length > 3) {
            $undertow_routing_handler.add(method, route, PredicateParser.parse(arguments[2], $undertow_class_loader), $undertow._create_handler_function(arguments[3]));
        } else {
            $undertow_routing_handler.add(method, route, $undertow._create_handler_function(arguments[2]));
        }

        return $undertow;
    },

    alias: function (alias, injection) {
        $undertow.injection_aliases[alias] = $undertow._create_injection_function(injection);
        return $undertow;
    }


};