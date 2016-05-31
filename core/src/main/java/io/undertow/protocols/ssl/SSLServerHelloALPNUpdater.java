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

import io.undertow.UndertowMessages;
import io.undertow.connector.PooledByteBuffer;
import sun.security.ssl.ProtocolVersion;
import sun.security.ssl.SSLEngineImpl;

import javax.crypto.SecretKey;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Hacks up ALPN support into the server hello message, then munges the SSLEngine internal state to make this work the way you would expect.
 *
 * We only care about TLS 1.2, as TLS 1.1 is not allowed to use ALPN.
 *
 * Super hacky, but slightly less hacky than modifying the boot class path
 */
final class SSLServerHelloALPNUpdater {

    // Private constructor prevents construction outside this class.
    private SSLServerHelloALPNUpdater() {
    }

    public static final boolean ENABLED;
    private static final Field HANDSHAKER;
    private static final Field HANDSHAKER_PROTOCOL_VERSION;
    private static final Field HANDSHAKE_HASH;
    private static final Field HANDSHAKE_SESSION;
    private static final Field HANDSHAKE_HASH_VERSION;
    private static final Method HANDSHAKE_HASH_UPDATE;
    private static final Method HANDSHAKE_HASH_PROTOCOL_DETERMINED;
    private static final Method SESSION_MASTER_SECRET;
    private static final Field HANDSHAKE_HASH_DATA;
    private static final Field HANDSHAKE_HASH_FIN_MD;
    private static final Field HANDSHAKE_CIPHER_SUITE;
    private static final Constructor FINISHED_MESSAGE_CONSTRUCTOR;


    static {

        boolean enabled = true;
        Field handshaker = null;
        Field handshakeHash = null;
        Field handshakeHashVersion = null;
        Field handshakeHashData = null;
        Field handshakeHashFinMd = null;
        Field handshakeCipherSuite = null;
        Field protocolVersion = null;
        Field handshakeSession;
        Method handshakeHashUpdate = null;
        Method handshakeHashProtocolDetermined = null;
        Method sessionMasterSecret = null;
        Constructor finishedMessageConstructor = null;
        try {
            handshaker = SSLEngineImpl.class.getDeclaredField("handshaker");
            handshaker.setAccessible(true);
            handshakeHash = handshaker.getType().getDeclaredField("handshakeHash");
            handshakeHash.setAccessible(true);
            handshakeSession = handshaker.getType().getDeclaredField("session");
            handshakeSession.setAccessible(true);
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
            handshakeCipherSuite = handshaker.getType().getDeclaredField("cipherSuite");
            handshakeCipherSuite.setAccessible(true);

            Class<?> finishedClass = Class.forName("sun.security.ssl.HandshakeMessage$Finished", true, ProtocolVersion.class.getClassLoader());
            finishedMessageConstructor = finishedClass.getDeclaredConstructor(ProtocolVersion.class, handshakeHash.getType(), int.class, SecretKey.class, handshakeCipherSuite.getType());

            Class<?> sessionImpl = Class.forName("sun.security.ssl.SSLSessionImpl", true, ProtocolVersion.class.getClassLoader());
            sessionMasterSecret = sessionImpl.getDeclaredMethod("getMasterSecret");
            sessionMasterSecret.setAccessible(true);
        } catch (Exception e) {
            enabled = false;
            handshaker = null;
            handshakeHash = null;
            handshakeHashVersion = null;
            handshakeHashUpdate = null;
            handshakeHashProtocolDetermined = null;
            handshakeHashData = null;
            handshakeHashFinMd = null;
            handshakeCipherSuite = null;
            finishedMessageConstructor = null;
            sessionMasterSecret = null;
            protocolVersion = null;
            handshakeSession = null;
        }
        ENABLED = enabled;
        HANDSHAKER = handshaker;
        HANDSHAKE_HASH = handshakeHash;
        HANDSHAKE_HASH_PROTOCOL_DETERMINED = handshakeHashProtocolDetermined;
        HANDSHAKE_HASH_VERSION = handshakeHashVersion;
        HANDSHAKE_HASH_UPDATE = handshakeHashUpdate;
        HANDSHAKE_HASH_DATA = handshakeHashData;
        HANDSHAKE_HASH_FIN_MD = handshakeHashFinMd;
        HANDSHAKE_CIPHER_SUITE = handshakeCipherSuite;
        FINISHED_MESSAGE_CONSTRUCTOR = finishedMessageConstructor;
        HANDSHAKER_PROTOCOL_VERSION = protocolVersion;
        SESSION_MASTER_SECRET = sessionMasterSecret;
        HANDSHAKE_SESSION = handshakeSession;
    }

    /**
     * The header size of TLS/SSL records.
     * <P>
     * The value of this constant is {@value}.
     */
    public static final int RECORD_HEADER_SIZE = 0x05;

    /**
     * Returns the required number of bytes in the {@code source}
     * {@link ByteBuffer} necessary to explore SSL/TLS connection.
     * <P>
     * This method tries to parse as few bytes as possible from
     * {@code source} byte buffer to get the length of an
     * SSL/TLS record.
     * <P>
     * This method accesses the {@code source} parameter in read-only
     * mode, and does not update the buffer's properties such as capacity,
     * limit, position, and mark values.
     *
     * @param  source
     *         a {@link ByteBuffer} containing
     *         inbound or outbound network data for an SSL/TLS connection.
     * @throws BufferUnderflowException if less than {@code RECORD_HEADER_SIZE}
     *         bytes remaining in {@code source}
     * @return the required size in byte to explore an SSL/TLS connection
     */
    public static int getRequiredSize(ByteBuffer source) {

        ByteBuffer input = source.duplicate();

        // Do we have a complete header?
        if (input.remaining() < RECORD_HEADER_SIZE) {
            throw new BufferUnderflowException();
        }

        // Is it a handshake message?
        byte firstByte = input.get();
        byte secondByte = input.get();
        byte thirdByte = input.get();
        if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
            // looks like a V2ClientHello
            // return (((firstByte & 0x7F) << 8) | (secondByte & 0xFF)) + 2;
            return RECORD_HEADER_SIZE;   // Only need the header fields
        } else {
            return ((input.get() & 0xFF) << 8 | input.get() & 0xFF) + 5;
        }
    }

    public static PooledByteBuffer exploreServerHello(ByteBuffer source, String selectedAlpnProtocol, SSLEngine sslEngineToHack, byte[] clientHelloRecord)
            throws SSLException {

        ByteBuffer input = source.duplicate();

        // Do we have a complete header?
        if (input.remaining() < RECORD_HEADER_SIZE) {
            throw new BufferUnderflowException();
        }

        // Is it a handshake message?
        byte firstByte = input.get();
        byte secondByte = input.get();
        byte thirdByte = input.get();
        if (firstByte == 22) {   // 22: handshake record
            return exploreTLSRecord(input,
                                    firstByte, secondByte, thirdByte, selectedAlpnProtocol, sslEngineToHack, source.duplicate(), clientHelloRecord);
        } else {
            throw UndertowMessages.MESSAGES.notHandshakeRecord();
        }
    }

    /*
     * struct {
     *     uint8 major;
     *     uint8 minor;
     * } ProtocolVersion;
     *
     * enum {
     *     change_cipher_spec(20), alert(21), handshake(22),
     *     application_data(23), (255)
     * } ContentType;
     *
     * struct {
     *     ContentType type;
     *     ProtocolVersion version;
     *     uint16 length;
     *     opaque fragment[TLSPlaintext.length];
     * } TLSPlaintext;
     */
    private static PooledByteBuffer exploreTLSRecord(
            ByteBuffer input, byte firstByte, byte secondByte,
            byte thirdByte, String selectedAlpnProtocol, SSLEngine sslEngineToHack, ByteBuffer original, byte[] clientHelloRecord) throws SSLException {

        // Is it a handshake message?
        if (firstByte != 22) {        // 22: handshake record
            throw UndertowMessages.MESSAGES.notHandshakeRecord();
        }

        // Is there enough data for a full record?
        int recordLength = getInt16(input);
        if (recordLength > input.remaining()) {
            throw new BufferUnderflowException();
        }

        // We have already had enough source bytes.
        try {
            return exploreHandshake(input,
                secondByte, thirdByte, recordLength, selectedAlpnProtocol, sslEngineToHack, original, clientHelloRecord);
        } catch (BufferUnderflowException ignored) {
            throw UndertowMessages.MESSAGES.invalidHandshakeRecord();
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
    private static PooledByteBuffer exploreHandshake(
            ByteBuffer input, byte recordMajorVersion,
            byte recordMinorVersion, int recordLength, String selectedAlpnProtocol, SSLEngine sslEngineToHack, ByteBuffer original, byte[] clientHelloRecord) throws SSLException {

        // What is the handshake type?
        byte handshakeType = input.get();
        if (handshakeType != 0x02) {   // 0x01: server_hello message
            throw UndertowMessages.MESSAGES.expectedClientHello();
        }

        // What is the handshake body length?
        int handshakeLength = getInt24(input);

        // Theoretically, a single handshake message might span multiple
        // records, but in practice this does not occur.
        if (handshakeLength > recordLength - 4) { // 4: handshake header size
            throw UndertowMessages.MESSAGES.multiRecordSSLHandshake();
        }

        input = input.duplicate();
        input.limit(handshakeLength + input.position());
        return exploreServerHello(input,
                                    recordMajorVersion, recordMinorVersion, selectedAlpnProtocol, sslEngineToHack, original, clientHelloRecord);
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
     *     ProtocolVersion client_version;
     *     Random random;
     *     SessionID session_id;
     *     CipherSuite cipher_suites<2..2^16-2>;
     *     CompressionMethod compression_methods<1..2^8-1>;
     *     select (extensions_present) {
     *         case false:
     *             struct {};
     *         case true:
     *             Extension extensions<0..2^16-1>;
     *     };
     * } ClientHello;
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
    private static PooledByteBuffer exploreServerHello(
            ByteBuffer input,
            byte recordMajorVersion,
            byte recordMinorVersion, String selectedAlpnProtocol, SSLEngine sslEngineToHack, ByteBuffer original, byte[] clientHelloRecord) throws SSLException {

        // server version
        byte helloMajorVersion = input.get();
        byte helloMinorVersion = input.get();

        // ignore random
        int position = input.position();
        input.position(position + 32);  // 32: the length of Random

        // ignore session id
        ignoreByteVector8(input);

        // ignore cipher_suite
        getInt16(input);

        // ignore compression methods
        getInt8(input);

        boolean alpnPresent = false;
        if (input.remaining() > 0) {
            alpnPresent = exploreExtensions(input);
        }
        if(!alpnPresent) {

        }
        List<ByteBuffer> outgoingRecords = extractRecords(original);

        return hackSslEngine(sslEngineToHack, outgoingRecords, clientHelloRecord);
    }

    private static List<ByteBuffer> extractRecords(ByteBuffer data) {
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

    private static PooledByteBuffer hackSslEngine(SSLEngine sslEngineToHack, List<ByteBuffer> outgoingRecords, byte[] clientHelloRecord) {

        //hack up the SSL engine internal state
        try {
            Object handshaker = HANDSHAKER.get(sslEngineToHack);
            Object hash = HANDSHAKE_HASH.get(handshaker);
            int version = (int) HANDSHAKE_HASH_VERSION.get(hash);
            ByteArrayOutputStream data = (ByteArrayOutputStream) HANDSHAKE_HASH_DATA.get(hash);
            data.reset();
            ProtocolVersion protocolVersion = (ProtocolVersion) HANDSHAKER_PROTOCOL_VERSION.get(handshaker);
            if(version != -1) {
                HANDSHAKE_HASH_VERSION.set(hash, -1);
                HANDSHAKE_HASH_PROTOCOL_DETERMINED.invoke(hash, protocolVersion);
            }
            MessageDigest digest = (MessageDigest) HANDSHAKE_HASH_FIN_MD.get(hash);
            digest.reset();
            byte[] hsm = extractHandshakeMessage(ByteBuffer.wrap(clientHelloRecord));
            if(hsm != null) {
                HANDSHAKE_HASH_UPDATE.invoke(hash, hsm, 0, hsm.length);
            }
            long length = 0;
            for (int i = 0, outgoingRecordsSize = outgoingRecords.size(); i < outgoingRecordsSize; i++) {
                ByteBuffer b = outgoingRecords.get(i);
                length += b.remaining();
                byte[] m = extractHandshakeMessage(b.duplicate());

                if (m != null) {
                    if (m[0] == 20) {
                        //finished message, need to regenerate it
                        Object message = FINISHED_MESSAGE_CONSTRUCTOR.newInstance(protocolVersion, hash, 2, SESSION_MASTER_SECRET.invoke(HANDSHAKE_SESSION.get(handshaker)), HANDSHAKE_CIPHER_SUITE.get(handshaker));
                        System.out.println(message);
                    } else {
                        HANDSHAKE_HASH_UPDATE.invoke(hash, m, 0, m.length);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return null;
    }


    private static byte[] extractHandshakeMessage(ByteBuffer data) {
        int type = data.get();
        data.get();
        data.get();
        int length = getInt16(data);
        byte[] b = new byte[length];
        data.get(b);
        if (type == 22) {
            return b;
        }
        return null;
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
    private static boolean exploreExtensions(ByteBuffer input)
            throws SSLException {
        boolean alpnPresent = false;

        int length = getInt16(input);           // length of extensions
        while (length > 0) {
            int extType = getInt16(input);      // extenson type
            int extLen = getInt16(input);       // length of extension data
            if (extType == 16) {      // 0x00: type of server name indication
                alpnPresent = true;
            }
            ignoreByteVector(input, extLen);
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

    private static void ignoreByteVector8(ByteBuffer input) {
        ignoreByteVector(input, getInt8(input));
    }
    private static String  readByteVector8(ByteBuffer input) {
        int length = getInt8(input);
        byte[] data = new byte[length];
        input.get(data);
        return new String(data, StandardCharsets.US_ASCII);
    }

    private static void ignoreByteVector16(ByteBuffer input) {
        ignoreByteVector(input, getInt16(input));
    }

    private static void ignoreByteVector24(ByteBuffer input) {
        ignoreByteVector(input, getInt24(input));
    }

    private static void ignoreByteVector(ByteBuffer input, int length) {
        if (length != 0) {
            int position = input.position();
            input.position(position + length);
        }
    }
}

