package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.RouteDetourPlanner;
import net.vas.rdp.uav.mission.TerminalCommitPolicy;
import org.junit.Test;

import static org.junit.Assert.*;

public class RouteDetourPlannerTest {
    @Test
    public void clearCorridorKeepsDirectRoute() {
        RouteDetourPlanner.Plan plan = RouteDetourPlanner.plan(
                0.0D, 10.0D, 0.0D,
                20.0D, 12.0D, 0.0D,
                false, 12.0D);

        assertFalse(plan.isDetourRequired());
        assertEquals("DIRECT", plan.getReason());
        assertEquals(20.0D, plan.getX(), 0.0D);
        assertEquals(12.0D, plan.getY(), 0.0D);
    }

    @Test
    public void blockedCorridorProducesBoundedDetour() {
        RouteDetourPlanner.Plan plan = RouteDetourPlanner.plan(
                0.0D, 10.0D, 0.0D,
                80.0D, 14.0D, 0.0D,
                true, 24.0D);

        assertTrue(plan.isDetourRequired());
        assertEquals("OFFSET_DETOUR", plan.getReason());
        assertTrue(plan.getOffset() >= 8.0D && plan.getOffset() <= 64.0D);
        assertTrue(Math.abs(plan.getX()) >= 8.0D || Math.abs(plan.getZ()) >= 8.0D);
    }

    @Test
    public void terminalCommitPolicyAllowsClearApproach() {
        TerminalCommitPolicy.Decision decision = TerminalCommitPolicy.evaluate(
                18.0D, 10.0D, false, false, false, true, true, 24.0D, 12.0D);

        assertEquals(TerminalCommitPolicy.DecisionState.RELEASE, decision.getState());
        assertEquals("terminal commit authorized", decision.getReason());
        assertNotNull(decision.getCorridor());
    }

    @Test
    public void terminalCommitPolicyRequiresDetourWhenBlocked() {
        TerminalCommitPolicy.Decision decision = TerminalCommitPolicy.evaluate(
                28.0D, 14.0D, true, false, false, true, true, 24.0D, 12.0D);

        assertEquals(TerminalCommitPolicy.DecisionState.DETOUR_REQUIRED, decision.getState());
        assertNotNull(decision.getDetour());
        assertTrue(decision.getDetour().isDetourRequired());
    }
}
