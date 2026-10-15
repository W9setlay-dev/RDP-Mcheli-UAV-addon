package net.vas.rdp.uav.path;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PathConfigTest {
    @Test
    public void parsesConfiguredTerminalAngles() {
        double[] angles = PathConfig.parseTerminalDiveAngles("15, 35, 60");

        assertEquals(3, angles.length);
        assertEquals(15.0D, angles[0], 0.0D);
        assertEquals(35.0D, angles[1], 0.0D);
        assertEquals(60.0D, angles[2], 0.0D);
    }

    @Test
    public void rejectsOutOfRangeTerminalDiveAngle() {
        assertInvalidAngles("80", "Terminal dive angle is outside [-25, 60]");
    }

    @Test
    public void rejectsMalformedTerminalDiveAngle() {
        assertInvalidAngles("abc", "Invalid terminal dive angle: abc");
    }

    @Test
    public void rejectsMoreThanSixteenTerminalAngles() {
        assertInvalidAngles("0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16",
                "terminalDiveAngles must contain 1 to 16 values");
    }

    @Test
    public void rejectsMaximumRunBelowMinimumRun() {
        try {
            PathConfig.validateTerminalRunBounds(80.0D, 40.0D);
            fail("Expected inverted run bounds to be rejected");
        } catch (IllegalStateException expected) {
            assertEquals("terminalMaximumRun must not be below terminalMinimumRun",
                    expected.getMessage());
        }
    }

    private static void assertInvalidAngles(String value, String expectedMessage) {
        try {
            PathConfig.parseTerminalDiveAngles(value);
            fail("Expected invalid terminal angles to be rejected");
        } catch (IllegalStateException expected) {
            assertEquals(expectedMessage, expected.getMessage());
        }
    }
}
