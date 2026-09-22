package net.vas.rdp.uav.mission;

/** Compatibility projection of the mission lifecycle into flight phases. */
public enum MissionPhase {
    PLANNING, TAKEOFF, TRANSIT, SEARCH, APPROACH, ATTACK,
    TERMINAL_GUIDANCE, PAYLOAD_EXECUTION, RECOVERY, COMPLETE, FAILED
}
