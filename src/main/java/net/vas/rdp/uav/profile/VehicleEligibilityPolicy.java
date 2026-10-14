package net.vas.rdp.uav.profile;

/** Fail-closed eligibility rules for autonomous aircraft movement. */
public final class VehicleEligibilityPolicy {
    private VehicleEligibilityPolicy() { }

    public enum VehicleKind {
        FIXED_WING_PLANE,
        NATIVE_HELICOPTER,
        UNSUPPORTED
    }

    /**
     * Returns a stable rejection reason, or {@code null} when the native
     * vehicle kind is supported by the current movement implementation.
     */
    public static String validate(VehicleKind kind, boolean vtolEnabled) {
        if (kind == null || kind == VehicleKind.UNSUPPORTED) {
            return "UNSUPPORTED_AIRCRAFT_TYPE";
        }
        if (kind == VehicleKind.FIXED_WING_PLANE && vtolEnabled) {
            return "VTOL_PLANES_UNSUPPORTED";
        }
        return null;
    }
}
