package io.undertow.ajp;

import java.nio.ByteBuffer;
import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.OpenListener;
import io.undertow.server.ResetableConduit;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.conduits.StreamSourceConduit;

/**
 * @author Stuart Douglas
 */
public class AjpOpenListener implements OpenListener {

    private final Pool<ByteBuffer> bufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;

    public AjpOpenListener(final Pool<ByteBuffer> pool, final int bufferSize) {
        this(pool, OptionMap.EMPTY, bufferSize);
    }

    public AjpOpenListener(final Pool<ByteBuffer> pool, final OptionMap undertowOptions, final int bufferSize) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
        this.bufferSize = bufferSize;
    }

    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }

        HttpServerConnection connection = new HttpServerConnection(channel, bufferPool, rootHandler, undertowOptions, bufferSize);

        final ResetableConduit[] resetableConduits = new ResetableConduit[2];
        AjpResponseConduit responseConduit = new AjpResponseConduit(channel.getSinkChannel().getConduit(), bufferPool);
        resetableConduits[0] = responseConduit;
        channel.getSinkChannel().setConduit(responseConduit);

        StreamSourceConduit streamSourceConduit = channel.getSourceChannel().getConduit();
        streamSourceConduit = new ReadDataStreamSourceConduit(streamSourceConduit, connection);
        AjpRequestConduit requestConduit = new AjpRequestConduit(streamSourceConduit, responseConduit);
        resetableConduits[1] = requestConduit;
        channel.getSourceChannel().setConduit(requestConduit);


        AjpReadListener readListener = new AjpReadListener(channel, connection, resetableConduits);
        readListener.startRequest();
    }

    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    public void setRootHandler(final HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
    }

    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    public void setUndertowOptions(final OptionMap undertowOptions) {
        if (undertowOptions == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("undertowOptions");
        }
        this.undertowOptions = undertowOptions;
    }
}
