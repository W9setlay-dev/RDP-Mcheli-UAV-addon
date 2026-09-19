package net.vas.rdp.uav.weapon;

/** Bounded release policy; it never changes projectile or aircraft physics. */
public final class GuidedTerminalProfile {
    private final double maximumAircraftSpeed;
    private final boolean noOrbit;
    private final double minimumAltitude;
    private final double maximumAltitude;
    private final double maximumCoordinateCorrection;
    private final long coordinateCorrectionInterval;

    public GuidedTerminalProfile(double maximumAircraftSpeed, boolean noOrbit) {
        this(maximumAircraftSpeed, noOrbit, 0.0D, 4096.0D);
    }

    public GuidedTerminalProfile(double maximumAircraftSpeed, boolean noOrbit,
                                 double minimumAltitude, double maximumAltitude) {
        this(maximumAircraftSpeed, noOrbit, minimumAltitude, maximumAltitude, 0.0D, 1L);
    }

    public GuidedTerminalProfile(double maximumAircraftSpeed, boolean noOrbit,
                                 double minimumAltitude, double maximumAltitude,
                                 double maximumCoordinateCorrection,
                                 long coordinateCorrectionInterval) {
        if (maximumAircraftSpeed < 0.0D) {
            throw new IllegalArgumentException("Maximum aircraft speed must not be negative");
        }
        if (minimumAltitude < 0.0D || maximumAltitude < minimumAltitude) {
            throw new IllegalArgumentException("Invalid terminal altitude envelope");
        }
        if (maximumCoordinateCorrection < 0.0D || coordinateCorrectionInterval < 1L) {
            throw new IllegalArgumentException("Invalid coordinate correction policy");
        }
        this.maximumAircraftSpeed = maximumAircraftSpeed;
        this.noOrbit = noOrbit;
        this.minimumAltitude = minimumAltitude;
        this.maximumAltitude = maximumAltitude;
        this.maximumCoordinateCorrection = maximumCoordinateCorrection;
        this.coordinateCorrectionInterval = coordinateCorrectionInterval;
    }

    public double getMaximumAircraftSpeed() { return maximumAircraftSpeed; }

    public double getMinimumAltitude() { return minimumAltitude; }
    public double getMaximumAltitude() { return maximumAltitude; }
    public double getMaximumCoordinateCorrection() { return maximumCoordinateCorrection; }
    public long getCoordinateCorrectionInterval() { return coordinateCorrectionInterval; }
}
