package net.vas.rdp.uav.mission;

/** Effective movement authority for one mission tick. */
public enum ControlOwner {
    MANUAL, MISSION_AI, TERMINAL_GUIDANCE, RECOVERY, SAFETY
}
