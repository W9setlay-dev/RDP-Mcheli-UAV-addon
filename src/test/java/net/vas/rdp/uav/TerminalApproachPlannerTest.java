package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.TerminalApproachPlanner;
import org.junit.Test;

import static org.junit.Assert.*;

public class TerminalApproachPlannerTest {
    @Test
    public void selectsAStableAngleForHighTarget() {
        TerminalApproachPlanner.Plan plan = TerminalApproachPlanner.plan(
                0, 100, 0, 100, 20, 0, 0);
        assertTrue(plan.isDescending());
        assertTrue(plan.getAngleDegrees() >= 20.0D);
        assertTrue(plan.getAngleDegrees() <= 60.0D);
        assertTrue(plan.getApproachY() > plan.getTargetY());
    }

    @Test
    public void belowTargetUsesBoundedClimbInsteadOfUnboundedDive() {
        TerminalApproachPlanner.Plan plan = TerminalApproachPlanner.plan(
                0, 10, 0, 100, 80, 0, 90);
        assertFalse(plan.isDescending());
        assertTrue(plan.getApproachY() >= plan.getTargetY());
        assertTrue(plan.getApproachY() - plan.getTargetY() <= 24.0D);
        assertTrue(plan.getRun() <= 96.0D);
    }

    @Test
    public void terminalRunIncludesTurnAndTargetClearance() {
        TerminalApproachPlanner.Plan plan = TerminalApproachPlanner.plan(
                0, 80, 0, 20, 20, 0, 0, 40, 8, 24);
        assertTrue(plan.getRun() >= 72.0D);
        assertTrue(plan.getRun() <= 192.0D);
    }
}
