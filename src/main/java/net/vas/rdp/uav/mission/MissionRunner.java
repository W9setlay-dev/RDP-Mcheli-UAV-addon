package net.vas.rdp.uav.mission;

import java.util.EnumMap;
import java.util.Map;

public final class MissionRunner {
    public interface Handler {
        void onEnter(RDPMission mission);
        void onUpdate(RDPMission mission, long tick);
        void onExit(RDPMission mission);
        long timeoutTicks();
    }
    private final Map<MissionState, Handler> handlers = new EnumMap<MissionState, Handler>(MissionState.class);
    private final MissionQueue queue;
    private final MissionManager manager;
    private final Map<java.util.UUID, Long> enteredAt = new java.util.HashMap<java.util.UUID, Long>();

    public MissionRunner(MissionManager manager, MissionQueue queue) {
        this.manager = manager;
        this.queue = queue;
    }
    public void register(MissionState state, Handler handler) { handlers.put(state, handler); }
    public boolean acceptNext(long tick) {
        RDPMission mission = queue.peek();
        if (mission == null) return false;
        Handler handler = handlers.get(MissionState.PLANNED);
        if (handler == null) return false;
        if (!queue.remove(mission)) return false;
        mission.transitionTo(MissionState.DISPATCHED, tick);
        enteredAt.put(mission.getMissionId(), tick);
        handler.onEnter(mission);
        return true;
    }
    public void update(RDPMission mission, long tick) {
        if (mission == null) return;
        Handler handler = handlers.get(mission.getState());
        if (handler == null) return;
        Long entered = enteredAt.get(mission.getMissionId());
        if (entered != null && tick - entered > handler.timeoutTicks()) {
            mission.fail(mission.getState().name() + "_TIMEOUT", tick);
            handler.onExit(mission);
            manager.release(mission);
            return;
        }
        handler.onUpdate(mission, tick);
    }
}
