package net.vas.rdp.uav.mission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public final class AircraftRegistry {
    private final Map<UUID, AircraftRecord> records = new LinkedHashMap<UUID, AircraftRecord>();
    public synchronized void register(AircraftRecord record) { records.put(record.getAircraftId(), record); }
    public synchronized void unregister(UUID id) { records.remove(id); }
    public synchronized AircraftRecord find(UUID id) { return records.get(id); }
    public synchronized boolean canAssign(UUID aircraftId, UUID ownerId) {
        AircraftRecord record = records.get(aircraftId);
        return record != null && record.availableFor(ownerId);
    }
    public synchronized void assign(UUID aircraftId, UUID missionId, UUID ownerId) {
        AircraftRecord record = records.get(aircraftId);
        if (record == null || !record.availableFor(ownerId)) throw new IllegalStateException("AIRCRAFT_UNAVAILABLE");
        record.assign(missionId);
    }
    public synchronized void release(UUID aircraftId) {
        AircraftRecord record = records.get(aircraftId);
        if (record != null) record.release();
    }
    public synchronized List<AircraftRecord> snapshot() {
        return new ArrayList<AircraftRecord>(records.values());
    }
}
