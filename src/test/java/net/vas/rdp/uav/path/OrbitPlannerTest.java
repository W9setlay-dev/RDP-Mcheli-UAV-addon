package net.vas.rdp.uav.path;

import net.vas.rdp.uav.deconfliction.TrajectorySample;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OrbitPlannerTest {
    private final OrbitPlanner planner = new OrbitPlanner();

    @Test
    public void computeEntry_outsideOrbit_returnsTangentPointAndSmallHeadingChange() {
        OrbitEntry entry = planner.computeEntry(40.0D, 10.0D, 90.0D,
                0.0D, 0.0D, 10.0D);
        double radius = Math.hypot(entry.getEntryTangentX(), entry.getEntryTangentZ());
        double dx = entry.getEntryTangentX();
        double dz = entry.getEntryTangentZ();
        double heading = Math.toRadians(90.0D - entry.getEntryHeading());
        double tangentDot = dx * Math.cos(heading) + dz * Math.sin(heading);

        assertEquals(10.0D, radius, 1.0E-8D);
        assertEquals(0.0D, tangentDot, 1.0E-7D);
        assertTrue(Math.abs(entry.getEntryHeading() - 90.0D) < 90.0D);
    }

    @Test
    public void computeExit_desiredHeading_returnsPointOnOrbit() {
        OrbitEntry exit = planner.computeExit(Math.PI, 0.0D, 5.0D, 7.0D, 12.0D);

        assertEquals(12.0D, Math.hypot(exit.getEntryTangentX() - 5.0D,
                exit.getEntryTangentZ() - 7.0D), 1.0E-8D);
        assertEquals(0.0D, exit.getEntryHeading(), 1.0E-8D);
    }

    @Test
    public void generateOrbit_figureEight_crossesCenterAndHasBothSides() {
        OrbitEntry entry = new OrbitEntry(10.0D, 0.0D, 0.0D,
                OrbitEntry.OrbitDirection.LEFT);
        List<TrajectorySample> samples = planner.generateOrbit(0.0D, 0.0D,
                10.0D, 80.0D, OrbitPrimitive.FIGURE_EIGHT, entry);
        boolean crossesCenter = false;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (TrajectorySample sample : samples) {
            minZ = Math.min(minZ, sample.getZ());
            maxZ = Math.max(maxZ, sample.getZ());
            if (Math.hypot(sample.getX(), sample.getZ()) < 0.5D) crossesCenter = true;
        }

        assertTrue(crossesCenter);
        assertTrue(minZ < 0.0D);
        assertTrue(maxZ > 0.0D);
    }
}
