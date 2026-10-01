package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.vas.rdp.uav.RDPUavMod;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import net.vas.rdp.uav.target.TargetPolicy;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;

import java.util.UUID;

/** Server-authoritative creation of a mission from a client camera designation. */
public final class OperatorAimMissionService {
    private static final double POSITION_EPSILON = 1.0E-3D;
    private static final double POINT_OBSERVATION_DISTANCE = 32.0D;

    private OperatorAimMissionService() { }

    public static Result createPointMission(EntityPlayerMP operator,
                                            BlockPos blockPos,
                                            double hitX, double hitY, double hitZ) {
        Context context = context(operator);
        if (context.error != null) return Result.rejected(context.error);
        if (blockPos == null || !Double.isFinite(hitX) || !Double.isFinite(hitY)
                || !Double.isFinite(hitZ)) {
            return Result.rejected("Camera designation contains invalid coordinates.");
        }

        World world = operator.world;
        if (blockPos.getY() < 0 || blockPos.getY() >= world.getHeight()
                || !world.getWorldBorder().contains(blockPos)
                || !world.isBlockLoaded(blockPos)) {
            return Result.rejected("Selected block is outside the loaded, valid world area.");
        }
        if (!insideReportedBlock(blockPos, hitX, hitY, hitZ)) {
            return Result.rejected("Selected hit point does not match the reported block.");
        }
        if (world.getBlockState(blockPos).getCollisionBoundingBox(world, blockPos) == null) {
            return Result.rejected("Selected block no longer has a collision surface.");
        }
        if (!withinRange(context.aircraft, hitX, hitY, hitZ,
                context.profile.getDetectionRange())) {
            return Result.rejected("Selected point is outside this aircraft profile's mission range.");
        }

        long tick = world.getTotalWorldTime();
        assert context.aircraft.getAcInfo() != null;
        RDPMission mission = new RDPMission(UUID.randomUUID(), operator.getUniqueID(),
                context.aircraft.getUniqueID(), context.aircraft.getAcInfo().name,
                MissionType.STRIKE,
                TargetSpec.point(hitX, hitY, hitZ, TargetPriority.HIGH),
                true, false, false, context.profile.getDetectionRange(),
                POINT_OBSERVATION_DISTANCE, tick);
        mission.setDimension(operator.dimension);
        return persist(context, mission, "BLOCK", null, hitX, hitY, hitZ);
    }

    public static Result createEntityMission(EntityPlayerMP operator, UUID targetId) {
        Context context = context(operator);
        if (context.error != null) return Result.rejected(context.error);
        if (targetId == null) return Result.rejected("No entity was selected by the camera ray.");
        if (!context.profile.supportsTargetLock()) {
            return Result.rejected("This aircraft profile does not support exact entity targets.");
        }

        Entity target = new net.vas.rdp.uav.target.TargetResolver()
                .findLoaded(operator.world, targetId);
        if (target == null || target == context.aircraft || target == operator
                || !target.canBeCollidedWith()) {
            return Result.rejected("Selected entity is no longer available in this dimension.");
        }
        if (!TargetPolicy.isSelectableExactContact(operator.world, target, operator)) {
            return Result.rejected("Selected entity is rejected by the active target safety policy.");
        }
        if (target.getDistance(context.aircraft) > context.profile.getLockRange()) {
            return Result.rejected("Selected entity is outside this aircraft profile's lock range.");
        }
        ResourceLocation registryKey = EntityList.getKey(target);
        if (registryKey == null) return Result.rejected("Selected entity has no registry identity.");

        long tick = operator.world.getTotalWorldTime();
        assert context.aircraft.getAcInfo() != null;
        RDPMission mission = new RDPMission(UUID.randomUUID(), operator.getUniqueID(),
                context.aircraft.getUniqueID(), context.aircraft.getAcInfo().name,
                MissionType.STRIKE,
                TargetSpec.manual(registryKey.toString(), TargetPriority.HIGH),
                true, false, false, context.profile.getDetectionRange(),
                POINT_OBSERVATION_DISTANCE, tick);
        mission.setDimension(operator.dimension);
        // Preserve the exact selected entity UUID. The mission must not replace
        // it with another nearby entity of the same registry type if it unloads.
        mission.reacquireTarget(targetId, tick);
        return persist(context, mission, "ENTITY", targetId,
                target.posX, target.posY, target.posZ);
    }

    private static Context context(EntityPlayerMP operator) {
        if (operator == null || operator.world == null) {
            return Context.error("Operator world is unavailable.");
        }
        MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(operator);
        if (!UavControlAccess.isOperator(aircraft, operator)) {
            return Context.error("Point task requires an authorized McHeli aircraft operator.");
        }
        if (RDPUavMod.missions().ensureAuthorized(aircraft, operator)) {
            return Context.error("This aircraft is not registered to the current operator.");
        }
        if (RDPUavMod.missions().isAircraftUnavailable(
                operator.world, aircraft.getUniqueID(), operator.getUniqueID())) {
            return Context.error("This aircraft already has an assigned mission.");
        }
        if (aircraft.getAcInfo() == null) {
            return Context.error("Aircraft metadata is unavailable.");
        }
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        if (profile == null || profile.getExecutionProfile() == UavExecutionProfile.UNKNOWN) {
            return Context.error("This aircraft has no supported mission profile.");
        }
        return new Context(operator.world, aircraft, profile, null);
    }

    private static Result persist(Context context, RDPMission mission, String hitType,
                                  UUID targetId, double x, double y, double z) {
        World world = context.world;
        try {
            RDPUavMod.missions().create(world, mission);
            RDPMissionWorldData.get(world).markDirty();
            RdpUavDebug.event("PointTask", "CAMERA_AIM_ACCEPTED",
                    "mission", mission.getMissionId(),
                    "aircraft", mission.getAircraftId(),
                    "hitType", hitType,
                    "target", targetId,
                    "x", x, "y", y, "z", z,
                    "dimension", mission.getDimension());
            return Result.accepted(mission.getMissionId());
        } catch (RuntimeException failure) {
            RdpUavDebug.event("PointTask", "CAMERA_AIM_REJECTED",
                    "aircraft", mission.getAircraftId(),
                    "hitType", hitType,
                    "reason", failure.getMessage());
            return Result.rejected(failure.getMessage() == null
                    ? "Mission assignment failed." : failure.getMessage());
        }
    }

    private static boolean insideReportedBlock(BlockPos pos, double x, double y, double z) {
        return x >= pos.getX() - POSITION_EPSILON
                && x <= pos.getX() + 1.0D + POSITION_EPSILON
                && y >= pos.getY() - POSITION_EPSILON
                && y <= pos.getY() + 1.0D + POSITION_EPSILON
                && z >= pos.getZ() - POSITION_EPSILON
                && z <= pos.getZ() + 1.0D + POSITION_EPSILON;
    }

    private static boolean withinRange(MCH_EntityAircraft aircraft,
                                      double x, double y, double z, double range) {
        double dx = aircraft.posX - x;
        double dy = aircraft.posY - y;
        double dz = aircraft.posZ - z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        return Double.isFinite(distanceSquared) && Double.isFinite(range)
                && range > 0.0D && distanceSquared <= range * range;
    }

    public static final class Result {
        private final boolean accepted;
        private final UUID missionId;
        private final String message;

        private Result(boolean accepted, UUID missionId, String message) {
            this.accepted = accepted;
            this.missionId = missionId;
            this.message = message;
        }

        private static Result accepted(UUID missionId) {
            return new Result(true, missionId, "Camera-selected mission accepted.");
        }

        private static Result rejected(String message) {
            return new Result(false, null, message);
        }

        public boolean isAccepted() { return accepted; }
        public UUID getMissionId() { return missionId; }
        public String getMessage() { return message; }
    }

    private static final class Context {
        private final World world;
        private final MCH_EntityAircraft aircraft;
        private final UavVehicleProfile profile;
        private final String error;

        private Context(World world, MCH_EntityAircraft aircraft,
                        UavVehicleProfile profile, String error) {
            this.world = world;
            this.aircraft = aircraft;
            this.profile = profile;
            this.error = error;
        }

        private static Context error(String message) {
            return new Context(null, null, null, message);
        }
    }
}
