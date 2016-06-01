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

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import sun.security.ssl.ProtocolVersion;
import sun.security.ssl.SSLEngineImpl;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Hacks up ALPN support into the server hello message, then munges the SSLEngine internal state to make this work the way you would expect.
 * <p>
 * We only care about TLS 1.2, as TLS 1.1 is not allowed to use ALPN.
 * <p>
 * Super hacky, but slightly less hacky than modifying the boot class path
 */
final class SSLServerHelloALPNUpdater {

    // Private constructor prevents construction outside this class.
    private SSLServerHelloALPNUpdater() {
    }

    static final boolean ENABLED;
    private static final Field HANDSHAKER;
    private static final Field HANDSHAKER_PROTOCOL_VERSION;
    private static final Field HANDSHAKE_HASH;
    private static final Field HANDSHAKE_HASH_VERSION;
    private static final Method HANDSHAKE_HASH_UPDATE;
    private static final Method HANDSHAKE_HASH_PROTOCOL_DETERMINED;
    private static final Field HANDSHAKE_HASH_DATA;
    private static final Field HANDSHAKE_HASH_FIN_MD;


    static {

        boolean enabled = true;
        Field handshaker;
        Field handshakeHash;
        Field handshakeHashVersion;
        Field handshakeHashData;
        Field handshakeHashFinMd;
        Field protocolVersion;
        Method handshakeHashUpdate;
        Method handshakeHashProtocolDetermined;
        try {
            handshaker = SSLEngineImpl.class.getDeclaredField("handshaker");
            handshaker.setAccessible(true);
            handshakeHash = handshaker.getType().getDeclaredField("handshakeHash");
            handshakeHash.setAccessible(true);
            protocolVersion = handshaker.getType().getDeclaredField("protocolVersion");
            protocolVersion.setAccessible(true);
            handshakeHashVersion = handshakeHash.getType().getDeclaredField("version");
            handshakeHashVersion.setAccessible(true);
            handshakeHashUpdate = handshakeHash.getType().getDeclaredMethod("update", byte[].class, int.class, int.class);
            handshakeHashUpdate.setAccessible(true);
            handshakeHashProtocolDetermined = handshakeHash.getType().getDeclaredMethod("protocolDetermined", ProtocolVersion.class);
            handshakeHashProtocolDetermined.setAccessible(true);
            handshakeHashData = handshakeHash.getType().getDeclaredField("data");
            handshakeHashData.setAccessible(true);
            handshakeHashFinMd = handshakeHash.getType().getDeclaredField("finMD");
            handshakeHashFinMd.setAccessible(true);

        } catch (Exception e) {
            enabled = false;
            handshaker = null;
            handshakeHash = null;
            handshakeHashVersion = null;
            handshakeHashUpdate = null;
            handshakeHashProtocolDetermined = null;
            handshakeHashData = null;
            handshakeHashFinMd = null;
            protocolVersion = null;
        }
        ENABLED = enabled;
        HANDSHAKER = handshaker;
        HANDSHAKE_HASH = handshakeHash;
        HANDSHAKE_HASH_PROTOCOL_DETERMINED = handshakeHashProtocolDetermined;
        HANDSHAKE_HASH_VERSION = handshakeHashVersion;
        HANDSHAKE_HASH_UPDATE = handshakeHashUpdate;
        HANDSHAKE_HASH_DATA = handshakeHashData;
        HANDSHAKE_HASH_FIN_MD = handshakeHashFinMd;
        HANDSHAKER_PROTOCOL_VERSION = protocolVersion;
    }

    /**
     * The header size of TLS/SSL records.
     * <p>
     * The value of this constant is {@value}.
     */
    public static final int RECORD_HEADER_SIZE = 0x05;

    public static byte[] addAlpnExtensionsToServerHello(byte[] source, String selectedAlpnProtocol)
            throws SSLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer input = ByteBuffer.wrap(source);
        try {

            exploreHandshake(input, source.length, selectedAlpnProtocol, out);
            //we need to adjust the record length;
            int serverHelloLength = out.size() - 4;
            out.write(source, input.position(), input.remaining()); //there may be more messages (cert etc), so we append them
            byte[] data = out.toByteArray();

            //now we need to adjust the handshake frame length
            data[1] = (byte) ((serverHelloLength >> 16) & 0xFF);
            data[2] = (byte) ((serverHelloLength >> 8) & 0xFF);
            data[3] = (byte) (serverHelloLength & 0xFF);


            return data;
        } catch (AlpnPresentException e) {
            return source;
        }
    }

    /*
     * enum {
     *     hello_request(0), client_hello(1), server_hello(2),
     *     certificate(11), server_key_exchange (12),
     *     certificate_request(13), server_hello_done(14),
     *     certificate_verify(15), client_key_exchange(16),
     *     finished(20)
     *     (255)
     * } HandshakeType;
     *
     * struct {
     *     HandshakeType msg_type;
     *     uint24 length;
     *     select (HandshakeType) {
     *         case hello_request:       HelloRequest;
     *         case client_hello:        ClientHello;
     *         case server_hello:        ServerHello;
     *         case certificate:         Certificate;
     *         case server_key_exchange: ServerKeyExchange;
     *         case certificate_request: CertificateRequest;
     *         case server_hello_done:   ServerHelloDone;
     *         case certificate_verify:  CertificateVerify;
     *         case client_key_exchange: ClientKeyExchange;
     *         case finished:            Finished;
     *     } body;
     * } Handshake;
     */
    private static void exploreHandshake(
            ByteBuffer input, int recordLength, String selectedAlpnProtocol, ByteArrayOutputStream out) throws SSLException {

        // What is the handshake type?
        byte handshakeType = input.get();
        if (handshakeType != 0x02) {   // 0x01: server_hello message
            throw UndertowMessages.MESSAGES.expectedServerHello();
        }
        out.write(handshakeType);

        // What is the handshake body length?
        int handshakeLength = getInt24(input);
        out.write(0); //placeholders
        out.write(0);
        out.write(0);

        // Theoretically, a single handshake message might span multiple
        // records, but in practice this does not occur.
        if (handshakeLength > recordLength - 4) { // 4: handshake header size
            throw UndertowMessages.MESSAGES.multiRecordSSLHandshake();
        }
        int old = input.limit();
        input.limit(handshakeLength + input.position());
        exploreServerHello(input, selectedAlpnProtocol, out);
        input.limit(old);
    }

    /*
     * struct {
     *     uint32 gmt_unix_time;
     *     opaque random_bytes[28];
     * } Random;
     *
     * opaque SessionID<0..32>;
     *
     * uint8 CipherSuite[2];
     *
     * enum { null(0), (255) } CompressionMethod;
     *
     * struct {
     *    ProtocolVersion server_version;
     *    Random random;
     *    SessionID session_id;
     *    CipherSuite cipher_suite;
     *    CompressionMethod compression_method;
     *    select (extensions_present) {
     *        case false:
     *            struct {};
     *        case true:
     *            Extension extensions<0..2^16-1>;
     *    };
     *} ServerHello;
     */
    private static void exploreServerHello(
            ByteBuffer input, String selectedAlpnProtocol, ByteArrayOutputStream out) throws SSLException {

        // server version
        byte helloMajorVersion = input.get();
        byte helloMinorVersion = input.get();
        out.write(helloMajorVersion);
        out.write(helloMinorVersion);

        for (int i = 0; i < 32; ++i) { //the Random is 32 bytes
            out.write(input.get() & 0xFF);
        }

        // ignore session id
        processByteVector8(input, out);

        // ignore cipher_suite
        out.write(input.get() & 0xFF);
        out.write(input.get() & 0xFF);

        // ignore compression methods
        out.write(input.get() & 0xFF);

        boolean alpnPresent = false;
        ByteArrayOutputStream extensionsOutput = null;
        if (input.remaining() > 0) {
            extensionsOutput = new ByteArrayOutputStream();
            alpnPresent = exploreExtensions(input, extensionsOutput);
        }
        if (alpnPresent) {
            throw new AlpnPresentException();
        }

        ByteArrayOutputStream alpnBits = new ByteArrayOutputStream();
        alpnBits.write(0);
        alpnBits.write(16); //ALPN type
        int length = 3 + selectedAlpnProtocol.length(); //length of extension data
        alpnBits.write((length >> 8) & 0xFF);
        alpnBits.write(length & 0xFF);
        length -= 2;
        alpnBits.write((length >> 8) & 0xFF);
        alpnBits.write(length & 0xFF);
        alpnBits.write(selectedAlpnProtocol.length() & 0xFF);
        for (int i = 0; i < selectedAlpnProtocol.length(); ++i) {
            alpnBits.write(selectedAlpnProtocol.charAt(i) & 0xFF);
        }

        if (extensionsOutput != null) {
            byte[] existing = extensionsOutput.toByteArray();
            int newLength = existing.length - 2 + alpnBits.size();
            existing[0] = (byte) ((newLength >> 8) & 0xFF);
            existing[1] = (byte) (newLength & 0xFF);
            try {
                out.write(existing);
                out.write(alpnBits.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            int al = alpnBits.size();
            out.write((al >> 8) & 0xFF);
            out.write(al & 0xFF);
            try {
                out.write(alpnBits.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static List<ByteBuffer> extractRecords(ByteBuffer data) {
        List<ByteBuffer> ret = new ArrayList<>();
        while (data.hasRemaining()) {
            byte d1 = data.get();
            byte d2 = data.get();
            byte d3 = data.get();
            byte d4 = data.get();
            byte d5 = data.get();
            int length = (d4 & 0xFF) << 8 | d5 & 0xFF;
            byte[] b = new byte[length + 5];
            b[0] = d1;
            b[1] = d2;
            b[2] = d3;
            b[3] = d4;
            b[4] = d5;
            data.get(b, 5, length);
            ret.add(ByteBuffer.wrap(b));
        }
        return ret;
    }

    /*
     * struct {
     *     ExtensionType extension_type;
     *     opaque extension_data<0..2^16-1>;
     * } Extension;
     *
     * enum {
     *     server_name(0), max_fragment_length(1),
     *     client_certificate_url(2), trusted_ca_keys(3),
     *     truncated_hmac(4), status_request(5), (65535)
     * } ExtensionType;
     */
    private static boolean exploreExtensions(ByteBuffer input, ByteArrayOutputStream out)
            throws SSLException {
        boolean alpnPresent = false;

        int length = getInt16(input);           // length of extensions
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        while (length > 0) {
            int extType = getInt16(input);      // extenson type
            out.write((extType >> 8) & 0xFF);
            out.write(extType & 0xFF);
            int extLen = getInt16(input);       // length of extension data
            out.write((extLen >> 8) & 0xFF);
            out.write(extLen & 0xFF);
            if (extType == 16) {      // 0x00: type of server name indication
                alpnPresent = true;
            }
            processByteVector(input, extLen, out);
            length -= extLen + 4;
        }
        return alpnPresent;
    }

    private static int getInt8(ByteBuffer input) {
        return input.get();
    }

    private static int getInt16(ByteBuffer input) {
        return (input.get() & 0xFF) << 8 | input.get() & 0xFF;
    }

    private static int getInt24(ByteBuffer input) {
        return (input.get() & 0xFF) << 16 | (input.get() & 0xFF) << 8 |
                input.get() & 0xFF;
    }
    private static void processByteVector8(ByteBuffer input, ByteArrayOutputStream out) {
        int int8 = getInt8(input);
        out.write(int8 & 0xFF);
        processByteVector(input, int8, out);
    }

    private static void ignoreByteVector(ByteBuffer input, int length) {
        if (length != 0) {
            int position = input.position();
            input.position(position + length);
        }
    }

    private static void processByteVector(ByteBuffer input, int length, ByteArrayOutputStream out) {
        for (int i = 0; i < length; ++i) {
            out.write(input.get() & 0xFF);
        }
    }

    public static ALPNHackByteArrayOutputStream replaceByteOutput(SSLEngine sslEngine, String selectedAlpnProtocol) {
        try {
            Object handshaker = HANDSHAKER.get(sslEngine);
            Object hash = HANDSHAKE_HASH.get(handshaker);
            ByteArrayOutputStream existing = (ByteArrayOutputStream) HANDSHAKE_HASH_DATA.get(hash);

            ALPNHackByteArrayOutputStream out = new ALPNHackByteArrayOutputStream(sslEngine, existing.toByteArray(), selectedAlpnProtocol);
            HANDSHAKE_HASH_DATA.set(hash, out);
            return out;
        } catch (Exception e) {
            UndertowLogger.ROOT_LOGGER.debug("Failed to replace hash output stream ", e);
            return null;
        }
    }

    public static ByteBuffer createNewOutputData(byte[] newServerHello, List<ByteBuffer> records) {
        int length = newServerHello.length;
        length += 5; //Framing layer
        for (int i = 1; i < records.size(); ++i) {
            //the first record is the old server hello, so we start at 1 rather than zero
            ByteBuffer rec = records.get(i);
            length += rec.remaining();
        }
        byte[] newData = new byte[length];
        ByteBuffer ret = ByteBuffer.wrap(newData);
        ByteBuffer oldHello = records.get(0);
        ret.put(oldHello.get()); //type
        ret.put(oldHello.get()); //major
        ret.put(oldHello.get()); //minor
        ret.put((byte) ((newServerHello.length >> 8) & 0xFF));
        ret.put((byte) (newServerHello.length & 0xFF));
        ret.put(newServerHello);
        for (int i = 1; i < records.size(); ++i) {
            ByteBuffer rec = records.get(i);
            ret.put(rec);
        }
        ret.flip();
        return ret;
    }

    public static void regenerateHashes(SSLEngine sslEngineToHack, ByteArrayOutputStream data, byte[]... hashBytes) {
        //hack up the SSL engine internal state
        try {
            Object handshaker = HANDSHAKER.get(sslEngineToHack);
            Object hash = HANDSHAKE_HASH.get(handshaker);
            data.reset();
            ProtocolVersion protocolVersion = (ProtocolVersion) HANDSHAKER_PROTOCOL_VERSION.get(handshaker);
            HANDSHAKE_HASH_VERSION.set(hash, -1);
            HANDSHAKE_HASH_PROTOCOL_DETERMINED.invoke(hash, protocolVersion);
            MessageDigest digest = (MessageDigest) HANDSHAKE_HASH_FIN_MD.get(hash);
            digest.reset();
            for (byte[] b : hashBytes) {
                HANDSHAKE_HASH_UPDATE.invoke(hash, b, 0, b.length);
            }
        } catch (Exception e) {
            e.printStackTrace(); //TODO: remove
            throw new RuntimeException(e);
        }
    }

    private static final class AlpnPresentException extends RuntimeException {

    }
}

