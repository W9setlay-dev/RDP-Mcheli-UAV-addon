package net.vas.rdp.uav.path;

import net.vas.rdp.uav.deconfliction.TrajectorySample;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerminalApproachTest {
    @Test
    public void planTerminalApproach_clampsDiveAndProducesStraightLine() {
        PathPlanner planner = new PathPlanner();
        List<TrajectorySample> path = planner.planTerminalApproach(
                10.0D, 70.0D, 20.0D, 90.0D, profile());
        TrajectorySample start = path.get(0);
        TrajectorySample end = path.get(path.size() - 1);
        double ratio = (start.getY() - end.getY()) / (end.getZ() - start.getZ());

        assertEquals(60.0D, start.getPitch(), 1.0E-4D);
        assertEquals(60.0D, Math.toDegrees(Math.atan(ratio)), 1.0E-4D);
        assertEquals(10.0D, end.getX(), 1.0E-8D);
        assertEquals(70.0D, end.getY(), 1.0E-8D);
        assertEquals(20.0D, end.getZ(), 1.0E-8D);
        for (TrajectorySample sample : path) {
            assertEquals(10.0D, sample.getX(), 1.0E-8D);
        }
    }

    @Test
    public void planTerminalApproach_negativeAngleClampsAtMinusTwentyFive() {
        List<TrajectorySample> path = new PathPlanner().planTerminalApproach(
                0.0D, 50.0D, 0.0D, -90.0D, profile());

        assertEquals(-25.0D, path.get(0).getPitch(), 1.0E-4D);
        assertTrue(path.size() > 1);
    }

    private static UavVehicleProfile profile() {
        return new UavVehicleProfile(UavExecutionProfile.REUSABLE_UAV, 80.0D,
                40.0D, 160.0D, 8.0D, true, "NONE");
    }
}
