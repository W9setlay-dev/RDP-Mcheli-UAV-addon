package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerrainRoutePlannerAltitudeTest {
    private final UavVehicleProfile profile = new UavVehicleProfile(
            UavExecutionProfile.REUSABLE_UAV, 100, 40, 180, 6,
            true, "NONE", 256, 256, 64, "", 0, 64);

    @Test
    public void explicitWorldYIsNotClampedToProfileCruiseAltitude() {
        TerrainRoutePlanner.AltitudeDecision decision =
                TerrainRoutePlanner.transitAltitude(20, 256, 0, profile, 140);

        assertTrue(decision.isSafe());
        assertEquals(140, decision.getY(), 0);
        assertEquals("ABSOLUTE_WORLD_Y_TRANSIT", decision.getReason());
    }

    @Test
    public void terrainClearanceOverridesAnUnsafeRequestedWorldY() {
        TerrainRoutePlanner.AltitudeDecision decision =
                TerrainRoutePlanner.transitAltitude(100, 256, 0, profile, 110);

        assertTrue(decision.isSafe());
        assertEquals(140, decision.getY(), 0);
        assertEquals("TERRAIN_CLEARANCE_OVERRIDES_CRUISE_ALTITUDE", decision.getReason());
    }

    @Test
    public void explicitWorldYOutsideDimensionBoundsIsRejected() {
        TerrainRoutePlanner.AltitudeDecision decision =
                TerrainRoutePlanner.transitAltitude(20, 256, 0, profile, 255);

        assertFalse(decision.isSafe());
        assertEquals("CRUISE_ALTITUDE_OUT_OF_WORLD_BOUNDS", decision.getReason());
    }

    @Test
    public void unloadedOrUnknownWorldDataIsClassifiedAsWaitable() {
        assertTrue(TerrainRoutePlanner.isWorldDataUnavailableReason("CORRIDOR_UNLOADED"));
        assertTrue(TerrainRoutePlanner.isWorldDataUnavailableReason("CORRIDOR_UNKNOWN"));
        assertTrue(TerrainRoutePlanner.isWorldDataUnavailableReason("AIRSPACE_UNLOADED"));
        assertTrue(TerrainRoutePlanner.isWorldDataUnavailableReason("AIRSPACE_UNKNOWN"));
        assertFalse(TerrainRoutePlanner.isWorldDataUnavailableReason(
                "AIRSPACE_COLLISION_BLOCKED"));
    }
}
