package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.deconfliction.TrajectorySample;
import net.vas.rdp.uav.profile.UavExecutionProfile;
import net.vas.rdp.uav.profile.UavVehicleProfile;

/**
 * Deterministic per-airframe controller contract.
 *
 * <p>The controller converts a route setpoint and observed airframe state into
 * absolute attitude/throttle setpoints. The adapter rate-limits these values
 * and applies them through MCheliCE's aircraft API.</p>
 */
public interface AirframeController {
    NativeFlightControlCommand update(AircraftState state, double targetX,
                                     double targetY, double targetZ,
                                     long currentTick);

    default NativeFlightControlCommand update(AircraftState state, double targetX,
                                             double targetY, double targetZ,
                                             long currentTick,
                                             boolean absoluteAltitude) {
        return update(state, targetX, targetY, targetZ, currentTick);
    }

    default NativeFlightControlCommand update(AircraftState state,
                                              TrajectorySample sample,
                                              long currentTick,
                                              boolean absoluteAltitude) {
        if (sample == null) throw new IllegalArgumentException("Trajectory sample is required");
        return update(state, sample.getX(), sample.getY(), sample.getZ(),
                currentTick, absoluteAltitude);
    }

    static AirframeController forProfile(UavVehicleProfile profile) {
        return forProfile(profile, false);
    }

    static AirframeController forProfile(UavVehicleProfile profile,
                                         boolean nativeHelicopterAirframe) {
        if (profile == null) {
            throw new IllegalArgumentException("Vehicle profile is required");
        }
        if (nativeHelicopterAirframe || profile.isHelicopterProfile()) {
            return new HelicopterFlightController(profile);
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
