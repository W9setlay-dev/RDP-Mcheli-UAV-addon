package net.vas.rdp.uav;

import net.vas.rdp.uav.profile.MissionDoctrine;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.VehicleCapability;
import net.vas.rdp.uav.mission.MissionType;
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

    @Test
    public void unknownProfileFailsClosedForMissionExecution() {
        VehicleCapability capability = VehicleCapability.fromProfile(
                new UavVehicleProfile(UavExecutionProfile.UNKNOWN,
                        0, 0, 4096, 0, false, "NONE"));

        assertEquals("UNSUPPORTED_AIRCRAFT_PROFILE",
                capability.validateMission(MissionType.RECONNAISSANCE, false));
    }

    @Test
    public void configuredProfilesAcceptTheirDeclaredMissionFamilies() {
        VehicleCapability oneWay = VehicleCapability.fromProfile(
                new UavVehicleProfile(UavExecutionProfile.ONE_WAY,
                        0, 0, 4096, 4, false, "SELF_DESTRUCT"));
        VehicleCapability reusable = VehicleCapability.fromProfile(
                new UavVehicleProfile(UavExecutionProfile.REUSABLE_UAV,
                        250, 200, 320, 15, true, "NONE"));

        assertNull(oneWay.validateMission(MissionType.SQUARE_STRIKE, true));
        assertNull(reusable.validateMission(MissionType.STRIKE, true));
    }

    @Test
    public void profileRejectsImpossibleAltitudeConfiguration() {
        try {
            new UavVehicleProfile(UavExecutionProfile.REUSABLE_UAV,
                    100, 200, 320, 15, true, "NONE");
            fail("Execution altitude below the profile minimum must be rejected");
        } catch (IllegalArgumentException expected) {
            // Configuration must fail closed.
        }
    }
}
