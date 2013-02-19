package io.undertow.proxy;

import java.io.IOException;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;

public class ProxyCallBack  implements IoCallback {

    @Override
    public void onComplete(HttpServerExchange exchange, Sender sender) {
        System.out.println("onComplete");
    }

    @Override
    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
        System.out.println("onException: " + exception);
    }

}