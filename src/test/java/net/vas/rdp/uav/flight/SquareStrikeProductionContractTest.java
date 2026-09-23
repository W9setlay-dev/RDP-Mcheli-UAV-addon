package net.vas.rdp.uav.flight;

import org.junit.Test;

import static org.junit.Assert.*;

public class SquareStrikeProductionContractTest {
    @Test
    public void squarePlanPreservesBoundsAndGeneratesPasses() {
        SquareStrikePlan plan = new SquareStrikePlan(
                100.0D, 80.0D, -40.0D,
                64.0D, 32.0D, 0.0D,
                8.0D, 6.0D, 4);

        assertTrue(plan.contains(100.0D, -40.0D));
        assertTrue(plan.contains(131.0D, -24.0D));
        assertFalse(plan.contains(140.0D, -40.0D));
        assertEquals(12, plan.createNodes().size());
        assertEquals(4, plan.getStrikePasses());
    }
}
