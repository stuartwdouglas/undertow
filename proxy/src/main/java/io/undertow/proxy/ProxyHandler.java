package io.undertow.proxy;

import io.undertow.io.Sender;
import io.undertow.proxy.container.Node;
import io.undertow.proxy.container.NodeService;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;

import org.xnio.channels.StreamSinkChannel;

public class ProxyHandler implements HttpHandler {

    static NodeService nodeservice = new NodeService();
    static ProxyCallBack callback = new ProxyCallBack();

    public ProxyHandler() throws Exception {
        nodeservice.init();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        HeaderMap headers = exchange.getRequestHeaders();
        Deque<String> head = headers.get(Headers.COOKIE);
        String cooky = null;
        if (head != null)
            cooky = head.toString();
        System.out.println("Cookie:" + cooky);
        try {
            Node node = nodeservice.getNode(cooky);
            System.out.println("Node:" + node);
            if (node==null) {
                exchange.setResponseCode(503);
                StreamSinkChannel resp = exchange.getResponseChannel();

                ByteBuffer bb = ByteBuffer.allocate(100);
                bb.put("No node to process request".getBytes());
                bb.flip();

                resp.write(bb);
                exchange.endExchange();
                return;
            } else {
                System.out.println("Writing OK");
                exchange.setResponseCode(200);
                Sender resp = exchange.getResponseSender();

                ByteBuffer bb = ByteBuffer.allocate(100);
                bb.put("OK".getBytes());
                bb.flip();

                resp.send(bb, callback);
                exchange.endExchange();
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.setResponseCode(500);
            StreamSinkChannel resp = exchange.getResponseChannel();

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.put(e.toString().getBytes());
            bb.flip();

            try {
                resp.write(bb);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            exchange.endExchange();
            return;
        }
    }
}
