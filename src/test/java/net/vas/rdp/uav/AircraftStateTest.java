package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.AircraftState;
import net.vas.rdp.uav.mission.ControlOwner;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class AircraftStateTest {
    @Test
    public void capturesObservedVelocityAndSpeed() {
        AircraftState state = AircraftState.observe(UUID.randomUUID(), 0, 20L,
                10.0D, 15.0D, 14.0D,
                7.0D, 13.0D, 10.0D,
                90.0F, 2.0F, -1.0F, 0.6D, "ENROUTE",
                UUID.randomUUID(), ControlOwner.MISSION_AI, true,
                2, 0.5D, 8.0D, 20.0D, 25.0D, 20L, "UNKNOWN");

        assertEquals(3.0D, state.getVelocityX(), 0.0D);
        assertEquals(2.0D, state.getVelocityY(), 0.0D);
        assertEquals(4.0D, state.getVelocityZ(), 0.0D);
        assertEquals(5.0D, state.getHorizontalSpeed(), 0.0D);
        assertEquals(2.0D, state.getVerticalSpeed(), 0.0D);
        assertTrue(state.isControlLeaseValid());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteObservedState() {
        AircraftState.observe(UUID.randomUUID(), 0, 1L,
                Double.NaN, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0.0F, 0.0F, 0.0F, 0.0D, "UNKNOWN",
                null, null, false, -1, 0.0D, 0.0D, 0.0D, 0.0D, 1L, "UNKNOWN");
    }
}
