package net.vas.rdp.uav.mission;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MissionCoordinator {
    private final Map<UUID, UUID> aircraftByTarget = new HashMap<>();
    private final Map<UUID, Set<UUID>> targetsByAircraft = new HashMap<>();
    private final Map<UUID, String> rolesByAircraft = new HashMap<>();
    private final Map<UUID, String> coverageByAircraft = new HashMap<>();
    private final Map<UUID, String> coverageByTarget = new HashMap<>();

    public synchronized void setRole(UUID aircraftId, String role) {
        if (aircraftId == null) throw new IllegalArgumentException("Aircraft id is required");
        if (role == null || role.trim().isEmpty()) {
            rolesByAircraft.remove(aircraftId);
        } else {
            rolesByAircraft.put(aircraftId, role.trim());
        }
    }

    public synchronized void setCoverageZone(UUID aircraftId, String coverageZone) {
        if (aircraftId == null) throw new IllegalArgumentException("Aircraft id is required");
        if (coverageZone == null || coverageZone.trim().isEmpty()) {
            coverageByAircraft.remove(aircraftId);
        } else {
            coverageByAircraft.put(aircraftId, coverageZone.trim());
        }
    }

    public synchronized boolean claim(UUID aircraftId, UUID targetId) {
        return claim(aircraftId, targetId, null, null, false);
    }

    public synchronized boolean claim(UUID aircraftId, UUID targetId, String role, String coverageZone) {
        return claim(aircraftId, targetId, role, coverageZone, false);
    }

    public synchronized boolean claim(UUID aircraftId, UUID targetId, String role, String coverageZone,
                                    boolean force) {
        if (aircraftId == null || targetId == null) {
            throw new IllegalArgumentException("Aircraft and target are required");
        }
        UUID assignedAircraft = aircraftByTarget.get(targetId);
        if (assignedAircraft != null) {
            if (assignedAircraft.equals(aircraftId)) {
                if (role != null && !role.trim().isEmpty()) {
                    rolesByAircraft.put(aircraftId, role.trim());
                }
                if (coverageZone != null && !coverageZone.trim().isEmpty()) {
                    coverageByAircraft.put(aircraftId, coverageZone.trim());
                    coverageByTarget.put(targetId, coverageZone.trim());
                }
                return true;
            }
            if (!force) {
                return false;
            }
            release(assignedAircraft, targetId);
        }
        if (role != null && !role.trim().isEmpty()) {
            rolesByAircraft.put(aircraftId, role.trim());
        }
        if (coverageZone != null && !coverageZone.trim().isEmpty()) {
            coverageByAircraft.put(aircraftId, coverageZone.trim());
            coverageByTarget.put(targetId, coverageZone.trim());
        }
        if (!targetsByAircraft.containsKey(aircraftId)) {
            targetsByAircraft.put(aircraftId, new HashSet<>());
        }
        targetsByAircraft.get(aircraftId).add(targetId);
        aircraftByTarget.put(targetId, aircraftId);
        return true;
    }

    public synchronized boolean reassign(UUID aircraftId, UUID targetId, UUID replacementTargetId,
                                        boolean force) {
        return reassign(aircraftId, targetId, replacementTargetId, force, null, null);
    }

    public synchronized boolean reassign(UUID aircraftId, UUID targetId, UUID replacementTargetId,
                                        boolean force, String role, String coverageZone) {
        if (aircraftId == null || targetId == null || replacementTargetId == null) {
            throw new IllegalArgumentException("Aircraft and target ids are required");
        }
        if (!force && aircraftByTarget.containsKey(replacementTargetId)
                && !aircraftByTarget.get(replacementTargetId).equals(aircraftId)) {
            return false;
        }
        release(aircraftId, targetId);
        return claim(aircraftId, replacementTargetId, role, coverageZone, force);
    }

    public synchronized void release(UUID aircraftId) {
        if (aircraftId == null) return;
        Set<UUID> targets = targetsByAircraft.remove(aircraftId);
        if (targets != null) {
            for (UUID targetId : targets) {
                if (aircraftByTarget.get(targetId) != null
                        && aircraftByTarget.get(targetId).equals(aircraftId)) {
                    aircraftByTarget.remove(targetId);
                    coverageByTarget.remove(targetId);
                }
            }
        }
        rolesByAircraft.remove(aircraftId);
        coverageByAircraft.remove(aircraftId);
    }

    public synchronized void release(UUID aircraftId, UUID targetId) {
        if (aircraftId == null) {
            return;
        }
        if (targetId != null) {
            UUID assignedAircraft = aircraftByTarget.get(targetId);
            if (assignedAircraft != null && assignedAircraft.equals(aircraftId)) {
                aircraftByTarget.remove(targetId);
                coverageByTarget.remove(targetId);
            }
        }
        Set<UUID> targets = targetsByAircraft.get(aircraftId);
        if (targets != null) {
            if (targetId != null) targets.remove(targetId);
            if (targets.isEmpty()) {
                targetsByAircraft.remove(aircraftId);
            }
        }
        if (targetsByAircraft.get(aircraftId) == null) {
            rolesByAircraft.remove(aircraftId);
        }
        if (targetsByAircraft.get(aircraftId) == null) {
            coverageByAircraft.remove(aircraftId);
        }
    }

    public synchronized boolean isAssigned(UUID aircraftId, UUID targetId) {
        return aircraftId != null && targetId != null
                && aircraftByTarget.containsKey(targetId)
                && aircraftByTarget.get(targetId).equals(aircraftId);
    }

    public synchronized boolean isTargetOccupied(UUID targetId) {
        return targetId != null && aircraftByTarget.containsKey(targetId);
    }

    @SuppressWarnings("unused")
    public synchronized Set<UUID> targetsFor(UUID aircraftId) {
        if (aircraftId == null) return Collections.emptySet();
        Set<UUID> targets = targetsByAircraft.get(aircraftId);
        return targets == null ? Collections.emptySet() : new HashSet<>(targets);
    }

    public synchronized int activeMissionCount() {
        return aircraftByTarget.size();
    }

    public synchronized String aircraftRole(UUID aircraftId) {
        return aircraftId == null ? null : rolesByAircraft.get(aircraftId);
    }

    public synchronized String coverageZone(UUID aircraftId) {
        return aircraftId == null ? null : coverageByAircraft.get(aircraftId);
    }

    @SuppressWarnings("unused")
    public synchronized String targetCoverage(UUID targetId) {
        return targetId == null ? null : coverageByTarget.get(targetId);
    }
}
