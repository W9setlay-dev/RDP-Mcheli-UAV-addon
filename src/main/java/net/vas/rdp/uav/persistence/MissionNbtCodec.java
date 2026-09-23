package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.target.TargetSpec;

import java.util.UUID;

public final class MissionNbtCodec {
    private static final int CURRENT_SCHEMA = 2;
    private MissionNbtCodec() {
    }

    public static NBTTagCompound write(RDPMission mission) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schemaVersion", CURRENT_SCHEMA);
        tag.setString("missionId", mission.getMissionId().toString());
        tag.setString("ownerId", mission.getOwnerId().toString());
        tag.setString("aircraftId", mission.getAircraftId().toString());
        tag.setString("aircraftType", mission.getAircraftType());
        tag.setString("missionType", mission.getMissionType().name());
        tag.setString("targetMode", mission.getTargetSelectionMode().name());
        tag.setString("targetId", mission.getTargetSpec().getRegistryId() == null
                ? "" : mission.getTargetSpec().getRegistryId());
        tag.setString("targetNamespace", mission.getTargetSpec().getNamespace() == null
                ? "" : mission.getTargetSpec().getNamespace());
        tag.setDouble("targetX", mission.getTargetSpec().getX());
        tag.setDouble("targetY", mission.getTargetSpec().getY());
        tag.setDouble("targetZ", mission.getTargetSpec().getZ());
        tag.setString("priority", mission.getTargetPriority().name());
        tag.setBoolean("attack", mission.isAttack());
        tag.setBoolean("eliminateAllCost", mission.isEliminateAllCost());
        tag.setBoolean("deepScan", mission.isDeepScan());
        tag.setDouble("searchRadius", mission.getSearchRadius());
        tag.setDouble("observationDistance", mission.getObservationDistance());
        tag.setLong("createdTick", mission.getCreatedTick());
        tag.setLong("lastUpdateTick", mission.getLastUpdateTick());
        tag.setString("state", mission.getState().name());
        tag.setString("targetUuid", mission.getTargetId() == null ? "" : mission.getTargetId().toString());
        tag.setString("failureReason", mission.getFailureReason() == null ? "" : mission.getFailureReason());
        tag.setString("reportId", mission.getReportId() == null ? "" : mission.getReportId());
        tag.setInteger("dimension", mission.getDimension());
        tag.setBoolean("queued", mission.isQueued());
        tag.setString("recoveryPolicy", mission.getRecoveryPolicy());
        tag.setBoolean("destructiveAuthorized", mission.isDestructiveAuthorized());
        tag.setBoolean("paused", mission.isPaused());
        tag.setString("guidedWeapon", mission.getGuidedWeapon() == null ? "" : mission.getGuidedWeapon());
        tag.setInteger("recoveryAttempts", mission.getRecoveryAttempts());
        tag.setString("recoveryReason", mission.getRecoveryReason() == null ? "" : mission.getRecoveryReason());
        tag.setDouble("squareWidth", mission.getSquareWidth());
        tag.setDouble("squareDepth", mission.getSquareDepth());
        return tag;
    }

    public static RDPMission read(NBTTagCompound tag) {
        String registryId = tag.getString("targetId");
        String namespace = tag.getString("targetNamespace");
        TargetSelectionMode mode = TargetSelectionMode.valueOf(tag.getString("targetMode"));
        TargetSpec spec = mode == TargetSelectionMode.MANUAL
                ? TargetSpec.manual(registryId, TargetPriority.valueOf(tag.getString("priority")))
                : mode == TargetSelectionMode.AUTO
                ? TargetSpec.automatic(namespace, TargetPriority.valueOf(tag.getString("priority")))
                : TargetSpec.point(tag.getDouble("targetX"), tag.getDouble("targetY"),
                        tag.getDouble("targetZ"), TargetPriority.valueOf(tag.getString("priority")));
        String targetUuid = tag.getString("targetUuid");
        String failureReason = tag.getString("failureReason");
        String report = tag.getString("reportId");
        int schemaVersion = tag.hasKey("schemaVersion") ? tag.getInteger("schemaVersion") : 1;
        String stateName = tag.getString("state");
        MissionState restoredState;
        try {
            restoredState = MissionState.valueOf(stateName);
        } catch (IllegalArgumentException failure) {
            restoredState = MissionState.FAILED;
        }
        // v1 used DIRECT_LAUNCH_COMPLETE for an accepted release. It did not
        // prove impact, so migrate it to the new non-terminal state.
        if (schemaVersion < CURRENT_SCHEMA
                && restoredState == MissionState.DIRECT_LAUNCH_COMPLETE) {
            restoredState = MissionState.LAUNCH_ACCEPTED;
        }
        RDPMission mission = RDPMission.restore(UUID.fromString(tag.getString("missionId")),
                UUID.fromString(tag.getString("ownerId")),
                UUID.fromString(tag.getString("aircraftId")),
                tag.getString("aircraftType"),
                MissionType.valueOf(tag.getString("missionType")), spec,
                tag.getBoolean("attack"), tag.getBoolean("eliminateAllCost"),
                tag.getBoolean("deepScan"), tag.getDouble("searchRadius"),
                tag.getDouble("observationDistance"), tag.getLong("createdTick"),
                restoredState,
                targetUuid.isEmpty() ? null : UUID.fromString(targetUuid),
                tag.getLong("lastUpdateTick"), failureReason.isEmpty() ? null : failureReason,
                report.isEmpty() ? null : report);
        mission.setDimension(tag.getInteger("dimension"));
        if (tag.hasKey("squareWidth") && tag.hasKey("squareDepth")
                && tag.getDouble("squareWidth") > 0.0D && tag.getDouble("squareDepth") > 0.0D) {
            mission.setSquareArea(tag.getDouble("squareWidth"), tag.getDouble("squareDepth"));
        }
        mission.setQueued(tag.hasKey("queued") && tag.getBoolean("queued"));
        if (tag.hasKey("recoveryPolicy")) mission.setRecoveryPolicy(tag.getString("recoveryPolicy"));
        mission.setDestructiveAuthorized(tag.getBoolean("destructiveAuthorized"));
        mission.setPaused(tag.getBoolean("paused"));
        if (tag.hasKey("guidedWeapon")) mission.setGuidedWeapon(tag.getString("guidedWeapon"));
        String recoveryReason = tag.getString("recoveryReason");
        mission.restoreRecovery(tag.getInteger("recoveryAttempts"), recoveryReason);
        return mission;
    }
}
