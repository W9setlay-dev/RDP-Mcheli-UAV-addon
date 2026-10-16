package net.vas.rdp.uav.debug;

import java.util.Objects;

public final class DebugContext implements AutoCloseable {
    private static final DebugContext NONE = new DebugContext(null, null, null, null, 0, 0L);

    private final String missionId;
    private final String aircraftId;
    private final String targetId;
    private final String operatorId;
    private final int dimension;
    private final long tick;

    public DebugContext(String missionId, String aircraftId, String targetId, String operatorId,
                        int dimension, long tick) {
        this.missionId = missionId;
        this.aircraftId = aircraftId;
        this.targetId = targetId;
        this.operatorId = operatorId;
        this.dimension = dimension;
        this.tick = tick;
    }

    public static DebugContext none() {
        return NONE;
    }

    public static DebugContext of(String missionId, String aircraftId) {
        return new DebugContext(missionId, aircraftId, null, null, 0, 0L);
    }

    public DebugContext withMissionId(String value) {
        return new DebugContext(value, aircraftId, targetId, operatorId, dimension, tick);
    }

    public DebugContext withAircraftId(String value) {
        return new DebugContext(missionId, value, targetId, operatorId, dimension, tick);
    }

    public DebugContext withTargetId(String value) {
        return new DebugContext(missionId, aircraftId, value, operatorId, dimension, tick);
    }

    public DebugContext withOperatorId(String value) {
        return new DebugContext(missionId, aircraftId, targetId, value, dimension, tick);
    }

    public DebugContext withDimension(int value) {
        return new DebugContext(missionId, aircraftId, targetId, operatorId, value, tick);
    }

    public DebugContext withTick(long value) {
        return new DebugContext(missionId, aircraftId, targetId, operatorId, dimension, value);
    }

    public String getMissionId() {
        return missionId;
    }

    public String getAircraftId() {
        return aircraftId;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public int getDimension() {
        return dimension;
    }

    public long getTick() {
        return tick;
    }

    boolean matches(DebugContext other) {
        return other != null
                && Objects.equals(missionId, other.missionId)
                && Objects.equals(aircraftId, other.aircraftId)
                && Objects.equals(targetId, other.targetId)
                && Objects.equals(operatorId, other.operatorId)
                && dimension == other.dimension
                && tick == other.tick;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebugContext)) return false;
        DebugContext context = (DebugContext) other;
        return matches(context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(missionId, aircraftId, targetId, operatorId, dimension, tick);
    }
}
