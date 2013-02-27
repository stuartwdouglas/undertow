package io.undertow.proxy;

import java.io.IOException;
import java.net.SocketAddress;

import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedStreamChannel;

import io.undertow.client.HttpClient;
import io.undertow.client.HttpClientConnection;
import io.undertow.client.HttpClientRequest;

public class ProxyClient extends HttpClient {

    protected ProxyClient(XnioWorker worker) {
        super(worker);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public IoFuture<HttpClientConnection> connect(SocketAddress destination, OptionMap optionMap) {
        ChannelListener<? super ConnectedStreamChannel> openListener = null;
        // TODO Auto-generated method stub
        IoFuture<ConnectedStreamChannel> connection = getWorker().connectStream(destination, openListener, optionMap);
        return null;
    }

    @Override
    public IoFuture<HttpClientRequest> sendRequest(String method, String requestUri, OptionMap optionMap) {
        // TODO Auto-generated method stub
        return null;
    }

}
