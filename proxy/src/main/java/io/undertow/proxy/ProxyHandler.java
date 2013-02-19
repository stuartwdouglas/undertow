package io.undertow.proxy;

import io.undertow.proxy.container.Node;
import io.undertow.proxy.container.NodeService;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

import java.util.Deque;

public class ProxyHandler implements HttpHandler {

    static NodeService nodeservice = new NodeService();

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        HeaderMap headers = exchange.getRequestHeaders();
        Deque<String> head = headers.get(Headers.COOKIE);
        System.out.println("Cookie:" + head);
        Node node = nodeservice.getNode(head.toString());
    }

}
