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

    @Test
    public void preservesPhysicalMotionAndGroundTrackSeparatelyFromPositionDelta() {
        AircraftState state = AircraftState.observeWithPhysicalMotion(
                UUID.randomUUID(), 0, 30L,
                10.0D, 15.0D, 14.0D,
                8.0D, 15.0D, 14.0D,
                0.4D, 0.0D, 0.8D, 2.0D,
                90.0F, 2.0F, -1.0F, 0.6D, "ENROUTE",
                null, ControlOwner.MISSION_AI, true,
                2, 0.5D, 8.0D, 20.0D, 25.0D, 20L, "KNOWN");

        assertEquals(0.4D, state.getMotionX(), 0.0D);
        assertEquals(0.8D, state.getMotionZ(), 0.0D);
        assertEquals(2.0D, state.getSampleIntervalTicks(), 0.0D);
        assertEquals(333.4349488D, state.getGroundTrackYaw(), 1.0E-6D);
        assertEquals(1.0D, state.getVelocityX(), 0.0D);
        assertEquals("PHYSICAL_MOTION", state.getSource());
        assertEquals("BLOCKS_PER_TICK", state.getUnits());
        assertEquals(0.9D, state.getConfidence(), 0.0D);
        assertEquals("OK", state.getValidityReason());
    }

    @Test
    public void carriesExplicitTelemetryMetadataWhenMotionIsPartial() {
        AircraftState state = AircraftState.observe(UUID.randomUUID(), 0, 5L,
                15.0D, 10.0D, 12.0D,
                10.0D, 8.0D, 10.0D,
                90.0F, 1.0F, 0.0F, 0.5D, "SEARCHING",
                UUID.randomUUID(), ControlOwner.MISSION_AI, true,
                1, 0.25D, 6.0D, 12.0D, 24.0D, 4L, "PARTIAL",
                "POSITION_DELTA", "BLOCKS_PER_TICK", 0.68D, "SAMPLE_INTERVAL_LOW");

        assertEquals("POSITION_DELTA", state.getSource());
        assertEquals("SAMPLE_INTERVAL_LOW", state.getValidityReason());
        assertEquals(0.68D, state.getConfidence(), 0.0D);
    }

    @Test
    public void invalidNativeMotionFallsBackToPositionDeltaInsteadOfRejectingObservation() {
        AircraftState state = AircraftState.observeWithPhysicalMotion(
                UUID.randomUUID(), 0, 30L,
                12.0D, 16.0D, 20.0D,
                8.0D, 10.0D, 14.0D,
                Double.NaN, 0.0D, Double.POSITIVE_INFINITY, 2.0D,
                90.0F, 2.0F, -1.0F, 0.6D, "ENROUTE",
                null, ControlOwner.MISSION_AI, true,
                2, 0.5D, 8.0D, 20.0D, 25.0D, 20L, "KNOWN");

        assertEquals(2.0D, state.getVelocityX(), 0.0D);
        assertEquals(3.0D, state.getVelocityY(), 0.0D);
        assertEquals(3.0D, state.getVelocityZ(), 0.0D);
        assertEquals(0.0D, state.getMotionX(), 0.0D);
        assertEquals(0.0D, state.getMotionZ(), 0.0D);
        assertEquals(326.3099325D, state.getGroundTrackYaw(), 1.0E-6D);
        assertEquals("POSITION_DELTA", state.getSource());
        assertEquals("MOTION_UNAVAILABLE", state.getValidityReason());
        assertEquals(0.68D, state.getConfidence(), 0.0D);
    }
}
