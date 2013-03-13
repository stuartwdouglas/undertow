package io.undertow.proxy;

import io.undertow.client.HttpClient;
import io.undertow.client.HttpClientCallback;
import io.undertow.client.HttpClientConnection;
import io.undertow.client.HttpClientRequest;
import io.undertow.client.HttpClientResponse;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.proxy.container.Node;
import io.undertow.proxy.container.NodeService;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;

public class ProxyHandler implements HttpHandler {

    private NodeService nodeservice = null;

    /* Initialise the node provider to the xml one if there was none set before */
    public void init() throws Exception {
        if (getNodeservice() ==null)
            setNodeservice(new NodeService());
    }

    private static XnioWorker worker;
    private static OptionMap options;
    @Override
    public void handleRequest(HttpServerExchange exchange) {
        Map<String, Cookie> map = exchange.getAttachment(Cookie.REQUEST_COOKIES);
        String cookie = getNodeservice().getNodeByCookie(map);
        try {
            Node node = null;
            if (cookie != null) {
                // that should match a JVMRoute.
                node = getNodeservice().getNodeByCookie(cookie);
            } else {
                node = getNodeservice().getNode();
            }

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
                /* TODO that is a bit hacky */
                System.out.println("WORKER: " + getWorker());
                HttpClient client = HttpClient.create(getWorker(), options);
                SocketAddress addr = new InetSocketAddress(node.getHostname(), node.getPort());
                HttpClientCallback<HttpClientConnection> connectioncallback = createConnectionCallback(exchange);
                client.connect(addr, OptionMap.EMPTY, connectioncallback);
                return;
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

    private HttpClientCallback<HttpClientConnection> createConnectionCallback(final HttpServerExchange exchange) {
        return new HttpClientCallback<HttpClientConnection>() {

            @Override
            public void completed(HttpClientConnection connection) {
                System.out.println("ConnectionCallback done");
                HttpClientRequest request = null;
                try {
                    request = connection.createRequest(exchange.getRequestMethod().toString(), new URI(exchange.getRequestURI()));
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (URISyntaxException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                System.out.println("waiting from " + connection);
                final HttpClientCallback<HttpClientResponse> callback = createProxyCallback(exchange);
                try {
                    request.writeRequest(callback);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    System.out.println("Error in ConnectionCallback");
                    e.printStackTrace();
                }

                // TODO Auto-generated method stub

            }

            @Override
            public void failed(IOException e) {
                System.out.println("ConnectionCallback failed");
                // TODO Auto-generated method stub

            }

        };
    }

    /* MORE HACKS */
    /**
     * Handle the http response for the proxied request.
     *
     * @param result the http response
     * @param exchange the server exchange
     * @throws IOException
     */
    void handleProxyResult(final HttpClientResponse result, final HttpServerExchange exchange) throws IOException {
        final long contentLength = result.getContentLength();
        // Copy the headers for the server to the client
        for (HttpString name : result.getResponseHeaders()) {
            exchange.getResponseHeaders().addAll(name, result.getResponseHeaders().get(name));
        }
        if(contentLength >= 0L) {
            IoCallback callback = createProxyResponseCallback(result, exchange);
            // TODO Horrible blocking hack...
            ByteBuffer bitbuff = ByteBuffer.allocate(20000);
            result.readReplyBody().read(bitbuff);
            exchange.getResponseSender().send(bitbuff, callback);
            return;
        } else {
            exchange.endExchange();
        }
    }

    private IoCallback createProxyResponseCallback(HttpClientResponse result, HttpServerExchange exchange) {
        return new IoCallback() {

            @Override
            public void onComplete(HttpServerExchange exchange, Sender sender) {
                exchange.endExchange();
            }

            @Override
            public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                // TODO Auto-generated method stub

            }

        };
    }

    /**
     * Create a request completion callback to handle the proxied result.
     *
     * @param exchange the http server exchange
     * @return the callback
     */
    HttpClientCallback<HttpClientResponse> createProxyCallback(final HttpServerExchange exchange) {
        return new HttpClientCallback<HttpClientResponse>() {
            @Override
            public void completed(final HttpClientResponse result) {
                System.out.println("ProxyCallback done");
                try {
                    handleProxyResult(result, exchange);
                } catch (IOException e) {
                    failed(e);
                }
            }

            @Override
            public void failed(final IOException e) {
                System.out.println("ProxyCallback failed");
                e.printStackTrace();
                exchange.setResponseCode(500);
                exchange.setPersistent(false);
                exchange.endExchange();
            }
        };
    }
    /* END HACKS */
    public NodeService getNodeservice() {
        return nodeservice;
    }

    public void setNodeservice(NodeService nodeservice) {
        this.nodeservice = nodeservice;
    }

    public static XnioWorker getWorker() {
        return worker;
    }

    public static void setWorker(XnioWorker worker) {
        ProxyHandler.worker = worker;
    }

    public OptionMap getOptions() {
        return options;
    }

    public static void setOptions(OptionMap options) {
        ProxyHandler.options = options;
    }

}
