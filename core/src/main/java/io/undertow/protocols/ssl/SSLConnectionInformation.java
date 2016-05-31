/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.protocols.ssl;

import javax.net.ssl.SNIServerName;
import java.util.List;

/**
 * Information about the SSL connection currently being built.
 */
public interface SSLConnectionInformation {
    /**
     * Get the SNI server names of this connection (if any)
     *
     * @return the SNI server names of this connection, or an empty list if there are none
     */
    List<SNIServerName> getSNIServerNames();

    /**
     *
     * @return The list of ALPN protocols, or null if ALPN was not present in the handshake
     */
    List<String> getAlpnProtocols();

    /**
     * Returns the record version of an SSL/TLS connection.
     *
     * @return the record version (not {@code null})
     */
    String getRecordVersion();

    /**
     * Returns the hello version of an SSL/TLS connection.
     *
     * @return the hello version (not {@code null})
     */
    String getHelloVersion();
}
