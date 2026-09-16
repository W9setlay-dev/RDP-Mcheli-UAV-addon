package net.vas.rdp.uav.weapon;

/** Bounded release policy; it never changes projectile or aircraft physics. */
public final class GuidedTerminalProfile {
    private final double maximumAircraftSpeed;
    private final boolean noOrbit;
    private final double minimumAltitude;
    private final double maximumAltitude;

    public GuidedTerminalProfile(double maximumAircraftSpeed, boolean noOrbit) {
        this(maximumAircraftSpeed, noOrbit, 0.0D, 4096.0D);
    }

    public GuidedTerminalProfile(double maximumAircraftSpeed, boolean noOrbit,
                                 double minimumAltitude, double maximumAltitude) {
        if (maximumAircraftSpeed < 0.0D) {
            throw new IllegalArgumentException("Maximum aircraft speed must not be negative");
        }
        if (minimumAltitude < 0.0D || maximumAltitude < minimumAltitude) {
            throw new IllegalArgumentException("Invalid terminal altitude envelope");
        }
        this.maximumAircraftSpeed = maximumAircraftSpeed;
        this.noOrbit = noOrbit;
        this.minimumAltitude = minimumAltitude;
        this.maximumAltitude = maximumAltitude;
    }

    public double getMaximumAircraftSpeed() { return maximumAircraftSpeed; }
    public boolean isNoOrbit() { return noOrbit; }
    public double getMinimumAltitude() { return minimumAltitude; }
    public double getMaximumAltitude() { return maximumAltitude; }
}
