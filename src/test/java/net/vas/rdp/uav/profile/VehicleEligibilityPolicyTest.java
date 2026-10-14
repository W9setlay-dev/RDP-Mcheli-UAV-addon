package net.vas.rdp.uav.profile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VehicleEligibilityPolicyTest {
    @Test
    public void acceptsFixedWingPlaneWhenVtolIsDisabled() {
        assertNull(VehicleEligibilityPolicy.validate(
                VehicleEligibilityPolicy.VehicleKind.FIXED_WING_PLANE, false));
    }

    @Test
    public void rejectsVtolEnabledPlaneEvenWhenCurrentlyInFixedWingMode() {
        assertEquals("VTOL_PLANES_UNSUPPORTED",
                VehicleEligibilityPolicy.validate(
                        VehicleEligibilityPolicy.VehicleKind.FIXED_WING_PLANE, true));
    }

    @Test
    public void acceptsNativeHelicopter() {
        assertNull(VehicleEligibilityPolicy.validate(
                VehicleEligibilityPolicy.VehicleKind.NATIVE_HELICOPTER, false));
    }

    @Test
    public void rejectsUnknownVehicleKind() {
        assertEquals("UNSUPPORTED_AIRCRAFT_TYPE",
                VehicleEligibilityPolicy.validate(
                        VehicleEligibilityPolicy.VehicleKind.UNSUPPORTED, false));
        assertEquals("UNSUPPORTED_AIRCRAFT_TYPE",
                VehicleEligibilityPolicy.validate(null, false));
    }

    @Test
    public void onlyConfiguredOrNativeHelicopterProfilesAdvertiseStationHold() {
        UavVehicleProfile fixedWingWithoutHold = profile(
                UavExecutionProfile.ONE_WAY, false);
        UavVehicleProfile reusableWithHold = profile(
                UavExecutionProfile.REUSABLE_UAV, true);
        UavVehicleProfile helicopter = profile(
                UavExecutionProfile.HELICOPTER_ONE_WAY_FPV, false);

        assertFalse(fixedWingWithoutHold.supportsStationHold());
        assertTrue(reusableWithHold.supportsStationHold());
        assertTrue(helicopter.supportsStationHold());
    }

    private static UavVehicleProfile profile(UavExecutionProfile execution, boolean holding) {
        return new UavVehicleProfile(execution, 32, 8, 128, 4, holding, "NONE");
    }
}
