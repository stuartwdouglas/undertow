package io.undertow.server.handlers.security;

import io.undertow.UndertowMessages;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class AuthenticationMechanisms {

    public static void require(final Collection<Class<? extends Callback>> provided, final Class ... require) {
        final Set<Class<? extends Callback>> callbacks = new HashSet<Class<? extends Callback>>(Arrays.asList(require));
        for(Class<? extends Callback> c : provided) {
            callbacks.remove(c);
        }
        if(!callbacks.isEmpty()) {
            throw UndertowMessages.MESSAGES.missingAuthenticationMechanism(require);
        }
    }

}
