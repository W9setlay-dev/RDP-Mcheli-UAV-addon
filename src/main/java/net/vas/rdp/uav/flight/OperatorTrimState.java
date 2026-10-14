package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavVehicleProfile;

import java.util.UUID;

/** Server-owned, expiring attitude correction for one aircraft mission. */
public final class OperatorTrimState {
    private static final double MAX_PACKET_YAW_DEGREES = 7.5D;
    private static final double MAX_PACKET_PITCH_DEGREES = 5.0D;
    private static final double MAX_PACKET_ROLL_DEGREES = 6.0D;
    private static final double DECAY_PER_TICK = 0.96D;
    private final UUID missionId;
    private long lastSequence = -1L;
    private long lastClientTick = -1L;
    private long lastPacketTick = -1L;
    private long lastDecayTick = -1L;
    private double yawDegrees;
    private double pitchDegrees;
    private double rollDegrees;

    public OperatorTrimState(UUID missionId) {
        if (missionId == null) throw new IllegalArgumentException("Mission id is required");
        this.missionId = missionId;
    }

    public UUID getMissionId() { return missionId; }

    /**
     * Accept only finite, ordered, per-tick camera/key deltas. Authentication and
     * aircraft/mission identity are checked by the server packet handler.
     */
    public synchronized boolean accept(long sequence, double yawDelta, double pitchDelta,
                                       long serverTick, UavVehicleProfile profile) {
        return accept(sequence, serverTick, yawDelta, pitchDelta, 0.0D, serverTick, profile);
    }

    public synchronized boolean accept(long sequence, long clientTick,
                                       double yawDelta, double pitchDelta,
                                       long serverTick, UavVehicleProfile profile) {
        return accept(sequence, clientTick, yawDelta, pitchDelta, 0.0D, serverTick, profile);
    }

    public synchronized boolean accept(long sequence, long clientTick,
                                       double yawDelta, double pitchDelta,
                                       double rollDelta, long serverTick,
                                       UavVehicleProfile profile) {
        if (profile == null || sequence < 0L || sequence <= lastSequence
                || serverTick < 0L || serverTick <= lastPacketTick
                || clientTick < 0L || clientTick <= lastClientTick
                || clientTick > serverTick + 5L || serverTick - clientTick > 100L
                || !Double.isFinite(yawDelta) || !Double.isFinite(pitchDelta)
                || !Double.isFinite(rollDelta)
                || Math.abs(yawDelta) > MAX_PACKET_YAW_DEGREES
                || Math.abs(pitchDelta) > MAX_PACKET_PITCH_DEGREES
                || Math.abs(rollDelta) > MAX_PACKET_ROLL_DEGREES) return false;
        decayTo(serverTick);
        yawDegrees = clamp(yawDegrees + yawDelta,
                -profile.getMaximumYawCorrectionDegrees(),
                profile.getMaximumYawCorrectionDegrees());
        pitchDegrees = clamp(pitchDegrees + pitchDelta,
                -profile.getMaximumPitchCorrectionDegrees(),
                profile.getMaximumPitchCorrectionDegrees());
        rollDegrees = clamp(rollDegrees + rollDelta,
                -Math.max(20.0D, profile.getMaximumBankDegrees()),
                Math.max(20.0D, profile.getMaximumBankDegrees()));
        lastSequence = sequence;
        lastClientTick = clientTick;
        lastPacketTick = serverTick;
        return true;
    }

    public synchronized Snapshot snapshot(long tick) {
        decayTo(tick);
        return new Snapshot(yawDegrees, pitchDegrees, rollDegrees, lastSequence,
                lastPacketTick, missionId);
    }

    private void decayTo(long tick) {
        if (tick < 0L) return;
        if (lastDecayTick < 0L) {
            lastDecayTick = tick;
            return;
        }
        long elapsed = Math.min(200L, Math.max(0L, tick - lastDecayTick));
        if (elapsed > 0L) {
            double decay = Math.pow(DECAY_PER_TICK, elapsed);
            yawDegrees *= decay;
            pitchDegrees *= decay;
            rollDegrees *= decay;
            if (Math.abs(yawDegrees) < 0.02D) yawDegrees = 0.0D;
            if (Math.abs(pitchDegrees) < 0.02D) pitchDegrees = 0.0D;
            if (Math.abs(rollDegrees) < 0.02D) rollDegrees = 0.0D;
            lastDecayTick = tick;
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Snapshot {
        private final double yawDegrees;
        private final double pitchDegrees;
        private final double rollDegrees;
        private final long sequence;
        private final long sampleTick;
        private final UUID missionId;

        private Snapshot(double yawDegrees, double pitchDegrees, double rollDegrees,
                         long sequence, long sampleTick, UUID missionId) {
            this.yawDegrees = yawDegrees;
            this.pitchDegrees = pitchDegrees;
            this.rollDegrees = rollDegrees;
            this.sequence = sequence;
            this.sampleTick = sampleTick;
            this.missionId = missionId;
        }

        public double getYawDegrees() { return yawDegrees; }
        public double getPitchDegrees() { return pitchDegrees; }
        public double getRollDegrees() { return rollDegrees; }
        public long getSequence() { return sequence; }
        public long getSampleTick() { return sampleTick; }
        public UUID getMissionId() { return missionId; }
    }

}
