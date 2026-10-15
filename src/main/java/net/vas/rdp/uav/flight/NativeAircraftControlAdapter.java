package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.helicopter.MCH_EntityHeli;
import net.vas.rdp.uav.deconfliction.TrajectorySample;
import net.minecraft.world.World;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.MissionMovementAuthority;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;
import net.vas.rdp.uav.RdpUavDebug;

/**
 * The single addon-owned MCheliCE movement boundary.
 *
 * <p>Only the attitude/throttle setpoints are written here. MCheliCE's entity
 * update integrates those controls into velocity and position and performs its
 * normal collision handling.</p>
 */
public final class NativeAircraftControlAdapter {
    private static final float MAX_PITCH_STEP_DEGREES = 1.6F;
    private static final float MAX_ROLL_STEP_DEGREES = 2.4F;
    private static final double MAX_THROTTLE_STEP = 0.035D;

    private NativeAircraftControlAdapter() { }

    public static boolean apply(World world, MissionManager missions,
                                MCH_EntityAircraft aircraft, RDPMission mission,
                                NativeFlightControlCommand command, long tick) {
        if (world == null || world.isRemote || missions == null || aircraft == null
                || mission == null || command == null || !command.isSupported()
                || aircraft.isDead || aircraft.world != world
                || missions.activeMission(world, aircraft.getUniqueID()) != mission
                || !MissionMovementAuthority.claimControl(mission,
                aircraft.getUniqueID(), tick)
                || !MissionMovementAuthority.canIssueNativeControl(mission,
                aircraft.getUniqueID(), tick)
                || canControlAircraft(aircraft)) {
            return false;
        }

        UavVehicleProfile profile = profileFor(aircraft);
        if (profile == null) return false;
        boolean helicopter = aircraft instanceof MCH_EntityHeli || profile.isHelicopterProfile();
        float maxYawStep = (float) NativeControlMath.clamp(
                Math.toDegrees(Math.max(0.01D, aircraft.getCurrentThrottle())
                        / Math.max(8.0D, profile.getTurnRadius())),
                helicopter ? 1.5D : 0.8D,
                helicopter ? 4.0D : 3.5D);

        float yaw = NativeControlMath.approachAngle(
                aircraft.getYaw(), command.getYaw(), maxYawStep);
        boolean terminal = mission.getControlOwner()
                == net.vas.rdp.uav.mission.ControlOwner.TERMINAL_GUIDANCE;
        float pitchLimit = terminal
                ? (helicopter ? 45.0F : 60.0F)
                : (helicopter ? 24.0F : 22.0F);
        float rollLimit = NativeControlMath.maximumRollDegrees(profile, helicopter);
        float pitchTarget = NativeControlMath.clamp(command.getPitch(), -pitchLimit, pitchLimit);
        float rollTarget = NativeControlMath.clamp(command.getRoll(), -rollLimit, rollLimit);
        float pitch = NativeControlMath.approach(aircraft.getPitch(), pitchTarget,
                MAX_PITCH_STEP_DEGREES);
        float roll = NativeControlMath.approach(aircraft.getRoll(), rollTarget,
                MAX_ROLL_STEP_DEGREES);
        double throttle = NativeControlMath.approach(aircraft.getThrottle(),
                command.getThrottle(), MAX_THROTTLE_STEP);
        throttle = NativeControlMath.clamp(throttle, 0.0D, 1.0D);

        aircraft.setRotYaw(yaw);
        aircraft.setRotPitch(pitch);
        aircraft.setRotRoll(roll);
        aircraft.setThrottle(throttle);
        aircraft.updateControl();
        if (tick % 20L == 0L) {
            OperatorTrimState.Snapshot trim = OperatorTrimRegistry.snapshot(
                    aircraft.getUniqueID(), mission.getMissionId(), tick);
            RdpUavDebug.event("FlightControl", "COMMAND_APPLIED",
                    "mission", mission.getMissionId(), "aircraft", aircraft.getUniqueID(),
                    "stage", mission.getExecutionStage(), "owner", mission.getControlOwner(),
                    "profile", profile.getExecutionProfile(),
                    "finalGuidanceYaw", command.getYaw(), "finalGuidancePitch", command.getPitch(),
                    "finalGuidanceRoll", command.getRoll(), "finalGuidanceThrottle", command.getThrottle(),
                    "trimYaw", trim == null ? 0.0D : trim.getYawDegrees(),
                    "trimPitch", trim == null ? 0.0D : trim.getPitchDegrees(),
                    "trimRoll", trim == null ? 0.0D : trim.getRollDegrees(),
                    "trimAge", trim == null ? -1L : tick - trim.getSampleTick(),
                    "appliedYaw", yaw, "appliedPitch", pitch, "appliedRoll", roll,
                    "appliedThrottle", throttle, "observedYaw", aircraft.getYaw(),
                    "observedPitch", aircraft.getPitch(), "observedRoll", aircraft.getRoll(),
                    "x", aircraft.posX, "y", aircraft.posY, "z", aircraft.posZ,
                    "motionX", aircraft.motionX, "motionY", aircraft.motionY,
                    "motionZ", aircraft.motionZ);
        }
        return true;
    }

    /** Computes and applies one path sample through the existing leased control boundary. */
    public static boolean applyTrajectorySample(World world, MissionManager missions,
                                                MCH_EntityAircraft aircraft,
                                                RDPMission mission, AircraftState state,
                                                TrajectorySample sample, long tick) {
        if (aircraft == null || mission == null || state == null || sample == null
                || !aircraft.getUniqueID().equals(state.getAircraftId())
                || !mission.getMissionId().equals(state.getMissionId())) {
            return false;
        }
        UavVehicleProfile profile = profileFor(aircraft);
        if (profile == null) return false;
        NativeFlightControlCommand command = aircraft instanceof MCH_EntityHeli
                || profile.isHelicopterProfile()
                ? new HelicopterFlightController(profile).update(state, sample, tick)
                : new FixedWingFlightController(profile).update(state, sample, tick);
        return apply(world, missions, aircraft, mission, command, tick);
    }

    public static boolean canControlAircraft(MCH_EntityAircraft aircraft) {
        UavVehicleProfile profile = profileFor(aircraft);
        return aircraft == null || aircraft.isDead || profile == null;
    }

    public static String controlPathStatus(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return "NO_AIRCRAFT";
        return profileFor(aircraft) == null
                ? "UNSUPPORTED_PROFILE" : "RDP_NATIVE_PHYSICS_CONTROL";
    }

    private static UavVehicleProfile profileFor(MCH_EntityAircraft aircraft) {
        if (aircraft == null || aircraft.getAcInfo() == null) return null;
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        return profile == null || profile.getExecutionProfile()
                == net.vas.rdp.uav.profile.UavExecutionProfile.UNKNOWN ? null : profile;
    }
}
