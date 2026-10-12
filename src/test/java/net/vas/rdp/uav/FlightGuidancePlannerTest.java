package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.AircraftState;
import net.vas.rdp.uav.flight.FixedWingFlightController;
import net.vas.rdp.uav.flight.FlightGuidancePlanner;
import net.vas.rdp.uav.flight.TerminalApproachPlanner;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class FlightGuidancePlannerTest {
    @Test
    public void predictsMovingTargetWithinBoundedLead() {
        AircraftState state = state(10.0D);
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV, 100, 40, 180, 6,
                true, "NONE", 256, 256, 64, "", 0, 64);

        FlightGuidancePlanner.Plan plan = FlightGuidancePlanner.plan(
                state, 100, 100, 0, 2, 0, 0, 2, profile, false);

        assertTrue(plan.isFeasible());
        assertTrue(plan.getLeadTicks() >= 2.0D);
        assertTrue(plan.getLeadTicks() <= 20.0D);
        assertTrue(plan.getX() > state.getX());
    }

    @Test
    public void usesTerminalApproachGeometryInsteadOfCruiseProfileClamp() {
        AircraftState state = state(8.0D);
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV, 100, 60, 120, 6,
                true, "NONE");

        FlightGuidancePlanner.Plan plan = FlightGuidancePlanner.plan(
                state, 40, 10, 0, 0, 0, 0, 1, profile, true);

        TerminalApproachPlanner.Plan expected = TerminalApproachPlanner.plan(
                state.getX(), state.getY(), state.getZ(), 40.0D, 10.0D, 0.0D,
                0.0D, profile.getTurnRadius(), 0.0D, 24.0D);
        assertEquals(expected.getApproachY(), plan.getY(), 0.0D);
        assertTrue(plan.getLeadTicks() <= 4.0D);
    }

    @Test
    public void explicitCruiseAltitudeSurvivesControllerProfileClamp() {
        AircraftState state = AircraftState.observe(UUID.randomUUID(), 0, 100,
                0.0, 60.0, 0.0, -10.0, 60.0, 0.0,
                0, 0, 0, 1, "ENROUTE", UUID.randomUUID(),
                ControlOwner.MISSION_AI, true, 0, 0,
                100, 100, 0, 100, "KNOWN");
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV, 100, 60, 120, 6,
                true, "NONE");
        FixedWingFlightController controller = new FixedWingFlightController(profile);

        double profileDefaultDemand = controller.update(state, 100, 50, 0, 100, false)
                .getDesiredVerticalSpeed();
        double explicitDemand = controller.update(state, 100, 50, 0, 100, true)
                .getDesiredVerticalSpeed();

        assertTrue("Explicit=" + explicitDemand + ", profile=" + profileDefaultDemand,
                explicitDemand < profileDefaultDemand);
    }

    private static AircraftState state(double vx) {
        return AircraftState.observe(UUID.randomUUID(), 0, 100,
                0.0, 100.0, 0.0, 0.0 - vx, 100.0 - 0.0, 0.0,
                0, 0, 0, 1, "ENROUTE", UUID.randomUUID(),
                ControlOwner.MISSION_AI, true, 0, 0,
                100, 100, 0, 100, "KNOWN");
    }
}
