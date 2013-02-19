package io.undertow.examples.proxy;

import io.undertow.Undertow;
import io.undertow.proxy.ProxyHandler;

/**
 * @author Jean-Frederic Clere
 */
public class ProxyServer {

    public static void main(final String[] args) {
        Undertow server = Undertow.builder().addListener(8080, "localhost").addPathHandler("/", new ProxyHandler()).build();
        server.start();
    }

}
