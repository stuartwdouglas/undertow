package io.undertow.websockets.highlevel;

/**
 * @author Stuart Douglas
 */
public interface FragmentedSender {

    /**
     * Calling this method marks the next frame to be sent as the final
     * frame for this fragmented message.
     *
     * Attempting to use this sender after the last message has been sent will
     * result an an {@link IllegalStateException}.
     *
     */
    void finalFragment();
}
