package net.vas.rdp.uav.persistence;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.mission.MissionWaypoint;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.MissionPhase;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.mission.MissionExecutionStage;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSelectionMode;
import net.vas.rdp.uav.target.TargetSpec;

import java.util.UUID;

public final class MissionNbtCodec {
    private static final int CURRENT_SCHEMA = 7;
    private MissionNbtCodec() {
    }

    public static NBTTagCompound write(RDPMission mission) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schemaVersion", CURRENT_SCHEMA);
        tag.setString("missionId", mission.getMissionId().toString());
        tag.setString("ownerId", mission.getOwnerId().toString());
        tag.setString("aircraftId", mission.getAircraftId().toString());
        tag.setString("aircraftType", mission.getAircraftType());
        tag.setString("policyDomain", mission.getTargetPolicy().getDomain());
        tag.setInteger("policyVersion", mission.getTargetPolicy().getVersion());
        writeList(tag, "policyInclude", mission.getTargetPolicy().getInclude());
        writeList(tag, "policyRequire", mission.getTargetPolicy().getRequire());
        writeList(tag, "policyExclude", mission.getTargetPolicy().getExclude());
        tag.setBoolean("policyRejectEmbedded", mission.getTargetPolicy().isRejectEmbedded());
        tag.setBoolean("policyAllowVehicles", mission.getTargetPolicy().isAllowVehicleTargets());
        tag.setDouble("policyMinimumConfidence", mission.getTargetPolicy().getMinimumConfidence());
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
        tag.setDouble("routeProgress", mission.getRouteProgress());
        tag.setLong("lastMeaningfulProgressTick", mission.getLastMeaningfulProgressTick());
        tag.setLong("lastTargetTick", mission.getLastTargetTick());
        tag.setString("state", mission.getState().name());
        tag.setString("phase", mission.getPhase().name());
        tag.setString("controlOwner", mission.getControlOwner().name());
        tag.setString("executionStage", mission.getExecutionStage().name());
        tag.setString("parentMissionId", mission.getParentMissionId() == null
                ? "" : mission.getParentMissionId().toString());
        tag.setString("childMissionId", mission.getChildMissionId() == null
                ? "" : mission.getChildMissionId().toString());
        tag.setString("targetUuid", mission.getTargetId() == null ? "" : mission.getTargetId().toString());
        tag.setString("failureReason", mission.getFailureReason() == null ? "" : mission.getFailureReason());
        tag.setString("lastTransitionReason", mission.getLastTransitionReason() == null
                ? "" : mission.getLastTransitionReason());
        tag.setString("reportId", mission.getReportId() == null ? "" : mission.getReportId());
        tag.setInteger("dimension", mission.getDimension());
        tag.setBoolean("queued", mission.isQueued());
        tag.setString("recoveryPolicy", mission.getRecoveryPolicy());
        tag.setBoolean("destructiveAuthorized", mission.isDestructiveAuthorized());
        tag.setBoolean("kamikazePayloadLaunched", mission.isKamikazePayloadLaunched());
        tag.setBoolean("paused", mission.isPaused());
        tag.setString("guidedWeapon", mission.getGuidedWeapon() == null ? "" : mission.getGuidedWeapon());
        tag.setInteger("recoveryAttempts", mission.getRecoveryAttempts());
        tag.setString("recoveryReason", mission.getRecoveryReason() == null ? "" : mission.getRecoveryReason());
        tag.setDouble("squareWidth", mission.getSquareWidth());
        tag.setDouble("squareDepth", mission.getSquareDepth());
        if (!mission.getPatrolRoute().isEmpty()) {
            NBTTagList route = new NBTTagList();
            for (MissionWaypoint waypoint : mission.getPatrolRoute()) {
                NBTTagCompound point = new NBTTagCompound();
                point.setDouble("x", waypoint.getX());
                point.setDouble("z", waypoint.getZ());
                point.setInteger("loiterTicks", waypoint.getLoiterTicks());
                route.appendTag(point);
            }
            tag.setTag("patrolRoute", route);
        }
        return tag;
    }

    public static RDPMission read(NBTTagCompound tag) {
        if (tag == null) throw new IllegalArgumentException("Mission NBT is required");
        String registryId = tag.getString("targetId");
        String namespace = tag.getString("targetNamespace");
        TargetSelectionMode mode = enumValue(tag, "targetMode", TargetSelectionMode.class);
        TargetPriority priority = enumValue(tag, "priority", TargetPriority.class);
        TargetSpec spec = mode == TargetSelectionMode.MANUAL
                ? TargetSpec.manual(registryId, priority)
                : mode == TargetSelectionMode.AUTO
                ? TargetSpec.automatic(namespace, priority)
                : TargetSpec.point(finiteDouble(tag, "targetX"),
                        finiteDouble(tag, "targetY"), finiteDouble(tag, "targetZ"), priority);
        String targetUuid = tag.getString("targetUuid");
        String failureReason = tag.getString("failureReason");
        String report = tag.getString("reportId");
        int schemaVersion = tag.hasKey("schemaVersion") ? tag.getInteger("schemaVersion") : 1;
        MissionState restoredState = enumValue(tag, "state", MissionState.class);
        // v1 used DIRECT_LAUNCH_COMPLETE for an accepted release. It did not
        // prove impact, so migrate it to the new non-terminal state.
        if (schemaVersion < CURRENT_SCHEMA
                && restoredState == MissionState.DIRECT_LAUNCH_COMPLETE) {
            restoredState = MissionState.LAUNCH_ACCEPTED;
        }
        RDPMission mission = RDPMission.restore(requiredUuid(tag, "missionId"),
                requiredUuid(tag, "ownerId"),
                requiredUuid(tag, "aircraftId"),
                requiredString(tag, "aircraftType"),
                enumValue(tag, "missionType", MissionType.class), spec,
                tag.getBoolean("attack"), tag.getBoolean("eliminateAllCost"),
                tag.getBoolean("deepScan"), positiveDouble(tag, "searchRadius"),
                positiveDouble(tag, "observationDistance"), tag.getLong("createdTick"),
                restoredState,
                targetUuid.isEmpty() ? null : parseUuid(targetUuid, "targetUuid"),
                tag.getLong("lastUpdateTick"), failureReason.isEmpty() ? null : failureReason,
                report.isEmpty() ? null : report);
        MissionExecutionStage executionStage = tag.hasKey("executionStage")
                ? enumValue(tag, "executionStage", MissionExecutionStage.class)
                : MissionExecutionStage.fromState(restoredState, mission.getMissionType());
        if (executionStage != MissionExecutionStage.fromState(
                restoredState, mission.getMissionType())) {
            throw new IllegalArgumentException("Mission execution stage does not match state");
        }
        String parent = tag.hasKey("parentMissionId") ? tag.getString("parentMissionId") : "";
        String child = tag.hasKey("childMissionId") ? tag.getString("childMissionId") : "";
        mission.restoreExecutionMetadata(executionStage,
                parent.isEmpty() ? null : parseUuid(parent, "parentMissionId"),
                child.isEmpty() ? null : parseUuid(child, "childMissionId"));
        mission.setDimension(tag.getInteger("dimension"));
        if (mission.getMissionType() == MissionType.PATROL && tag.hasKey("patrolRoute")) {
            NBTTagList routeTag = tag.getTagList("patrolRoute", 10);
            if (routeTag.tagCount() > net.vas.rdp.uav.mission.PatrolRouteCodec.MAX_WAYPOINTS) {
                throw new IllegalArgumentException("Patrol route exceeds waypoint limit");
            }
            java.util.List<MissionWaypoint> route = new java.util.ArrayList<>();
            for (int i = 0; i < routeTag.tagCount(); i++) {
                NBTTagCompound point = routeTag.getCompoundTagAt(i);
                route.add(new MissionWaypoint(finiteDouble(point, "x"),
                        finiteDouble(point, "z"), point.getInteger("loiterTicks")));
            }
            mission.setPatrolRoute(route);
        }
        mission.restoreProgressState(
                tag.hasKey("routeProgress") ? tag.getDouble("routeProgress") : 0.0D,
                tag.hasKey("lastMeaningfulProgressTick")
                        ? tag.getLong("lastMeaningfulProgressTick") : mission.getCreatedTick());
        mission.restoreLastTargetTick(tag.hasKey("lastTargetTick")
                ? tag.getLong("lastTargetTick") : -1L);
        if (tag.hasKey("policyDomain") && !"ENTITY".equals(tag.getString("policyDomain"))) {
            throw new IllegalArgumentException("Unsupported target policy domain");
        }
        net.vas.rdp.uav.target.TargetPolicy.Builder policy =
                net.vas.rdp.uav.target.TargetPolicy.builder();
        addList(policy, tag, "policyInclude", 0);
        addList(policy, tag, "policyRequire", 1);
        addList(policy, tag, "policyExclude", 2);
        if (tag.hasKey("policyRejectEmbedded")) {
            policy.rejectDeeplyEmbedded(tag.getBoolean("policyRejectEmbedded"));
        }
        if (tag.hasKey("policyAllowVehicles")) {
            policy.allowHostileVehicles(tag.getBoolean("policyAllowVehicles"));
        }
        if (tag.hasKey("policyMinimumConfidence")) {
            policy.withMinimumConfidence(tag.getDouble("policyMinimumConfidence"));
        }
        mission.setTargetPolicy(policy.snapshot());
        if (tag.hasKey("lastTransitionReason")) {
            mission.restoreTransitionReason(tag.getString("lastTransitionReason"));
        }
        if (tag.hasKey("squareWidth") && tag.hasKey("squareDepth")
                && tag.getDouble("squareWidth") > 0.0D && tag.getDouble("squareDepth") > 0.0D) {
            mission.setSquareArea(tag.getDouble("squareWidth"), tag.getDouble("squareDepth"));
        }
        mission.setQueued(tag.hasKey("queued") && tag.getBoolean("queued"));
        if (mission.isQueued() && mission.isTerminal()) {
            throw new IllegalArgumentException("Terminal mission cannot be queued");
        }
        if (tag.hasKey("recoveryPolicy")) mission.setRecoveryPolicy(tag.getString("recoveryPolicy"));
        mission.setDestructiveAuthorized(tag.getBoolean("destructiveAuthorized"));
        mission.restoreKamikazePayloadLaunched(tag.getBoolean("kamikazePayloadLaunched"));
        mission.setPaused(tag.getBoolean("paused"));
        if (tag.hasKey("guidedWeapon")) mission.setGuidedWeapon(tag.getString("guidedWeapon"));
        String recoveryReason = tag.getString("recoveryReason");
        mission.restoreRecovery(tag.getInteger("recoveryAttempts"), recoveryReason);
        if (tag.hasKey("phase")) {
            MissionPhase persistedPhase = enumValue(tag, "phase", MissionPhase.class);
            if (persistedPhase != mission.getPhase()) {
                throw new IllegalArgumentException("Mission phase does not match state");
            }
        }
        if (tag.hasKey("controlOwner")) {
            ControlOwner persistedOwner = enumValue(tag, "controlOwner", ControlOwner.class);
            if (persistedOwner != mission.getControlOwner()) {
                throw new IllegalArgumentException("Mission control owner does not match state");
            }
        }
        return mission;
    }

    private static void writeList(NBTTagCompound tag, String key, java.util.List<String> values) {
        NBTTagList list = new NBTTagList();
        for (String value : values) {
            NBTTagCompound item = new NBTTagCompound();
            item.setString("value", value);
            list.appendTag(item);
        }
        tag.setTag(key, list);
    }

    private static void addList(net.vas.rdp.uav.target.TargetPolicy.Builder policy,
                                    NBTTagCompound tag, String key, int kind) {
            NBTTagList list = tag.getTagList(key, 10);
            String[] values = new String[list.tagCount()];
            for (int i = 0; i < list.tagCount(); i++) {
                values[i] = list.getCompoundTagAt(i).getString("value");
            }
            if (kind == 0) policy.include(values);
            else if (kind == 1) policy.require(values);
            else policy.exclude(values);
        }

    private static String requiredString(NBTTagCompound tag, String key) {
        String value = tag.getString(key);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Mission NBT field is required: " + key);
        }
        return value;
    }

    private static UUID requiredUuid(NBTTagCompound tag, String key) {
        return parseUuid(requiredString(tag, key), key);
    }

    private static double positiveDouble(NBTTagCompound tag, String key) {
        double value = finiteDouble(tag, key);
        if (value <= 0.0D) {
            throw new IllegalArgumentException("Mission NBT field must be positive: " + key);
        }
        return value;
    }

    private static double finiteDouble(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key)) {
            throw new IllegalArgumentException("Mission NBT field is required: " + key);
        }
        double value = tag.getDouble(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Mission NBT field must be finite: " + key);
        }
        return value;
    }

    private static UUID parseUuid(String value, String key) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid mission NBT UUID: " + key, failure);
        }
    }

    private static <E extends Enum<E>> E enumValue(NBTTagCompound tag, String key,
                                                    Class<E> type) {
        String value = requiredString(tag, key);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid mission NBT value for " + key
                    + ": " + value, failure);
        }
    }
}
