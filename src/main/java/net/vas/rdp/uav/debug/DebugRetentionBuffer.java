package net.vas.rdp.uav.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DebugRetentionBuffer {
    public static final int DEFAULT_CAPACITY = 512;

    private final DebugEvent[] events;
    private int nextIndex;
    private int size;

    public DebugRetentionBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public DebugRetentionBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.events = new DebugEvent[capacity];
    }

    public synchronized void record(DebugEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        events[nextIndex] = event;
        nextIndex = (nextIndex + 1) % events.length;
        if (size < events.length) size++;
    }

    public synchronized List<DebugEvent> snapshot(DebugContext filter, int max) {
        if (max <= 0 || size == 0) return Collections.emptyList();
        List<DebugEvent> matching = new ArrayList<>(Math.min(size, max));
        int firstIndex = (nextIndex - size + events.length) % events.length;
        for (int i = 0; i < size; i++) {
            DebugEvent event = events[(firstIndex + i) % events.length];
            if (filter == null || filter.matches(event.getContext())) matching.add(event);
        }
        int start = Math.max(0, matching.size() - max);
        return Collections.unmodifiableList(new ArrayList<>(matching.subList(start, matching.size())));
    }

    public synchronized void clear() {
        for (int i = 0; i < events.length; i++) events[i] = null;
        nextIndex = 0;
        size = 0;
    }
}
