package net.vas.rdp.uav.mission;

import java.util.ArrayDeque;
import java.util.Deque;

public final class MissionQueue {
    private final Deque<RDPMission> queue = new ArrayDeque<>();

    public synchronized void enqueue(RDPMission mission) {
        if (mission == null) {
            throw new IllegalArgumentException("Mission is required");
        }
        if (!queue.contains(mission)) {
            queue.addLast(mission);
        }
    }

    public synchronized RDPMission poll() {
        return queue.pollFirst();
    }

    public synchronized RDPMission peek() {
        return queue.peekFirst();
    }

    public synchronized boolean remove(RDPMission mission) {
        return queue.remove(mission);
    }

    public synchronized boolean contains(RDPMission mission) {
        return queue.contains(mission);
    }

    public synchronized int size() {
        return queue.size();
    }
}
