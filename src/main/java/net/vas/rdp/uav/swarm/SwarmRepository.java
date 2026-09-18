package net.vas.rdp.uav.swarm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SwarmRepository {
    private final Map<UUID, SwarmRecord> records = new LinkedHashMap<>();
    public void put(SwarmRecord record) { if (record != null) records.put(record.getSwarmId(), record); }
    public SwarmRecord find(UUID id) { return id == null ? null : records.get(id); }
    public void remove(UUID id) { if (id != null) records.remove(id); }
    public void clear() { records.clear(); }
    public List<SwarmRecord> snapshot() { return new ArrayList<>(records.values()); }
}
