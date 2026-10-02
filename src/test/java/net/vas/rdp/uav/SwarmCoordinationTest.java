package net.vas.rdp.uav;

import net.vas.rdp.uav.swarm.FormationController;
import net.vas.rdp.uav.swarm.SearchCoordinator;
import net.vas.rdp.uav.swarm.SearchSector;
import net.vas.rdp.uav.swarm.SwarmMember;
import net.vas.rdp.uav.swarm.SwarmRecord;
import net.vas.rdp.uav.swarm.SwarmState;
import net.vas.rdp.uav.swarm.SwarmStatus;
import net.vas.rdp.uav.swarm.SwarmRole;
import net.vas.rdp.uav.swarm.SwarmTaskAllocator;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SwarmCoordinationTest {
    @Test
    public void searchSectorsAreDeterministicAndAssignedOncePerAvailableMember() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SwarmMember a = new SwarmMember(first, "SEARCHER", false, false);
        SwarmMember b = new SwarmMember(second, "SEARCHER", false, false);
        SearchCoordinator coordinator = new SearchCoordinator();
        List<SearchSector> sectors = coordinator.sectors(0D, 0D, 100D, 40D, 2);
        assertEquals(2, coordinator.assign(Arrays.asList(a, b), sectors).size());
        assertEquals(-50D, sectors.get(0).getMinX(), 0.001D);
        assertEquals(0D, sectors.get(0).getMaxX(), 0.001D);
    }

    @Test
    public void recoveryMarksPartialSwarmWithoutControllingMissions() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "recon");
        SwarmMember a = new SwarmMember(UUID.randomUUID(), "SEARCHER", false, false);
        SwarmMember b = new SwarmMember(UUID.randomUUID(), "SEARCHER", false, false);
        swarm.add(a);
        swarm.add(b);
        a.setAvailable(false);
        new net.vas.rdp.uav.swarm.SwarmRecoveryManager().reconcile(swarm);
        assertEquals(SwarmState.PARTIAL, swarm.getState());
    }

    @Test
    public void formationControllerExposesExistingSlots() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "formation");
        swarm.add(new SwarmMember(UUID.randomUUID(), "SEARCHER", false, false));
        assertEquals(1, new FormationController().slots(swarm).size());
    }

    @Test
    public void statusIsAnImmutableCoordinationProjection() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "status");
        swarm.add(new SwarmMember(UUID.randomUUID(), "SEARCHER", false, false));
        SwarmStatus status = SwarmStatus.from(swarm);
        assertEquals(1, status.getMembers());
        assertEquals(1, status.getAvailableMembers());
        assertEquals(SwarmState.READY, status.getState());
    }

    @Test
    public void autonomousRoleAllocationIsDeterministicAndDoesNotAssignStrikeRoles() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "roles");
        SwarmMember searcher = new SwarmMember(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "SEARCHER", false, false);
        SwarmMember tracker = new SwarmMember(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "LONG_RANGE_REUSABLE_UAV", true, true);
        swarm.add(tracker);
        swarm.add(searcher);

        new SwarmTaskAllocator().assignAutonomyRoles(swarm);

        assertEquals(SwarmRole.SEARCHER, searcher.getRole());
        assertEquals(SwarmRole.TRACKER, tracker.getRole());
        assertFalse(searcher.getRole() == SwarmRole.EXECUTOR);
        assertFalse(tracker.getRole() == SwarmRole.EXECUTOR);
    }

    @Test
    public void unavailableSearcherIsReplacedWithoutAssigningAnExecutor() {
        SwarmRecord swarm = new SwarmRecord(UUID.randomUUID(), UUID.randomUUID(), "recovery");
        SwarmMember unavailable = new SwarmMember(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "REUSABLE_UAV", false, false);
        SwarmMember replacement = new SwarmMember(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "REUSABLE_UAV", false, false);
        swarm.add(unavailable);
        swarm.add(replacement);
        unavailable.setRole(SwarmRole.SEARCHER);
        unavailable.setAvailable(false);

        new SwarmTaskAllocator().assignAutonomyRoles(swarm);

        assertEquals(SwarmRole.RESERVE, unavailable.getRole());
        assertEquals(SwarmRole.SEARCHER, replacement.getRole());
        assertFalse(replacement.getRole() == SwarmRole.EXECUTOR);
    }
}
