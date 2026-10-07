package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.mission.MissionRepository;
import net.vas.rdp.uav.mission.RDPMission;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class RDPMissionWorldData extends WorldSavedData {
    public static final String DATA_NAME = "rdpuav_missions";
    private final MissionRepository repository = new MissionRepository();

    public RDPMissionWorldData() {
        super(DATA_NAME);
    }

    public RDPMissionWorldData(String name) {
        super(name);
    }

    public static RDPMissionWorldData get(World world) {
        MapStorage storage = world.getMapStorage();
        assert storage != null;
        RDPMissionWorldData data = (RDPMissionWorldData) storage.getOrLoadData(
                RDPMissionWorldData.class, DATA_NAME);
        if (data == null) {
            data = new RDPMissionWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public MissionRepository getRepository() {
        return repository;
    }

    public void add(RDPMission mission) {
        repository.add(mission);
        markDirty();
    }

    public void remove(UUID missionId) {
        boolean removed = repository.remove(missionId);
        if (removed) markDirty();
    }

    public void restoreQueued(net.vas.rdp.uav.mission.MissionQueue queue) {
        if (queue == null) {
            throw new IllegalArgumentException("Mission queue is required");
        }
        for (RDPMission mission : repository.snapshot()) {
            if (mission.isQueued() && !queue.contains(mission)) queue.enqueue(mission);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        repository.clear();
        NBTTagList list = compound.getTagList("missions", 10);
        int rejectedRecords = 0;
        String firstRejection = null;
        for (int i = 0; i < list.tagCount(); i++) {
            try {
                repository.add(MissionNbtCodec.read(list.getCompoundTagAt(i)));
            } catch (IllegalArgumentException malformed) {
                rejectedRecords++;
                if (firstRejection == null) {
                    String reason = malformed.getMessage();
                    if (reason == null) reason = "unspecified";
                    if (reason.length() > 160) reason = reason.substring(0, 157) + "...";
                    firstRejection = "index=" + i + " reason=" + reason;
                }
            }
        }
        if (rejectedRecords > 0) {
            markDirty();
            RdpUavDebug.warn("MissionPersistence", "Rejected " + rejectedRecords
                    + " malformed saved mission record(s); first " + firstRejection);
        }
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (RDPMission mission : repository.snapshot()) {
            list.appendTag(MissionNbtCodec.write(mission));
        }
        compound.setTag("missions", list);
        return compound;
    }
}
