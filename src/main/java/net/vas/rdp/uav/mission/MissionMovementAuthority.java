package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.vas.rdp.uav.RDPUavMod;

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
        if (active != null && !active.isExpired(tick) && !active.getMissionId().equals(mission.getMissionId())) {
            return false;
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
        if (mission == null || !nativeWingManRequired(mission)) {
            return false;
        }
        aircraft.throttleUp = false;
        aircraft.throttleDown = false;
        aircraft.moveLeft = false;
        aircraft.moveRight = false;
        return true;
    }
}
