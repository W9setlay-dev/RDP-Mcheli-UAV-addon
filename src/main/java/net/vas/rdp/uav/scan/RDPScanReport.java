package net.vas.rdp.uav.scan;

import java.util.UUID;
import net.vas.rdp.uav.target.TargetPriority;

public final class RDPScanReport {
    private final String reportId;
    private final UUID missionId;
    private final UUID targetId;
    private final String targetRegistryId;
    private final int dimension;
    private final double x;
    private final double y;
    private final double z;
    private final boolean deepScan;
    private final TargetPriority priority;
    private final long durationTicks;
    private final long timestamp;

    public RDPScanReport(String reportId, UUID missionId, UUID targetId, String targetRegistryId,
                         int dimension, double x, double y, double z, boolean deepScan,
                         long durationTicks, long timestamp) {
        this(reportId, missionId, targetId, targetRegistryId, dimension, x, y, z, deepScan,
                TargetPriority.NORMAL, durationTicks, timestamp);
    }

    public RDPScanReport(String reportId, UUID missionId, UUID targetId, String targetRegistryId,
                         int dimension, double x, double y, double z, boolean deepScan,
                         TargetPriority priority, long durationTicks, long timestamp) {
        if (reportId == null || missionId == null || targetId == null
                || targetRegistryId == null || priority == null || durationTicks < 0) {
            throw new IllegalArgumentException("Invalid scan report");
        }
        this.reportId = reportId;
        this.missionId = missionId;
        this.targetId = targetId;
        this.targetRegistryId = targetRegistryId;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.deepScan = deepScan;
        this.priority = priority;
        this.durationTicks = durationTicks;
        this.timestamp = timestamp;
    }

    public String getReportId() { return reportId; }
    public UUID getMissionId() { return missionId; }
    public UUID getTargetId() { return targetId; }
    public String getTargetRegistryId() { return targetRegistryId; }
    public int getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public boolean isDeepScan() { return deepScan; }
    public TargetPriority getPriority() { return priority; }
    public long getDurationTicks() { return durationTicks; }
    public long getTimestamp() { return timestamp; }
}
