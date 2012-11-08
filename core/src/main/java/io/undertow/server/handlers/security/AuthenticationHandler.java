package io.undertow.server.handlers.security;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.util.Collection;

/**
 * @author Stuart Douglas
 */
public interface AuthenticationHandler {

    /**
     * Attempts to authenticate the request. If this returns true then authentication was sucessful
     * @param callbacks The callbacks. These contain user supplied authentication information
     * @return <code>true</code> if authentication was successful
     */
    boolean authenticate(final Collection<Callback> callbacks) throws UnsupportedCallbackException;

    /**
     *
     * @return A list of the callback types that this handler suppports
     */
    Collection<Class<? extends Callback>> getSupportedCallbacks();

    /**
     * Creates a collection of callback instances that can be used by this mechanism to authenticate.
     *
     * @return the callback instances
     */
    Collection<Callback> createCallbacks();
}
