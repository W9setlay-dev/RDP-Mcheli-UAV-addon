package net.vas.rdp.uav;

import net.vas.rdp.uav.flight.NativeControlMath;
import net.vas.rdp.uav.flight.NativeFlightControlCommand;
import net.vas.rdp.uav.mission.MissionMovementAuthority;
import net.vas.rdp.uav.mission.MissionType;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.target.TargetPriority;
import net.vas.rdp.uav.target.TargetSpec;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NativeControlMathTest {
    @Test
    public void rateLimitsYawAcrossZeroWithoutLargeWraparound() {
        float result = NativeControlMath.approachAngle(179.0F, -179.0F, 3.0F);
        assertEquals(181.0F, result, 0.0001F);
    }

    @Test
    public void blendsAiAndOperatorCommandsWithoutNeutralizingTheBaseController() {
        assertEquals(15.0F, NativeControlMath.blendAxis(10.0F, 5.0F, 0.15F, 10.0F), 0.0001F);
        assertEquals(10.0F, NativeControlMath.blendAxis(10.0F, 0.05F, 0.15F, 10.0F), 0.0001F);
        assertEquals(1.0D, NativeControlMath.blendAxis(0.8D, 0.2D, 0.15D, 1.0D), 0.0001D);
        assertEquals(10.4F, NativeControlMath.blendAxis(10.0F, 0.5F, 0.15F, 10.0F, 0.8F), 0.0001F);
    }

    @Test
    public void decayToZeroSmoothsOperatorAssistBackToTheAiBaseline() {
        assertEquals(0.0D, NativeControlMath.decayToZero(0.06D, 0.0D), 0.0001D);
        assertEquals(0.8D, NativeControlMath.decayToZero(1.0D, 0.8D), 0.0001D);
        assertEquals(0.0F, NativeControlMath.decayToZero(0.00001F, 0.5F), 0.0001F);
    }

    @Test
    public void fuseAiAndOperatorAppliesRollOnlyForSupervisedControl() {
        RDPMission mission = new RDPMission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "TEST_UAV", MissionType.STRIKE, TargetSpec.point(0.0D, 32.0D, 0.0D, TargetPriority.NORMAL),
                false, false, false, 64.0D, 32.0D, 1L);
        NativeFlightControlCommand ai = NativeFlightControlCommand.setpoints(10.0F, 5.0F, 3.0F,
                0.5D, 120.0D, 0.0D, 0.0D);
        mission.enterSupervisedOperatorMode(2L);
        NativeFlightControlCommand fused = MissionMovementAuthority.fuseAiAndOperator(
                mission, ai, 2.0D);

        assertEquals(10.0F, fused.getYaw(), 0.0001F);
        assertEquals(5.0F, fused.getPitch(), 0.0001F);
        assertEquals(5.0F, fused.getRoll(), 0.0001F);
        assertEquals(0.5D, fused.getThrottle(), 0.0001D);

        assertEquals(net.vas.rdp.uav.mission.ControlOwner.SUPERVISED_OPERATOR,
                mission.getControlOwner());

        MissionMovementAuthority.applySupervisedOperatorState(mission, false, 4L);
        assertEquals(net.vas.rdp.uav.mission.ControlOwner.MISSION_AI,
                mission.getControlOwner());

        MissionMovementAuthority.applySupervisedOperatorState(mission, true, 5L);
        assertEquals(net.vas.rdp.uav.mission.ControlOwner.SUPERVISED_OPERATOR,
                mission.getControlOwner());

        MissionMovementAuthority.applySupervisedOperatorState(mission, false, 6L);
        assertEquals(net.vas.rdp.uav.mission.ControlOwner.MISSION_AI,
                mission.getControlOwner());
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
