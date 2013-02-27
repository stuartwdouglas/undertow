package io.undertow.examples.proxy;

import io.undertow.Undertow;
import io.undertow.proxy.MCMPHandler;
// import io.undertow.proxy.ProxyHandler;

/**
 * @author Jean-Frederic Clere
 */
public class ProxyServer {

    public static void main(final String[] args) {
        Undertow server;
        try {
            server = Undertow.builder()
//                    .addListener(8000, "localhost")
                    .addListener(6666, "localhost")
//                    .addPathHandler("/", new ProxyHandler())
                    .addPathHandler("/", new MCMPHandler())
                    .build();
            server.start();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
