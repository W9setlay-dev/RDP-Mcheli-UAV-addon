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
    private final List<SwarmMember> members = new ArrayList<SwarmMember>();
    private SwarmState state = SwarmState.FORMING;
    private SwarmLeadership leadership = SwarmLeadership.DYNAMIC_LEADER;
    private SwarmFormation formation = SwarmFormation.LINE;
    private UUID designatedLeader;
    private SwarmResult result = SwarmResult.AWAITING_OPERATOR;
    private SearchPattern searchPattern = SearchPattern.GRID;

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
        members.sort(new Comparator<SwarmMember>() {
            public int compare(SwarmMember left, SwarmMember right) {
                return left.getAircraftId().toString().compareTo(right.getAircraftId().toString());
            }
        });
        assignSlots();
        if (state == SwarmState.FORMING) state = SwarmState.READY;
    }

    public void remove(UUID aircraftId) {
        if (aircraftId == null) return;
        for (int i = members.size() - 1; i >= 0; i--) {
            if (aircraftId.equals(members.get(i).getAircraftId())) members.remove(i);
        }
        assignSlots();
        if (members.isEmpty()) state = SwarmState.DISBANDED;
    }

    private void assignSlots() {
        List<FormationSlot> slots = FormationGeometry.slots(formation, members.size(),
                SwarmConfig.horizontalSpacing, SwarmConfig.verticalSpacing);
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
    public void setLeadership(SwarmLeadership value) { if (value != null) leadership = value; }
    public SwarmFormation getFormation() { return formation; }
    public void setFormation(SwarmFormation value) { if (value != null) formation = value; }
    public SwarmResult getResult() { return result; }
    public void setResult(SwarmResult value) { if (value != null) result = value; }
    public SearchPattern getSearchPattern() { return searchPattern; }
    public void setSearchPattern(SearchPattern value) { if (value != null) searchPattern = value; }
    public UUID getDesignatedLeader() { return designatedLeader; }
    public void setDesignatedLeader(UUID value) { designatedLeader = value; }

    public UUID referenceMember() {
        if (members.isEmpty() || leadership == SwarmLeadership.NO_SINGLE_LEADER) return null;
        if (leadership == SwarmLeadership.DESIGNATED_LEADER && designatedLeader != null) {
            for (SwarmMember member : members) {
                if (designatedLeader.equals(member.getAircraftId()) && member.isAvailable()) {
                    return designatedLeader;
                }
            }
        }
        for (SwarmMember member : members) {
            if (member.isAvailable()) return member.getAircraftId();
        }
        return null;
    }
}
