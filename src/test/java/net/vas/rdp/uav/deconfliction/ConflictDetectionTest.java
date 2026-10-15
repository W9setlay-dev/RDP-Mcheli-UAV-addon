package net.vas.rdp.uav.deconfliction;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConflictDetectionTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    public void detectConflicts_convergingTrajectories_reportsFirstOverlap() {
        PredictedTrajectory first = trajectory(FIRST, 0.0D, 0.0D, 0.0D, 1.0D, 0L);
        PredictedTrajectory second = trajectory(SECOND, 40.0D, 0.0D, 0.0D, -1.0D, 0L);

        List<Conflict> conflicts =
                new AirspaceDeconflictionService().detectConflicts(Arrays.asList(first, second));

        assertEquals(1, conflicts.size());
        assertTrue(conflicts.get(0).getFirstConflictTick() > 0L);
    }

    @Test
    public void detectConflicts_divergingTrajectories_reportsNoOverlap() {
        PredictedTrajectory first = trajectory(FIRST, 0.0D, 0.0D, 0.0D, -1.0D, 0L);
        PredictedTrajectory second = trajectory(SECOND, 30.0D, 0.0D, 0.0D, 1.0D, 0L);

        assertTrue(new AirspaceDeconflictionService().detectConflicts(
                Arrays.asList(first, second)).isEmpty());
    }

    @Test
    public void detectConflicts_parallelSeparatedTrajectories_reportsNoOverlap() {
        PredictedTrajectory first = trajectory(FIRST, 0.0D, 0.0D, 0.0D, 1.0D, 0L);
        PredictedTrajectory second = trajectory(SECOND, 0.0D, 9.0D, 80.0D, 1.0D, 0L);

        assertTrue(new AirspaceDeconflictionService().detectConflicts(
                Arrays.asList(first, second)).isEmpty());
    }

    @Test
    public void detectConflicts_nearbyAtSameTime_reportsBothAxes() {
        PredictedTrajectory first = trajectory(FIRST, 0.0D, 0.0D, 0.0D, 0.0D, 0L);
        PredictedTrajectory second = trajectory(SECOND, 1.0D, 1.0D, 1.0D, 0.0D, 0L);
        Conflict conflict = new AirspaceDeconflictionService().detectConflicts(
                Arrays.asList(first, second)).get(0);

        assertEquals(ConflictType.BOTH, conflict.getConflictType());
        assertTrue(conflict.getMinimumDistance() < 2.0D);
    }

    private static PredictedTrajectory trajectory(UUID aircraft, double x, double y,
                                                   double z, double velocityX, long startTick) {
        List<TrajectorySample> samples = Arrays.asList(
                new TrajectorySample(x, y, z, 0.0F, 0.0F, 0.0F, 0L, 1.0D),
                new TrajectorySample(x + velocityX * 5.0D, y, z,
                        0.0F, 0.0F, 0.0F, 5L, 0.95D),
                new TrajectorySample(x + velocityX * 10.0D, y, z,
                        0.0F, 0.0F, 0.0F, 10L, 0.9D));
        return new PredictedTrajectory(aircraft, samples, startTick);
    }
}
