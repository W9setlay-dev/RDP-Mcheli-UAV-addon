package net.vas.rdp.uav.scan;

import net.minecraft.nbt.NBTTagCompound;
import java.util.UUID;

public final class ReportNbtCodec {
    private ReportNbtCodec() { }
    public static NBTTagCompound write(RDPScanReport report) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("reportId", report.getReportId());
        tag.setString("missionId", report.getMissionId().toString());
        tag.setString("targetId", report.getTargetId().toString());
        tag.setString("registryId", report.getTargetRegistryId());
        tag.setInteger("dimension", report.getDimension());
        tag.setDouble("x", report.getX()); tag.setDouble("y", report.getY()); tag.setDouble("z", report.getZ());
        tag.setBoolean("deepScan", report.isDeepScan());
        tag.setString("priority", report.getPriority().name());
        tag.setLong("duration", report.getDurationTicks()); tag.setLong("timestamp", report.getTimestamp());
        return tag;
    }
    public static RDPScanReport read(NBTTagCompound tag) {
        return new RDPScanReport(tag.getString("reportId"), UUID.fromString(tag.getString("missionId")),
                UUID.fromString(tag.getString("targetId")), tag.getString("registryId"),
                tag.getInteger("dimension"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                tag.getBoolean("deepScan"), net.vas.rdp.uav.target.TargetPriority.valueOf(
                        tag.hasKey("priority") ? tag.getString("priority") : "NORMAL"),
                tag.getLong("duration"), tag.getLong("timestamp"));
    }
}
