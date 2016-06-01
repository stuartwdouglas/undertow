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

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLException;
import javax.net.ssl.StandardConstants;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instances of this class acts as an explorer of the network data of an
 * SSL/TLS connection.
 */
final class SSLClientHelloExplorer {

    // Private constructor prevents construction outside this class.
    private SSLClientHelloExplorer() {
    }

    /**
     * The header size of TLS/SSL records.
     * <P>
     * The value of this constant is {@value}.
     */
    public static final int RECORD_HEADER_SIZE = 0x05;

    /**
     * Launch and explore the security capabilities from byte buffer.
     * <P>
     * This method tries to parse as few records as possible from
     * {@code source} byte buffer to get the capabilities
     * of an SSL/TLS connection.
     * <P>
     * Please NOTE that this method must be called before any handshaking
     * occurs.  The behavior of this method is not defined in this release
     * if the handshake has begun, or has completed.
     * <P>
     * This method accesses the {@code source} parameter in read-only
     * mode, and does not update the buffer's properties such as capacity,
     * limit, position, and mark values.
     *
     * @param  source
     *         a {@link ByteBuffer} containing
     *         inbound or outbound network data for an SSL/TLS connection.
     *
     * @throws IOException on network data error
     * @throws BufferUnderflowException if not enough source bytes available
     *         to make a complete exploration.
     *
     * @return the explored capabilities of the SSL/TLS
     *         connection
     */
    public static SSLConnectionInformation exploreClientHello(ByteBuffer source)
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
        if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
            // looks like a V2ClientHello
            return exploreV2HelloRecord(input,
                    thirdByte);
        } else if (firstByte == 22) {   // 22: handshake record
            return exploreTLSRecord(input,
                                    firstByte, secondByte, thirdByte);
        } else {
            throw UndertowMessages.MESSAGES.notHandshakeRecord();
        }
    }

    /*
     * uint8 V2CipherSpec[3];
     * struct {
     *     uint16 msg_length;         // The highest bit MUST be 1;
     *                                // the remaining bits contain the length
     *                                // of the following data in bytes.
     *     uint8 msg_type;            // MUST be 1
     *     Version version;
     *     uint16 cipher_spec_length; // It cannot be zero and MUST be a
     *                                // multiple of the V2CipherSpec length.
     *     uint16 session_id_length;  // This field MUST be empty.
     *     uint16 challenge_length;   // SHOULD use a 32-byte challenge
     *     V2CipherSpec cipher_specs[V2ClientHello.cipher_spec_length];
     *     opaque session_id[V2ClientHello.session_id_length];
     *     opaque challenge[V2ClientHello.challenge_length;
     * } V2ClientHello;
     */
    private static SSLConnectionInformationImpl exploreV2HelloRecord(
            ByteBuffer input,
            byte thirdByte) throws SSLException {

        // We only need the header. We have already had enough source bytes.
        // int recordLength = (firstByte & 0x7F) << 8) | (secondByte & 0xFF);
        try {
            // Is it a V2ClientHello?
            if (thirdByte != 0x01) {
                throw UndertowMessages.MESSAGES.unsupportedSslRecord();
            }

            // What's the hello version?
            byte helloVersionMajor = input.get();
            byte helloVersionMinor = input.get();

            // 0x00: major version of SSLv20
            // 0x02: minor version of SSLv20
            //
            // SNIServerName is an extension, SSLv20 doesn't support extension.
            return new SSLConnectionInformationImpl((byte)0x00, (byte)0x02,
                        helloVersionMajor, helloVersionMinor,
                        Collections.emptyList(), null);
        } catch (BufferUnderflowException ignored) {
            throw UndertowMessages.MESSAGES.invalidHandshakeRecord();
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
    private static SSLConnectionInformationImpl exploreTLSRecord(
            ByteBuffer input, byte firstByte, byte secondByte,
            byte thirdByte) throws SSLException {

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
                secondByte, thirdByte, recordLength);
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
    private static SSLConnectionInformationImpl exploreHandshake(
            ByteBuffer input, byte recordMajorVersion,
            byte recordMinorVersion, int recordLength) throws SSLException {

        // What is the handshake type?
        byte handshakeType = input.get();
        if (handshakeType != 0x01) {   // 0x01: client_hello message
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
        return exploreClientHello(input,
                                    recordMajorVersion, recordMinorVersion);
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
     */
    private static SSLConnectionInformationImpl exploreClientHello(
            ByteBuffer input,
            byte recordMajorVersion,
            byte recordMinorVersion) throws SSLException {

        Holder extensionInfo = new Holder(Collections.emptyList(), Collections.emptyList());

        // client version
        byte helloMajorVersion = input.get();
        byte helloMinorVersion = input.get();

        // ignore random
        int position = input.position();
        input.position(position + 32);  // 32: the length of Random

        // ignore session id
        ignoreByteVector8(input);

        // ignore cipher_suites
        ignoreByteVector16(input);

        // ignore compression methods
        ignoreByteVector8(input);

        if (input.remaining() > 0) {
            extensionInfo = exploreExtensions(input);
        }

        return new SSLConnectionInformationImpl(
                recordMajorVersion, recordMinorVersion,
                helloMajorVersion, helloMinorVersion, extensionInfo.serverNames, extensionInfo.alpnNames);
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
    private static Holder exploreExtensions(ByteBuffer input)
            throws SSLException {
        List<SNIServerName> serverNames = Collections.emptyList();
        List<String> alpnNames = null;
        int length = getInt16(input);           // length of extensions
        while (length > 0) {
            int extType = getInt16(input);      // extenson type
            int extLen = getInt16(input);       // length of extension data

            if (extType == 0x00) {      // 0x00: type of server name indication
                serverNames = exploreSNIExt(input, extLen);
            } else if (extType == 16) {      // 0x00: type of server name indication
                alpnNames = exploreALPNExt(input, extLen);
            } else {                    // ignore other extensions
                ignoreByteVector(input, extLen);
            }

            length -= extLen + 4;
        }

        return new Holder(serverNames, alpnNames);
    }

    private static List<String> exploreALPNExt(ByteBuffer input, int extLen) {
        int length = getInt16(input);
        int end = input.position() + length;
        List<String> ret = new ArrayList<>();
        while (input.position() < end) {
            ret.add(readByteVector8(input));
        }
        return ret;
    }

    /*
     * struct {
     *     NameType name_type;
     *     select (name_type) {
     *         case host_name: HostName;
     *     } name;
     * } ServerName;
     *
     * enum {
     *     host_name(0), (255)
     * } NameType;
     *
     * opaque HostName<1..2^16-1>;
     *
     * struct {
     *     ServerName server_name_list<1..2^16-1>
     * } ServerNameList;
     */
    private static List<SNIServerName> exploreSNIExt(ByteBuffer input,
            int extLen) throws SSLException {

        Map<Integer, SNIServerName> sniMap = new LinkedHashMap<>();

        int remains = extLen;
        if (extLen >= 2) {     // "server_name" extension in ClientHello
            int listLen = getInt16(input);     // length of server_name_list
            if (listLen == 0 || listLen + 2 != extLen) {
                throw UndertowMessages.MESSAGES.invalidSniExt();
            }

            remains -= 2;     // 0x02: the length field of server_name_list
            while (remains > 0) {
                int code = getInt8(input);      // name_type
                int snLen = getInt16(input);    // length field of server name
                if (snLen > remains) {
                    throw UndertowMessages.MESSAGES.notEnoughData();
                }
                byte[] encoded = new byte[snLen];
                input.get(encoded);

                SNIServerName serverName;
                switch (code) {
                    case StandardConstants.SNI_HOST_NAME:
                        if (encoded.length == 0) {
                            throw UndertowMessages.MESSAGES.emptyHostNameSni();
                        }
                        serverName = new SNIHostName(encoded);
                        break;
                    default:
                        serverName = new UnknownServerName(code, encoded);
                }
                // check for duplicated server name type
                if (sniMap.put(serverName.getType(), serverName) != null) {
                    throw UndertowMessages.MESSAGES.duplicatedSniServerName(serverName.getType());
                }

                remains -= encoded.length + 3;  // NameType: 1 byte
                                                // HostName length: 2 bytes
            }
        } else if (extLen == 0) {     // "server_name" extension in ServerHello
            throw UndertowMessages.MESSAGES.invalidSniExt();
        }

        if (remains != 0) {
            throw UndertowMessages.MESSAGES.invalidSniExt();
        }

        return Collections.unmodifiableList(new ArrayList<>(sniMap.values()));
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

    private static void ignoreByteVector(ByteBuffer input, int length) {
        if (length != 0) {
            int position = input.position();
            input.position(position + length);
        }
    }

    private static class UnknownServerName extends SNIServerName {
        UnknownServerName(int code, byte[] encoded) {
            super(code, encoded);
        }
    }

    private static final class SSLConnectionInformationImpl implements SSLConnectionInformation {

        private final String recordVersion;
        private final String helloVersion;
        private final List<SNIServerName> sniNames;
        private final List<String> alpnProtocols;


        SSLConnectionInformationImpl(byte recordMajorVersion, byte recordMinorVersion,
                                     byte helloMajorVersion, byte helloMinorVersion,
                                     List<SNIServerName> sniNames, List<String> alpnProtocols) {
            this.alpnProtocols = alpnProtocols;

            this.recordVersion = getVersionString(recordMajorVersion, recordMinorVersion);
            this.helloVersion = getVersionString(helloMajorVersion, helloMinorVersion);
            this.sniNames = sniNames;
        }

        private static String getVersionString(final byte helloMajorVersion, final byte helloMinorVersion) {
            switch (helloMajorVersion) {
                case 0x00: {
                    switch (helloMinorVersion) {
                        case 0x02: return "SSLv2Hello";
                        default: return unknownVersion(helloMajorVersion, helloMinorVersion);
                    }
                }
                case 0x03: {
                    switch (helloMinorVersion) {
                        case 0x00: return "SSLv3";
                        case 0x01: return "TLSv1";
                        case 0x02: return "TLSv1.1";
                        case 0x03: return "TLSv1.2";
                        case 0x04: return "TLSv1.3";
                        default: return unknownVersion(helloMajorVersion, helloMinorVersion);
                    }
                }
                default: return unknownVersion(helloMajorVersion, helloMinorVersion);
            }
        }

        @Override
        public String getRecordVersion() {
            return recordVersion;
        }

        @Override
        public String getHelloVersion() {
            return helloVersion;
        }

        @Override
        public List<SNIServerName> getSNIServerNames() {
            return Collections.unmodifiableList(sniNames);
        }

        @Override
        public List<String> getAlpnProtocols() {
            return alpnProtocols;
        }

        private static String unknownVersion(byte major, byte minor) {
            return "Unknown-" + (major & 0xff) + "." + (minor & 0xff);
        }

        @Override
        public String toString() {
            return "SSLConnectionInformationImpl{" +
                    "recordVersion='" + recordVersion + '\'' +
                    ", helloVersion='" + helloVersion + '\'' +
                    ", sniNames=" + sniNames +
                    ", alpnProtocols=" + alpnProtocols +
                    '}';
        }
    }

    private static class Holder {
        final List<SNIServerName> serverNames;
        final List<String> alpnNames;

        private Holder(List<SNIServerName> serverNames, List<String> alpnNames) {
            this.serverNames = serverNames;
            this.alpnNames = alpnNames;
        }
    }
}

