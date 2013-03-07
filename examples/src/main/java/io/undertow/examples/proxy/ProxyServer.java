package io.undertow.examples.proxy;

import io.undertow.Undertow;
import io.undertow.proxy.MCMPHandler;
// import io.undertow.proxy.ProxyHandler;

/**
 * @author Jean-Frederic Clere
 */

public class ProxyServer {

    static String chost = System.getProperty("io.undertow.examples.proxy.ADDRESS");
    static final int cport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy", "6666"));


    public static void main(final String[] args) {
        Undertow server;
        try {
            if (chost == null) {
                // We are going to guess it.
                chost = java.net.InetAddress.getLocalHost().getHostName();
                System.out.println("Using: " + chost);
            }
            MCMPHandler handler = new MCMPHandler();
            handler.setChost(chost);
            handler.setCport(cport);
            server = Undertow.builder()
                    .addListener(cport, chost)
                    .addPathHandler("/", handler)
                    .build();
            server.start();
            handler.init();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
