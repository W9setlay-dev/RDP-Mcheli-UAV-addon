package net.vas.rdp.uav.weapon;

import java.util.UUID;
import net.vas.rdp.uav.RdpUavDebug;

/**
 * Server-side identity for one native McHeli projectile execution.
 */
public final class ProjectileGuidanceMission {
    private final UUID projectileId;
    private final UUID aircraftId;
    private final String weapon;
    private final GuidedNativeBehavior behavior;
    private final GuidedStrikeObjective objective;
    private ProjectileGuidanceState state = ProjectileGuidanceState.ASSIGNED;
    private String reason = "ASSIGNED";

    public ProjectileGuidanceMission(UUID projectileId, UUID aircraftId, String weapon,
                                     GuidedNativeBehavior behavior,
                                     GuidedStrikeObjective objective) {
        if (projectileId == null || aircraftId == null || weapon == null
                || behavior == null || objective == null) {
            throw new IllegalArgumentException("Invalid projectile guidance mission");
        }
        this.projectileId = projectileId;
        this.aircraftId = aircraftId;
        this.weapon = weapon;
        this.behavior = behavior;
        this.objective = objective;
    }

    public void nativeGuidanceActive() {
        transition(ProjectileGuidanceState.NATIVE_GUIDANCE_ACTIVE, "NATIVE_GUIDANCE_ACTIVE");
    }

    public void avoidanceActive() {
        transition(ProjectileGuidanceState.AVOIDANCE_ACTIVE, "AVOIDANCE_ACTIVE");
    }

    public void targetLost() {
        transition(ProjectileGuidanceState.TARGET_LOST, "GUIDED_TARGET_LOST");
    }

    public void obstacleUnavailable() {
        transition(ProjectileGuidanceState.OBSTACLE_UNAVAILABLE,
                "NATIVE_AVOIDANCE_UNAVAILABLE");
    }

    public void impacted() {
        transition(ProjectileGuidanceState.IMPACTED, "IMPACT_CONFIRMED");
    }

    public void abort() {
        transition(ProjectileGuidanceState.ABORTED, "ABORTED");
    }

    public void timeout() {
        transition(ProjectileGuidanceState.TIMED_OUT, "PROJECTILE_TIMEOUT");
    }

    private void transition(ProjectileGuidanceState next, String nextReason) {
        if (state == ProjectileGuidanceState.IMPACTED
                || state == ProjectileGuidanceState.ABORTED
                || state == ProjectileGuidanceState.TIMED_OUT) {
            return;
        }
        if (state == next && reason.equals(nextReason)) return;
        ProjectileGuidanceState previous = state;
        state = next;
        reason = nextReason;
        RdpUavDebug.event("Guidance", "projectile_state",
                "aircraft", aircraftId, "projectile", projectileId,
                "weapon", weapon, "objective", objective.getObjectiveId(),
                "from", previous, "to", next, "reason", nextReason);
    }

    public UUID getProjectileId() { return projectileId; }
    public UUID getAircraftId() { return aircraftId; }
    public String getWeapon() { return weapon; }
    public GuidedNativeBehavior getBehavior() { return behavior; }
    public GuidedStrikeObjective getObjective() { return objective; }
    public ProjectileGuidanceState getState() { return state; }
    public String getReason() { return reason; }
}
