package net.vas.rdp.uav.world;

public final class ChunkForecast {
    public enum Decision {
        CONTINUE,
        WAIT,
        REPLAN
    }

    private ChunkForecast() { }

    public static Decision decide(ChunkObservation observation, boolean canLoad) {
        if (observation == null) return Decision.REPLAN;
        switch (observation.getState()) {
            case LOADED:
                return Decision.CONTINUE;
            case UNLOADED:
                return canLoad ? Decision.WAIT : Decision.REPLAN;
            case UNKNOWN:
            default:
                return Decision.WAIT;
        }
    }
}
