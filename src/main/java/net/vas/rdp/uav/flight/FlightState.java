package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import com.norwood.mcheli.wingman.mission.AutonomousState;
import com.norwood.mcheli.wingman.wingman.WingmanEntry;

/**
 * Read-only snapshot of the real McHeli/WingMan state.
 *
 * <p>Movement remains owned by McHeli. This value is intended for mission
 * diagnostics and completion checks, not as a replacement flight model.</p>
 */
public final class FlightState {
    public final double x;
    public final double y;
    public final double z;
    public final double motionX;
    public final double motionY;
    public final double motionZ;
    public final float yaw;
    public final float pitch;
    public final float roll;
    public final double throttle;
    public final int fuel;
    public final int maxFuel;
    public final boolean operational;
    public final AutonomousState autonomousState;
    public final int missionIndex;
    public final String currentNodeType;

    private FlightState(MCH_EntityAircraft aircraft, WingmanEntry entry) {
        x = aircraft.posX;
        y = aircraft.posY;
        z = aircraft.posZ;
        motionX = aircraft.motionX;
        motionY = aircraft.motionY;
        motionZ = aircraft.motionZ;
        yaw = aircraft.getYaw();
        pitch = aircraft.getPitch();
        roll = aircraft.getRoll();
        throttle = aircraft.getThrottle();
        fuel = aircraft.getFuel();
        maxFuel = aircraft.getMaxFuel();
        operational = !aircraft.isDead && !aircraft.isDestroyed();
        autonomousState = entry == null ? AutonomousState.NONE : entry.autoState;
        missionIndex = entry == null ? -1 : entry.missionIndex;
        currentNodeType = entry == null || entry.currentNode() == null
                ? null : entry.currentNode().type.name();
    }

    static FlightState capture(MCH_EntityAircraft aircraft, WingmanEntry entry) {
        if (aircraft == null) {
            throw new IllegalArgumentException("Aircraft is required");
        }
        return new FlightState(aircraft, entry);
    }

    public double speedSquared() {
        return motionX * motionX + motionY * motionY + motionZ * motionZ;
    }
}
