package net.vas.rdp.uav.deconfliction;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConflictResolutionTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    public void resolve_repeatedConflict_selectsAltitudeThenDelayThenSlot() {
        AirspaceDeconflictionService service = new AirspaceDeconflictionService();
        Conflict conflict = new Conflict(FIRST, SECOND, 10L, ConflictType.BOTH, 1.0D);
        Map<UUID, Integer> priority = new HashMap<>();
        priority.put(FIRST, 10);
        priority.put(SECOND, 1);

        Resolution altitude = service.resolve(conflict, priority);
        Resolution delay = service.resolve(conflict, priority);
        Resolution slot = service.resolve(conflict, priority);

        assertEquals(Resolution.Type.ALTITUDE_OFFSET, altitude.getType());
        assertEquals(SECOND, altitude.getAircraftUuid());
        assertEquals(Resolution.Type.TIME_DELAY, delay.getType());
        assertEquals(Resolution.Type.SLOT_REASSIGNMENT, slot.getType());
        assertNull(service.resolve(conflict, priority));
    }

    @Test
    public void validateResolution_altitudeOffsetClearsSeparationViolation() {
        AirspaceDeconflictionService service = new AirspaceDeconflictionService();
        PredictedTrajectory first = trajectory(FIRST, 0.0D, 0.0D, 0.0D);
        PredictedTrajectory second = trajectory(SECOND, 1.0D, 1.0D, 1.0D);
        Resolution resolution = new Resolution(Resolution.Type.ALTITUDE_OFFSET,
                SECOND, 12.0D, 0, -1);

        assertTrue(service.validateResolution(resolution, Arrays.asList(first, second)));
    }

    @Test
    public void validateResolution_missingAircraft_rejectsProposal() {
        AirspaceDeconflictionService service = new AirspaceDeconflictionService();
        Resolution resolution = new Resolution(Resolution.Type.TIME_DELAY,
                SECOND, 0.0D, 10, -1);

        assertFalse(service.validateResolution(resolution,
                Collections.singletonList(trajectory(FIRST, 0.0D, 0.0D, 0.0D))));
    }

    private static PredictedTrajectory trajectory(UUID aircraft, double x, double y, double z) {
        return new PredictedTrajectory(aircraft, Collections.singletonList(
                new TrajectorySample(x, y, z, 0.0F, 0.0F, 0.0F, 0L, 1.0D)), 0L);
    }
}
