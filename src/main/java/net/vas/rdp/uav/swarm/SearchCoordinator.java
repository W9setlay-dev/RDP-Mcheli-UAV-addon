package net.vas.rdp.uav.swarm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic sector allocation; flight remains mission/WingMan-owned. */
public final class SearchCoordinator {
    public List<SearchSector> sectors(double centerX, double centerZ, double width,
                                      double depth, int count) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || width <= 0 || depth <= 0 || count <= 0) {
            throw new IllegalArgumentException("Search area and sector count are required");
        }
        List<SearchSector> result = new ArrayList<>();
        double sectorWidth = width / count;
        double minX = centerX - width / 2D;
        double minZ = centerZ - depth / 2D;
        for (int i = 0; i < count; i++) {
            result.add(new SearchSector(i, minX + i * sectorWidth,
                    minX + (i + 1) * sectorWidth, minZ, minZ + depth));
        }
        return Collections.unmodifiableList(result);
    }

    public Map<UUID, SearchSector> assign(List<SwarmMember> members,
                                           List<SearchSector> sectors) {
        if (members == null || sectors == null) {
            throw new IllegalArgumentException("Members and sectors are required");
        }
        Map<UUID, SearchSector> result = new LinkedHashMap<>();
        int index = 0;
        for (SwarmMember member : members) {
            if (!member.isAvailable() || member.isOperatorControlled()
                    || sectors.isEmpty()) continue;
            result.put(member.getAircraftId(), sectors.get(index++ % sectors.size()));
        }
        return Collections.unmodifiableMap(result);
    }
}
