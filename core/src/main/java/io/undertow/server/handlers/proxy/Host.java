package io.undertow.server.handlers.proxy;

import io.undertow.UndertowMessages;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioIoThread;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
class Host {

    private final LoadBalancingProxyClient loadBalancingProxyClient;

    private final URI uri;


    private final UndertowClient client;

    /**
     * flag that is set when a problem is detected with this host. It will be taken out of consideration
     * until the flag is cleared.
     * <p/>
     * The exception to this is if all flags are marked as problems, in which case it will be tried anyway
     */
    private volatile boolean problem;

    /**
     * Set to true when the host is removed from this load balancer
     */
    private volatile boolean closed;

    private final ConcurrentMap<XnioIoThread, HostThreadData> hostThreadData = new ConcurrentHashMap<XnioIoThread, HostThreadData>();

    public Host(LoadBalancingProxyClient loadBalancingProxyClient, URI uri, UndertowClient client) {
        this.loadBalancingProxyClient = loadBalancingProxyClient;
        this.uri = uri;
        this.client = client;
    }

    URI getUri() {
        return uri;
    }

    void close() {
        this.closed = true;
    }

    /**
     * Called when the IO thread has completed a successful request
     *
     * @param connection The client connection
     */
    void returnConnection(final ClientConnection connection) {
        HostThreadData hostData = getData();
        if (closed) {
            //the host has been closed
            IoUtils.safeClose(connection);
            ClientConnection con = hostData.availbleConnections.poll();
            while (con != null) {
                IoUtils.safeClose(con);
                con = hostData.availbleConnections.poll();
            }
            CallbackHolder callback = hostData.awaitingConnections.poll();
            while (callback != null) {
                callback.getCallback().failed(callback.getExchange());
                callback = hostData.awaitingConnections.poll();
            }
            return;
        }

        if (connection.isOpen()) {
            CallbackHolder callback = hostData.awaitingConnections.poll();
            if (callback != null) {
                connectionReady(connection, callback.callback, callback.exchange);
            } else {
                hostData.availbleConnections.add(connection);
            }
        } else {
            int connections = --hostData.connections;
            if (connections < loadBalancingProxyClient.getConnectionsPerThread()) {
                CallbackHolder task = hostData.awaitingConnections.poll();
                if(task != null) {
                    openConnection(task.exchange, task.callback);
                }
            }
        }
    }

    private void openConnection(final HttpServerExchange exchange, final ProxyCallback<ClientConnection> callback) {
        client.connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(final ClientConnection result) {
                problem = false;
                connectionReady(result, callback, exchange);
            }

            @Override
            public void failed(IOException e) {
                problem = true;
                scheduleFailedHostRetry(exchange);
                callback.failed(exchange);


            }
        }, getUri(), exchange.getIoThread(), exchange.getConnection().getBufferPool(), OptionMap.EMPTY);
    }

    private void connectionReady(final ClientConnection result, final ProxyCallback<ClientConnection> callback, final HttpServerExchange exchange) {
        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                returnConnection(result);
                nextListener.proceed();
            }
        });
        callback.completed(exchange, result);
    }

    AvailabilityType availible() {
        if (closed) {
            return AvailabilityType.CLOSED;
        }
        if (problem) {
            return AvailabilityType.PROBLEM;
        }
        HostThreadData data = getData();
        if (data.connections < loadBalancingProxyClient.getConnectionsPerThread()) {
            return AvailabilityType.AVAILABLE;
        }
        if (!data.availbleConnections.isEmpty()) {
            return AvailabilityType.AVAILABLE;
        }
        return AvailabilityType.FULL;
    }

    /**
     * If a host fails we periodically retry
     *
     * @param exchange The server exchange
     */
    private void scheduleFailedHostRetry(final HttpServerExchange exchange) {
        exchange.getIoThread().executeAfter(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                client.connect(new ClientCallback<ClientConnection>() {
                    @Override
                    public void completed(ClientConnection result) {
                        problem = false;
                        returnConnection(result);
                    }

                    @Override
                    public void failed(IOException e) {
                        scheduleFailedHostRetry(exchange);
                    }
                }, getUri(), exchange.getIoThread(), exchange.getConnection().getBufferPool(), OptionMap.EMPTY);
            }
        }, loadBalancingProxyClient.getProblemServerRetry(), TimeUnit.SECONDS);
    }

    /**
     * Gets the host data for this thread
     *
     * @return The data for this thread
     */
    private HostThreadData getData() {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof XnioIoThread)) {
            throw UndertowMessages.MESSAGES.canOnlyBeCalledByIoThread();
        }
        XnioIoThread ioThread = (XnioIoThread) thread;
        HostThreadData data = hostThreadData.get(ioThread);
        if (data != null) {
            return data;
        }
        data = new HostThreadData();
        HostThreadData existing = hostThreadData.putIfAbsent(ioThread, data);
        if (existing != null) {
            return existing;
        }
        return data;
    }

    public void connect(HttpServerExchange exchange, ProxyCallback<ClientConnection> callback) {
        HostThreadData data = getData();
        ClientConnection conn = data.availbleConnections.poll();
        if(conn != null) {
            connectionReady(conn, callback, exchange);
        } else if(data.connections < loadBalancingProxyClient.getConnectionsPerThread()){
            openConnection(exchange, callback);
        } else {
            data.awaitingConnections.add(new CallbackHolder(callback, exchange));
        }
    }

    private static final class HostThreadData {

        int connections = 0;
        final Deque<ClientConnection> availbleConnections = new ArrayDeque<ClientConnection>();
        final Deque<CallbackHolder> awaitingConnections = new ArrayDeque<CallbackHolder>();

    }


    private static final class CallbackHolder {
        final ProxyCallback<ClientConnection> callback;
        final HttpServerExchange exchange;

        private CallbackHolder(ProxyCallback<ClientConnection> callback, HttpServerExchange exchange) {
            this.callback = callback;
            this.exchange = exchange;
        }

        private ProxyCallback<ClientConnection> getCallback() {
            return callback;
        }

        private HttpServerExchange getExchange() {
            return exchange;
        }
    }

    enum AvailabilityType {
        /**
         * The host is read to accept requests
         */
        AVAILABLE,
        /**
         * All connections are in use, connections will be queued
         */
        FULL,
        /**
         * The host is probably down, only try as a last resort
         */
        PROBLEM,
        /**
         * The host is closed. connections will always fail
         */
        CLOSED;
    }
}
