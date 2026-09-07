package net.vas.rdp.uav.scan;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RDPScanReportRepository extends WorldSavedData {
    public static final String DATA_NAME = "rdpuav_reports";
    private final Map<String, RDPScanReport> reports = new LinkedHashMap<String, RDPScanReport>();
    public RDPScanReportRepository() { super(DATA_NAME); }
    public RDPScanReportRepository(String name) { super(name); }
    public static RDPScanReportRepository get(World world) {
        RDPScanReportRepository data = (RDPScanReportRepository) world.getMapStorage()
                .getOrLoadData(RDPScanReportRepository.class, DATA_NAME);
        if (data == null) { data = new RDPScanReportRepository(); world.getMapStorage().setData(DATA_NAME, data); }
        return data;
    }
    public void put(RDPScanReport report) { reports.put(report.getReportId(), report); markDirty(); }
    public RDPScanReport find(String id) { return reports.get(id); }
    @Override public void readFromNBT(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("reports", 10);
        for (int i = 0; i < list.tagCount(); i++) reports.put(
                list.getCompoundTagAt(i).getString("reportId"), ReportNbtCodec.read(list.getCompoundTagAt(i)));
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (RDPScanReport report : reports.values()) list.appendTag(ReportNbtCodec.write(report));
        tag.setTag("reports", list); return tag;
    }
}
