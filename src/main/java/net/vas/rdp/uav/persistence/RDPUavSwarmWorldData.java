package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.vas.rdp.uav.swarm.*;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class RDPUavSwarmWorldData extends WorldSavedData {
    public static final String DATA_NAME = "rdpuav_swarms";
    private final SwarmRepository repository = new SwarmRepository();

    public RDPUavSwarmWorldData() { super(DATA_NAME); }
    public RDPUavSwarmWorldData(String name) { super(name); }
    public static RDPUavSwarmWorldData get(World world) {
        MapStorage storage = world.getMapStorage();
        assert storage != null;
        RDPUavSwarmWorldData data = (RDPUavSwarmWorldData) storage.getOrLoadData(
                RDPUavSwarmWorldData.class, DATA_NAME);
        if (data == null) {
            data = new RDPUavSwarmWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }
    public SwarmRepository getRepository() { return repository; }
    public void put(SwarmRecord record) { repository.put(record); markDirty(); }
    @Override public void readFromNBT(NBTTagCompound tag) {
        repository.clear();
        NBTTagList swarms = tag.getTagList("swarms", 10);
        for (int i = 0; i < swarms.tagCount(); i++) {
            NBTTagCompound value = swarms.getCompoundTagAt(i);
            SwarmRecord swarm = new SwarmRecord(UUID.fromString(value.getString("id")),
                    UUID.fromString(value.getString("owner")), value.getString("name"));
            try { swarm.setState(SwarmState.valueOf(value.getString("state"))); }
            catch (IllegalArgumentException ignored) { }
            try { swarm.setLeadership(SwarmLeadership.valueOf(value.getString("leadership"))); }
            catch (IllegalArgumentException ignored) { }
            try { swarm.setFormation(SwarmFormation.valueOf(value.getString("formation"))); }
            catch (IllegalArgumentException ignored) { }
            try { swarm.setResult(SwarmResult.valueOf(value.getString("result"))); }
            catch (IllegalArgumentException ignored) { }
            try { swarm.setMissionState(net.vas.rdp.uav.swarm.SwarmMissionState.valueOf(value.getString("missionState"))); }
            catch (IllegalArgumentException ignored) { }
            try { swarm.setLockState(net.vas.rdp.uav.swarm.SwarmLockState.valueOf(value.getString("lockState"))); }
            catch (IllegalArgumentException ignored) { }
            try { swarm.setOperatorState(net.vas.rdp.uav.swarm.SwarmOperatorState.valueOf(value.getString("operatorState"))); }
            catch (IllegalArgumentException ignored) { }
            try { swarm.setSearchPattern(SearchPattern.valueOf(value.getString("search"))); }
            catch (IllegalArgumentException ignored) { }
            if (value.hasKey("missionParticipation")) {
                swarm.setMissionParticipation(value.getBoolean("missionParticipation"));
            }
            if (value.hasKey("missionMaximumMembers")) {
                swarm.setMissionMaximumMembers(value.getInteger("missionMaximumMembers"));
            }
            if (value.hasKey("allowDuplicateTargets")) {
                swarm.setAllowDuplicateTargets(value.getBoolean("allowDuplicateTargets"));
            }
            if (value.hasKey("leader")) swarm.setDesignatedLeader(UUID.fromString(value.getString("leader")));
            NBTTagList members = value.getTagList("members", 10);
            for (int j = 0; j < members.tagCount(); j++) {
                NBTTagCompound member = members.getCompoundTagAt(j);
                SwarmMember item = new SwarmMember(UUID.fromString(member.getString("aircraft")),
                        member.getString("profile"), member.getBoolean("longRange"),
                        member.getBoolean("payload"));
                item.setAvailable(member.getBoolean("available"));
                item.setOperatorControlled(member.getBoolean("operatorControlled"));
                if (member.hasKey("target")) {
                    try { item.setAssignedTarget(UUID.fromString(member.getString("target"))); }
                    catch (IllegalArgumentException ignored) { }
                }
                try { item.setState(SwarmMemberState.valueOf(member.getString("state"))); }
                catch (IllegalArgumentException ignored) { }
                swarm.add(item);
            }
            repository.put(swarm);
        }
    }
    @NotNull
    @Override public NBTTagCompound writeToNBT(@NotNull NBTTagCompound tag) {
        NBTTagList swarms = new NBTTagList();
        for (SwarmRecord swarm : repository.snapshot()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setString("id", swarm.getSwarmId().toString());
            value.setString("owner", swarm.getOwnerId().toString());
            value.setString("name", swarm.getName());
            value.setString("state", swarm.getState().name());
            value.setString("leadership", swarm.getLeadership().name());
            value.setString("formation", swarm.getFormation().name());
            value.setString("result", swarm.getResult().name());
            value.setString("missionState", swarm.getMissionState().name());
            value.setString("lockState", swarm.getLockState().name());
            value.setString("operatorState", swarm.getOperatorState().name());
            value.setString("search", swarm.getSearchPattern().name());
            value.setBoolean("missionParticipation", swarm.isMissionParticipation());
            value.setInteger("missionMaximumMembers", swarm.getMissionMaximumMembers());
            value.setBoolean("allowDuplicateTargets", swarm.isAllowDuplicateTargets());
            if (swarm.getDesignatedLeader() != null) {
                value.setString("leader", swarm.getDesignatedLeader().toString());
            }
            NBTTagList members = new NBTTagList();
            for (SwarmMember item : swarm.getMembers()) {
                NBTTagCompound member = new NBTTagCompound();
                member.setString("aircraft", item.getAircraftId().toString());
                member.setString("profile", item.getProfile());
                member.setBoolean("longRange", item.supportsLongRange());
                member.setBoolean("payload", item.hasPayload());
                member.setString("state", item.getState().name());
                member.setBoolean("available", item.isAvailable());
                member.setBoolean("operatorControlled", item.isOperatorControlled());
                if (item.getAssignedTarget() != null) {
                    member.setString("target", item.getAssignedTarget().toString());
                }
                members.appendTag(member);
            }
            value.setTag("members", members);
            swarms.appendTag(value);
        }
        tag.setTag("swarms", swarms);
        return tag;
    }
}
