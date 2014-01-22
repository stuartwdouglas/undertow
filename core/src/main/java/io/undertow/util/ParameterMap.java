/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

import io.undertow.UndertowMessages;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * An optimized array-backed header map.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ParameterMap implements Iterable<ParameterValues>, Map<String, ParameterValues> {

    private Object[] table;
    private int size;
    private Collection<String> headerNames;

    public ParameterMap() {
        table = null;
    }

    private ParameterValues getEntry(final String headerName) {
        if (headerName == null || table == null) {
            return null;
        }
        final int hc = headerName.hashCode();
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            return null;
        }
        ParameterValues ParameterValues;
        if (o instanceof ParameterValues) {
            ParameterValues = (ParameterValues) o;
            if (!headerName.equals(ParameterValues.key)) {
                return null;
            }
            return ParameterValues;
        } else {
            final ParameterValues[] row = (ParameterValues[]) o;
            for (int i = 0; i < row.length; i++) {
                ParameterValues = row[i];
                if (ParameterValues != null && headerName.equals(ParameterValues.key)) {
                    return ParameterValues;
                }
            }
            return null;
        }
    }

    private ParameterValues removeEntry(final String headerName) {
        if (headerName == null || table == null) {
            return null;
        }
        final int hc = headerName.hashCode();
        final Object[] table = this.table;
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            return null;
        }
        ParameterValues ParameterValues;
        if (o instanceof ParameterValues) {
            ParameterValues = (ParameterValues) o;
            if (!headerName.equals(ParameterValues.key)) {
                return null;
            }
            table[idx] = null;
            size--;
            return ParameterValues;
        } else {
            final ParameterValues[] row = (ParameterValues[]) o;
            for (int i = 0; i < row.length; i++) {
                ParameterValues = row[i];
                if (ParameterValues != null && headerName.equals(ParameterValues.key)) {
                    row[i] = null;
                    size--;
                    return ParameterValues;
                }
            }
            return null;
        }
    }

    private void resize() {
        final int oldLen = table.length;
        if (oldLen == 0x40000000) {
            return;
        }
        assert Integer.bitCount(oldLen) == 1;
        Object[] newTable = Arrays.copyOf(table, oldLen << 1);
        table = newTable;
        for (int i = 0; i < oldLen; i++) {
            if (newTable[i] == null) {
                continue;
            }
            if (newTable[i] instanceof ParameterValues) {
                ParameterValues e = (ParameterValues) newTable[i];
                if ((e.key.hashCode() & oldLen) != 0) {
                    newTable[i] = null;
                    newTable[i + oldLen] = e;
                }
                continue;
            }
            ParameterValues[] oldRow = (ParameterValues[]) newTable[i];
            ParameterValues[] newRow = oldRow.clone();
            int rowLen = oldRow.length;
            newTable[i + oldLen] = newRow;
            ParameterValues item;
            for (int j = 0; j < rowLen; j++) {
                item = oldRow[j];
                if (item != null) {
                    if ((item.key.hashCode() & oldLen) != 0) {
                        oldRow[j] = null;
                    } else {
                        newRow[j] = null;
                    }
                }
            }
        }
    }

    private ParameterValues getOrCreateEntry(final String headerName) {
        if (headerName == null) {
            return null;
        }
        if(table == null) {
            table = new Object[16];
        }
        final int hc = headerName.hashCode();
        final Object[] table = this.table;
        final int length = table.length;
        final int idx = hc & (length - 1);
        final Object o = table[idx];
        ParameterValues ParameterValues;
        if (o == null) {
            if (size >= length >> 1) {
                resize();
                return getOrCreateEntry(headerName);
            }
            ParameterValues = new ParameterValues(headerName);
            table[idx] = ParameterValues;
            size++;
            return ParameterValues;
        }
        return getOrCreateNonEmpty(headerName, table, length, idx, o);
    }

    private ParameterValues getOrCreateNonEmpty(String headerName, Object[] table, int length, int idx, Object o) {
        ParameterValues ParameterValues;
        if (o instanceof ParameterValues) {
            ParameterValues = (ParameterValues) o;
            if (!headerName.equals(ParameterValues.key)) {
                if (size >= length >> 1) {
                    resize();
                    return getOrCreateEntry(headerName);
                }
                size++;
                final ParameterValues[] row = {ParameterValues, new ParameterValues(headerName), null, null};
                table[idx] = row;
                return row[1];
            }
            return ParameterValues;
        } else {
            final ParameterValues[] row = (ParameterValues[]) o;
            int empty = -1;
            for (int i = 0; i < row.length; i++) {
                ParameterValues = row[i];
                if (ParameterValues != null) {
                    if (headerName.equals(ParameterValues.key)) {
                        return ParameterValues;
                    }
                } else if (empty == -1) {
                    empty = i;
                }
            }
            if (size >= length >> 1) {
                resize();
                return getOrCreateEntry(headerName);
            }
            size++;
            ParameterValues = new ParameterValues(headerName);
            if (empty != -1) {
                row[empty] = ParameterValues;
            } else {
                if (row.length >= 16) {
                    throw new SecurityException("Excessive collisions");
                }
                final ParameterValues[] newRow = Arrays.copyOf(row, row.length + 3);
                newRow[row.length] = ParameterValues;
                table[idx] = newRow;
            }
            return ParameterValues;
        }
    }

    // get

    public ParameterValues get(final String headerName) {
        return getEntry(headerName);
    }

    public String getFirst(String headerName) {
        ParameterValues ParameterValues = getEntry(headerName);
        if (ParameterValues == null) return null;
        return ParameterValues.getFirst();
    }

    public String get(String headerName, int index) throws IndexOutOfBoundsException {
        if (headerName == null) {
            return null;
        }
        final ParameterValues ParameterValues = getEntry(headerName);
        if (ParameterValues == null) {
            return null;
        }
        return ParameterValues.get(index);
    }

    public String getLast(String headerName) {
        if (headerName == null) {
            return null;
        }
        ParameterValues ParameterValues = getEntry(headerName);
        if (ParameterValues == null) return null;
        return ParameterValues.getLast();
    }

    // count

    public int count(String headerName) {
        if (headerName == null) {
            return 0;
        }
        final ParameterValues ParameterValues = getEntry(headerName);
        if (ParameterValues == null) {
            return 0;
        }
        return ParameterValues.size();
    }

    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String) {
            return getEntry((String) key) != null;
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        if(table == null) {
            return false;
        }
        if (value instanceof HeaderValues) {
            for (int i = 0; i < table.length; ++i) {
                Object o = table[i];
                if (o == value) {
                    return true;
                } else if (o instanceof HeaderValues[]) {
                    HeaderValues[] v = (HeaderValues[]) o;
                    for (int j = 0; j < v.length; ++j) {
                        if (v[j] == value) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public ParameterValues get(Object key) {
        if (key instanceof String) {
            return get((String) key);
        }
        return null;
    }

    @Override
    public ParameterValues put(String key, ParameterValues value) {
        if (key == null) {
            return null;
        }
        if (value.getParameterName() != key) {
            throw UndertowMessages.MESSAGES.keyDoesNotMatchParameterName(key, value.getParameterName());
        }
        if(this.table == null) {
            this.table = new Object[16];
        }
        final int hc = key.hashCode();
        int length = table.length;
        final int idx = hc & (table.length - 1);
        final Object o = table[idx];
        if (o == null) {
            if (size >= length >> 1) {
                resize();
                return put(key, value);
            }
            table[idx] = value;
            return null;
        }
        ParameterValues parameterValues;
        if (o instanceof ParameterValues) {
            parameterValues = (ParameterValues) o;
            if (!key.equals(parameterValues.key)) {
                if (size >= length >> 1) {
                    resize();
                    return put(key, value);
                }
                ParameterValues[] array = new ParameterValues[2];
                array[0] = parameterValues;
                array[1] = value;
                return null;
            }
            table[idx] = value;
            return parameterValues;
        } else {
            final ParameterValues[] row = (ParameterValues[]) o;
            for (int i = 0; i < row.length; i++) {
                parameterValues = row[i];
                if (parameterValues != null && key.equals(parameterValues.key)) {
                    row[i] = value;
                    return parameterValues;
                }
            }
            //check for blank space in existing row
            for (int i = 0; i < row.length; i++) {
                parameterValues = row[i];
                if (parameterValues == null) {
                    row[i] = value;
                    return null;
                }
            }
            //allocate a new row
            if (row.length >= 16) {
                throw new SecurityException("Excessive collisions");
            }
            final ParameterValues[] newRow = Arrays.copyOf(row, row.length + 3);
            newRow[row.length] = value;
            table[idx] = newRow;
            return null;
        }
    }

    @Override
    public ParameterValues remove(Object key) {
        if (key instanceof String) {
            return remove((String) key);
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends String, ? extends ParameterValues> m) {
        for (Entry<? extends String, ? extends ParameterValues> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    // iterate

    /**
     * Do a fast iteration of this header map without creating any objects.
     *
     * @return an opaque iterating cookie, or -1 if no iteration is possible
     * @see #fiNext(long)
     * @see #fiCurrent(long)
     */
    public long fastIterate() {
        if(table == null) {
            return -1;
        }
        final Object[] table = this.table;
        final int len = table.length;
        int ri = 0;
        int ci;
        while (ri < len) {
            final Object item = table[ri];
            if (item != null) {
                if (item instanceof ParameterValues) {
                    return (long) ri << 32L;
                } else {
                    final ParameterValues[] row = (ParameterValues[]) item;
                    ci = 0;
                    final int rowLen = row.length;
                    while (ci < rowLen) {
                        if (row[ci] != null) {
                            return (long) ri << 32L | (ci & 0xffffffffL);
                        }
                        ci++;
                    }
                }
            }
            ri++;
        }
        return -1L;
    }

    /**
     * Do a fast iteration of this header map without creating any objects, only considering non-empty header values.
     *
     * @return an opaque iterating cookie, or -1 if no iteration is possible
     */
    public long fastIterateNonEmpty() {
        final Object[] table = this.table;
        if(table == null) {
            return -1;
        }
        final int len = table.length;
        int ri = 0;
        int ci;
        while (ri < len) {
            final Object item = table[ri];
            if (item != null) {
                if (item instanceof ParameterValues && !((ParameterValues) item).isEmpty()) {
                    return (long) ri << 32L;
                } else {
                    final ParameterValues[] row = (ParameterValues[]) item;
                    ci = 0;
                    final int rowLen = row.length;
                    while (ci < rowLen) {
                        if (row[ci] != null && !row[ci].isEmpty()) {
                            return (long) ri << 32L | (ci & 0xffffffffL);
                        }
                        ci++;
                    }
                }
            }
            ri++;
        }
        return -1L;
    }

    /**
     * Find the next index in a fast iteration.
     *
     * @param cookie the previous cookie value
     * @return the next cookie value, or -1L if iteration is done
     */
    public long fiNext(long cookie) {
        if (cookie == -1L) return -1L;
        final Object[] table = this.table;
        final int len = table.length;
        int ri = (int) (cookie >> 32);
        int ci = (int) cookie;
        Object item = table[ri];
        if (item instanceof ParameterValues[]) {
            final ParameterValues[] row = (ParameterValues[]) item;
            final int rowLen = row.length;
            if (++ci >= rowLen) {
                ri++;
                ci = 0;
            } else if (row[ci] != null) {
                return (long) ri << 32L | (ci & 0xffffffffL);
            }
        } else {
            ri++;
            ci = 0;
        }
        while (ri < len) {
            item = table[ri];
            if (item instanceof ParameterValues) {
                return (long) ri << 32L;
            } else if (item instanceof ParameterValues[]) {
                final ParameterValues[] row = (ParameterValues[]) item;
                final int rowLen = row.length;
                while (ci < rowLen) {
                    if (row[ci] != null) {
                        return (long) ri << 32L | (ci & 0xffffffffL);
                    }
                    ci++;
                }
            }
            ci = 0;
            ri++;
        }
        return -1L;
    }

    /**
     * Find the next non-empty index in a fast iteration.
     *
     * @param cookie the previous cookie value
     * @return the next cookie value, or -1L if iteration is done
     */
    public long fiNextNonEmpty(long cookie) {
        if (cookie == -1L) return -1L;
        final Object[] table = this.table;
        final int len = table.length;
        int ri = (int) (cookie >> 32);
        int ci = (int) cookie;
        Object item = table[ri];
        if (item instanceof ParameterValues[]) {
            final ParameterValues[] row = (ParameterValues[]) item;
            final int rowLen = row.length;
            if (++ci >= rowLen) {
                ri++;
                ci = 0;
            } else if (row[ci] != null && !row[ci].isEmpty()) {
                return (long) ri << 32L | (ci & 0xffffffffL);
            }
        } else {
            ri++;
            ci = 0;
        }
        while (ri < len) {
            item = table[ri];
            if (item instanceof ParameterValues && !((ParameterValues) item).isEmpty()) {
                return (long) ri << 32L;
            } else if (item instanceof ParameterValues[]) {
                final ParameterValues[] row = (ParameterValues[]) item;
                final int rowLen = row.length;
                while (ci < rowLen) {
                    if (row[ci] != null && !row[ci].isEmpty()) {
                        return (long) ri << 32L | (ci & 0xffffffffL);
                    }
                    ci++;
                }
            }
            ci = 0;
            ri++;
        }
        return -1L;
    }

    /**
     * Return the value at the current index in a fast iteration.
     *
     * @param cookie the iteration cookie value
     * @return the values object at this position
     * @throws java.util.NoSuchElementException
     *          if the cookie value is invalid
     */
    public ParameterValues fiCurrent(long cookie) {
        try {
            final Object[] table = this.table;
            int ri = (int) (cookie >> 32);
            int ci = (int) cookie;
            final Object item = table[ri];
            if (item instanceof ParameterValues[]) {
                return ((ParameterValues[]) item)[ci];
            } else if (ci == 0) {
                return (ParameterValues) item;
            } else {
                throw new NoSuchElementException();
            }
        } catch (RuntimeException e) {
            throw new NoSuchElementException();
        }
    }

    public Iterable<String> eachValue(final String headerName) {
        if (headerName == null) {
            return Collections.emptyList();
        }
        final ParameterValues entry = getEntry(headerName);
        if (entry == null) {
            return Collections.emptyList();
        }
        return entry;
    }

    public Iterator<ParameterValues> iterator() {
        return new Iterator<ParameterValues>() {
            final Object[] table = ParameterMap.this.table;
            boolean consumed;
            int ri
                    ,
                    ci;

            private ParameterValues _next() {
                if(table == null) {
                    return null;
                }
                for (; ; ) {
                    if (ri >= table.length) {
                        return null;
                    }
                    final Object o = table[ri];
                    if (o == null) {
                        // zero-entry row
                        ri++;
                        ci = 0;
                        consumed = false;
                        continue;
                    }
                    if (o instanceof ParameterValues) {
                        // one-entry row
                        if (ci > 0 || consumed) {
                            ri++;
                            ci = 0;
                            consumed = false;
                            continue;
                        }
                        return (ParameterValues) o;
                    }
                    final ParameterValues[] row = (ParameterValues[]) o;
                    final int len = row.length;
                    if (ci >= len) {
                        ri++;
                        ci = 0;
                        consumed = false;
                        continue;
                    }
                    if (consumed) {
                        ci++;
                        consumed = false;
                        continue;
                    }
                    final ParameterValues ParameterValues = row[ci];
                    if (ParameterValues == null) {
                        ci++;
                        continue;
                    }
                    return ParameterValues;
                }
            }

            public boolean hasNext() {
                return _next() != null;
            }

            public ParameterValues next() {
                final ParameterValues next = _next();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                consumed = true;
                return next;
            }

            public void remove() {
            }
        };
    }

    public Collection<String> getParameterNames() {
        if (headerNames != null) {
            return headerNames;
        }
        return headerNames = new AbstractCollection<String>() {
            public boolean contains(final Object o) {
                return o instanceof String && getEntry((String) o) != null;
            }

            public boolean add(final String String) {
                getOrCreateEntry(String);
                return true;
            }

            public boolean remove(final Object o) {
                if (!(o instanceof String)) return false;
                String s = (String) o;
                ParameterValues entry = getEntry(s);
                if (entry == null) {
                    return false;
                }
                entry.clear();
                return true;
            }

            public void clear() {
                ParameterMap.this.clear();
            }

            public Iterator<String> iterator() {
                final Iterator<ParameterValues> iterator = ParameterMap.this.iterator();
                return new Iterator<String>() {
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    public String next() {
                        return iterator.next().getParameterName();
                    }

                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            public int size() {
                return ParameterMap.this.size();
            }
        };
    }

    // add

    public ParameterMap add(String headerName, String headerValue) {
        addLast(headerName, headerValue);
        return this;
    }

    public ParameterMap addFirst(final String headerName, final String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            return this;
        }
        getOrCreateEntry(headerName).addFirst(headerValue);
        return this;
    }

    public ParameterMap addLast(final String headerName, final String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            return this;
        }
        getOrCreateEntry(headerName).addLast(headerValue);
        return this;
    }

    public ParameterMap add(String headerName, long headerValue) {
        add(headerName, Long.toString(headerValue));
        return this;
    }


    public ParameterMap addAll(String headerName, Collection<String> ParameterValues) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (ParameterValues == null || ParameterValues.isEmpty()) {
            return this;
        }
        getOrCreateEntry(headerName).addAll(ParameterValues);
        return this;
    }

    // put

    public ParameterMap put(String headerName, String headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (headerValue == null) {
            remove(headerName);
            return this;
        }
        final ParameterValues ParameterValues = getOrCreateEntry(headerName);
        ParameterValues.clear();
        ParameterValues.add(headerValue);
        return this;
    }

    public ParameterMap put(String headerName, long headerValue) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        final ParameterValues entry = getOrCreateEntry(headerName);
        entry.clear();
        entry.add(Long.toString(headerValue));
        return this;
    }

    public ParameterMap putAll(String headerName, Collection<String> ParameterValues) {
        if (headerName == null) {
            throw new IllegalArgumentException("headerName is null");
        }
        if (ParameterValues == null || ParameterValues.isEmpty()) {
            remove(headerName);
        }
        final ParameterValues entry = getOrCreateEntry(headerName);
        entry.clear();
        entry.addAll(ParameterValues);
        return this;
    }

    // clear

    public void clear() {
        if(table == null) {
            return;
        }
        Arrays.fill(table, null);
        size = 0;
    }

    @Override
    public Set<String> keySet() {
        return new AbstractSet<String>() {
            @Override
            public Iterator<String> iterator() {
                final Iterator<ParameterValues> iterator = ParameterMap.this.iterator();
                return new Iterator<String>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public String next() {
                        return iterator.next().getParameterName();
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            @Override
            public int size() {
                return size();
            }
        };
    }

    @Override
    public Collection<ParameterValues> values() {
        final Iterator<ParameterValues> iterator = iterator();
        return new AbstractCollection<ParameterValues>() {
            @Override
            public Iterator<ParameterValues> iterator() {
                return ParameterMap.this.iterator();
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    @Override
    public Set<Entry<String, ParameterValues>> entrySet() {
        return new AbstractSet<Entry<String, ParameterValues>>() {
            @Override
            public Iterator<Entry<String, ParameterValues>> iterator() {
                final Iterator<ParameterValues> iterator = ParameterMap.this.iterator();
                return new Iterator<Entry<String, ParameterValues>>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Entry<String, ParameterValues> next() {
                        final ParameterValues next = iterator.next();
                        return new Entry<String, ParameterValues>() {

                            ParameterValues value = next;

                            @Override
                            public String getKey() {
                                return value.getParameterName();
                            }

                            @Override
                            public ParameterValues getValue() {
                                return value;
                            }

                            @Override
                            public ParameterValues setValue(ParameterValues value) {
                                if(value.getParameterName() != this.value.getParameterName()) {
                                    throw UndertowMessages.MESSAGES.keyDoesNotMatchParameterName(this.value.getParameterName(), value.getParameterName());
                                }
                                ParameterValues old = this.value;
                                this.value = value;
                                put(value.getParameterName(), value);
                                return old;
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    // remove

    public ParameterValues remove(String headerName) {
        if (headerName == null) {
            return null;
        }
        final ParameterValues values = removeEntry(headerName);
        return values;
    }

    // contains

    public boolean contains(String headerName) {
        final ParameterValues ParameterValues = getEntry(headerName);
        if (ParameterValues == null) {
            return false;
        }
        final Object v = ParameterValues.value;
        if (v instanceof String) {
            return true;
        }
        final String[] list = (String[]) v;
        for (int i = 0; i < list.length; i++) {
            if (list[i] != null) {
                return true;
            }
        }
        return false;
    }

    // compare

    @Override
    public boolean equals(final Object o) {
        return o == this;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String name : getParameterNames()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(name);
            sb.append("=[");
            boolean f = true;
            for (String val : get(name)) {
                if (f) {
                    f = false;
                } else {
                    sb.append(", ");
                }
                sb.append(val);
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }
}
