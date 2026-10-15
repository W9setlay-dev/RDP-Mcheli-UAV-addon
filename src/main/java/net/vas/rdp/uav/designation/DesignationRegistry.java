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

    public enum AddResult {
        ADDED,
        DUPLICATE,
        QUEUE_FULL,
        TTL_EXPIRED,
        INVALID_REPLACEMENT
    }

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

    public synchronized AddResult tryAdd(Designation designation, long currentTick,
                                          int maxPerOperator, double duplicatePointRadius,
                                          String duplicateEntityPolicy) {
        if (designation == null) throw new IllegalArgumentException("designation is required");
        validateLimits(maxPerOperator, duplicatePointRadius);
        if (designation.isExpired(currentTick)) return AddResult.TTL_EXPIRED;
        if (!designation.isActive() || byId.containsKey(designation.getDesignationId())
                || isDuplicate(designation, currentTick, duplicatePointRadius,
                duplicateEntityPolicy, null)) {
            return AddResult.DUPLICATE;
        }
        if (unresolvedCount(designation.getOperatorId(), currentTick) >= maxPerOperator) {
            return AddResult.QUEUE_FULL;
        }
        add(designation);
        return AddResult.ADDED;
    }

    public synchronized AddResult replace(UUID existingId, Designation replacement,
                                           long currentTick, int maxPerOperator,
                                           double duplicatePointRadius,
                                           String duplicateEntityPolicy) {
        Designation existing = byId.get(existingId);
        if (existing == null || replacement == null || existing.isAttached()
                || !existing.isActive() || existing.isExpired(currentTick)
                || !existing.getOperatorId().equals(replacement.getOperatorId())
                || !replacement.isActive() || replacement.isExpired(currentTick)
                || existingId.equals(replacement.getDesignationId())
                || byId.containsKey(replacement.getDesignationId())) {
            return AddResult.INVALID_REPLACEMENT;
        }
        validateLimits(maxPerOperator, duplicatePointRadius);
        if (isDuplicate(replacement, currentTick, duplicatePointRadius,
                duplicateEntityPolicy, existingId)) {
            return AddResult.DUPLICATE;
        }
        if (unresolvedCount(existing.getOperatorId(), currentTick) > maxPerOperator) {
            return AddResult.QUEUE_FULL;
        }
        byId.put(existingId, existing.withStatus(DesignationStatus.SUPERSEDED));
        add(replacement);
        return AddResult.ADDED;
    }

    private static void validateLimits(int maxPerOperator, double duplicatePointRadius) {
        if (maxPerOperator < 1) throw new IllegalArgumentException("maxPerOperator must be positive");
        if (!Double.isFinite(duplicatePointRadius) || duplicatePointRadius < 0.0D) {
            throw new IllegalArgumentException("duplicatePointRadius must be finite and non-negative");
        }
    }

    private boolean isDuplicate(Designation candidate, long currentTick, double pointRadius,
                               String entityPolicy, UUID excludedId) {
        boolean looseEntityMatch = "LOOSE".equalsIgnoreCase(entityPolicy);
        double radiusSquared = pointRadius * pointRadius;
        for (Designation existing : byOperator(candidate.getOperatorId())) {
            if (existing == null || existing.getDesignationId().equals(excludedId)
                    || !existing.isActive() || existing.isAttached()
                    || existing.isExpired(currentTick)) {
                continue;
            }
            Designation.EntityPayload existingEntity = entityPayload(existing);
            Designation.EntityPayload candidateEntity = entityPayload(candidate);
            if (existingEntity != null && candidateEntity != null) {
                if (existingEntity.entityUuid.equals(candidateEntity.entityUuid)) return true;
                if (looseEntityMatch && existing.getDimension() == candidate.getDimension()
                        && existingEntity.registryId != null
                        && existingEntity.registryId.equals(candidateEntity.registryId)
                        && distanceSquared(existingEntity.lastKnownX, existingEntity.lastKnownY,
                        existingEntity.lastKnownZ, candidateEntity.lastKnownX,
                        candidateEntity.lastKnownY, candidateEntity.lastKnownZ) <= radiusSquared) {
                    return true;
                }
            }
            Designation.PointPayload existingPoint = pointPayload(existing);
            Designation.PointPayload candidatePoint = pointPayload(candidate);
            if (existing.getDimension() == candidate.getDimension()
                    && existingPoint != null && candidatePoint != null
                    && distanceSquared(existingPoint.x, existingPoint.y, existingPoint.z,
                    candidatePoint.x, candidatePoint.y, candidatePoint.z) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private static Designation.EntityPayload entityPayload(Designation designation) {
        if (designation.getEntity() != null) return designation.getEntity();
        return designation.getComposite() == null ? null : designation.getComposite().entity;
    }

    private static Designation.PointPayload pointPayload(Designation designation) {
        if (designation.getPoint() != null) return designation.getPoint();
        return designation.getComposite() == null ? null : designation.getComposite().point;
    }

    private static double distanceSquared(double ax, double ay, double az,
                                          double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
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

    public synchronized int unresolvedCount(UUID operatorId, long currentTick) {
        int count = 0;
        for (Designation designation : byOperator(operatorId)) {
            if (designation.isActive() && !designation.isAttached()
                    && !designation.isExpired(currentTick)) {
                count++;
            }
        }
        return count;
    }

    public synchronized int removeUnattached(UUID operatorId) {
        if (operatorId == null) return 0;
        ArrayList<UUID> remove = new ArrayList<>();
        for (Designation designation : byOperator(operatorId)) {
            if (!designation.isAttached()) remove.add(designation.getDesignationId());
        }
        for (UUID id : remove) remove(id);
        return remove.size();
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
        root.setInteger("schemaVersion", 2);
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
        if (schema < 1 || schema > 2) {
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
