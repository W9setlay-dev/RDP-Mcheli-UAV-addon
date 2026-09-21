package net.vas.rdp.uav;

import net.vas.rdp.uav.mission.AircraftAssignmentRegistry;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetAcquisition;
import net.vas.rdp.uav.target.TargetSpec;
import net.vas.rdp.uav.target.TargetSelector;
import net.vas.rdp.uav.mission.AircraftRecord;
import net.vas.rdp.uav.mission.AircraftRegistry;
import net.vas.rdp.uav.mission.MissionQueue;
import net.vas.rdp.uav.flight.SquareStrikePlan;
import com.norwood.mcheli.wingman.mission.MissionNode;

import java.util.Collections;
import java.util.UUID;

public final class CoreMissionTest {
    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        UUID aircraft = UUID.randomUUID();
        RDPMission mission = new RDPMission(
                UUID.randomUUID(), owner, aircraft, "switchblade",
                MissionType.RECONNAISSANCE,
                TargetSpec.manual("srparasites:rupter", TargetPriority.HIGH),
                true, false, true, 256, 64, 10);

        check(mission.getState() == MissionState.PLANNED, "mission starts planned");
        check(mission.getMissionRangeLimit() == 256.0D, "mission envelope uses the broader target range");
        AircraftAssignmentRegistry assignments = new AircraftAssignmentRegistry();
        check(assignments.reserve(aircraft, mission.getMissionId()), "first assignment succeeds");
        check(!assignments.reserve(aircraft, UUID.randomUUID()), "duplicate assignment rejected");
        mission.transitionTo(MissionState.DISPATCHED, 10);
        mission.transitionTo(MissionState.TAXI, 11);
        mission.transitionTo(MissionState.TAKEOFF, 12);
        mission.transitionTo(MissionState.CRUISE, 13);
        mission.transitionTo(MissionState.SEARCHING, 14);
        mission.transitionTo(MissionState.TARGET_ACQUIRED, 19);
        mission.lockTarget(UUID.randomUUID(), 20);
        check(mission.getState() == MissionState.TARGET_LOCKED, "target lock transitions state");
        mission.transitionTo(MissionState.APPROACHING, 21);
        mission.transitionTo(MissionState.ON_TARGET, 22);
        mission.transitionTo(MissionState.SCANNING, 30);
        mission.fail("TARGET_LOST", 40);
        check(mission.getFailureReason().equals("TARGET_LOST"), "failure reason retained");
        assignments.release(aircraft, mission.getMissionId());
        check(assignments.missionFor(aircraft) == null, "assignment released");

        MissionQueue queue = new MissionQueue();
        queue.enqueue(mission);
        check(queue.peek() == mission, "queue retains pending mission");
        check(queue.peek() == mission, "peek does not discard pending mission");
        check(queue.remove(mission), "accepted mission is explicitly removed");

        AircraftRegistry registry = new AircraftRegistry();
        AircraftRecord record = new AircraftRecord(aircraft, owner, 0, "switchblade", true, true);
        registry.register(record);
        check(registry.canAssign(aircraft, owner), "owner can assign aircraft");
        check(!registry.canAssign(aircraft, UUID.randomUUID()), "other player cannot assign aircraft");
        registry.assign(aircraft, mission.getMissionId(), owner);
        check(registry.find(aircraft).getStatus() == net.vas.rdp.uav.mission.AircraftStatus.ON_MISSION,
                "aircraft assignment changes status");

        TargetAcquisition<String> acquisition = new TargetAcquisition<>();
        String selected = acquisition.acquire(
                TargetSpec.automatic("srparasites", TargetPriority.NORMAL),
                java.util.Arrays.asList(
                        candidate("srparasites:rupter", 4, TargetPriority.NORMAL, "near"),
                        candidate("srparasites:beckon", 25, TargetPriority.CRITICAL, "critical")));
        check("critical".equals(selected), "automatic selection respects priority");
        selected = acquisition.acquire(
                TargetSpec.manual("srparasites:rupter", TargetPriority.HIGH),
                Collections.singletonList(
                        candidate("srparasites:beckon", 1, TargetPriority.CRITICAL, "wrong")));
        check(selected == null, "manual selection never substitutes a target");

        SquareStrikePlan square = new SquareStrikePlan(100, 140, -50,
                80, 60, 0, 20, 30, 2);
        java.util.List<MissionNode> nodes = square.createNodes();
        check(nodes.size() == 9, "square creates two native nodes and one attack per pass");
        check(nodes.get(2).type == MissionNode.Type.ATTACK, "square pass ends with attack node");
        check(nodes.get(3).type == MissionNode.Type.FLY_TO, "square alternates into next pass");
        boolean rejected = false;
        try {
            new SquareStrikePlan(0, 100, 0, 10, 10, 0, 20, 5, 1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "square rejects spacing larger than its length");
    }

    private static TargetSelector.Candidate<String> candidate(final String id, final double distance,
                                                               final TargetPriority priority,
                                                               final String value) {
        return new TargetSelector.Candidate<String>() {
            public String getRegistryId() { return id; }
            public double getDistanceSquared() { return distance; }
            public boolean isValid() { return true; }
            public TargetPriority getPriority() { return priority; }
            public String getValue() { return value; }
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
