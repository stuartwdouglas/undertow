package io.undertow.websockets.highlevel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface SendCallback {
    /**
     * Called once a send operation complete without an error
     */
    void onCompletion();

    /**
     * Called once an error was thrown during a send operation
    */
    void onError(Throwable cause);
}
