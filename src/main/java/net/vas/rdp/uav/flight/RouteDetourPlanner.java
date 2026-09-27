package net.vas.rdp.uav.flight;

/**
 * Pure, bounded detour planning for blocked approach corridors.
 *
 * <p>This planner never writes aircraft motion; it only resolves a safe
 * side-step route when a mission corridor is blocked. The result is a finite
 * waypoint offset that can be used by the higher-level mission controller.
 */
public final class RouteDetourPlanner {
    private RouteDetourPlanner() { }

    public static Plan plan(double routeX, double routeY, double routeZ,
                            double targetX, double targetY, double targetZ,
                            boolean blocked, double preferredOffset) {
        if (!Double.isFinite(routeX) || !Double.isFinite(routeY) || !Double.isFinite(routeZ)
                || !Double.isFinite(targetX) || !Double.isFinite(targetY)
                || !Double.isFinite(targetZ) || !Double.isFinite(preferredOffset)) {
            throw new IllegalArgumentException("Invalid detour planning inputs");
        }
        double dx = targetX - routeX;
        double dz = targetZ - routeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-6D) {
            return new Plan(routeX, routeY, routeZ, 0.0D, false, "AT_TARGET");
        }
        double offset = clamp(Math.abs(preferredOffset));
        double sideX = -dz / horizontal * Math.signum(offset) * offset;
        double sideZ = dx / horizontal * Math.signum(offset) * offset;
        if (!blocked) {
            return new Plan(targetX, targetY, targetZ, offset, false, "DIRECT");
        }
        double detourX = routeX + sideX;
        double detourZ = routeZ + sideZ;
        return new Plan(detourX, Math.max(0.0D, targetY), detourZ,
                offset, true, "OFFSET_DETOUR");
    }

    private static double clamp(double value) {
        return Math.max(8.0, Math.min(64.0, value));
    }

    public static final class Plan {
        private final double x;
        private final double y;
        private final double z;
        private final double offset;
        private final boolean detourRequired;
        private final String reason;

        private Plan(double x, double y, double z, double offset,
                     boolean detourRequired, String reason) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.offset = offset;
            this.detourRequired = detourRequired;
            this.reason = reason;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public double getOffset() { return offset; }
        public boolean isDetourRequired() { return detourRequired; }
        public String getReason() { return reason; }
    }
}
