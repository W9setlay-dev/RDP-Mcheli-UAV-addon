package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.NativeControlMath;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NativeControlMathTest {
    @Test
    public void limitsAttitudeChangesPerTick() {
        NativeControlMath.Command command = NativeControlMath.compute(
                0.0F, 0.0F, 0.0F, 0.0D,
                100.0D, 100.0D, 0.0D, false, Double.NaN);

        assertEquals(-3.0F, command.getYaw(), 0.0001F);
        assertEquals(-3.0F, command.getPitch(), 0.0001F);
        assertEquals(-5.0F, command.getRoll(), 0.0001F);
        assertEquals(0.1D, command.getThrottle(), 0.0001D);
    }

    @Test
    public void wrapsYawAcrossNorthWithoutLargeJump() {
        NativeControlMath.Command command = NativeControlMath.compute(
                179.0F, 0.0F, 0.0F, 0.5D,
                1.0D, 0.0D, -100.0D, false, Double.NaN);

        assertTrue(Math.abs(command.getYaw() - 179.0F) <= 3.0F
                || Math.abs(command.getYaw() + 181.0F) <= 3.0F);
    }

    @Test
    public void helicopterUsesCollectiveAndNoBankCommand() {
        NativeControlMath.Command command = NativeControlMath.compute(
                0.0F, 0.0F, 18.0F, 0.5D,
                0.0D, 20.0D, 20.0D, true, 1.0D);

        assertEquals(13.0F, command.getRoll(), 0.0001F);
        assertTrue(command.getThrottle() > 0.5D);
        assertTrue(command.getPitch() < 0.0F);
    }

    @Test
    public void extremeFiniteTargetStillProducesFiniteBoundedCommands() {
        NativeControlMath.Command command = NativeControlMath.compute(
                -179.0F, 24.0F, -34.0F, 0.0D,
                Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE,
                false, Double.NaN);

        assertTrue(Float.isFinite(command.getYaw()));
        assertTrue(Float.isFinite(command.getPitch()));
        assertTrue(Float.isFinite(command.getRoll()));
        assertTrue(Double.isFinite(command.getThrottle()));
        assertTrue(command.getThrottle() >= 0.0D && command.getThrottle() <= 1.0D);
    }
}
