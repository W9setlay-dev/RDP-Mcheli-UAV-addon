package net.vas.rdp.uav.mission;

public enum MissionPresets {
    SINGLE_UAV,
    SMALL_SWARM,
    RECON,
    SURVEILLANCE,
    PATROL,
    TEST;

    public MissionDefinition create() {
        MissionDefinition definition = new MissionDefinition();
        switch (this) {
            case SMALL_SWARM:
                definition.setSwarmEnabled(true).setSwarmMaximumMembers(4);
                break;
            case SURVEILLANCE:
                definition.setMissionType(MissionType.OBSERVATION);
                break;
            case PATROL:
                definition.setMissionType(MissionType.RECONNAISSANCE);
                break;
            case TEST:
                definition.setTarget("minecraft:zombie");
                break;
            case RECON:
            case SINGLE_UAV:
            default:
                break;
        }
        return definition;
    }
}
