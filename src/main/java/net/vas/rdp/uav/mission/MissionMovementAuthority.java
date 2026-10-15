package net.vas.rdp.uav.mission;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.entity.Entity;
import net.vas.rdp.uav.RDPUavMod;
import net.vas.rdp.uav.flight.NativeAircraftControlAdapter;
import net.vas.rdp.uav.flight.NativeFlightControlCommand;

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
        if (mission == null) return true;
        ControlOwner owner = mission.getControlOwner();
        return owner == ControlOwner.MANUAL
                || owner == ControlOwner.SUPERVISED_OPERATOR;
    }

    public static void applyManualOperatorState(RDPMission mission, boolean operatorInStation, long tick) {
        if (mission == null || tick < 0L) return;
        ControlOwner owner = mission.getControlOwner();
        if (operatorInStation && acceptsOperatorControl(owner)) {
            mission.enterManualControl(tick);
            return;
        }
        if (!operatorInStation && owner == ControlOwner.MANUAL) {
            mission.exitManualControl(tick);
        }
    }

    public static void applySupervisedOperatorState(RDPMission mission, boolean operatorAssistActive, long tick) {
        if (mission == null || tick < 0L) return;
        ControlOwner owner = mission.getControlOwner();
        if (operatorAssistActive && (owner == ControlOwner.MISSION_AI
                || owner == ControlOwner.SUPERVISED_OPERATOR)) {
            mission.enterSupervisedOperatorMode(tick);
            return;
        }
        if (!operatorAssistActive && owner == ControlOwner.SUPERVISED_OPERATOR) {
            mission.exitSupervisedOperatorMode(tick);
        }
    }

    public static void applyDirectRiderOperatorState(RDPMission mission,
                                                     boolean directRiderPresent,
                                                     boolean operatorAssistActive,
                                                     long tick) {
        if (mission == null || tick < 0L) return;
        if (directRiderPresent && isActivelyDispatched(mission)) {
            if (mission.getControlOwner() == ControlOwner.MANUAL) {
                mission.exitManualControl(tick);
            }
            applySupervisedOperatorState(mission, true, tick);
            return;
        }
        applyManualOperatorState(mission, directRiderPresent, tick);
        applySupervisedOperatorState(mission, operatorAssistActive, tick);
    }

    private static boolean isActivelyDispatched(RDPMission mission) {
        if (mission == null || mission.isTerminal()) return false;
        switch (mission.getState()) {
            case DISPATCHED:
            case TAXI:
            case TAKEOFF:
            case CRUISE:
            case SEARCHING:
            case TARGET_ACQUIRED:
            case TARGET_LOCKED:
            case ATTACK_TARGET:
            case APPROACHING:
            case ON_TARGET:
            case ORBITING:
            case SCANNING:
            case DEEP_SCANNING:
            case REPORTING:
            case LAUNCH_ACCEPTED:
            case PROJECTILE_TRACKED:
            case IMPACT_OBSERVED:
            case EFFECT_CONFIRMED:
            case OUTCOME_UNKNOWN:
            case DIRECT_LAUNCH_COMPLETE:
                return true;
            default:
                return false;
        }
    }

    private static boolean acceptsOperatorControl(ControlOwner owner) {
        return owner == ControlOwner.MISSION_AI
                || owner == ControlOwner.SUPERVISED_OPERATOR
                || owner == ControlOwner.MANUAL;
    }

    public static boolean nativeWingManRequired(RDPMission mission) {
        if (mission == null) return false;
        ControlOwner owner = mission.getControlOwner();
        return owner == ControlOwner.MISSION_AI
                || owner == ControlOwner.SUPERVISED_OPERATOR
                || owner == ControlOwner.TERMINAL_GUIDANCE
                || owner == ControlOwner.RECOVERY;
    }

    /**
     * Hybrid flight control model: AI remains the baseline controller while
     * operator assistance is blended into the selected axes. This keeps the
     * aircraft moving without forcing an artificial neutral state.
     */
    public static NativeFlightControlCommand fuseAiAndOperator(RDPMission mission,
                                                             NativeFlightControlCommand aiCommand,
                                                             double operatorRollDelta) {
        return fuseAiAndOperator(mission, aiCommand, 0.0D, 0.0D,
                operatorRollDelta, 0.0D, 0.0D, 6.0D);
    }

    public static NativeFlightControlCommand fuseAiAndOperator(RDPMission mission,
                                                             NativeFlightControlCommand aiCommand,
                                                             double operatorYawDelta,
                                                             double operatorPitchDelta,
                                                             double operatorRollDelta,
                                                             double maximumYawDelta,
                                                             double maximumPitchDelta,
                                                             double maximumRollDelta) {
        ControlOwner owner = mission == null ? null : mission.getControlOwner();
        if (mission == null || aiCommand == null || !aiCommand.isSupported()
                || (owner != ControlOwner.SUPERVISED_OPERATOR
                && owner != ControlOwner.TERMINAL_GUIDANCE)) {
            return aiCommand;
        }
        double deadband = net.vas.rdp.uav.flight.NativeControlMath.operatorDeadband();
        float blendedYaw = net.vas.rdp.uav.flight.NativeControlMath.blendAxis(
                aiCommand.getYaw(), (float) operatorYawDelta, (float) deadband,
                (float) Math.abs(maximumYawDelta));
        // Positive camera pitch means look-up, which is a negative attitude pitch.
        float blendedPitch = net.vas.rdp.uav.flight.NativeControlMath.blendAxis(
                aiCommand.getPitch(), (float) -operatorPitchDelta, (float) deadband,
                (float) Math.abs(maximumPitchDelta));
        double blendedRoll = net.vas.rdp.uav.flight.NativeControlMath.blendAxis(
                aiCommand.getRoll(), operatorRollDelta, deadband,
                Math.abs(maximumRollDelta));

        return NativeFlightControlCommand.setpoints(
                normalizeYaw(blendedYaw),
                blendedPitch,
                (float) blendedRoll,
                aiCommand.getThrottle(),
                aiCommand.getDesiredTrack(),
                aiCommand.getCourseError(),
                aiCommand.getDesiredVerticalSpeed());
    }

    private static float normalizeYaw(float yaw) {
        while (yaw >= 180.0F) yaw -= 360.0F;
        while (yaw < -180.0F) yaw += 360.0F;
        return yaw;
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

    /**
     * Clears the current native-control lease when WingMan or a direct route reset
     * drops the aircraft's mission flow without a matching mission handoff. This
     * is the only safe release path for direct native route teardown; without it,
     * a stale lease can outlive the actual route and reject the next valid mission.
     */
    public static void releaseControl(UUID aircraftId) {
        if (aircraftId == null) return;
        ACTIVE_LEASES.remove(aircraftId);
    }

    /** Release the lease immediately when it is clearly stale for the current tick. */
    public static void releaseControl(UUID aircraftId, long tick) {
        if (aircraftId == null || tick < 0L) return;
        AircraftControlLease active = ACTIVE_LEASES.get(aircraftId);
        if (active == null || active.isExpired(tick)) {
            ACTIVE_LEASES.remove(aircraftId);
        }
    }

    /** Release only the lease owned by this mission; a newer mission may already own the aircraft. */
    public static void releaseControl(UUID aircraftId, UUID missionId) {
        if (aircraftId == null || missionId == null) return;
        AircraftControlLease active = ACTIVE_LEASES.get(aircraftId);
        if (active != null && missionId.equals(active.getMissionId())) {
            ACTIVE_LEASES.remove(aircraftId, active);
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
                || aircraft.world.isRemote || !aircraft.isPilot(operator)) {
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
