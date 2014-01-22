package io.undertow.util;

import java.util.Deque;
import java.util.Map;

/**
 * Methods for dealing with the query string
 *
 * @author Stuart Douglas
 */
public class QueryParameterUtils {

    private QueryParameterUtils() {

    }

    public static String buildQueryString(final Map<String, Deque<String>> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Deque<String>> entry : params.entrySet()) {
            if (entry.getValue().isEmpty()) {
                if (first) {
                    first = false;
                } else {
                    sb.append('&');
                }
                sb.append(entry.getKey());
                sb.append('=');
            } else {
                for (String val : entry.getValue()) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append('&');
                    }
                    sb.append(entry.getKey());
                    sb.append('=');
                    sb.append(val);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parses a query string into a map
     * @param newQueryString The query string
     * @return The map of key value parameters
     */
    public static ParameterMap parseQueryString(final String newQueryString) {
        ParameterMap newQueryParameters = new ParameterMap();
        int startPos = 0;
        int equalPos = -1;
        for(int i = 0; i < newQueryString.length(); ++i) {
            char c = newQueryString.charAt(i);
            if(c == '=' && equalPos == -1) {
                equalPos = i;
            } else if(c == '&') {
                handleQueryParameter(newQueryString, newQueryParameters, startPos, equalPos, i);
                startPos = i + 1;
                equalPos = -1;
            }
        }
        if(startPos != newQueryString.length()) {
            handleQueryParameter(newQueryString, newQueryParameters, startPos, equalPos, newQueryString.length());
        }
        return newQueryParameters;
    }

    private static void handleQueryParameter(String newQueryString, ParameterMap newQueryParameters, int startPos, int equalPos, int i) {
        String key;
        String value = "";
        if(equalPos == -1) {
            key = newQueryString.substring(startPos, i);
        } else {
            key = newQueryString.substring(startPos, equalPos);
            value = newQueryString.substring(equalPos + 1, i);
        }
        newQueryParameters.add(key, value);
    }


    public static ParameterMap mergeQueryParametersWithNewQueryString(final ParameterMap queryParameters, final String newQueryString) {

        ParameterMap newQueryParameters = parseQueryString(newQueryString);
        //according to the spec the new query parameters have to 'take precedence'
        for (Map.Entry<String, ParameterValues> entry : queryParameters.entrySet()) {
            newQueryParameters.addAll(entry.getKey(), entry.getValue());
        }
        return newQueryParameters;
    }
}
