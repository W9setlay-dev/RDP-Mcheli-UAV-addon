package net.vas.rdp.uav.designation;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.vas.rdp.uav.RDPUavMod;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.mission.UavControlAccess;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import net.vas.rdp.uav.target.TargetPolicy;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class DesignationManager {
    private final DesignationRegistry registry;

    public DesignationManager() {
        this(new DesignationRegistry());
    }

    public DesignationManager(DesignationRegistry registry) {
        this.registry = registry == null ? new DesignationRegistry() : registry;
    }

    public static DesignationManager forWorld(World world) {
        DesignationWorldData data = DesignationWorldData.get(world);
        return data == null ? new DesignationManager() : new DesignationManager(data.getRegistry());
    }

    public synchronized Designation find(UUID designationId) {
        return registry.find(designationId);
    }

    public synchronized List<Designation> byOwner(UUID operatorId) {
        if (operatorId == null) return Collections.emptyList();
        return registry.byOperator(operatorId);
    }

    public synchronized List<DesignationView> snapshotForOperator(UUID operatorId) {
        List<Designation> designations = byOwner(operatorId);
        List<DesignationView> views = new ArrayList<>();
        for (Designation designation : designations) views.add(designation.toView());
        return views;
    }

    public synchronized Designation add(Designation designation) {
        registry.add(designation);
        return designation;
    }

    public synchronized Designation remove(UUID designationId) {
        return registry.remove(designationId);
    }

    public synchronized Designation attachMission(UUID designationId, UUID missionId) {
        Designation designation = registry.find(designationId);
        if (designation == null || designation.getAttachedMissionId() != null) return designation;
        Designation updated = Designation.builder()
                .designationId(designation.getDesignationId())
                .operatorId(designation.getOperatorId())
                .dimension(designation.getDimension())
                .type(designation.getType())
                .source(designation.getSource())
                .createdAtTick(designation.getCreatedAtTick())
                .expiresAtTick(designation.getExpiresAtTick())
                .confidence(designation.getConfidence())
                .provenance(designation.getProvenance())
                .point(designation.getPoint())
                .entity(designation.getEntity())
                .area(designation.getArea())
                .composite(designation.getComposite())
                .priority(designation.getPriority())
                .notes(designation.getNotes())
                .attachedMissionId(missionId)
                .build();
        registry.remove(designationId);
        registry.add(updated);
        return updated;
    }

    public synchronized RDPMission promoteToMission(EntityPlayerMP player, UUID designationId, MissionType requestedType) {
        if (player == null || player.world == null || designationId == null) return null;
        Designation designation = registry.find(designationId);
        if (designation == null || designation.isAttached() || !player.getUniqueID().equals(designation.getOperatorId())) {
            return null;
        }
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
        if (aircraft == null || !UavControlAccess.isOperator(aircraft, player)
                || RDPUavMod.missions().ensureAuthorized(aircraft, player)
                || RDPUavMod.missions().isAircraftUnavailable(player.world, aircraft.getUniqueID(), player.getUniqueID())) {
            return null;
        }
        if (aircraft.getAcInfo() == null) return null;
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        if (profile == null || profile.getExecutionProfile() == UavExecutionProfile.UNKNOWN) return null;

        MissionType missionType = requestedType == null ? MissionType.STRIKE : requestedType;
        if (missionType != MissionType.STRIKE && missionType != MissionType.DESTROYING)
            missionType = MissionType.STRIKE;

        UUID entityId = resolveEntityId(designation);
        String registryId = resolveRegistryId(designation);
        double targetX = resolvePointX(designation);
        double targetY = resolvePointY(designation);
        double targetZ = resolvePointZ(designation);

        if (entityId != null) {
            Entity target = new net.vas.rdp.uav.target.TargetResolver().findLoaded(player.world, entityId);
            if (target == null || target == aircraft || target == player || !target.canBeCollidedWith()
                    || !TargetPolicy.isSelectableExactContact(player.world, target, player)
                    || target.getDistance(aircraft) > profile.getLockRange()) {
                return null;
            }
            if (registryId == null || registryId.trim().isEmpty()) {
                registryId = net.minecraft.entity.EntityList.getKey(target) == null ? null : net.minecraft.entity.EntityList.getKey(target).toString();
            }
            if (registryId == null || registryId.trim().isEmpty()) return null;
        } else {
            if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) return null;
            double dx = aircraft.posX - targetX;
            double dy = aircraft.posY - targetY;
            double dz = aircraft.posZ - targetZ;
            if (dx * dx + dy * dy + dz * dz > profile.getDetectionRange() * profile.getDetectionRange()) {
                return null;
            }
        }

        long tick = player.world.getTotalWorldTime();
        TargetSpec targetSpec = entityId != null
                ? TargetSpec.manual(registryId, TargetPriority.HIGH)
                : TargetSpec.point(targetX, targetY, targetZ, TargetPriority.HIGH);
        RDPMission mission = new RDPMission(UUID.randomUUID(), player.getUniqueID(), aircraft.getUniqueID(),
                aircraft.getAcInfo().name, missionType, targetSpec,
                true, false, false, profile.getDetectionRange(), profile.getMissionExecutionRange(), tick);
        mission.setDimension(designation.getDimension());
        if (entityId != null) mission.reacquireTarget(entityId, tick);

        RDPUavMod.missions().create(player.world, mission);
        RDPMissionWorldData.get(player.world).markDirty();
        attachMission(designationId, mission.getMissionId());
        return mission;
    }

    private UUID resolveEntityId(Designation designation) {
        if (designation == null) return null;
        if (designation.getEntity() != null) return designation.getEntity().entityUuid;
        if (designation.getComposite() != null && designation.getComposite().entity != null) {
            return designation.getComposite().entity.entityUuid;
        }
        return null;
    }

    private String resolveRegistryId(Designation designation) {
        if (designation == null) return null;
        if (designation.getEntity() != null) return designation.getEntity().registryId;
        if (designation.getComposite() != null && designation.getComposite().entity != null) {
            return designation.getComposite().entity.registryId;
        }
        return null;
    }

    private double resolvePointX(Designation designation) {
        if (designation == null) return Double.NaN;
        if (designation.getPoint() != null) return designation.getPoint().x;
        if (designation.getComposite() != null && designation.getComposite().point != null) return designation.getComposite().point.x;
        if (designation.getEntity() != null) return designation.getEntity().lastKnownX;
        if (designation.getComposite() != null && designation.getComposite().entity != null) return designation.getComposite().entity.lastKnownX;
        return Double.NaN;
    }

    private double resolvePointY(Designation designation) {
        if (designation == null) return Double.NaN;
        if (designation.getPoint() != null) return designation.getPoint().y;
        if (designation.getComposite() != null && designation.getComposite().point != null) return designation.getComposite().point.y;
        if (designation.getEntity() != null) return designation.getEntity().lastKnownY;
        if (designation.getComposite() != null && designation.getComposite().entity != null) return designation.getComposite().entity.lastKnownY;
        return Double.NaN;
    }

    private double resolvePointZ(Designation designation) {
        if (designation == null) return Double.NaN;
        if (designation.getPoint() != null) return designation.getPoint().z;
        if (designation.getComposite() != null && designation.getComposite().point != null) return designation.getComposite().point.z;
        if (designation.getEntity() != null) return designation.getEntity().lastKnownZ;
        if (designation.getComposite() != null && designation.getComposite().entity != null) return designation.getComposite().entity.lastKnownZ;
        return Double.NaN;
    }

    public synchronized int countPending(UUID operatorId) {
        return registry.unresolvedCount(operatorId);
    }

    public synchronized void expire(long tick) {
        registry.expire(tick);
    }

    public DesignationRegistry getRegistry() {
        return registry;
    }
}
