package net.vas.rdp.uav.flight;

/**
 * Mission-level movement state for fixed-forward/one-way FPV vehicles.
 *
 * <p>This is deliberately separate from altitude hold. McHeli remains the
 * movement owner; the state only describes which native route policy the
 * mission is allowed to request.</p>
 */
public enum FpvFlightState {
    MOVING_TO_TARGET,
    HOVER,
    TERMINAL_APPROACH
}
