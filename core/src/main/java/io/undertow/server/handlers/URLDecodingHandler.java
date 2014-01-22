package io.undertow.server.handlers;

import java.util.Map;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ParameterMap;
import io.undertow.util.ParameterValues;
import io.undertow.util.URLUtils;

/**
 * A handler that will decode the URL and query parameters to the specified charset.
 * <p/>
 * If you are using this handler you must set the {@link io.undertow.UndertowOptions#DECODE_URL} parameter to false.
 * <p/>
 * This is not as efficient as using the parsers built in UTF-8 decoder. Unless you need to decode to something other
 * than UTF-8 you should rely on the parsers decoding instead.
 *
 * @author Stuart Douglas
 */
public class URLDecodingHandler implements HttpHandler {

    private final HttpHandler next;
    private final String charset;

    public URLDecodingHandler(final HttpHandler next, final String charset) {
        this.next = next;
        this.charset = charset;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        boolean decodeDone = exchange.getConnection().getUndertowOptions().get(UndertowOptions.DECODE_URL, true);
        if (!decodeDone) {
            final StringBuilder sb = new StringBuilder();
            final boolean decodeSlash = exchange.getConnection().getUndertowOptions().get(UndertowOptions.ALLOW_ENCODED_SLASH, false);
            exchange.setRequestPath(URLUtils.decode(exchange.getRequestPath(), charset, decodeSlash, sb));
            exchange.setRelativePath(URLUtils.decode(exchange.getRelativePath(), charset, decodeSlash, sb));
            exchange.setResolvedPath(URLUtils.decode(exchange.getResolvedPath(), charset, decodeSlash, sb));
            if (!exchange.getQueryString().isEmpty()) {
                final ParameterMap newParams = new ParameterMap();
                for (Map.Entry<String, ParameterValues> param : exchange.getQueryParameters().entrySet()) {
                    String key = URLUtils.decode(param.getKey(), charset, true, sb);
                    for (String val : param.getValue()) {
                        newParams.add(key, URLUtils.decode(val, charset, true, sb));
                    }
                }
                exchange.getQueryParameters().clear();
                exchange.getQueryParameters().putAll(newParams);
            }
        }
        next.handleRequest(exchange);
    }
}
