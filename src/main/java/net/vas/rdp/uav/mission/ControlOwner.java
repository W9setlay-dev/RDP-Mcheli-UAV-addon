package net.vas.rdp.uav.mission;

/** Effective movement authority for one mission tick. */
public enum ControlOwner {
    MANUAL,
    /** Autonomous guidance is active and is still the baseline flight authority. */
    MISSION_AI,
    /** AI remains the active flight intent, while operator input is blended in for assisted control. */
    SUPERVISED_OPERATOR,
    TERMINAL_GUIDANCE,
    RECOVERY,
    SAFETY
}
