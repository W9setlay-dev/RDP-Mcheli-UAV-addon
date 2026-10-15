package net.vas.rdp.uav.designation;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.vas.rdp.uav.RdpUavDebug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DesignationRegistry {
    private final Map<UUID, Designation> byId = new HashMap<>();
    private final Map<UUID, List<UUID>> byOperator = new HashMap<>();

    public synchronized void add(Designation designation) {
        if (designation == null) throw new IllegalArgumentException("designation is required");
        byId.put(designation.getDesignationId(), designation);
        List<UUID> ids = byOperator.get(designation.getOperatorId());
        if (ids == null) {
            ids = new ArrayList<>();
            byOperator.put(designation.getOperatorId(), ids);
        }
        if (!ids.contains(designation.getDesignationId())) ids.add(designation.getDesignationId());
    }

    public synchronized Designation find(UUID designationId) {
        return byId.get(designationId);
    }

    public synchronized List<Designation> byOperator(UUID operatorId) {
        List<UUID> ids = byOperator.get(operatorId);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        ArrayList<Designation> result = new ArrayList<>();
        for (UUID designationId : ids) {
            Designation designation = byId.get(designationId);
            if (designation != null) result.add(designation);
        }
        return result;
    }

    public synchronized Designation remove(UUID designationId) {
        Designation removed = byId.remove(designationId);
        if (removed == null) return null;
        List<UUID> ids = byOperator.get(removed.getOperatorId());
        if (ids != null) {
            ids.remove(designationId);
            if (ids.isEmpty()) byOperator.remove(removed.getOperatorId());
        }
        return removed;
    }

    public synchronized int unresolvedCount(UUID operatorId) {
        int count = 0;
        for (Designation designation : byOperator(operatorId)) {
            if (!designation.isAttached() && !designation.isExpired(System.currentTimeMillis() / 50L)) {
                count++;
            }
        }
        return count;
    }

    public synchronized void expire(long currentTick) {
        ArrayList<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Designation> entry : byId.entrySet()) {
            if (entry.getValue().isExpired(currentTick)) expired.add(entry.getKey());
        }
        for (UUID designationId : expired) remove(designationId);
    }

    public synchronized void clear() {
        byId.clear();
        byOperator.clear();
    }

    public synchronized List<Designation> snapshot() {
        return new ArrayList<>(byId.values());
    }

    public synchronized NBTTagCompound writeToNBT() {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("schemaVersion", 1);
        NBTTagList list = new NBTTagList();
        for (Designation designation : new ArrayList<>(byId.values())) {
            list.appendTag(designation.writeToNBT());
        }
        root.setTag("designations", list);
        return root;
    }

    public synchronized void readFromNBT(NBTTagCompound root) {
        clear();
        if (root == null) return;
        int schema = root.hasKey("schemaVersion") ? root.getInteger("schemaVersion") : 1;
        if (schema != 1) {
            RdpUavDebug.warn("DesignationRegistry", "Rejected designation save with unsupported schemaVersion=" + schema);
            return;
        }
        NBTTagList list = root.getTagList("designations", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            try {
                Designation designation = Designation.readFromNBT(list.getCompoundTagAt(i));
                add(designation);
            } catch (IllegalArgumentException ex) {
                RdpUavDebug.warn("DesignationRegistry", "Rejected malformed designation record: " + ex.getMessage());
            }
        }
    }
}
