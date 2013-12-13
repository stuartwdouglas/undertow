package io.undertow.util;

/**
 * Very simple non-threadsafe string cache
 *
 * @author Stuart Douglas
 */
public class StringCache {

    private final int maxEntries = 16;
    private int size;

    private final Entry[] entries = new Entry[16];

    public String toString(final StringBuilder builder, final int hashCode) {
        int hash = hashCode & (entries.length -1);
        Entry entry = entries[hash];
        while (entry != null) {
            final String value = entry.value;
            if (entry.hashCode == hashCode && value.length() == builder.length()) {
                boolean match = true;
                for (int i = 0; i < value.length(); ++i) {
                    if (value.charAt(i) != builder.charAt(i)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return value;
                }
            }
            entry = entry.next;
        }
        String val = builder.toString();
        if (size != maxEntries) {
            Entry old = entries[hash];
            entries[hash] = new Entry(val, old, hashCode);
            size++;
        }
        return val;
    }


    private static class Entry {
        final String value;
        final Entry next;
        final int hashCode;

        private Entry(String value, Entry next, int hashCode) {
            this.value = value;
            this.next = next;
            this.hashCode = hashCode;
        }
    }
}
