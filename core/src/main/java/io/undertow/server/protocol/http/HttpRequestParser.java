/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

package io.undertow.server.protocol.http;

import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.URLUtils;
import org.xnio.OptionMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static io.undertow.util.Headers.ACCEPT;
import static io.undertow.util.Headers.ACCEPT_CHARSET;
import static io.undertow.util.Headers.ACCEPT_ENCODING;
import static io.undertow.util.Headers.ACCEPT_LANGUAGE;
import static io.undertow.util.Headers.ACCEPT_RANGES;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.CACHE_CONTROL;
import static io.undertow.util.Headers.CONNECTION;
import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Headers.COOKIE;
import static io.undertow.util.Headers.EXPECT;
import static io.undertow.util.Headers.FROM;
import static io.undertow.util.Headers.HOST;
import static io.undertow.util.Headers.IF_MATCH;
import static io.undertow.util.Headers.IF_MODIFIED_SINCE;
import static io.undertow.util.Headers.IF_NONE_MATCH;
import static io.undertow.util.Headers.IF_RANGE;
import static io.undertow.util.Headers.IF_UNMODIFIED_SINCE;
import static io.undertow.util.Headers.MAX_FORWARDS;
import static io.undertow.util.Headers.ORIGIN;
import static io.undertow.util.Headers.PRAGMA;
import static io.undertow.util.Headers.PROXY_AUTHORIZATION;
import static io.undertow.util.Headers.RANGE;
import static io.undertow.util.Headers.REFERER;
import static io.undertow.util.Headers.REFRESH;
import static io.undertow.util.Headers.SEC_WEB_SOCKET_KEY;
import static io.undertow.util.Headers.SEC_WEB_SOCKET_VERSION;
import static io.undertow.util.Headers.SERVER;
import static io.undertow.util.Headers.SSL_CIPHER;
import static io.undertow.util.Headers.SSL_CIPHER_USEKEYSIZE;
import static io.undertow.util.Headers.SSL_CLIENT_CERT;
import static io.undertow.util.Headers.SSL_SESSION_ID;
import static io.undertow.util.Headers.STRICT_TRANSPORT_SECURITY;
import static io.undertow.util.Headers.TRAILER;
import static io.undertow.util.Headers.TRANSFER_ENCODING;
import static io.undertow.util.Headers.UPGRADE;
import static io.undertow.util.Headers.USER_AGENT;
import static io.undertow.util.Headers.VIA;
import static io.undertow.util.Headers.WARNING;
import static io.undertow.util.Methods.CONNECT;
import static io.undertow.util.Methods.DELETE;
import static io.undertow.util.Methods.GET;
import static io.undertow.util.Methods.HEAD;
import static io.undertow.util.Methods.OPTIONS;
import static io.undertow.util.Methods.POST;
import static io.undertow.util.Methods.PUT;
import static io.undertow.util.Methods.TRACE;
import static io.undertow.util.Protocols.HTTP_0_9;
import static io.undertow.util.Protocols.HTTP_1_0;
import static io.undertow.util.Protocols.HTTP_1_1;

/**
 * The basic HTTP parser. The actual parser is a sub class of this class that is generated as part of
 * the build process by the {@link io.undertow.annotationprocessor.AbstractParserGenerator} annotation processor.
 * <p/>
 * The actual processor is a state machine, that means that for common header, method, protocol values
 * it will return an interned string, rather than creating a new string for each one.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class HttpRequestParser {


    private static final HttpString[] METHODS = {
            GET,
            POST,
            HEAD,
            PUT,
            DELETE,
            TRACE,
            CONNECT,
            OPTIONS,};

    private static final HttpString[] VERSIONS = {
            HTTP_1_1, HTTP_0_9, HTTP_1_0
    };
    
    private static final HttpString[] HEADERS = {
            HOST,
            ACCEPT,
            ACCEPT_CHARSET,
            ACCEPT_ENCODING,
            ACCEPT_LANGUAGE,
            ACCEPT_RANGES,
            AUTHORIZATION,
            CACHE_CONTROL,
            COOKIE,
            CONNECTION,
            CONTENT_LENGTH,
            CONTENT_TYPE,
            EXPECT,
            FROM,
            IF_MATCH,
            IF_MODIFIED_SINCE,
            IF_NONE_MATCH,
            IF_RANGE,
            IF_UNMODIFIED_SINCE,
            MAX_FORWARDS,
            ORIGIN,
            PRAGMA,
            PROXY_AUTHORIZATION,
            RANGE,
            REFERER,
            REFRESH,
            SEC_WEB_SOCKET_KEY,
            SEC_WEB_SOCKET_VERSION,
            SERVER,
            SSL_CLIENT_CERT,
            SSL_CIPHER,
            SSL_SESSION_ID,
            SSL_CIPHER_USEKEYSIZE,
            STRICT_TRANSPORT_SECURITY,
            TRAILER,
            TRANSFER_ENCODING,
            UPGRADE,
            USER_AGENT,
            VIA,
            WARNING   
            
    };

    private final int maxParameters;
    private final int maxHeaders;
    private final boolean allowEncodedSlash;
    private final boolean decode;
    private final String charset;

    private final HttpMethodStateMachine methodParser;
    private final HttpVersionStateMachine versionParser;
    private final HttpHeaderStateMachine headerStateMachine;

    public HttpRequestParser(OptionMap options) {
        maxParameters = options.get(UndertowOptions.MAX_PARAMETERS, 1000);
        maxHeaders = options.get(UndertowOptions.MAX_HEADERS, 200);
        allowEncodedSlash = options.get(UndertowOptions.ALLOW_ENCODED_SLASH, false);
        decode = options.get(UndertowOptions.DECODE_URL, true);
        charset = options.get(UndertowOptions.URL_CHARSET, "UTF-8");
        methodParser = new HttpMethodStateMachine();
        methodParser.generate(Arrays.asList(METHODS));
        versionParser = new HttpVersionStateMachine();
        versionParser.generate(Arrays.asList(VERSIONS));
        headerStateMachine = new HttpHeaderStateMachine();
        headerStateMachine.generate(Arrays.asList(HEADERS));
    }

    public static final HttpRequestParser instance(final OptionMap options) {
        try {
            final Class<?> cls = HttpRequestParser.class.getClassLoader().loadClass(HttpRequestParser.class.getName() + "$$generated");

            Constructor<?> ctor = cls.getConstructor(OptionMap.class);
            return (HttpRequestParser) ctor.newInstance(options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void handle(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) {
        if (currentState.state == ParseState.VERB) {
            //fast path, we assume that it will parse fully so we avoid all the if statements
            handleHttpVerb(buffer, currentState, builder);
            handlePath(buffer, currentState, builder);
            handleHttpVersion(buffer, currentState, builder);
            handleAfterVersion(buffer, currentState, builder);
            while (currentState.state != ParseState.PARSE_COMPLETE && buffer.hasRemaining()) {
                handleHeader(buffer, currentState, builder);
                if (currentState.state == ParseState.HEADER_VALUE) {
                    handleHeaderValue(buffer, currentState, builder);
                }
            }
            return;
        }
        if (currentState.state == ParseState.PATH) {
            handlePath(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.QUERY_PARAMETERS) {
            handleQueryParameters(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.PATH_PARAMETERS) {
            handlePathParameters(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }

        if (currentState.state == ParseState.VERSION) {
            handleHttpVersion(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }
        if (currentState.state == ParseState.AFTER_VERSION) {
            handleAfterVersion(buffer, currentState, builder);
            if (!buffer.hasRemaining()) {
                return;
            }
        }
        while (currentState.state != ParseState.PARSE_COMPLETE) {
            if (currentState.state == ParseState.HEADER) {
                handleHeader(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
            if (currentState.state == ParseState.HEADER_VALUE) {
                handleHeaderValue(buffer, currentState, builder);
                if (!buffer.hasRemaining()) {
                    return;
                }
            }
        }
    }


    private void handleHttpVerb(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) {
        methodParser.parse(buffer, currentState, builder);
    }

   private void handleHttpVersion(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) {
       versionParser.parse(buffer, currentState, builder);
   }

    private void handleHeader(ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) {
        headerStateMachine.parse(buffer, currentState, builder);
    }

    /**
     * The parse states for parsing the path.
     */
    private static final int START = 0;
    private static final int FIRST_COLON = 1;
    private static final int FIRST_SLASH = 2;
    private static final int SECOND_SLASH = 3;
    private static final int HOST_DONE = 4;

    /**
     * Parses a path value
     *
     * @param buffer   The buffer
     * @param state    The current state
     * @param exchange The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handlePath(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int parseState = state.parseState;
        int canonicalPathStart = state.pos;
        boolean urlDecodeRequired = state.urlDecodeRequired;

        while (buffer.hasRemaining()) {
            char next = (char) buffer.get();
            if (next == ' ' || next == '\t') {
                if (stringBuilder.length() != 0) {
                    final String path = stringBuilder.toString();
                    if (parseState < HOST_DONE) {
                        String decodedPath = decode(path, urlDecodeRequired, state, allowEncodedSlash);
                        exchange.setRequestPath(decodedPath);
                        exchange.setRelativePath(decodedPath);
                        exchange.setRequestURI(path);
                    } else {
                        String thePath = decode(path.substring(canonicalPathStart), urlDecodeRequired, state, allowEncodedSlash);
                        exchange.setRequestPath(thePath);
                        exchange.setRelativePath(thePath);
                        exchange.setRequestURI(path, true);
                    }
                    exchange.setQueryString("");
                    state.state = ParseState.VERSION;
                    state.stringBuilder.setLength(0);
                    state.parseState = 0;
                    state.pos = 0;
                    state.urlDecodeRequired = false;
                    return;
                }
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else if (next == '?' && (parseState == START || parseState == HOST_DONE)) {
                final String path = stringBuilder.toString();
                if (parseState < HOST_DONE) {
                    String decodedPath = decode(path, urlDecodeRequired, state, allowEncodedSlash);
                    exchange.setRequestPath(decodedPath);
                    exchange.setRelativePath(decodedPath);
                    exchange.setRequestURI(path, false);
                } else {
                    String thePath = decode(path.substring(canonicalPathStart), urlDecodeRequired, state, allowEncodedSlash);
                    exchange.setRequestPath(thePath);
                    exchange.setRelativePath(thePath);
                    exchange.setRequestURI(path, true);
                }
                state.state = ParseState.QUERY_PARAMETERS;
                state.stringBuilder.setLength(0);
                state.parseState = 0;
                state.pos = 0;
                state.urlDecodeRequired = false;
                handleQueryParameters(buffer, state, exchange);
                return;
            } else if (next == ';' && (parseState == START || parseState == HOST_DONE)) {
                final String path = stringBuilder.toString();
                if (parseState < HOST_DONE) {
                    String decodedPath = decode(path, urlDecodeRequired, state, allowEncodedSlash);
                    exchange.setRequestPath(decodedPath);
                    exchange.setRelativePath(decodedPath);
                    exchange.setRequestURI(path);
                } else {
                    String thePath = path.substring(canonicalPathStart);
                    exchange.setRequestPath(thePath);
                    exchange.setRelativePath(thePath);
                    exchange.setRequestURI(path, true);
                }
                state.state = ParseState.PATH_PARAMETERS;
                state.stringBuilder.setLength(0);
                state.parseState = 0;
                state.pos = 0;
                state.urlDecodeRequired = false;
                handlePathParameters(buffer, state, exchange);
                return;
            } else {

                if (decode && (next == '+' || next == '%')) {
                    urlDecodeRequired = true;
                } else if (next == ':' && parseState == START) {
                    parseState = FIRST_COLON;
                } else if (next == '/' && parseState == FIRST_COLON) {
                    parseState = FIRST_SLASH;
                } else if (next == '/' && parseState == FIRST_SLASH) {
                    parseState = SECOND_SLASH;
                } else if (next == '/' && parseState == SECOND_SLASH) {
                    parseState = HOST_DONE;
                    canonicalPathStart = stringBuilder.length();
                } else if (parseState == FIRST_COLON || parseState == FIRST_SLASH) {
                    parseState = START;
                }
                stringBuilder.append(next);
            }

        }
        state.parseState = parseState;
        state.pos = canonicalPathStart;
        state.urlDecodeRequired = urlDecodeRequired;
    }


    /**
     * Parses a path value
     *
     * @param buffer   The buffer
     * @param state    The current state
     * @param exchange The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handleQueryParameters(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int queryParamPos = state.pos;
        int mapCount = state.mapCount;
        boolean urlDecodeRequired = state.urlDecodeRequired;
        String nextQueryParam = state.nextQueryParam;

        //so this is a bit funky, because it not only deals with parsing, but
        //also deals with URL decoding the query parameters as well, while also
        //maintaining a non-decoded version to use as the query string
        //In most cases these string will be the same, and as we do not want to
        //build up two seperate strings we don't use encodedStringBuilder unless
        //we encounter an encoded character

        while (buffer.hasRemaining()) {
            char next = (char) buffer.get();
            if (next == ' ' || next == '\t') {
                final String queryString = stringBuilder.toString();
                exchange.setQueryString(queryString);
                if (nextQueryParam == null) {
                    if (queryParamPos != stringBuilder.length()) {
                        exchange.addQueryParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true), "");
                    }
                } else {
                    exchange.addQueryParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true));
                }
                state.state = ParseState.VERSION;
                state.stringBuilder.setLength(0);
                state.pos = 0;
                state.nextQueryParam = null;
                state.urlDecodeRequired = false;
                state.mapCount = 0;
                return;
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else {
                if (decode && (next == '+' || next == '%')) {
                    urlDecodeRequired = true;
                } else if (next == '=' && nextQueryParam == null) {
                    nextQueryParam = decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true);
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&' && nextQueryParam == null) {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    exchange.addQueryParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true), "");
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&') {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    exchange.addQueryParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true));
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                    nextQueryParam = null;
                }
                stringBuilder.append(next);

            }

        }
        state.pos = queryParamPos;
        state.nextQueryParam = nextQueryParam;
        state.urlDecodeRequired = urlDecodeRequired;
        state.mapCount = 0;
    }

    private String decode(final String value, boolean urlDecodeRequired, ParseState state, final boolean allowEncodedSlash) {
        if (urlDecodeRequired) {
            return URLUtils.decode(value, charset, allowEncodedSlash, state.decodeBuffer);
        } else {
            return value;
        }
    }


    final void handlePathParameters(ByteBuffer buffer, ParseState state, HttpServerExchange exchange) {
        StringBuilder stringBuilder = state.stringBuilder;
        int queryParamPos = state.pos;
        int mapCount = state.mapCount;
        boolean urlDecodeRequired = state.urlDecodeRequired;
        String nextQueryParam = state.nextQueryParam;

        //so this is a bit funky, because it not only deals with parsing, but
        //also deals with URL decoding the query parameters as well, while also
        //maintaining a non-decoded version to use as the query string
        //In most cases these string will be the same, and as we do not want to
        //build up two seperate strings we don't use encodedStringBuilder unless
        //we encounter an encoded character

        while (buffer.hasRemaining()) {
            char next = (char) buffer.get();
            if (next == ' ' || next == '\t' || next == '?') {
                if (nextQueryParam == null) {
                    if (queryParamPos != stringBuilder.length()) {
                        exchange.addPathParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true), "");
                    }
                } else {
                    exchange.addPathParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true));
                }
                exchange.setRequestURI(exchange.getRequestURI() + ';' + stringBuilder.toString(), state.parseState > HOST_DONE);
                state.stringBuilder.setLength(0);
                state.pos = 0;
                state.nextQueryParam = null;
                state.mapCount = 0;
                state.urlDecodeRequired = false;
                if (next == '?') {
                    state.state = ParseState.QUERY_PARAMETERS;
                    handleQueryParameters(buffer, state, exchange);
                } else {
                    state.state = ParseState.VERSION;
                }
                return;
            } else if (next == '\r' || next == '\n') {
                throw UndertowMessages.MESSAGES.failedToParsePath();
            } else {
                if (decode && (next == '+' || next == '%')) {
                    urlDecodeRequired = true;
                }
                if (next == '=' && nextQueryParam == null) {
                    nextQueryParam = decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true);
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&' && nextQueryParam == null) {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }
                    exchange.addPathParam(decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true), "");
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                } else if (next == '&') {
                    if (mapCount++ > maxParameters) {
                        throw UndertowMessages.MESSAGES.tooManyQueryParameters(maxParameters);
                    }

                    exchange.addPathParam(nextQueryParam, decode(stringBuilder.substring(queryParamPos), urlDecodeRequired, state, true));
                    urlDecodeRequired = false;
                    queryParamPos = stringBuilder.length() + 1;
                    nextQueryParam = null;
                }
                stringBuilder.append(next);

            }

        }
        state.pos = queryParamPos;
        state.nextQueryParam = nextQueryParam;
        state.mapCount = 0;
        state.urlDecodeRequired = urlDecodeRequired;
    }


    /**
     * The parse states for parsing heading values
     */
    private static final int NORMAL = 0;
    private static final int WHITESPACE = 1;
    private static final int BEGIN_LINE_END = 2;
    private static final int LINE_END = 3;
    private static final int AWAIT_DATA_END = 4;

    /**
     * Parses a header value. This is called from the generated  bytecode.
     *
     * @param buffer  The buffer
     * @param state   The current state
     * @param builder The exchange builder
     * @return The number of bytes remaining
     */
    @SuppressWarnings("unused")
    final void handleHeaderValue(ByteBuffer buffer, ParseState state, HttpServerExchange builder) {
        StringBuilder stringBuilder = state.stringBuilder;
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
            state.parseState = 0;

            if (state.mapCount++ > maxHeaders) {
                throw UndertowMessages.MESSAGES.tooManyHeaders(maxHeaders);
            }
        }


        int parseState = state.parseState;
        while (buffer.hasRemaining() && parseState == NORMAL) {
            final byte next = buffer.get();
            if (next == '\r') {
                parseState = BEGIN_LINE_END;
            } else if (next == '\n') {
                parseState = LINE_END;
            } else if (next == ' ' || next == '\t') {
                parseState = WHITESPACE;
            } else {
                stringBuilder.append((char) next);
            }
        }

        while (buffer.hasRemaining()) {
            final byte next = buffer.get();
            switch (parseState) {
                case NORMAL: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                        parseState = WHITESPACE;
                    } else {
                        stringBuilder.append((char) next);
                    }
                    break;
                }
                case WHITESPACE: {
                    if (next == '\r') {
                        parseState = BEGIN_LINE_END;
                    } else if (next == '\n') {
                        parseState = LINE_END;
                    } else if (next == ' ' || next == '\t') {
                    } else {
                        if (stringBuilder.length() > 0) {
                            stringBuilder.append(' ');
                        }
                        stringBuilder.append((char) next);
                        parseState = NORMAL;
                    }
                    break;
                }
                case LINE_END:
                case BEGIN_LINE_END: {
                    if (next == '\n' && parseState == BEGIN_LINE_END) {
                        parseState = LINE_END;
                    } else if (next == '\t' ||
                            next == ' ') {
                        //this is a continuation
                        parseState = WHITESPACE;
                    } else {
                        //we have a header
                        HttpString nextStandardHeader = state.nextHeader;
                        String headerValue = stringBuilder.toString();

                        //TODO: we need to decode this according to RFC-2047 if we have seen a =? symbol
                        builder.getRequestHeaders().add(nextStandardHeader, headerValue);

                        state.nextHeader = null;

                        state.leftOver = next;
                        state.stringBuilder.setLength(0);
                        if (next == '\r') {
                            parseState = AWAIT_DATA_END;
                        } else {
                            state.state = ParseState.HEADER;
                            state.parseState = 0;
                            return;
                        }
                    }
                    break;
                }
                case AWAIT_DATA_END: {
                    state.state = ParseState.PARSE_COMPLETE;
                    return;
                }
            }
        }
        //we only write to the state if we did not finish parsing
        state.parseState = parseState;
        return;
    }

    protected void handleAfterVersion(ByteBuffer buffer, ParseState state, HttpServerExchange builder) {
        boolean newLine = state.leftOver == '\n';
        while (buffer.hasRemaining()) {
            final byte next = buffer.get();
            if (newLine) {
                if (next == '\n') {
                    state.state = ParseState.PARSE_COMPLETE;
                    return;
                } else {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return;
                }
            } else {
                if (next == '\n') {
                    newLine = true;
                } else if (next != '\r' && next != ' ' && next != '\t') {
                    state.state = ParseState.HEADER;
                    state.leftOver = next;
                    return;
                }
            }
        }
        if (newLine) {
            state.leftOver = '\n';
        }
    }

    /**
     * This is a bit of hack to enable the parser to get access to the HttpString's that are sorted
     * in the static fields of the relevant classes. This means that in most cases a HttpString comparison
     * will take the fast path == route, as they will be the same object
     *
     * @return
     */
    protected static Map<String, HttpString> httpStrings() {
        final Map<String, HttpString> results = new HashMap<String, HttpString>();
        final Class[] classs = {Headers.class, Methods.class, Protocols.class};

        for (Class<?> c : classs) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType().equals(HttpString.class)) {
                    field.setAccessible(true);
                    HttpString result = null;
                    try {
                        result = (HttpString) field.get(null);
                        results.put(result.toString(), result);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return results;

    }

}
