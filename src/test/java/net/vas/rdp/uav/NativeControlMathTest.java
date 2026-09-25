package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.NativeControlMath;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NativeControlMathTest {
    @Test
    public void rateLimitsYawAcrossZeroWithoutLargeWraparound() {
        float result = NativeControlMath.approachAngle(179.0F, -179.0F, 3.0F);
        assertEquals(181.0F, result, 0.0001F);
    }

    @Test
    public void pitchRollAndThrottleSetpointsCanBeRateLimited() {
        assertEquals(-1.6F, NativeControlMath.approach(0.0F, -20.0F, 1.6F), 0.0001F);
        assertEquals(2.4F, NativeControlMath.approach(0.0F, 20.0F, 2.4F), 0.0001F);
        assertEquals(0.035D, NativeControlMath.approach(0.0D, 1.0D, 0.035D), 0.0001D);
    }

    @Test
    public void clampsEveryNativeAxisToItsAirframeEnvelope() {
        assertEquals(24.0F, NativeControlMath.clamp(30.0F, -24.0F, 24.0F), 0.0F);
        assertEquals(-35.0F, NativeControlMath.clamp(-50.0F, -35.0F, 35.0F), 0.0F);
        assertEquals(1.0D, NativeControlMath.clamp(1.5D, 0.0D, 1.0D), 0.0D);
        assertTrue(Float.isFinite(NativeControlMath.approachAngle(-179.0F, 179.0F, 2.0F)));
    }

    @Test
    public void rotorcraftKeepsASeparateLateralRollAuthority() {
        UavVehicleProfile helicopter = new UavVehicleProfile(
                UavExecutionProfile.HELICOPTER_ONE_WAY_FPV,
                8.0D, 4.0D, 32.0D, 6.0D, false, "NONE");
        UavVehicleProfile fixedWing = new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                50.0D, 20.0D, 200.0D, 6.0D, false, "SELF_DESTRUCT");

        assertEquals(20.0F, NativeControlMath.maximumRollDegrees(helicopter), 0.0F);
        assertEquals(35.0F, NativeControlMath.maximumRollDegrees(fixedWing), 0.0F);
    }
}
