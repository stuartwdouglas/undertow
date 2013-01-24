package io.undertow.websockets.highlevel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class CloseCode {
    public static CloseCode OK = new CloseCode(1000);
    private final int code;

    private CloseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
