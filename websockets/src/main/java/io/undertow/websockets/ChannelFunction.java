package io.undertow.websockets;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * An arbitrary function that can be applied to channel data
 *
 * @author Stuart Douglas
 */
public interface ChannelFunction {

    void afterRead(ByteBuffer buffer) throws IOException;

    void beforeWrite(ByteBuffer buffer) throws IOException;

}
