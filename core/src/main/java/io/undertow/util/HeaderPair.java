package io.undertow.util;

/**
 * Representation of a header name and value.
 *
 * Using this class can be more efficient when doing {@link HeaderMap}
 * add() and put() operations, as it can remove the need to allocate a
 * {@link HeaderValues} object
 *
 * @author Stuart Douglas
 */
public class HeaderPair {

    private final HttpString name;
    private final String value;
    private final HeaderValues headerValues;

    public HeaderPair(HttpString name, String value) {
        this.name = name;
        this.value = value;
        this.headerValues = new HeaderValues(name, value, true);
    }

    public HttpString getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    HeaderValues getHeaderValues() {
        return headerValues;
    }
}
