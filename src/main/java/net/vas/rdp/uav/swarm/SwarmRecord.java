package net.vas.rdp.uav.swarm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SwarmRecord {
    private final UUID swarmId;
    private final UUID ownerId;
    private final String name;
    private final List<SwarmMember> members = new ArrayList<>();
    private SwarmState state = SwarmState.FORMING;
    private SwarmLeadership leadership = SwarmLeadership.DYNAMIC_LEADER;
    private SwarmFormation formation = SwarmFormation.LINE;
    private UUID designatedLeader;
    private UUID commanderId;
    private SwarmResult result = SwarmResult.AWAITING_OPERATOR;
    private SearchPattern searchPattern = SearchPattern.GRID;
    private SwarmMissionState missionState = SwarmMissionState.IDLE;
    private SwarmLockState lockState = SwarmLockState.CLEAR;
    private SwarmOperatorState operatorState = SwarmOperatorState.UNASSIGNED;
    private boolean missionParticipation = true;
    private int missionMaximumMembers = 8;
    private boolean allowDuplicateTargets;
    private SwarmConfiguration configuration = SwarmConfiguration.defaults();
    private final SwarmAssignmentManager assignmentManager = new SwarmAssignmentManager();
    private final SwarmTargetRegistry targetRegistry = new SwarmTargetRegistry();

    public SwarmRecord(UUID swarmId, UUID ownerId, String name) {
        if (swarmId == null || ownerId == null || name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Swarm identity is required");
        }
        this.swarmId = swarmId;
        this.ownerId = ownerId;
        this.name = name;
    }

    public void add(SwarmMember member) {
        if (member == null) throw new IllegalArgumentException("Member is required");
        for (SwarmMember existing : members) {
            if (existing.getAircraftId().equals(member.getAircraftId())) return;
        }
        members.add(member);
        members.sort(Comparator.comparing(
                left -> left.getAircraftId().toString()));
        assignSlots();
        if (state == SwarmState.FORMING) state = SwarmState.READY;
    }

    public void remove(UUID aircraftId) {
        if (aircraftId == null) return;
        for (int i = members.size() - 1; i >= 0; i--) {
            if (aircraftId.equals(members.get(i).getAircraftId())) members.remove(i);
        }

        assignSlots();
        if (aircraftId.equals(commanderId)) commanderId = null;
        if (members.isEmpty()) state = SwarmState.DISBANDED;
    }

    public boolean assignTarget(UUID aircraftId, UUID targetId, boolean allowDuplicate) {
        if (aircraftId == null || targetId == null) return false;
        SwarmMember selected = member(aircraftId);
        if (selected == null) return false;
        if (!allowDuplicate) {
            for (SwarmMember member : members) {
                if (!aircraftId.equals(member.getAircraftId())
                        && targetId.equals(member.getAssignedTarget())) return false;
            }
        }
        selected.setAssignedTarget(targetId);
        return true;
    }

    public void releaseTarget(UUID aircraftId) {
        SwarmMember selected = member(aircraftId);
        if (selected != null) selected.setAssignedTarget(null);
    }

    private SwarmMember member(UUID aircraftId) {
        for (SwarmMember member : members) {
            if (aircraftId.equals(member.getAircraftId())) return member;
        }
        return null;
    }

    private void assignSlots() {
        List<FormationSlot> slots = FormationGeometry.slots(formation, members.size(),
                configuration.getHorizontalSpacing(), configuration.getVerticalSpacing());
        for (int i = 0; i < members.size(); i++) {
            members.get(i).setSlot(slots.get(i));
        }
    }

    public UUID getSwarmId() { return swarmId; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public List<SwarmMember> getMembers() {
        return Collections.unmodifiableList(members);
    }
    public SwarmState getState() { return state; }
    public void setState(SwarmState state) { if (state != null) this.state = state; }
    public SwarmLeadership getLeadership() { return leadership; }
    public void setLeadership(SwarmLeadership value) {
        if (value != null) {
            leadership = value;
            configuration = configuration.toBuilder().leadership(value).build();
        }
    }
    public SwarmFormation getFormation() { return formation; }
    public void setFormation(SwarmFormation value) {
        if (value != null) {
            formation = value;
            configuration = configuration.toBuilder().formation(value).build();
            assignSlots();
        }
    }
    public SwarmResult getResult() { return result; }
    public void setResult(SwarmResult value) { if (value != null) result = value; }
    public SearchPattern getSearchPattern() { return searchPattern; }
    public void setSearchPattern(SearchPattern value) {
        if (value != null) {
            searchPattern = value;
            configuration = configuration.toBuilder().searchPattern(value).build();
        }
    }
    public UUID getDesignatedLeader() { return designatedLeader; }
    public void setDesignatedLeader(UUID value) { designatedLeader = value; }
    public UUID getCommanderId() { return commanderId; }
    public void setCommanderId(UUID value) { commanderId = value; }
    public SwarmMissionState getMissionState() { return missionState; }
    public void setMissionState(SwarmMissionState value) { if (value != null) missionState = value; }
    public SwarmLockState getLockState() { return lockState; }
    public void setLockState(SwarmLockState value) { if (value != null) lockState = value; }
    public SwarmOperatorState getOperatorState() { return operatorState; }
    public void setOperatorState(SwarmOperatorState value) { if (value != null) operatorState = value; }
    public boolean isMissionParticipation() { return missionParticipation; }
    public void setMissionParticipation(boolean value) { missionParticipation = value; }
    public int getMissionMaximumMembers() { return missionMaximumMembers; }
    public void setMissionMaximumMembers(int value) {
        missionMaximumMembers = Math.max(1, Math.min(SwarmConfig.maximumSize, value));
    }
    public boolean isAllowDuplicateTargets() { return allowDuplicateTargets; }
    public void setAllowDuplicateTargets(boolean value) {
        allowDuplicateTargets = value;
        configuration = configuration.toBuilder().allowDuplicateTargets(value).build();
    }
    public SwarmConfiguration getConfiguration() { return configuration; }
    public void setConfiguration(SwarmConfiguration value) {
        if (value == null) throw new IllegalArgumentException("Swarm configuration is required");
        configuration = value;
        missionMaximumMembers = Math.min(missionMaximumMembers, value.getMaximumMembers());
        allowDuplicateTargets = value.isAllowDuplicateTargets();
        formation = value.getFormation();
        searchPattern = value.getSearchPattern();
        leadership = value.getLeadership();
        assignSlots();
    }
    public SwarmAssignmentManager getAssignmentManager() { return assignmentManager; }
    public SwarmTargetRegistry getTargetRegistry() { return targetRegistry; }

    public UUID referenceMember() {
        if (members.isEmpty() || leadership == SwarmLeadership.NO_SINGLE_LEADER) return null;
        if (leadership == SwarmLeadership.DESIGNATED_LEADER && designatedLeader != null) {
            for (SwarmMember member : members) {
                if (designatedLeader.equals(member.getAircraftId())) {
                    if (member.isAvailable()) {
                        commanderId = designatedLeader;
                        return designatedLeader;
                    }
                    if (!configuration.isAllowCommanderFailover()) return null;
                    break;
                }
            }
            if (!configuration.isAllowCommanderFailover()) return null;
        }
        if (commanderId != null) {
            for (SwarmMember member : members) {
                if (commanderId.equals(member.getAircraftId()) && member.isAvailable()) {
                    return commanderId;
                }
            }
            if (!configuration.isAllowCommanderFailover()) return null;
        }
        for (SwarmMember member : members) {
            if (member.isAvailable()) {
                commanderId = member.getAircraftId();
                return commanderId;
            }
        }
        return null;
    }
}
