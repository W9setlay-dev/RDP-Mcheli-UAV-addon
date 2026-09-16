package net.vas.rdp.uav.swarm;

import net.minecraft.world.World;
import net.vas.rdp.uav.persistence.RDPUavSwarmWorldData;
import java.util.UUID;

/** Server-side swarm aggregate; individual missions remain MissionManager-owned. */
public final class SwarmManager {
    public SwarmRecord create(World world, UUID owner, String name) {
        SwarmRecord record = new SwarmRecord(UUID.randomUUID(), owner, name);
        RDPUavSwarmWorldData.get(world).put(record);
        return record;
    }
    public SwarmRecord find(World world, UUID id) {
        return world == null ? null : RDPUavSwarmWorldData.get(world).getRepository().find(id);
    }
    public boolean owns(SwarmRecord record, UUID owner) {
        return record != null && owner != null && owner.equals(record.getOwnerId());
    }
    public boolean addMember(World world, UUID owner, UUID swarmId, SwarmMember member) {
        SwarmRecord record = find(world, swarmId);
        if (!owns(record, owner) || member == null) return false;
        if (record.getMembers().size() >= SwarmConfig.maximumSize) return false;
        record.add(member);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean setState(World world, UUID owner, UUID id, SwarmState state) {
        SwarmRecord record = find(world, id);
        if (!owns(record, owner) || state == null) return false;
        if (state == SwarmState.AWAITING_OPERATOR && record.getResult() == SwarmResult.AWAITING_OPERATOR) {
            record.setResult(SwarmResult.AWAITING_OPERATOR);
        }
        record.setState(state);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean reform(World world, UUID owner, UUID id, SwarmFormation formation) {
        SwarmRecord record = find(world, id);
        if (!owns(record, owner) || formation == null) return false;
        record.setFormation(formation);
        record.setState(SwarmState.REORGANIZING);
        record.setState(SwarmState.READY);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public boolean removeMember(World world, UUID owner, UUID swarmId, UUID aircraftId) {
        SwarmRecord record = find(world, swarmId);
        if (!owns(record, owner)) return false;
        record.remove(aircraftId);
        RDPUavSwarmWorldData.get(world).markDirty();
        return true;
    }
    public void reconcile(World world) {
        if (world == null) return;
        for (SwarmRecord swarm : RDPUavSwarmWorldData.get(world).getRepository().snapshot()) {
            for (SwarmMember member : swarm.getMembers()) {
                boolean loaded = false;
                for (net.minecraft.entity.Entity entity : world.loadedEntityList) {
                    if (member.getAircraftId().equals(entity.getUniqueID()) && !entity.isDead) {
                        loaded = true;
                        break;
                    }
                }
                member.setAvailable(loaded);
                if (!loaded && member.getState() != SwarmMemberState.DESTROYED) {
                    member.setState(SwarmMemberState.DISCONNECTED);
                }
            }
            if (swarm.referenceMember() == null && !swarm.getMembers().isEmpty()) {
                swarm.setState(SwarmState.DEGRADED);
            } else if (swarm.getState() == SwarmState.DEGRADED) {
                swarm.setState(SwarmState.PARTIAL);
            }
        }
        RDPUavSwarmWorldData.get(world).markDirty();
    }
}
