package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.vas.rdp.uav.RDPUavMod;
import net.vas.rdp.uav.flight.NativeAircraftControlAdapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Describes who may supply aircraft movement for a mission tick.
 *
 * <p>This is the server-authoritative control boundary. The lease is the real
 * enforcement point; mission-state ownership remains the policy reference.</p>
 */
public final class MissionMovementAuthority {
    private static final long CONTROL_LEASE_TICKS = 2L;
    private static final Map<UUID, AircraftControlLease> ACTIVE_LEASES = new ConcurrentHashMap<>();

    private MissionMovementAuthority() { }

    public static boolean operatorFlightInputAllowed(RDPMission mission) {
        return mission == null || mission.getControlOwner() == ControlOwner.MANUAL;
    }

    public static boolean nativeWingManRequired(RDPMission mission) {
        if (mission == null) return false;
        ControlOwner owner = mission.getControlOwner();
        return owner == ControlOwner.MISSION_AI
                || owner == ControlOwner.TERMINAL_GUIDANCE
                || owner == ControlOwner.RECOVERY;
    }

    public static boolean claimControl(RDPMission mission, UUID aircraftId, long tick) {
        if (aircraftId == null || !nativeWingManRequired(mission)
                || tick < 0L) return false;
        long expiryTick = tick + CONTROL_LEASE_TICKS;
        AircraftControlLease active = ACTIVE_LEASES.get(aircraftId);
        if (active != null && !active.isExpired(tick)
                && !active.getMissionId().equals(mission.getMissionId())) {
            return false;
        }
        if (active != null && !active.isExpired(tick)
                && active.getMissionId().equals(mission.getMissionId())) {
            ACTIVE_LEASES.put(aircraftId,
                    AircraftControlLease.forMission(mission, aircraftId, tick, expiryTick,
                            "MISSION_CONTROL_RENEWAL_" + mission.getControlOwner()));
            return true;
        }
        ACTIVE_LEASES.put(aircraftId,
                AircraftControlLease.forMission(mission, aircraftId, tick, expiryTick,
                        "MISSION_CONTROL_" + mission.getControlOwner()));
        return true;
    }

    public static boolean canIssueNativeControl(RDPMission mission, UUID aircraftId, long tick) {
        if (aircraftId == null || !nativeWingManRequired(mission)
                || tick < 0L) return false;
        AircraftControlLease active = ACTIVE_LEASES.get(aircraftId);
        return active != null
                && !active.isExpired(tick)
                && active.getMissionId().equals(mission.getMissionId());
    }

    public static void releaseControl(UUID aircraftId) {
        if (aircraftId != null) {
            ACTIVE_LEASES.remove(aircraftId);
        }
    }

    public static void releaseExpiredControls(long tick) {
        if (tick < 0L) return;
        for (Map.Entry<UUID, AircraftControlLease> entry : ACTIVE_LEASES.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isExpired(tick)) {
                ACTIVE_LEASES.remove(entry.getKey());
            }
        }
    }

    public static AircraftControlLease getActiveLease(UUID aircraftId) {
        return aircraftId == null ? null : ACTIVE_LEASES.get(aircraftId);
    }

    /**
     * Packet boundary used by the MCheli transformer. Movement packets are
     * still allowed to carry camera, weapon, and disconnect actions; only the
     * native flight-axis flags are rejected while an addon mission owns the
     * aircraft.
     */
    public static boolean suppressOperatorFlightInput(MCH_EntityAircraft aircraft,
                                                       Entity operator) {
        if (aircraft == null || operator == null || aircraft.world == null
                || aircraft.world.isRemote) {
            return false;
        }
        RDPMission mission = RDPUavMod.missions().activeMission(
                aircraft.world, aircraft.getUniqueID());
        if (!nativeWingManRequired(mission)) {
            return false;
        }
        aircraft.throttleUp = false;
        aircraft.throttleDown = false;
        aircraft.moveLeft = false;
        aircraft.moveRight = false;
        return true;
    }

    /**
     * Guard injected into native WingMan movement writers. It acquires the
     * addon lease before WingMan can write, closing the first-tick race; the
     * addon controller renews the same lease and applies setpoints later in
     * the world-tick callback.
     */
    public static boolean suppressWingManMovement(Entity entity) {
        if (!(entity instanceof MCH_EntityAircraft) || entity.world == null || entity.world.isRemote) {
            return false;
        }
        MCH_EntityAircraft aircraft = (MCH_EntityAircraft) entity;
        RDPMission mission = RDPUavMod.missions().activeMission(
                aircraft.world, aircraft.getUniqueID());
        long tick = aircraft.world.getTotalWorldTime();
        if (mission == null || mission.isTerminal()
                || !nativeWingManRequired(mission)
                || NativeAircraftControlAdapter.canControlAircraft(aircraft)) {
            return false;
        }
        if (mission.getState() == MissionState.LANDING
                || mission.getState() == MissionState.PARKING) {
            // MCheliCE's landing/parking order contains direct native
            // movement updates outside the generic FLY_TO helpers. Return
            // final recovery control to that route handler as one unit.
            releaseControl(aircraft.getUniqueID());
            return false;
        }
        return claimControl(mission, aircraft.getUniqueID(), tick)
                && canIssueNativeControl(mission, aircraft.getUniqueID(), tick);
    }
}
