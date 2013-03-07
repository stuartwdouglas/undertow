package io.undertow.proxy;

import io.undertow.proxy.container.Node;
import io.undertow.proxy.container.NodeService;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.xnio.channels.StreamSinkChannel;

public class ProxyHandler implements HttpHandler {

    private NodeService nodeservice = null;

    /* Initialise the node provider to the xml one if there was none set before */
    public void init() throws Exception {
        if (getNodeservice() ==null)
            setNodeservice(new NodeService());
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        HeaderMap headers = exchange.getRequestHeaders();
        List<String> head = headers.get(Headers.COOKIE);
        String cooky = null;
        if (head != null)
            cooky = head.toString();
        System.out.println("Cookie:" + cooky);
        try {
            Node node = getNodeservice().getNodeByCookie(head);
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
                StreamSinkChannel resp = exchange.getResponseChannel();

                ByteBuffer bb = ByteBuffer.allocate(100);
                bb.put("OK".getBytes());
                bb.flip();

                resp.write(bb);
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

    public NodeService getNodeservice() {
        return nodeservice;
    }

    public void setNodeservice(NodeService nodeservice) {
        this.nodeservice = nodeservice;
    }
}
