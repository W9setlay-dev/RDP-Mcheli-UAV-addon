package net.vas.rdp.uav.weapon;

/** Bounded release policy; it never changes projectile or aircraft physics. */
public final class GuidedTerminalProfile {
    private final double maximumAircraftSpeed;
    private final double minimumAltitude;
    private final double maximumAltitude;
    private final double maximumCoordinateCorrection;
    private final long coordinateCorrectionInterval;
    private final boolean noOrbit;

    public GuidedTerminalProfile(double maximumAircraftSpeed, boolean noOrbit) {
        this(maximumAircraftSpeed, 0.0D, 4096.0D, 0.0D, 1L, noOrbit);
    }

    public GuidedTerminalProfile(double maximumAircraftSpeed,
                                 double minimumAltitude, double maximumAltitude) {
        this(maximumAircraftSpeed, minimumAltitude, maximumAltitude, 0.0D, 1L);
    }

    public GuidedTerminalProfile(double maximumAircraftSpeed,
                                 double minimumAltitude, double maximumAltitude,
                                 double maximumCoordinateCorrection,
                                 long coordinateCorrectionInterval) {
        this(maximumAircraftSpeed, minimumAltitude, maximumAltitude,
                maximumCoordinateCorrection, coordinateCorrectionInterval, true);
    }

    public GuidedTerminalProfile(double maximumAircraftSpeed,
                                 double minimumAltitude, double maximumAltitude,
                                 double maximumCoordinateCorrection,
                                 long coordinateCorrectionInterval, boolean noOrbit) {
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
        this.minimumAltitude = minimumAltitude;
        this.maximumAltitude = maximumAltitude;
        this.maximumCoordinateCorrection = maximumCoordinateCorrection;
        this.coordinateCorrectionInterval = coordinateCorrectionInterval;
        this.noOrbit = noOrbit;
    }

    public double getMaximumAircraftSpeed() { return maximumAircraftSpeed; }

    public double getMinimumAltitude() { return minimumAltitude; }
    public double getMaximumAltitude() { return maximumAltitude; }
    public double getMaximumCoordinateCorrection() { return maximumCoordinateCorrection; }
    public long getCoordinateCorrectionInterval() { return coordinateCorrectionInterval; }
    public boolean isNoOrbit() { return noOrbit; }
}
