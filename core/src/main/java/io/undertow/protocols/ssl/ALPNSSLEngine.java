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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/**
 * SSLEngine wrapper that provides some super hacky ALPN support on JDK8.
 *
 * Even though this is a nasty hack that relies on JDK internals it is still preferable to modifying the boot class path.
 *
 * It is expected to work with all JDK8 versions, however this cannot be guaranteed if the SSL internals are changed
 * in an incompatible way.
 *
 * This class will go away once JDK8 is no longer in use.
 *
 * @author Stuart Douglas
 */
public class ALPNSSLEngine extends SSLEngine {

    public static final boolean ENABLED = SSLServerHelloALPNUpdater.ENABLED && !Boolean.getBoolean("io.undertow.disable-jdk8-alpn");

    private final SSLEngine delegate;

    //ALPN Hack specific variables
    private byte[] clientHelloRecord; //needed to regenerate a handhsake
    private boolean clientHelloExplored = false;
    private boolean serverHelloSent = false;
    private ALPNHackByteArrayOutputStream alpnHackByteArrayOutputStream;
    private Set<String> applicationProtocols;
    private String selectedApplicationProtocol;
    private ByteBuffer bufferedWrapData;

    public ALPNSSLEngine(SSLEngine delegate) {
        this.delegate = delegate;
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] byteBuffers, int i, int i1, ByteBuffer byteBuffer) throws SSLException {
        if(bufferedWrapData != null) {
            int prod = bufferedWrapData.remaining();
            byteBuffer.put(bufferedWrapData);
            bufferedWrapData = null;
            return new SSLEngineResult(SSLEngineResult.Status.OK, SSLEngineResult.HandshakeStatus.NEED_WRAP, 0, prod);
        }
        int pos = byteBuffer.position();
        int limit = byteBuffer.limit();
        SSLEngineResult res =  delegate.wrap(byteBuffers, i, i1, byteBuffer);
        if(!delegate.getUseClientMode() && selectedApplicationProtocol != null && !serverHelloSent && alpnHackByteArrayOutputStream != null) {
            byteBuffer.flip();
            byte[] newServerHello = alpnHackByteArrayOutputStream.getServerHello(); //this is the new server hello, it will be part of the first TLS plaintext record
            if(newServerHello != null) {
                List<ByteBuffer> records = SSLServerHelloALPNUpdater.extractRecords(byteBuffer);
                ByteBuffer newData  = SSLServerHelloALPNUpdater.createNewOutputData(newServerHello, records);
                byteBuffer.position(pos); //erase the data
                byteBuffer.limit(limit);
                if(newData.remaining() > byteBuffer.remaining()) {
                    int old = newData.limit();
                    newData.limit(newData.position() + byteBuffer.remaining());
                    res = new SSLEngineResult(res.getStatus(), res.getHandshakeStatus(), res.bytesConsumed(), newData.remaining());
                    byteBuffer.put(newData);
                    newData.limit(old);
                    bufferedWrapData = newData;
                } else {
                    res = new SSLEngineResult(res.getStatus(), res.getHandshakeStatus(), res.bytesConsumed(), newData.remaining());
                    byteBuffer.put(newData);
                }
            }
        }
        if(res.bytesProduced() > 0) {
            serverHelloSent = true;
        }
        return res;
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer dataToUnwrap, ByteBuffer[] byteBuffers, int i, int i1) throws SSLException {
        if(!clientHelloExplored && !delegate.getUseClientMode() && applicationProtocols != null) {
            try {
                SSLConnectionInformation result = SSLClientHelloExplorer.exploreClientHello(dataToUnwrap.duplicate());
                if(result.getAlpnProtocols() != null) {
                    for(String protocol : result.getAlpnProtocols()) {
                        if(applicationProtocols.contains(protocol)) {
                            selectedApplicationProtocol = protocol;
                            break;
                        }
                    }
                }
                clientHelloRecord = new byte[dataToUnwrap.remaining()];
                dataToUnwrap.duplicate().get(clientHelloRecord);
                clientHelloExplored = true;
            } catch (BufferUnderflowException e) {
                return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, SSLEngineResult.HandshakeStatus.NEED_UNWRAP, 0, 0);
            }
        }
        SSLEngineResult res = delegate.unwrap(dataToUnwrap, byteBuffers, i, i1);
        if(selectedApplicationProtocol != null && alpnHackByteArrayOutputStream == null) {
            alpnHackByteArrayOutputStream = SSLServerHelloALPNUpdater.replaceByteOutput(delegate, selectedApplicationProtocol);
        }
        return res;
    }

    @Override
    public Runnable getDelegatedTask() {
        return delegate.getDelegatedTask();
    }

    @Override
    public void closeInbound() throws SSLException {
        delegate.closeInbound();
    }

    @Override
    public boolean isInboundDone() {
        return delegate.isInboundDone();
    }

    @Override
    public void closeOutbound() {
        delegate.closeOutbound();
    }

    @Override
    public boolean isOutboundDone() {
        return delegate.isOutboundDone();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return delegate.getEnabledCipherSuites();
    }

    @Override
    public void setEnabledCipherSuites(String[] strings) {
        delegate.setEnabledCipherSuites(strings);
    }

    @Override
    public String[] getSupportedProtocols() {
        return delegate.getSupportedProtocols();
    }

    @Override
    public String[] getEnabledProtocols() {
        return delegate.getEnabledProtocols();
    }

    @Override
    public void setEnabledProtocols(String[] strings) {
        delegate.setEnabledProtocols(strings);
    }

    @Override
    public SSLSession getSession() {
        return delegate.getSession();
    }

    @Override
    public void beginHandshake() throws SSLException {
        delegate.beginHandshake();
    }

    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return delegate.getHandshakeStatus();
    }

    @Override
    public void setUseClientMode(boolean b) {
        delegate.setUseClientMode(b);
    }

    @Override
    public boolean getUseClientMode() {
        return delegate.getUseClientMode();
    }

    @Override
    public void setNeedClientAuth(boolean b) {
        delegate.setNeedClientAuth(b);
    }

    @Override
    public boolean getNeedClientAuth() {
        return delegate.getNeedClientAuth();
    }

    @Override
    public void setWantClientAuth(boolean b) {
        delegate.setWantClientAuth(b);
    }

    @Override
    public boolean getWantClientAuth() {
        return delegate.getWantClientAuth();
    }

    @Override
    public void setEnableSessionCreation(boolean b) {
        delegate.setEnableSessionCreation(b);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return delegate.getEnableSessionCreation();
    }

    /**
     * JDK8 ALPN hack support method.
     *
     * These methods will be removed once JDK8 ALPN support is no longer required
     * @param applicationProtocols
     */
    public void setApplicationProtocols(Set<String> applicationProtocols) {
        this.applicationProtocols = applicationProtocols;
    }

    /**
     * JDK8 ALPN hack support method.
     *
     * These methods will be removed once JDK8 ALPN support is no longer required
     */
    public Set<String> getApplicationProtocols() {
        return applicationProtocols;
    }

    /**
     * JDK8 ALPN hack support method.
     *
     * These methods will be removed once JDK8 ALPN support is no longer required
     */
    public String getSelectedApplicationProtocol() {
        return selectedApplicationProtocol;
    }
}
