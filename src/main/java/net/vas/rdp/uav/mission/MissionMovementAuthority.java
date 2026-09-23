package net.vas.rdp.uav.mission;

/**
 * Describes who may supply aircraft movement for a mission tick.
 *
 * <p>This is a policy value used by diagnostics and integrations. WingMan
 * remains the native movement implementation; the addon does not synthesize
 * aircraft motion from operator camera input.</p>
 */
public final class MissionMovementAuthority {
    private MissionMovementAuthority() { }

    public static boolean operatorFlightInputAllowed(RDPMission mission) {
        return mission == null || mission.getControlOwner() == ControlOwner.MANUAL;
    }

    public static boolean nativeWingManRequired(RDPMission mission) {
        if (mission == null) return false;
        ControlOwner owner = mission.getControlOwner();
        return owner == ControlOwner.MISSION_AI
                || owner == ControlOwner.TERMINAL_GUIDANCE
                || owner == ControlOwner.RECOVERY;
    }
}
