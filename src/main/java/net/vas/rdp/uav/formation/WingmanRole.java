package net.vas.rdp.uav.formation;

import net.vas.rdp.uav.swarm.SwarmRole;

/** Server-side tasking role; derived from the persisted swarm role. */
public enum WingmanRole {
    LEADER,
    WINGMAN,
    SCOUT,
    STRIKER,
    SUPPORT,
    DECOY;

    /** Maps the persisted swarm role into the formation tasking vocabulary. */
    public static WingmanRole fromSwarmRole(SwarmRole role) {
        if (role == null) throw new IllegalArgumentException("Swarm role is required");
        switch (role) {
            case COMMANDER: return LEADER;
            case SEARCHER: return SCOUT;
            case TRACKER: return WINGMAN;
            case EXECUTOR: return STRIKER;
            case SUPPORT: return SUPPORT;
            case RESERVE: return WINGMAN;
            case DECOY: return DECOY;
            default: throw new IllegalArgumentException("Unsupported swarm role: " + role);
        }
    }
}
