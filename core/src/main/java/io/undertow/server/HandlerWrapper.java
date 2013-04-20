package io.undertow.server;

/**
 * Interface that can be used to wrap the handler chains during deployment.
 *
 * @author Stuart Douglas
 */
public interface HandlerWrapper {

    HttpHandler wrap(HttpHandler handler);

}
