package net.vas.rdp.uav.ai;

import net.vas.rdp.uav.mission.AircraftRecord;

public final class AiResource {
    private final AircraftRecord aircraft;
    private final double distance;
    private final boolean attackCapable;

    public AiResource(AircraftRecord aircraft, double distance, boolean attackCapable) {
        if (aircraft == null || distance < 0) {
            throw new IllegalArgumentException("AI resource is invalid");
        }
        this.aircraft = aircraft;
        this.distance = distance;
        this.attackCapable = attackCapable;
    }

    public AircraftRecord getAircraft() { return aircraft; }
    public double getDistance() { return distance; }
    public boolean isAttackCapable() { return attackCapable; }
}
