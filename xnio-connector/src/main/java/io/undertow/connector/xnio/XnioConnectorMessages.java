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

package io.undertow.connector.xnio;

import io.undertow.connector.xnio.protocols.http2.HpackException;
import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

/**
 * starts at 40000
 * @author Stuart Douglas
 */
@MessageBundle(projectCode = "UT")
public interface XnioConnectorMessages {

    XnioConnectorMessages MESSAGES = Messages.getBundle(XnioConnectorMessages.class);

    @Message(id = 40001, value = "Huffman encoded value in HPACK headers did not end with EOS padding")
    HpackException huffmanEncodedHpackValueDidNotEndWithEOS();

    @Message(id = 40002, value = "HPACK variable length integer encoded over too many octects, max is %s")
    HpackException integerEncodedOverTooManyOctets(int maxIntegerOctets);

    @Message(id = 40003, value = "Zero is not a valid header table index")
    HpackException zeroNotValidHeaderTableIndex();

}
