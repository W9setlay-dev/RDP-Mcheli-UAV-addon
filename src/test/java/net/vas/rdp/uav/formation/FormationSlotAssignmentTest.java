package net.vas.rdp.uav.formation;

import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.swarm.SwarmRole;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FormationSlotAssignmentTest {
    @Test
    public void assign_unsortedAircraftIds_assignsStableUuidOrder() {
        UUID later = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Map<UUID, WingmanRole> roles = new LinkedHashMap<>();
        roles.put(later, WingmanRole.WINGMAN);
        roles.put(first, WingmanRole.LEADER);

        Map<UUID, FormationGeometry> result = new FormationSlotAssignment().assign(
                roles, FormationPreset.TRAIL, 12.0D, 4.0D, 16.0D);

        assertEquals(0, result.get(first).getSlotIndex());
        assertEquals(1, result.get(later).getSlotIndex());
        assertEquals(16.0D, result.get(later).getRearOffset(), 0.0D);
    }

    @Test
    public void assignVacatedMember_nextUuidFillsVacatedSlot() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Map<UUID, WingmanRole> roles = new LinkedHashMap<>();
        roles.put(first, WingmanRole.LEADER);
        roles.put(second, WingmanRole.WINGMAN);
        roles.put(third, WingmanRole.SCOUT);
        FormationSlotAssignment assignment = new FormationSlotAssignment();
        Map<UUID, FormationGeometry> before = assignment.assign(
                roles, FormationPreset.TRAIL, 12.0D, 4.0D, 16.0D);
        roles.remove(second);
        Map<UUID, FormationGeometry> after = assignment.assign(
                roles, FormationPreset.TRAIL, 12.0D, 4.0D, 16.0D);

        assertEquals(before.get(second).getSlotIndex(), after.get(third).getSlotIndex());
    }

    @Test
    public void generateRoleConstraints_missionStateAppliesExpectedRolePolicy() {
        Map<UUID, WingmanRole> roles = new LinkedHashMap<>();
        UUID scout = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID striker = UUID.fromString("00000000-0000-0000-0000-000000000002");
        roles.put(scout, WingmanRole.SCOUT);
        roles.put(striker, WingmanRole.STRIKER);
        WingmanTaskingService service = new WingmanTaskingService();

        Map<UUID, RoleConstraints> searching =
                service.generateRoleConstraints(MissionState.WAITING_FOR_TARGET, roles);
        Map<UUID, RoleConstraints> locked =
                service.generateRoleConstraints(MissionState.TARGET_LOCKED, roles);
        Map<UUID, RoleConstraints> holding =
                service.generateRoleConstraints(MissionState.HOLDING, roles);
        Map<UUID, RoleConstraints> replanning =
                service.generateRoleConstraints(MissionState.REPLAN, roles);

        assertEquals(1.5D, searching.get(scout).getDetectionRangeMultiplier(), 0.0D);
        assertTrue(searching.get(striker).isHoldFormation());
        assertTrue(locked.get(striker).isEngagementAuthorized());
        assertFalse(locked.get(scout).isEngagementAuthorized());
        assertTrue(holding.get(scout).isHoldFormation());
        assertTrue(replanning.get(striker).isHoldFormation());
    }

    @Test
    public void fromSwarmRole_persistedRoleMapsWithoutChangingStorageEnum() {
        assertEquals(WingmanRole.STRIKER, WingmanRole.fromSwarmRole(SwarmRole.EXECUTOR));
        assertEquals(WingmanRole.SCOUT, WingmanRole.fromSwarmRole(SwarmRole.SEARCHER));
    }
}
