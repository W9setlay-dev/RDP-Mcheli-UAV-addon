package net.vas.rdp.uav.weapon;

/**
 * Lifecycle of a spawned projectile. This is deliberately independent from
 * the carrier aircraft mission state.
 */
public enum ProjectileGuidanceState {
    ASSIGNED,
    NATIVE_GUIDANCE_ACTIVE,
    TARGET_LOST,
    OBSTACLE_UNAVAILABLE,
    IMPACTED,
    ABORTED,
    TIMED_OUT
}
