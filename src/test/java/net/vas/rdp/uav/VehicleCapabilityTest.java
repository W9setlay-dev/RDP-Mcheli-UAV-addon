package net.vas.rdp.uav;

import net.vas.rdp.uav.profile.MissionDoctrine;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.VehicleCapability;
import org.junit.Test;

import static org.junit.Assert.*;

public class VehicleCapabilityTest {
    @Test
    public void fpvMapsToExpendableHoverTerminalDoctrine() {
        VehicleCapability capability = VehicleCapability.fromProfile(
                new UavVehicleProfile(UavExecutionProfile.HELICOPTER_ONE_WAY_FPV,
                        8, 4, 32, 6, false, "SELF_DESTRUCT"));
        assertEquals(MissionDoctrine.EXPENDABLE_ATTACK, capability.getDoctrine());
        assertTrue(capability.canHover());
        assertTrue(capability.supportsTerminalGuidance());
        assertFalse(capability.canReturnToBase());
    }

    @Test
    public void reusableMapsToReturnCapableStrikeDoctrine() {
        VehicleCapability capability = VehicleCapability.fromProfile(
                new UavVehicleProfile(UavExecutionProfile.LONG_RANGE_REUSABLE_UAV,
                        250, 200, 320, 15, true, "NONE"));
        assertEquals(MissionDoctrine.REUSABLE_STRIKE, capability.getDoctrine());
        assertTrue(capability.canOrbit());
        assertTrue(capability.canLand());
        assertTrue(capability.canReturnToBase());
        assertFalse(capability.canHover());
    }
}
