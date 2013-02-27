package io.undertow.proxy;

import java.io.IOException;
import java.net.URI;

import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.channels.ConnectedStreamChannel;

import io.undertow.client.HttpClient;
import io.undertow.client.HttpClientConnection;
import io.undertow.client.HttpClientRequest;

public class ProxyClientConnection extends HttpClientConnection {

    protected ProxyClientConnection(HttpClient client) {
        super(client);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public HttpClientRequest sendRequest(String method, URI target) {
        getClient().sendRequest(method, target.getPath(), OptionMap.EMPTY);
        return null;
    }

    @Override
    public IoFuture<ConnectedStreamChannel> upgradeToWebSocket(String service, OptionMap optionMap) {
        // TODO Auto-generated method stub
        return null;
    }

}
