package io.undertow.server.protocol.http;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A super complicate class that does stuff
 *
 * @author Stuart Douglas
 */
public abstract class AbstractParsingStateMachine {


    private volatile StateMachine stateMachine;

    private static final int TERMINAL_STATE_MASK = 1 << 23;
    private static final int INVERSE_TERMINAL_STATE_MASK = ~TERMINAL_STATE_MASK;

    public AbstractParsingStateMachine generate(final Collection<HttpString> strings) {
        final List<State> allStates = new ArrayList<State>();
        final State initial = new State((byte) 0, new HttpString(""));
        allStates.add(initial);
        for (HttpString value : strings) {
            addStates(initial, value, allStates);
        }

        int stateLength = 0;
        for (State state : allStates) {
            stateLength = stateLength + 1 + state.next.size();
        }

        final int[] states = new int[stateLength];
        final List<HttpString> terminalStates = new ArrayList<HttpString>();

        generateStateList(allStates, states, terminalStates);


        final HttpString[] realTerminalStates = new HttpString[terminalStates.size()];
        for (int i = 0; i < terminalStates.size(); ++i) {
            realTerminalStates[i] = terminalStates.get(i);
        }

        StateMachine machine = new StateMachine(states, realTerminalStates);
        this.stateMachine = machine;
        return this;
    }

    public void parse(final ByteBuffer buffer, final ParseState currentState, final HttpServerExchange builder) {
        int pos = currentState.parseState;
        if (pos == -1) {
            handleNoMatch(buffer, currentState, builder);
            return;
        }
        HttpString string = currentState.current;

        StateMachine stateMachine = this.stateMachine;
        int[] states = stateMachine.states;

        while (string == null && buffer.hasRemaining()) {
            byte c = buffer.get();
            int head = states[pos];
            int length = head & 0xFF;
            boolean found = false;
            for (int i = 0; i < length; ++i) {
                int state = states[pos + i + 1];
                if (c == (state & 0xFF)) {
                    pos = state >>> 8;
                    if ((pos & TERMINAL_STATE_MASK) != 0) {
                        int index = pos & INVERSE_TERMINAL_STATE_MASK;
                        string = stateMachine.terminalStates[index];
                        pos = string.length();
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                HttpString soFar = stateMachine.terminalStates[head >> 8];
                currentState.pos = -1;
                currentState.stringBuilder.append(soFar.toString());
                handleNoMatch(buffer, currentState, builder);
                return;
            }
        }
        if (string != null) {
            while (buffer.hasRemaining()) {
                byte c = buffer.get();
                if (isEnd(c)) {
                    if (pos == string.length()) {
                        handleResult(string, currentState, builder);
                    } else {
                        handleResult(new HttpString(string.toString().substring(0, pos)), currentState, builder);
                    }
                    return;
                }
                if (string.length() == pos || c != string.byteAt(pos++)) {
                    currentState.pos = -1;
                    currentState.stringBuilder.append(string.toString().substring(0, pos));
                    currentState.stringBuilder.append((char) c);
                    handleNoMatch(buffer, currentState, builder);
                    return;
                }
            }
        }
        currentState.parseState = pos;
        currentState.current = string;
    }

    private void handleNoMatch(ByteBuffer buffer, ParseState currentState, HttpServerExchange builder) {
        StringBuilder stringBuilder = currentState.stringBuilder;
        while (buffer.hasRemaining()) {
            byte c = buffer.get();
            if (isEnd(c)) {
                currentState.state++;
                handleResult(new HttpString(stringBuilder.toString()), currentState, builder);
                stringBuilder.setLength(0);
                return;
            } else {
                stringBuilder.append((char) c);
            }
        }
    }

    protected abstract void handleResult(HttpString httpString, ParseState currentState, HttpServerExchange builder);

    protected abstract boolean isEnd(byte c);

    private void generateStateList(List<State> allStates, int[] states, List<HttpString> terminalStates) {
        int i = 0;

        for (State state : allStates) {
            state.location = i;
            state.mark(states);
            int terminalPos = terminalStates.size();
            terminalStates.add(state.soFar);
            states[i++] = terminalPos << 8 | state.next.size();
            for (Map.Entry<Byte, State> next : state.next.entrySet()) {
                next.getValue().branchEnds.add(i);
                int nextKey = next.getKey() & 0xFF;
                if (next.getValue().next.isEmpty()) {
                    int terminal = terminalStates.size();
                    terminalStates.add(next.getValue().soFar);

                    nextKey |= (terminal | TERMINAL_STATE_MASK) << 8;
                }
                states[i++] = nextKey;


            }
        }

    }

    private static void addStates(final State initial, final HttpString value, final List<State> allStates) {
        addStates(initial, value, 0, allStates);
    }

    private static void addStates(final State current, final HttpString value, final int i, final List<State> allStates) {
        if (i == value.length()) {
            current.finalState = true;
            return;
        }
        final byte currentByte = value.byteAt(i);
        State newState = current.next.get(currentByte);
        if (newState == null) {
            current.next.put(currentByte, newState = new State(currentByte, i + 1 == value.length() ? value : new HttpString(value.toString().substring(0, i + 1))));
            allStates.add(newState);
        }
        addStates(newState, value, i + 1, allStates);
    }


    private static final class StateMachine {

        final int[] states;
        final HttpString[] terminalStates;

        private StateMachine(int[] states, HttpString[] terminalStates) {
            this.states = states;
            this.terminalStates = terminalStates;
        }
    }

    private static class State implements Comparable<State> {

        Integer stateno;
        String terminalState;
        String fieldName;
        String httpStringFieldName;
        /**
         * If this state represents a possible final state
         */
        boolean finalState;
        final byte value;
        final HttpString soFar;
        final Map<Byte, State> next = new HashMap<Byte, State>();
        private final Set<Integer> branchEnds = new HashSet<Integer>();
        private int location;

        private State(final byte value, final HttpString soFar) {
            this.value = value;
            this.soFar = soFar;
        }

        @Override
        public int compareTo(final State o) {
            return stateno.compareTo(o.stateno);
        }

        void mark(final int[] states) {
            if(next.isEmpty()) {
                return;
            }
            for (Integer br : branchEnds) {
                int existing = states[br];
                states[br] = existing | (location << 8);

            }
        }
    }

}
