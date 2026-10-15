package net.vas.rdp.uav.designation;

import net.minecraft.nbt.NBTTagCompound;
import net.vas.rdp.uav.target.TargetPriority;

import java.util.Objects;
import java.util.UUID;

public final class Designation {
    private final UUID designationId;
    private final UUID operatorId;
    private final int dimension;
    private final DesignationType type;
    private final DesignationSource source;
    private final long createdAtTick;
    private final long expiresAtTick;
    private final float confidence;
    private final String provenance;
    private final PointPayload point;
    private final EntityPayload entity;
    private final AreaPayload area;
    private final CompositePayload composite;
    private final TargetPriority priority;
    private final String notes;
    private final UUID attachedMissionId;
    private final DesignationStatus status;

    private Designation(Builder builder) {
        this.designationId = builder.designationId == null ? UUID.randomUUID() : builder.designationId;
        this.operatorId = Objects.requireNonNull(builder.operatorId, "operatorId");
        this.dimension = builder.dimension;
        this.type = Objects.requireNonNull(builder.type, "type");
        this.source = Objects.requireNonNull(builder.source, "source");
        this.createdAtTick = builder.createdAtTick;
        this.expiresAtTick = builder.expiresAtTick;
        this.confidence = builder.confidence;
        this.provenance = builder.provenance == null ? "" : builder.provenance;
        this.point = builder.point;
        this.entity = builder.entity;
        this.area = builder.area;
        this.composite = builder.composite;
        this.priority = builder.priority == null ? TargetPriority.NORMAL : builder.priority;
        this.notes = builder.notes == null ? "" : limit(builder.notes, 256);
        this.attachedMissionId = builder.attachedMissionId;
        this.status = builder.status == null ? DesignationStatus.ACTIVE : builder.status;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getDesignationId() { return designationId; }
    public UUID getOperatorId() { return operatorId; }
    public int getDimension() { return dimension; }
    public DesignationType getType() { return type; }
    public DesignationSource getSource() { return source; }
    public long getCreatedAtTick() { return createdAtTick; }
    public long getExpiresAtTick() { return expiresAtTick; }
    public float getConfidence() { return confidence; }
    public String getProvenance() { return provenance; }
    public PointPayload getPoint() { return point; }
    public EntityPayload getEntity() { return entity; }
    public AreaPayload getArea() { return area; }
    public CompositePayload getComposite() { return composite; }
    public TargetPriority getPriority() { return priority; }
    public String getNotes() { return notes; }
    public UUID getAttachedMissionId() { return attachedMissionId; }
    public DesignationStatus getStatus() { return status; }

    public boolean isExpired(long currentTick) {
        return expiresAtTick != 0L && currentTick >= expiresAtTick;
    }

    public boolean isActive() {
        return status == DesignationStatus.ACTIVE;
    }

    public boolean isAttached() {
        return attachedMissionId != null;
    }

    public Designation withStatus(DesignationStatus newStatus) {
        return copyBuilder().status(newStatus).build();
    }

    public Designation withAttachedMission(UUID missionId) {
        return copyBuilder().attachedMissionId(missionId).build();
    }

    private Builder copyBuilder() {
        return builder().designationId(designationId).operatorId(operatorId).dimension(dimension)
                .type(type).source(source).createdAtTick(createdAtTick).expiresAtTick(expiresAtTick)
                .confidence(confidence).provenance(provenance).point(point).entity(entity).area(area)
                .composite(composite).priority(priority).notes(notes).attachedMissionId(attachedMissionId)
                .status(status);
    }

    public DesignationView toView() {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        UUID entityUuid = null;
        if (point != null) {
            x = point.x; y = point.y; z = point.z;
        }
        if (entity != null) {
            entityUuid = entity.entityUuid;
            x = entity.lastKnownX;
            y = entity.lastKnownY;
            z = entity.lastKnownZ;
        }
        if (composite != null) {
            if (composite.point != null) {
                x = composite.point.x;
                y = composite.point.y;
                z = composite.point.z;
            }
            if (composite.entity != null) {
                entityUuid = composite.entity.entityUuid;
            }
        }
        return new DesignationView(designationId, type, source, provenance,
                confidence, expiresAtTick, attachedMissionId, x, y, z, entityUuid);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("designationId", designationId.toString());
        tag.setString("operatorId", operatorId.toString());
        tag.setInteger("dimension", dimension);
        tag.setString("type", type.name());
        tag.setString("source", source.name());
        tag.setLong("createdAtTick", createdAtTick);
        tag.setLong("expiresAtTick", expiresAtTick);
        tag.setFloat("confidence", confidence);
        tag.setString("provenance", provenance);
        if (point != null) {
            tag.setDouble("pointX", point.x);
            tag.setDouble("pointY", point.y);
            tag.setDouble("pointZ", point.z);
            tag.setBoolean("terrainResolved", point.terrainResolved);
            tag.setInteger("surfaceY", point.surfaceY);
        }
        if (entity != null) {
            tag.setString("entityUuid", entity.entityUuid.toString());
            tag.setString("registryId", entity.registryId == null ? "" : entity.registryId);
            tag.setDouble("entityX", entity.lastKnownX);
            tag.setDouble("entityY", entity.lastKnownY);
            tag.setDouble("entityZ", entity.lastKnownZ);
            tag.setLong("lastSeenTick", entity.lastSeenTick);
        }
        if (area != null) {
            tag.setDouble("areaCenterX", area.centerX);
            tag.setDouble("areaCenterZ", area.centerZ);
            tag.setInteger("halfExtent", area.halfExtent);
            tag.setInteger("minY", area.minY);
            tag.setInteger("maxY", area.maxY);
        }
        if (composite != null) {
            NBTTagCompound compositeTag = new NBTTagCompound();
            if (composite.point != null) {
                compositeTag.setDouble("pointX", composite.point.x);
                compositeTag.setDouble("pointY", composite.point.y);
                compositeTag.setDouble("pointZ", composite.point.z);
                compositeTag.setBoolean("terrainResolved", composite.point.terrainResolved);
                compositeTag.setInteger("surfaceY", composite.point.surfaceY);
            }
            if (composite.entity != null) {
                compositeTag.setString("entityUuid", composite.entity.entityUuid.toString());
                compositeTag.setString("registryId", composite.entity.registryId == null ? "" : composite.entity.registryId);
                compositeTag.setDouble("entityX", composite.entity.lastKnownX);
                compositeTag.setDouble("entityY", composite.entity.lastKnownY);
                compositeTag.setDouble("entityZ", composite.entity.lastKnownZ);
                compositeTag.setLong("lastSeenTick", composite.entity.lastSeenTick);
            }
            tag.setTag("composite", compositeTag);
        }
        tag.setString("priority", priority.name());
        tag.setString("notes", notes);
        if (attachedMissionId != null) tag.setString("attachedMissionId", attachedMissionId.toString());
        tag.setString("status", status.name());
        tag.setInteger("schemaVersion", 2);
        return tag;
    }

    public static Designation readFromNBT(NBTTagCompound tag) {
        if (tag == null) throw new IllegalArgumentException("designation tag is required");
        int version = tag.hasKey("schemaVersion") ? tag.getInteger("schemaVersion") : 1;
        if (version < 1 || version > 2) {
            throw new IllegalArgumentException("unsupported schemaVersion " + version);
        }

        Builder builder = builder();
        builder.designationId(UUID.fromString(tag.getString("designationId")));
        builder.operatorId(UUID.fromString(tag.getString("operatorId")));
        builder.dimension(tag.getInteger("dimension"));
        builder.type(DesignationType.valueOf(tag.getString("type")));
        builder.source(DesignationSource.valueOf(tag.getString("source")));
        builder.createdAtTick(tag.getLong("createdAtTick"));
        builder.expiresAtTick(tag.getLong("expiresAtTick"));
        builder.confidence(tag.getFloat("confidence"));
        builder.provenance(tag.getString("provenance"));

        if (tag.hasKey("pointX") && tag.hasKey("pointY") && tag.hasKey("pointZ")) {
            builder.point(new PointPayload(
                    tag.getDouble("pointX"),
                    tag.getDouble("pointY"),
                    tag.getDouble("pointZ"),
                    tag.getBoolean("terrainResolved"),
                    tag.getInteger("surfaceY")));
        }
        if (tag.hasKey("entityUuid")) {
            builder.entity(new EntityPayload(
                    UUID.fromString(tag.getString("entityUuid")),
                    tag.getString("registryId"),
                    tag.getDouble("entityX"),
                    tag.getDouble("entityY"),
                    tag.getDouble("entityZ"),
                    tag.getLong("lastSeenTick")));
        }
        if (tag.hasKey("areaCenterX") && tag.hasKey("areaCenterZ")) {
            builder.area(new AreaPayload(
                    tag.getDouble("areaCenterX"),
                    tag.getDouble("areaCenterZ"),
                    tag.getInteger("halfExtent"),
                    tag.getInteger("minY"),
                    tag.getInteger("maxY")));
        }
        if (tag.hasKey("composite")) {
            NBTTagCompound compositeTag = tag.getCompoundTag("composite");
            PointPayload compositePoint = null;
            if (compositeTag.hasKey("pointX")) {
                compositePoint = new PointPayload(
                        compositeTag.getDouble("pointX"),
                        compositeTag.getDouble("pointY"),
                        compositeTag.getDouble("pointZ"),
                        compositeTag.getBoolean("terrainResolved"),
                        compositeTag.getInteger("surfaceY"));
            }
            EntityPayload compositeEntity = null;
            if (compositeTag.hasKey("entityUuid")) {
                compositeEntity = new EntityPayload(
                        UUID.fromString(compositeTag.getString("entityUuid")),
                        compositeTag.getString("registryId"),
                        compositeTag.getDouble("entityX"),
                        compositeTag.getDouble("entityY"),
                        compositeTag.getDouble("entityZ"),
                        compositeTag.getLong("lastSeenTick"));
            }
            builder.composite(new CompositePayload(compositePoint, compositeEntity));
        }
        if (tag.hasKey("priority")) builder.priority(TargetPriority.valueOf(tag.getString("priority")));
        if (tag.hasKey("notes")) builder.notes(tag.getString("notes"));
        if (tag.hasKey("attachedMissionId") && !tag.getString("attachedMissionId").isEmpty()) {
            builder.attachedMissionId(UUID.fromString(tag.getString("attachedMissionId")));
        }
        if (tag.hasKey("status")) builder.status(DesignationStatus.valueOf(tag.getString("status")));
        return builder.build();
    }

    private static String limit(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    public static final class PointPayload {
        public final double x;
        public final double y;
        public final double z;
        public final boolean terrainResolved;
        public final int surfaceY;

        public PointPayload(double x, double y, double z, boolean terrainResolved, int surfaceY) {
            this.x = x; this.y = y; this.z = z; this.terrainResolved = terrainResolved; this.surfaceY = surfaceY;
        }
    }

    public static final class EntityPayload {
        public final UUID entityUuid;
        public final String registryId;
        public final double lastKnownX;
        public final double lastKnownY;
        public final double lastKnownZ;
        public final long lastSeenTick;

        public EntityPayload(UUID entityUuid, String registryId, double lastKnownX, double lastKnownY,
                            double lastKnownZ, long lastSeenTick) {
            this.entityUuid = entityUuid;
            this.registryId = registryId;
            this.lastKnownX = lastKnownX;
            this.lastKnownY = lastKnownY;
            this.lastKnownZ = lastKnownZ;
            this.lastSeenTick = lastSeenTick;
        }
    }

    public static final class AreaPayload {
        public final double centerX;
        public final double centerZ;
        public final int halfExtent;
        public final int minY;
        public final int maxY;

        public AreaPayload(double centerX, double centerZ, int halfExtent, int minY, int maxY) {
            this.centerX = centerX; this.centerZ = centerZ; this.halfExtent = halfExtent; this.minY = minY; this.maxY = maxY;
        }
    }

    public static final class CompositePayload {
        public final PointPayload point;
        public final EntityPayload entity;

        public CompositePayload(PointPayload point, EntityPayload entity) {
            this.point = point; this.entity = entity;
        }
    }

    public static final class Builder {
        private UUID designationId;
        private UUID operatorId;
        private int dimension;
        private DesignationType type;
        private DesignationSource source;
        private long createdAtTick;
        private long expiresAtTick;
        private float confidence = 1.0F;
        private String provenance = "";
        private PointPayload point;
        private EntityPayload entity;
        private AreaPayload area;
        private CompositePayload composite;
        private TargetPriority priority = TargetPriority.NORMAL;
        private String notes = "";
        private UUID attachedMissionId;
        private DesignationStatus status = DesignationStatus.ACTIVE;

        public Builder designationId(UUID designationId) { this.designationId = designationId; return this; }
        public Builder operatorId(UUID operatorId) { this.operatorId = operatorId; return this; }
        public Builder dimension(int dimension) { this.dimension = dimension; return this; }
        public Builder type(DesignationType type) { this.type = type; return this; }
        public Builder source(DesignationSource source) { this.source = source; return this; }
        public Builder createdAtTick(long createdAtTick) { this.createdAtTick = createdAtTick; return this; }
        public Builder expiresAtTick(long expiresAtTick) { this.expiresAtTick = expiresAtTick; return this; }
        public Builder confidence(float confidence) { this.confidence = confidence; return this; }
        public Builder provenance(String provenance) { this.provenance = provenance; return this; }
        public Builder point(PointPayload point) { this.point = point; return this; }
        public Builder entity(EntityPayload entity) { this.entity = entity; return this; }
        public Builder area(AreaPayload area) { this.area = area; return this; }
        public Builder composite(CompositePayload composite) { this.composite = composite; return this; }
        public Builder priority(TargetPriority priority) { this.priority = priority; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }
        public Builder attachedMissionId(UUID attachedMissionId) { this.attachedMissionId = attachedMissionId; return this; }
        public Builder status(DesignationStatus status) { this.status = status; return this; }

        public Designation build() {
            return new Designation(this);
        }
    }
}
