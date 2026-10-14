package net.vas.rdp.uav.target;

/** Pure geometry policy for server-side visual candidate observations. */
public final class VisualObservationPolicy {
    private VisualObservationPolicy() { }

    public static boolean isWithinFieldOfView(
            double observerX, double observerY, double observerZ,
            double viewX, double viewY, double viewZ,
            double targetX, double targetY, double targetZ,
            double maximumRange, double fieldOfViewDegrees) {
        if (!finite(observerX) || !finite(observerY) || !finite(observerZ)
                || !finite(viewX) || !finite(viewY) || !finite(viewZ)
                || !finite(targetX) || !finite(targetY) || !finite(targetZ)
                || !finite(maximumRange) || !finite(fieldOfViewDegrees)
                || maximumRange <= 0.0D
                || fieldOfViewDegrees <= 0.0D || fieldOfViewDegrees > 180.0D) {
            return false;
        }
        double dx = targetX - observerX;
        double dy = targetY - observerY;
        double dz = targetZ - observerZ;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(distanceSquared) || distanceSquared <= 0.0D
                || distanceSquared > maximumRange * maximumRange) {
            return false;
        }
        double viewLength = Math.sqrt(viewX * viewX + viewY * viewY + viewZ * viewZ);
        if (!Double.isFinite(viewLength) || viewLength <= 0.0D) return false;
        double distance = Math.sqrt(distanceSquared);
        double dot = (dx * viewX + dy * viewY + dz * viewZ)
                / (distance * viewLength);
        double minimumDot = Math.cos(Math.toRadians(fieldOfViewDegrees * 0.5D));
        return Double.isFinite(dot) && dot + 1.0E-12D >= minimumDot;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
