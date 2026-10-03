package net.vas.rdp.uav.swarm;

import java.util.UUID;

/** Applies each member's configured slot around its current mission waypoint. */
public final class SwarmFormationGuidance {
    private SwarmFormationGuidance() { }

    public static Target offsetTarget(SwarmRecord swarm, UUID aircraftId,
                                      double targetX, double targetY, double targetZ,
                                      double aircraftX, double aircraftZ,
                                      double fallbackYawDegrees) {
        if (swarm == null || aircraftId == null || !Double.isFinite(targetX)
                || !Double.isFinite(targetY) || !Double.isFinite(targetZ)
                || !Double.isFinite(aircraftX) || !Double.isFinite(aircraftZ)) return null;
        SwarmMember member = find(swarm, aircraftId);
        if (member == null || member.getSlot() == null) return null;
        UUID anchorId = swarm.referenceMember();
        SwarmMember anchor = find(swarm, anchorId);
        if (anchor == null && swarm.getLeadership() == SwarmLeadership.NO_SINGLE_LEADER) {
            for (SwarmMember candidate : swarm.getMembers()) {
                if (candidate.getSlot() != null) {
                    anchor = candidate;
                    break;
                }
            }
        }
        if (anchor == null) return null;

        double dx = targetX - aircraftX;
        double dz = targetZ - aircraftZ;
        double yaw = Math.hypot(dx, dz) > 1.0D
                ? Math.atan2(-dx, dz) : Math.toRadians(fallbackYawDegrees);
        double localX = member.getSlot().getX() - anchor.getSlot().getX();
        double localY = member.getSlot().getY() - anchor.getSlot().getY();
        double localZ = member.getSlot().getZ() - anchor.getSlot().getZ();
        double worldX = localX * Math.cos(yaw) + localZ * Math.sin(yaw);
        double worldZ = localX * Math.sin(yaw) - localZ * Math.cos(yaw);
        return new Target(targetX + worldX, targetY + localY, targetZ + worldZ);
    }

    private static SwarmMember find(SwarmRecord swarm, UUID aircraftId) {
        if (aircraftId == null) return null;
        for (SwarmMember member : swarm.getMembers()) {
            if (aircraftId.equals(member.getAircraftId())) return member;
        }
        return null;
    }

    public static final class Target {
        public final double x;
        public final double y;
        public final double z;

        private Target(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
