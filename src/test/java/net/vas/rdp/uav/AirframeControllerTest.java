package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.AircraftState;
import net.vas.rdp.uav.flight.AirframeController;
import net.vas.rdp.uav.flight.FixedWingFlightController;
import net.vas.rdp.uav.flight.FlightCommand;
import net.vas.rdp.uav.flight.HelicopterFlightController;
import net.vas.rdp.uav.mission.ControlOwner;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class AirframeControllerTest {
    @Test
    public void fixedWingControllerSaturatesAndRespectsAltitudeTolerance() {
        UavVehicleProfile profile = new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                50.0D, 20.0D, 200.0D, 6.0D, false, "SELF_DESTRUCT");
        AircraftState state = AircraftState.observe(UUID.randomUUID(), 0, 10L,
                0.0D, 20.0D, 0.0D,
                0.0D, 18.0D, 0.0D,
                0.0F, 0.0F, 0.0F, 0.75D, "ENROUTE",
                UUID.randomUUID(), ControlOwner.MISSION_AI, true,
                0, 0.0D, 0.0D, 0.0D, 0.0D, 10L, "KNOWN");

        FlightCommand command = AirframeController.forProfile(profile)
                .update(state, 300.0D, 60.0D, 0.0D, 20L);

        assertEquals(1.0D, command.getForward(), 0.0D);
        assertTrue(command.getVertical() <= 16.0D);
        assertTrue(command.getVertical() >= -16.0D);
    }

    @Test
    public void helicopterControllerSeparatesVerticalAndLateralHandling() {
        UavVehicleProfile profile = new UavVehicleProfile(UavExecutionProfile.HELICOPTER_ONE_WAY_FPV,
                8.0D, 4.0D, 32.0D, 6.0D, false, "NONE");
        AircraftState state = AircraftState.observe(UUID.randomUUID(), 0, 12L,
                10.0D, 8.0D, 4.0D,
                8.0D, 6.0D, 2.0D,
                0.0F, 0.0F, 0.0F, 0.5D, "HOVER",
                UUID.randomUUID(), ControlOwner.MISSION_AI, true,
                0, 0.0D, 0.0D, 0.0D, 0.0D, 12L, "KNOWN");

        FlightCommand command = new HelicopterFlightController(profile)
                .update(state, 30.0D, 28.0D, 20.0D, 20L);

        assertTrue(command.getVertical() > 0.0D);
        assertTrue(Math.abs(command.getLateral()) <= 1.0D);
        assertEquals(net.vas.rdp.uav.flight.FpvFlightMode.AI_APPROACH, command.getMode());
    }

    @Test
    public void staleStateFailsClosedToHover() {
        UavVehicleProfile profile = new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                50.0D, 20.0D, 200.0D, 6.0D, false, "SELF_DESTRUCT");
        AircraftState state = AircraftState.observe(UUID.randomUUID(), 0, 1L,
                0.0D, 10.0D, 0.0D,
                0.0D, 10.0D, 0.0D,
                0.0F, 0.0F, 0.0F, 0.8D, "ENROUTE",
                UUID.randomUUID(), ControlOwner.MISSION_AI, true,
                0, 0.0D, 0.0D, 0.0D, 0.0D, 1L, "KNOWN");

        FlightCommand command = new FixedWingFlightController(profile)
                .update(state, 20.0D, 10.0D, 10.0D, 100L);

        assertEquals(0.0D, command.getForward(), 0.0D);
        assertEquals(0.0D, command.getVertical(), 0.0D);
        assertEquals(net.vas.rdp.uav.flight.FpvFlightMode.HOVER, command.getMode());
    }
}
