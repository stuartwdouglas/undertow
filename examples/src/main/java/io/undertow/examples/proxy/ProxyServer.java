package io.undertow.examples.proxy;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.proxy.MCMPHandler;
import io.undertow.proxy.ProxyHandler;

/**
 * @author Jean-Frederic Clere
 */

@UndertowExample("Proxy Server")
public class ProxyServer {

    /* the address and port to receive the MCMP elements */
    static String chost = System.getProperty("io.undertow.examples.proxy.CADDRESS");
    static final int cport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy.CPORT", "6666"));

    /* the address and port to receive normal requests */
    static String phost = System.getProperty("io.undertow.examples.proxy.ADDRESS", "localhost");
    static final int pport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy.PORT", "8000"));

    public static void main(final String[] args) {
        Undertow server;
        try {
            if (chost == null) {
                // We are going to guess it.
                chost = java.net.InetAddress.getLocalHost().getHostName();
                System.out.println("Using: " + chost);
            }
            MCMPHandler mcmp = new MCMPHandler();
            ProxyHandler proxy = new ProxyHandler();
            mcmp.setChost(chost);
            mcmp.setCport(cport);
            mcmp.setProxy(proxy);
            server = Undertow.builder()
                    .addListener(cport, chost)
                    .addListener(pport, phost)
                    .addPathHandler("/", mcmp)
                    .build();
            server.start();
            mcmp.init();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
