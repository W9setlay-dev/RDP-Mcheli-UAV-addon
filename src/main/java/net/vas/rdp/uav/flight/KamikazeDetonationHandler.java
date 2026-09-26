package net.vas.rdp.uav.flight;

import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.vas.rdp.uav.RDPUavMod;
import net.vas.rdp.uav.RdpUavDebug;
import net.vas.rdp.uav.mission.MissionState;
import net.vas.rdp.uav.mission.RDPMission;
import net.vas.rdp.uav.persistence.RDPMissionWorldData;
import net.vas.rdp.uav.profile.UavVehicleProfile;
import net.vas.rdp.uav.profile.UavVehicleProfiles;

import java.util.UUID;

/** Handles a committed kamikaze fuse before McHeli runs its native destruction logic. */
public final class KamikazeDetonationHandler {
    private final WingManFlightAdapter flight = new WingManFlightAdapter();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeNativeAircraftDestruction(KamikazePreDestructionEvent event) {
        MCH_EntityAircraft aircraft = event.getAircraft();
        if (aircraft == null || aircraft.world == null || aircraft.world.isRemote
                || aircraft.getAcInfo() == null) return;

        UUID missionId = RDPUavMod.missions().assignedMission(aircraft.getUniqueID());
        if (missionId == null) return;
        World world = aircraft.world;
        RDPMission mission = RDPMissionWorldData.get(world).getRepository().find(missionId);
        if (mission == null || mission.getState() != MissionState.KAMIKADZE_TARGET
                || mission.isKamikazePayloadLaunched()) return;

        UavVehicleProfile profile = UavVehicleProfiles.forAircraft(aircraft.getAcInfo().name);
        if (profile == null || profile.isReusable()
                || !"SELF_DESTRUCT".equalsIgnoreCase(profile.getTerminalPayloadBehavior())
                || !flight.isKamikazeSpeedReached(aircraft)
                || !KamikazeImpactDetector.hasNoseImpact(aircraft)) return;

        long tick = world.getTotalWorldTime();
        if (!flight.detonateSelfDestructWeapon(aircraft,
                aircraft.posX, aircraft.posY, aircraft.posZ, false)) return;

        mission.markKamikazePayloadLaunched(tick);
        mission.confirmKamikazeEffect(tick);
        RDPMissionWorldData.get(world).markDirty();
        RdpUavDebug.event("TerminalGuidance", "PRE_DESTRUCTION_FUSE_DETONATED",
                "mission", mission.getMissionId(),
                "aircraft", aircraft.getUniqueID(),
                "target", mission.getTargetId(),
                "tick", tick,
                "cause", event.getDestructionReason(),
                "targetHitInferred", false);
        event.markFuseHandled();
    }
}
