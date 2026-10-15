package net.vas.rdp.uav.path;

import net.vas.rdp.uav.deconfliction.TrajectorySample;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DubinsPlannerTest {
    @Test
    public void planCandidate_shortPose_generatesAllSixPathTypes() {
        DubinsPlanner planner = new DubinsPlanner();
        Map<DubinsPathType, DubinsPath> paths = new EnumMap<>(DubinsPathType.class);
        for (DubinsPathType type : DubinsPathType.values()) {
            DubinsPath path = findFeasibleCandidate(planner, type);
            assertNotNull("Expected a feasible " + type + " path", path);
            assertEquals(type, path.getType());
            assertSamplesAdvanceAlongTangent(path);
            paths.put(type, path);
        }
        assertEquals(6, paths.size());
    }

    private static DubinsPath findFeasibleCandidate(DubinsPlanner planner,
                                                     DubinsPathType type) {
        for (double startHeading : new double[] { 0.0D, 90.0D, 180.0D, 270.0D }) {
            for (double endX = -20.0D; endX <= 20.0D; endX += 10.0D) {
                for (double endZ = -20.0D; endZ <= 20.0D; endZ += 10.0D) {
                    for (double endHeading : new double[] { 0.0D, 90.0D, 180.0D, 270.0D }) {
                        DubinsPath path = planner.planCandidate(0.0D, 20.0D, 0.0D,
                                startHeading, endX, 30.0D, endZ, endHeading,
                                10.0D, 1.0D, type);
                        if (path != null) {
                            TrajectorySample endpoint =
                                    path.getSamples().get(path.getSamples().size() - 1);
                            assertEquals(endX, endpoint.getX(), 1.0E-7D);
                            assertEquals(30.0D, endpoint.getY(), 1.0E-7D);
                            assertEquals(endZ, endpoint.getZ(), 1.0E-7D);
                            assertEquals(endHeading, endpoint.getYaw(), 1.0E-6D);
                            return path;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void assertSamplesAdvanceAlongTangent(DubinsPath path) {
        for (int index = 0; index < path.getSamples().size() - 1; index++) {
            TrajectorySample sample = path.getSamples().get(index);
            TrajectorySample next = path.getSamples().get(index + 1);
            double heading = Math.toRadians(90.0D - sample.getYaw());
            double projection = (next.getX() - sample.getX()) * Math.cos(heading)
                    + (next.getZ() - sample.getZ()) * Math.sin(heading);
            assertTrue(path.getType() + " sample " + index
                    + " must advance along its tangent; projection=" + projection,
                    projection > 0.0D);
        }
    }

    @Test
    public void plan_shortestCandidate_selectsNoLongerThanAnyFeasibleCandidate() {
        DubinsPlanner planner = new DubinsPlanner();
        DubinsPath shortest = planner.plan(0.0D, 20.0D, 0.0D, 0.0D,
                10.0D, 30.0D, 10.0D, 180.0D, 10.0D, 1.0D);
        for (DubinsPathType type : DubinsPathType.values()) {
            DubinsPath candidate = planner.planCandidate(0.0D, 20.0D, 0.0D, 0.0D,
                    10.0D, 30.0D, 10.0D, 180.0D, 10.0D, 1.0D, type);
            if (candidate != null) {
                assertTrue(shortest.getTotalLength() <= candidate.getTotalLength() + 1.0E-8D);
            }
        }
    }

    @Test
    public void plan_sampling_respectsTurnResolutionAndExactEndpoint() {
        DubinsPath path = new DubinsPlanner().plan(0.0D, 25.0D, 0.0D, 0.0D,
                20.0D, 35.0D, 20.0D, 90.0D, 8.0D, 1.0D);
        TrajectorySample first = path.getSamples().get(0);
        TrajectorySample last = path.getSamples().get(path.getSamples().size() - 1);

        assertEquals(0L, first.getTickOffset());
        assertEquals(20.0D, last.getX(), 1.0E-8D);
        assertEquals(35.0D, last.getY(), 1.0E-8D);
        assertEquals(20.0D, last.getZ(), 1.0E-8D);
        assertEquals(90.0D, last.getYaw(), 1.0E-4D);
        for (int index = 0; index < path.getSamples().size() - 1; index++) {
            TrajectorySample sample = path.getSamples().get(index);
            TrajectorySample next = path.getSamples().get(index + 1);
            double heading = Math.toRadians(90.0D - sample.getYaw());
            double forwardProjection = (next.getX() - sample.getX()) * Math.cos(heading)
                    + (next.getZ() - sample.getZ()) * Math.sin(heading);
            assertTrue("each sampled segment must advance along its local tangent",
                    forwardProjection > 0.0D);
            assertTrue("sample ticks must increase strictly",
                    next.getTickOffset() > sample.getTickOffset());
        }
    }
}
