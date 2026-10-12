package net.vas.rdp.uav.mission;

import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetPolicy;
import net.vas.rdp.uav.swarm.SwarmConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Mutable mission request assembled by the client before it is validated and
 * committed by the server.
 *
 * <p>This object captures the operator's intent and the UAV/route profile to use,
 * but it is never treated as the authoritative runtime mission state.</p>
 */
public final class MissionDefinition {
    private MissionType missionType = MissionType.RECONNAISSANCE;
    private String target = "";
    private String aircraftType = "";
    private String guidedWeapon = "";
    private TargetPriority priority = TargetPriority.NORMAL;
    private boolean attack;
    private boolean swarmEnabled;
    private java.util.UUID swarmId;
    private SwarmConfiguration swarmConfiguration = SwarmConfiguration.builder()
            .maximumMembers(1).build();
    private final List<UUID> selectedUavs = new ArrayList<>();
    private TargetPolicy.Snapshot targetPolicy = TargetPolicy.builder().snapshot();
    private String searchMode = "DEFAULT";
    private String trackingMode = "CONTINUOUS";
    private String guidanceMode = "AI_APPROACH";
    private String executionMode = "MISSION_DEFINED";
    private String recoveryMode = "RETURN_TO_BASE";
    private Integer cruiseAltitudeY;
    private List<MissionWaypoint> waypoints = Collections.emptyList();

    /** @return the mission category selected by the operator */
    public MissionType getMissionType() { return missionType; }
    /** @return the exact registry or mod target string requested by the mission */
    public String getTarget() { return target; }
    /** @return the UAV aircraft type or identifier used for the mission */
    public String getAircraftType() { return aircraftType; }
    /** @return the guided weapon selected by the operator, if any */
    public String getGuidedWeapon() { return guidedWeapon; }
    /** @return the target priority assigned to this mission */
    public TargetPriority getPriority() { return priority; }
    /** @return {@code true} when the mission is configured to arm attack intent */
    public boolean isAttack() { return attack; }
    /** @return {@code true} when the mission is pinned to a swarm assignment */
    public boolean isSwarmEnabled() { return swarmEnabled; }
    /** @return the configured swarm size cap for this mission */
    public int getSwarmMaximumMembers() { return swarmConfiguration.getMaximumMembers(); }
    /** @return the named swarm identifier, if a swarm is assigned */
    public java.util.UUID getSwarmId() { return swarmId; }
    /** @return the swarm profile currently attached to the mission definition */
    public SwarmConfiguration getSwarmConfiguration() { return swarmConfiguration; }
    /** @return an immutable snapshot of the UAVs selected for this mission */
    public List<UUID> getSelectedUavs() { return Collections.unmodifiableList(selectedUavs); }
    /** @return the target policy that decides hostile, embedded, and confidence rules */
    public TargetPolicy.Snapshot getTargetPolicy() { return targetPolicy; }
    /** @return the configured search pattern mode */
    public String getSearchMode() { return searchMode; }
    /** @return the tracking update mode for the mission */
    public String getTrackingMode() { return trackingMode; }
    /** @return the guidance mode used to approach the objective */
    public String getGuidanceMode() { return guidanceMode; }
    /** @return the execution mode declared for this mission */
    public String getExecutionMode() { return executionMode; }
    /** @return the recovery mode selected after mission completion or shutdown */
    public String getRecoveryMode() { return recoveryMode; }
    /** @return the requested absolute world-Y cruise altitude, or {@code null} for profile default */
    public Integer getCruiseAltitudeY() { return cruiseAltitudeY; }
    /** @return the ordered patrol or route waypoints attached to the mission */
    public List<MissionWaypoint> getWaypoints() { return waypoints; }

    /**
     * Sets the primary mission type.
     *
     * @param value the mission family to use
     * @return {@code this} for chaining
     */
    public MissionDefinition setMissionType(MissionType value) {
        if (value == null) throw new IllegalArgumentException("Mission type is required");
        missionType = value; return this;
    }
    /** @param value the exact target identifier or mod filter to apply */
    public MissionDefinition setTarget(String value) { target = normalize(value); return this; }
    /** @param value the aircraft type or registry key for the assigned UAV */
    public MissionDefinition setAircraftType(String value) { aircraftType = normalize(value); return this; }
    /** @param value the guided weapon identifier or native weapon key */
    public MissionDefinition setGuidedWeapon(String value) { guidedWeapon = normalize(value); return this; }
    /**
     * Sets the target priority used during validation and target selection.
     *
     * @param value the priority bucket to request
     * @return {@code this} for chaining
     */
    public MissionDefinition setPriority(TargetPriority value) {
        if (value == null) throw new IllegalArgumentException("Priority is required");
        priority = value; return this;
    }
    /** @param value {@code true} to arm attack-specific logic */
    public MissionDefinition setAttack(boolean value) { attack = value; return this; }
    /** @param value {@code true} to bind the mission to swarm assignment rules */
    public MissionDefinition setSwarmEnabled(boolean value) { swarmEnabled = value; return this; }
    /**
     * Sets the swarm member cap for the mission. Values are constrained to the
     * active mission limits to avoid invalid swarm plans.
     *
     * @param value the maximum number of swarm members
     * @return {@code this} for chaining
     */
    public MissionDefinition setSwarmMaximumMembers(int value) {
        if (value < 1 || value > 64) throw new IllegalArgumentException("Swarm size must be 1..64");
        swarmConfiguration = swarmConfiguration.toBuilder().maximumMembers(value).build();
        return this;
    }
    /** @param value the swarm UUID to assign or clear */
    public MissionDefinition setSwarmId(java.util.UUID value) { swarmId = value; return this; }
    /** @param value the swarm configuration snapshot to attach */
    public MissionDefinition setSwarmConfiguration(SwarmConfiguration value) {
        if (value == null) throw new IllegalArgumentException("Swarm configuration is required");
        swarmConfiguration = value;
        return this;
    }
    /**
     * Adds a UAV to the selection list if it is not already present.
     *
     * @param value the selected UAV UUID
     * @return {@code this} for chaining
     */
    public MissionDefinition selectUav(UUID value) {
        if (value != null && !selectedUavs.contains(value)) selectedUavs.add(value);
        return this;
    }
    /**
     * Clears the UAV selection list without mutating the mission's other settings.
     *
     * @return {@code this} for chaining
     */
    public MissionDefinition clearSelectedUavs() { selectedUavs.clear(); return this; }
    /** @param value the target policy snapshot governing hostile and confidence checks */
    public MissionDefinition setTargetPolicy(TargetPolicy.Snapshot value) {
        if (value == null) throw new IllegalArgumentException("Target policy is required");
        targetPolicy = value; return this;
    }
    /** @param value the search-mode token to normalize and store */
    public MissionDefinition setSearchMode(String value) { searchMode = normalizeMode(value); return this; }
    /** @param value the tracking-mode token to normalize and store */
    public MissionDefinition setTrackingMode(String value) { trackingMode = normalizeMode(value); return this; }
    /** @param value the guidance-mode token to normalize and store */
    public void setGuidanceMode(String value) { guidanceMode = normalizeMode(value);
    }
    /** @param value the execution-mode token to normalize and store */
    public void setExecutionMode(String value) { executionMode = normalizeMode(value);
    }
    /** @param value the recovery-mode token to normalize and store */
    public void setRecoveryMode(String value) { recoveryMode = normalizeMode(value);
    }
    /** @param value absolute world Y, or {@code null} to use the legacy profile default */
    public MissionDefinition setCruiseAltitudeY(Integer value) {
        if (value != null && (value < 1 || value > 4094)) {
            throw new IllegalArgumentException("Cruise altitude must be in 1..4094");
        }
        cruiseAltitudeY = value;
        return this;
    }
    /**
     * Replaces the waypoint list after validating it against the patrol-route codec.
     *
     * @param value the ordered route points to attach
     * @return {@code this} for chaining
     */
    public MissionDefinition setWaypoints(List<MissionWaypoint> value) {
        if (value == null) throw new IllegalArgumentException("Patrol route is required");
        if (!value.isEmpty()) PatrolRouteCodec.validate(value);
        waypoints = Collections.unmodifiableList(new ArrayList<>(value));
        return this;
    }

    /** @return {@code true} when the mission retains any non-default target-policy filters */
    public boolean isDefaultTargetPolicy() {
        return !targetPolicy.getInclude().isEmpty()
                || !targetPolicy.getRequire().isEmpty()
                || !targetPolicy.getExclude().isEmpty()
                || targetPolicy.isRejectEmbedded() != TargetPolicy.rejectDeeplyEmbedded
                || targetPolicy.isAllowVehicleTargets() != TargetPolicy.allowHostileVehicles
                || !(targetPolicy.getMinimumConfidence() <= 0D);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
    private static String normalizeMode(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "UNSPECIFIED" : normalized.toUpperCase();
    }
}
