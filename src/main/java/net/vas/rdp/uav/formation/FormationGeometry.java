package net.vas.rdp.uav.formation;

/** Immutable server-side geometry and control envelope for one formation slot. */
public final class FormationGeometry {
    private final int slotIndex;
    private final double sideOffset;
    private final double altitudeOffset;
    private final double rearOffset;
    private final WingmanRole role;
    private final double maxBankDegrees;
    private final double maxPitchDegrees;

    /** Creates a validated immutable slot geometry. */
    public FormationGeometry(int slotIndex, double sideOffset, double altitudeOffset,
                             double rearOffset, WingmanRole role,
                             double maxBankDegrees, double maxPitchDegrees) {
        if (slotIndex < 0 || !finite(sideOffset, altitudeOffset, rearOffset,
                maxBankDegrees, maxPitchDegrees) || role == null
                || maxBankDegrees < 0.0D || maxBankDegrees > 45.0D
                || maxPitchDegrees < 0.0D || maxPitchDegrees > 30.0D) {
            throw new IllegalArgumentException("Invalid formation geometry");
        }
        this.slotIndex = slotIndex;
        this.sideOffset = sideOffset;
        this.altitudeOffset = altitudeOffset;
        this.rearOffset = rearOffset;
        this.role = role;
        this.maxBankDegrees = maxBankDegrees;
        this.maxPitchDegrees = maxPitchDegrees;
    }

    public int getSlotIndex() { return slotIndex; }
    public double getSideOffset() { return sideOffset; }
    public double getAltitudeOffset() { return altitudeOffset; }
    public double getRearOffset() { return rearOffset; }
    public WingmanRole getRole() { return role; }
    public double getMaxBankDegrees() { return maxBankDegrees; }
    public double getMaxPitchDegrees() { return maxPitchDegrees; }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
