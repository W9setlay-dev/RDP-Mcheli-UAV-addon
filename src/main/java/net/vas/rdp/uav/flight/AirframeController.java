package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;

/**
 * Deterministic per-airframe controller contract.
 *
 * <p>The controller emits a single bounded movement decision without writing
 * entity motion or directly taking over native WingMan authority. It is only a
 * policy bridge between observed state and the verified native control boundary.</p>
 */
public interface AirframeController {
    FlightCommand update(AircraftState state, double targetX, double targetY,
                         double targetZ, long currentTick);

    static AirframeController forProfile(UavVehicleProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Vehicle profile is required");
        }
        UavExecutionProfile executionProfile = profile.getExecutionProfile();
        switch (executionProfile) {
            case HELICOPTER_ONE_WAY_FPV:
                return new HelicopterFlightController(profile);
            case ONE_WAY:
            case REUSABLE_UAV:
            case LONG_RANGE_REUSABLE_UAV:
                return new FixedWingFlightController(profile);
            case UNKNOWN:
            default:
                throw new IllegalArgumentException("Unsupported aircraft controller profile: " + executionProfile);
        }
    }
}
