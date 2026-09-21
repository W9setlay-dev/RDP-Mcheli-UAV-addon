package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Client-editable mission intent; it is not authoritative runtime state. */
public final class MissionDefinition {
    private MissionType missionType = MissionType.RECONNAISSANCE;
    private String target = "";
    private String aircraftType = "";
    private String guidedWeapon = "";
    private TargetPriority priority = TargetPriority.NORMAL;
    private boolean attack;
    private boolean swarmEnabled;
    private int swarmMaximumMembers = 1;
    private final List<UUID> selectedUavs = new ArrayList<>();
    private TargetPolicy.Snapshot targetPolicy = TargetPolicy.builder().snapshot();
    private String searchMode = "DEFAULT";
    private String trackingMode = "CONTINUOUS";
    private String guidanceMode = "AI_APPROACH";
    private String executionMode = "MISSION_DEFINED";
    private String recoveryMode = "RETURN_TO_BASE";

    public MissionType getMissionType() { return missionType; }
    public String getTarget() { return target; }
    public String getAircraftType() { return aircraftType; }
    public String getGuidedWeapon() { return guidedWeapon; }
    public TargetPriority getPriority() { return priority; }
    public boolean isAttack() { return attack; }
    public boolean isSwarmEnabled() { return swarmEnabled; }
    public int getSwarmMaximumMembers() { return swarmMaximumMembers; }
    public List<UUID> getSelectedUavs() { return Collections.unmodifiableList(selectedUavs); }
    public TargetPolicy.Snapshot getTargetPolicy() { return targetPolicy; }
    public String getSearchMode() { return searchMode; }
    public String getTrackingMode() { return trackingMode; }
    public String getGuidanceMode() { return guidanceMode; }
    public String getExecutionMode() { return executionMode; }
    public String getRecoveryMode() { return recoveryMode; }

    public MissionDefinition setMissionType(MissionType value) {
        if (value == null) throw new IllegalArgumentException("Mission type is required");
        missionType = value; return this;
    }
    public MissionDefinition setTarget(String value) { target = normalize(value); return this; }
    public MissionDefinition setAircraftType(String value) { aircraftType = normalize(value); return this; }
    public MissionDefinition setGuidedWeapon(String value) { guidedWeapon = normalize(value); return this; }
    public MissionDefinition setPriority(TargetPriority value) {
        if (value == null) throw new IllegalArgumentException("Priority is required");
        priority = value; return this;
    }
    public MissionDefinition setAttack(boolean value) { attack = value; return this; }
    public MissionDefinition setSwarmEnabled(boolean value) { swarmEnabled = value; return this; }
    public MissionDefinition setSwarmMaximumMembers(int value) {
        if (value < 1 || value > 64) throw new IllegalArgumentException("Swarm size must be 1..64");
        swarmMaximumMembers = value; return this;
    }
    public MissionDefinition selectUav(UUID value) {
        if (value != null && !selectedUavs.contains(value)) selectedUavs.add(value);
        return this;
    }
    public MissionDefinition clearSelectedUavs() { selectedUavs.clear(); return this; }
    public MissionDefinition setTargetPolicy(TargetPolicy.Snapshot value) {
        if (value == null) throw new IllegalArgumentException("Target policy is required");
        targetPolicy = value; return this;
    }
    public MissionDefinition setSearchMode(String value) { searchMode = normalizeMode(value); return this; }
    public MissionDefinition setTrackingMode(String value) { trackingMode = normalizeMode(value); return this; }
    public MissionDefinition setGuidanceMode(String value) { guidanceMode = normalizeMode(value); return this; }
    public MissionDefinition setExecutionMode(String value) { executionMode = normalizeMode(value); return this; }
    public MissionDefinition setRecoveryMode(String value) { recoveryMode = normalizeMode(value); return this; }

    public boolean isDefaultTargetPolicy() {
        return targetPolicy.getInclude().isEmpty()
                && targetPolicy.getRequire().isEmpty()
                && targetPolicy.getExclude().isEmpty()
                && targetPolicy.getMinimumConfidence() <= 0D;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
    private static String normalizeMode(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "UNSPECIFIED" : normalized.toUpperCase();
    }
}
