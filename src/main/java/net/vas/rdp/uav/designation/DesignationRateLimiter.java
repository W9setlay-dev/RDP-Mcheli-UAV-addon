package net.vas.rdp.uav.designation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DesignationRateLimiter {
    private static final long SECOND_WINDOW_TICKS = 20L;
    private static final long MINUTE_WINDOW_TICKS = 1200L;
    private final Map<UUID, Deque<Long>> perSecond = new HashMap<>();
    private final Map<UUID, Deque<Long>> perMinute = new HashMap<>();

    public synchronized boolean allow(UUID operatorId, long currentTick,
                                      int requestsPerSecond, int requestsPerMinute) {
        if (operatorId == null || currentTick < 0L
                || requestsPerSecond < 1 || requestsPerMinute < 1) return false;
        Deque<Long> secondWindow = window(perSecond, operatorId);
        Deque<Long> minuteWindow = window(perMinute, operatorId);
        if ((!secondWindow.isEmpty() && currentTick < secondWindow.peekLast())
                || (!minuteWindow.isEmpty() && currentTick < minuteWindow.peekLast())) {
            secondWindow.clear();
            minuteWindow.clear();
        }
        expire(secondWindow, currentTick, SECOND_WINDOW_TICKS);
        expire(minuteWindow, currentTick, MINUTE_WINDOW_TICKS);
        if (secondWindow.size() >= requestsPerSecond
                || minuteWindow.size() >= requestsPerMinute) return false;
        secondWindow.addLast(currentTick);
        minuteWindow.addLast(currentTick);
        return true;
    }

    public synchronized void clear(UUID operatorId) {
        if (operatorId == null) return;
        perSecond.remove(operatorId);
        perMinute.remove(operatorId);
    }

    private static Deque<Long> window(Map<UUID, Deque<Long>> windows, UUID operatorId) {
        Deque<Long> values = windows.get(operatorId);
        if (values == null) {
            values = new ArrayDeque<>();
            windows.put(operatorId, values);
        }
        return values;
    }

    private static void expire(Deque<Long> window, long currentTick, long durationTicks) {
        while (!window.isEmpty() && currentTick - window.peekFirst() >= durationTicks) {
            window.removeFirst();
        }
    }
}
