package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.AircraftState;
import net.vas.rdp.uav.flight.FlightGuidancePlanner;
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
    public void clampsTerminalWaypointToProfileAltitude() {
        AircraftState state = state(8.0D);
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV, 100, 60, 120, 6,
                true, "NONE");

        FlightGuidancePlanner.Plan plan = FlightGuidancePlanner.plan(
                state, 40, 10, 0, 0, 0, 0, 1, profile, true);

        assertEquals(60.0D, plan.getY(), 0.0D);
        assertTrue(plan.getLeadTicks() <= 4.0D);
    }

    private static AircraftState state(double vx) {
        return AircraftState.observe(UUID.randomUUID(), 0, 100,
                0.0, 100.0, 0.0, 0.0 - vx, 100.0 - 0.0, 0.0,
                0, 0, 0, 1, "ENROUTE", UUID.randomUUID(),
                ControlOwner.MISSION_AI, true, 0, 0,
                100, 100, 0, 100, "KNOWN");
    }
}
