package net.vas.rdp.uav.mission;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class MissionQueue {
    private final Deque<RDPMission> queue = new ArrayDeque<>();

    public synchronized void enqueue(RDPMission mission) {
        if (mission == null) {
            throw new IllegalArgumentException("Mission is required");
        }
        for (RDPMission queued : queue) {
            if (queued != null && queued.getMissionId().equals(mission.getMissionId())) {
                return;
            }
        }
        queue.addLast(mission);
    }

    public synchronized RDPMission poll() {
        return queue.pollFirst();
    }

    public synchronized RDPMission peek() {
        return queue.peekFirst();
    }

    public synchronized boolean remove(RDPMission mission) {
        if (mission == null) {
            return false;
        }
        Iterator<RDPMission> iterator = queue.iterator();
        while (iterator.hasNext()) {
            RDPMission queued = iterator.next();
            if (queued != null && queued.getMissionId().equals(mission.getMissionId())) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean contains(RDPMission mission) {
        if (mission == null) {
            return false;
        }
        for (RDPMission queued : queue) {
            if (queued != null && queued.getMissionId().equals(mission.getMissionId())) {
                return true;
            }
        }
        return false;
    }

    public synchronized int size() {
        return queue.size();
    }
}
