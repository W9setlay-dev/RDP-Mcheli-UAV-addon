package net.vas.rdp.uav.profile;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UavVehicleProfileTest {
    @Test
    public void stationHoldRequiresConfiguredCapabilityForFixedWing() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV, 32, 8, 128, 6,
                false, "NONE");

        assertFalse(profile.supportsStationHold());
    }

    @Test
    public void configuredFixedWingCanUseStationHold() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.REUSABLE_UAV, 32, 8, 128, 6,
                true, "NONE");

        assertTrue(profile.supportsStationHold());
    }

    @Test
    public void nativeHelicopterUsesItsNativeHoverCapability() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.HELICOPTER_ONE_WAY_FPV, 8, 4, 32, 6,
                false, "SELF_DESTRUCT");

        assertTrue(profile.supportsStationHold());
    }

    @Test
    public void unknownProfileDoesNotGainTargetLockSupport() {
        UavVehicleProfile profile = new UavVehicleProfile(
                UavExecutionProfile.UNKNOWN, 0, 0, 4096, 0,
                false, "NONE", 256, 256, 64);

        assertFalse(profile.supportsTargetLock());
    }
}
