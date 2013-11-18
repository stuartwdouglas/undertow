package io.undertow.server.protocol.ajp;

import io.undertow.server.protocol.framed.FramePriority;

import java.util.Deque;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class AjpFramePriority implements FramePriority<AjpChannel, AjpStreamSourceChannel, AbstractAjpStreamSinkChannel> {

    public static final AjpFramePriority INSTANCE = new AjpFramePriority();

    private AjpFramePriority() {

    }

    @Override
    public boolean insertFrame(AbstractAjpStreamSinkChannel newFrame, List<AbstractAjpStreamSinkChannel> pendingFrames) {
        if (pendingFrames.isEmpty()) {
            pendingFrames.add(newFrame);
        } else {
            if (newFrame instanceof AjpReadBodyChunkStreamSinkChannel) {
                AbstractAjpStreamSinkChannel existing = pendingFrames.get(0);
                if(existing.isActive()) {
                    pendingFrames.add(1, newFrame);
                } else {
                    pendingFrames.add(0, newFrame);
                }
            } else {
                pendingFrames.add(newFrame);
            }
        }
        return true;
    }

    @Override
    public void frameAdded(AbstractAjpStreamSinkChannel addedFrame, List<AbstractAjpStreamSinkChannel> pendingFrames, Deque<AbstractAjpStreamSinkChannel> holdFrames) {
    }
}
