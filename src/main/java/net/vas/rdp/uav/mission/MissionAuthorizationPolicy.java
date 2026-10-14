package net.vas.rdp.uav.mission;

/** Server policy for destructive transitions derived from an observed target. */
public final class MissionAuthorizationPolicy {
    private MissionAuthorizationPolicy() { }

    public static boolean requiresOperatorStrikeAuthorization(RDPMission mission) {
        return mission != null && mission.isAttack()
                && !mission.isDestructiveAuthorized();
    }
}
