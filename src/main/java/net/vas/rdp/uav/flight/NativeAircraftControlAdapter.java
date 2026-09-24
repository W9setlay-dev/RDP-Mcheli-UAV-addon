package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.world.World;
import net.vas.rdp.uav.mission.MissionManager;
import net.vas.rdp.uav.mission.MissionMovementAuthority;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;

/**
 * Server-side native McHeliCE control boundary.
 *
 * <p>McHeliCE owns the flight physics. This adapter only supplies bounded
 * attitude and throttle inputs through the public native setters during the
 * server tick; it never writes position or motion.</p>
 */
public final class NativeAircraftControlAdapter {
    private NativeAircraftControlAdapter() { }

    /**
     * Applies one authoritative server command. McHeliCE's setters are
     * absolute, so every value is rate-limited against the observed attitude.
     */
    public static boolean apply(World world, MissionManager missions,
                                MCH_EntityAircraft aircraft, RDPMission mission,
                                double targetX, double targetY, double targetZ,
                                FlightCommand fpvCommand, long tick) {
        if (world == null || world.isRemote || missions == null || aircraft == null
                || mission == null || aircraft.isDead
                || !MissionMovementAuthority.claimControl(mission,
                aircraft.getUniqueID(), tick)
                || !MissionMovementAuthority.canIssueNativeControl(mission,
                aircraft.getUniqueID(), tick)
                || !canControlAircraft(aircraft)) {
            return false;
        }
        ControlCommand command = commandFor(aircraft, targetX, targetY, targetZ, fpvCommand);
        if (!command.isSupported()) return false;

        aircraft.setRotYaw(command.getYaw());
        aircraft.setRotPitch(command.getPitch());
        aircraft.setRotRoll(command.getRoll());
        aircraft.setThrottle(command.getThrottle());
        aircraft.updateControl();
        return true;
    }

    public static boolean canControlAircraft(MCH_EntityAircraft aircraft) {
        return aircraft != null && !aircraft.isDead && isKnownProfile(aircraft)
                && !operatorControlConflict(aircraft);
    }

    private static boolean operatorControlConflict(MCH_EntityAircraft aircraft) {
        // Mission ownership is authoritative while active. Operator presence
        // is observation-only and must not turn a valid AI mission into an
        // instant failure; the server command is the control boundary.
        return false;
    }

    public static String controlPathStatus(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return "NO_AIRCRAFT";
        if (!isKnownProfile(aircraft)) return "UNSUPPORTED_PROFILE";
        if (operatorControlConflict(aircraft)) return "OPERATOR_CONTROL_CONFLICT";
        return "SERVER_NATIVE_CONTROL_SUPPORTED";
    }

    public static boolean isKnownProfile(MCH_EntityAircraft aircraft) {
        if (aircraft == null) return false;
        String definition = aircraft.getAcInfo() == null ? "" : aircraft.getAcInfo().name;
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(definition);
        return profile != null && profile.getExecutionProfile() != UavExecutionProfile.UNKNOWN;
    }

    static ControlCommand commandFor(MCH_EntityAircraft aircraft,
                                     double targetX, double targetY, double targetZ,
                                     FlightCommand fpvCommand) {
        if (aircraft == null || !finite(targetX, targetY, targetZ)) {
            return ControlCommand.unsupported("INVALID_TARGET");
        }
        String definition = aircraft.getAcInfo() == null ? "" : aircraft.getAcInfo().name;
        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(definition);
        if (profile == null || profile.getExecutionProfile() == UavExecutionProfile.UNKNOWN) {
            return ControlCommand.unsupported("UNSUPPORTED_PROFILE");
        }

        double dx = targetX - aircraft.posX;
        double dy = targetY - aircraft.posY;
        double dz = targetZ - aircraft.posZ;
        NativeControlMath.Command shaped = NativeControlMath.compute(
                aircraft.getYaw(), aircraft.getPitch(), aircraft.getRoll(),
                aircraft.getCurrentThrottle(), dx, dy, dz,
                profile.isHelicopterProfile(),
                fpvCommand == null ? Double.NaN : fpvCommand.getForward());
        return ControlCommand.supported(shaped.getYaw(), shaped.getPitch(),
                shaped.getRoll(), shaped.getThrottle());
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    public static final class ControlCommand {
        private final float yaw;
        private final float pitch;
        private final float roll;
        private final double throttle;
        private final String reason;

        private ControlCommand(float yaw, float pitch, float roll,
                               double throttle, String reason) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.throttle = throttle;
            this.reason = reason;
        }

        private static ControlCommand supported(float yaw, float pitch,
                                                float roll, double throttle) {
            return new ControlCommand(yaw, pitch, roll, throttle, "OK");
        }

        private static ControlCommand unsupported(String reason) {
            return new ControlCommand(0.0F, 0.0F, 0.0F, 0.0D, reason);
        }

        public boolean isSupported() { return "OK".equals(reason); }
        public String getReason() { return reason; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public float getRoll() { return roll; }
        public double getThrottle() { return throttle; }
    }
}
