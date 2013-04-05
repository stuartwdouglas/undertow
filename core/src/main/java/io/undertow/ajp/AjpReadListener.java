package io.undertow.ajp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandlers;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResetableConduit;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.IoUtils.safeClose;

/**
 *
 * TODO----- FIX AND VERIFY AJP BEFORE THIS GOES TO UPSTREAM
 *
 *
 * @author Stuart Douglas
 */

final class AjpReadListener implements ChannelListener<StreamSourceChannel> , ExchangeCompletionListener, Runnable {

    private final StreamConnection channel;

    private AjpParseState state = new AjpParseState();
    private HttpServerExchange httpServerExchange;
    private final HttpServerConnection connection;
    private final ResetableConduit[] resetableConduitList;

    private HttpServerConnection.CurrentConduits conduitState;
    private volatile int read = 0;
    private final int maxRequestSize;

    AjpReadListener(final StreamConnection channel, final HttpServerConnection connection, final ResetableConduit[] resetableConduitList) {
        this.channel = channel;
        this.connection = connection;
        this.resetableConduitList = resetableConduitList;
        maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
    }

    public void handleEvent(final StreamSourceChannel channel) {
        Pooled<ByteBuffer> existing = connection.getExtraBytes();

        final Pooled<ByteBuffer> pooled = existing == null ? connection.getBufferPool().allocate() : existing;
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;

        try {
            int res;
            do {
                if (existing == null) {
                    buffer.clear();
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
                        }
                        safeClose(channel);
                        return;
                    }
                } else {
                    res = buffer.remaining();
                }
                if (res == 0) {
                    if (!channel.isReadResumed()) {
                        channel.getReadSetter().set(this);
                        channel.resumeReads();
                    }
                    return;
                }
                if (res == -1) {
                    try {
                        channel.shutdownReads();
                        final StreamSinkChannel responseChannel = this.channel.getSinkChannel();
                        responseChannel.shutdownWrites();
                        // will return false if there's a response queued ahead of this one, so we'll set up a listener then
                        if (!responseChannel.flush()) {
                            responseChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                            responseChannel.resumeWrites();
                        }
                    } catch (IOException e) {
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                        }
                        // fuck it, it's all ruined
                        IoUtils.safeClose(channel);
                        return;
                    }
                    return;
                }
                //TODO: we need to handle parse errors
                if (existing != null) {
                    existing = null;
                    connection.setExtraBytes(null);
                } else {
                    buffer.flip();
                }
                int begin = buffer.remaining();
                AjpParser.INSTANCE.parse(buffer, state, httpServerExchange);
                read += (begin - buffer.remaining());
                if (buffer.hasRemaining()) {
                    free = false;
                    connection.setExtraBytes(pooled);
                }
                if (read > maxRequestSize) {
                    UndertowLogger.REQUEST_LOGGER.requestHeaderWasTooLarge(connection.getPeerAddress(), maxRequestSize);
                    IoUtils.safeClose(connection);
                    return;
                }
            } while (!state.isComplete());

            // we remove ourselves as the read listener from the channel;
            // if the http handler doesn't set any then reads will suspend, which is the right thing to do
            channel.getReadSetter().set(null);
            channel.suspendReads();

            final HttpServerExchange httpServerExchange = this.httpServerExchange;
            httpServerExchange.putAttachment(UndertowOptions.ATTACHMENT_KEY, connection.getUndertowOptions());

            //httpServerExchange.addRequestWrapper(channelWrapper.getRequestWrapper());

            try {
                httpServerExchange.setRequestScheme(connection.getSslSession() != null ? "https" : "http"); //todo: determine if this is https
                state = null;
                this.httpServerExchange = null;
                for(ResetableConduit conduit : resetableConduitList) {
                    conduit.reset(httpServerExchange);
                }
                connection.revertConduitState(conduitState);
                HttpHandlers.executeRootHandler(connection.getRootHandler(), httpServerExchange, Thread.currentThread() instanceof XnioExecutor);

            } catch (Throwable t) {
                //TODO: we should attempt to return a 500 status code in this situation
                UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
                IoUtils.safeClose(channel);
                IoUtils.safeClose(connection);
            }
        } catch (Exception e) {
            UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
            IoUtils.safeClose(connection.getChannel());
        } finally {
            if (free) pooled.free();
        }
    }


    public void startRequest() {

        final HttpServerExchange oldExchange = this.httpServerExchange;
        HttpServerConnection connection = this.connection;
        conduitState = connection.revertToRawChannel();

        state = new AjpParseState();
        read = 0;
        HttpServerExchange exchange  = new HttpServerExchange(connection, connection.getChannel().getSinkChannel());
        this.httpServerExchange = exchange;
        exchange.addExchangeCompleteListener(this);

        if(oldExchange == null) {
            //only on the initial request, we just run the read listener directly
            handleEvent(connection.getChannel().getSourceChannel());
        } else if (oldExchange.isPersistent() && !oldExchange.isUpgrade()) {
            final StreamSourceChannel channel = connection.getChannel().getSourceChannel();
            if (connection.getExtraBytes() == null) {
                //if we are not pipelining we just register a listener
                channel.getReadSetter().set(this);
                channel.resumeReads();
            } else {
                if (channel.isReadResumed()) {
                    channel.suspendReads();
                }
                if (oldExchange.isInIoThread()) {
                    channel.getIoThread().execute(this);
                } else {
                    Executor executor = oldExchange.getDispatchExecutor();
                    if (executor == null) {
                        executor = connection.getWorker();
                    }
                    executor.execute(this);
                }
            }
        }
    }

    @Override
    public void run() {
        handleEvent(channel.getSourceChannel());
    }

    @Override
    public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
        startRequest();
        nextListener.proceed();
    }

}
