package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.vas.rdp.uav.formation.WingmanRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Server-to-client, presentation-only snapshot of authoritative formation slots. */
public final class PacketFormationSync implements IMessage {
    private static final int MAX_SLOTS = 64;
    private List<FormationSlotView> slots = Collections.emptyList();

    public PacketFormationSync() { }

    /** Creates a bounded immutable formation snapshot. */
    public PacketFormationSync(List<FormationSlotView> slots) {
        if (slots == null || slots.size() > MAX_SLOTS || slots.contains(null)) {
            throw new IllegalArgumentException("Invalid formation snapshot");
        }
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public List<FormationSlotView> getSlots() { return slots; }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (slots.size() > MAX_SLOTS) throw new IllegalStateException("Too many formation slots");
        buffer.writeInt(slots.size());
        for (FormationSlotView slot : slots) {
            writeUuid(buffer, slot.aircraftUuid);
            buffer.writeInt(slot.slotIndex);
            buffer.writeByte(slot.role.ordinal());
            buffer.writeDouble(slot.sideOffset);
            buffer.writeDouble(slot.altitudeOffset);
            buffer.writeDouble(slot.rearOffset);
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > MAX_SLOTS) {
            throw new IllegalArgumentException("Invalid formation slot count");
        }
        List<FormationSlotView> decoded = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID aircraft = readUuid(buffer);
            int slotIndex = buffer.readInt();
            int roleOrdinal = buffer.readUnsignedByte();
            if (roleOrdinal >= WingmanRole.values().length) {
                throw new IllegalArgumentException("Invalid formation role");
            }
            decoded.add(new FormationSlotView(aircraft, slotIndex,
                    WingmanRole.values()[roleOrdinal], buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble()));
        }
        slots = Collections.unmodifiableList(decoded);
    }

    private static void writeUuid(ByteBuf buffer, UUID uuid) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    /** Immutable client projection; it cannot authorize or mutate server state. */
    public static final class FormationSlotView {
        private final UUID aircraftUuid;
        private final int slotIndex;
        private final WingmanRole role;
        private final double sideOffset;
        private final double altitudeOffset;
        private final double rearOffset;

        public FormationSlotView(UUID aircraftUuid, int slotIndex, WingmanRole role,
                                 double sideOffset, double altitudeOffset,
                                 double rearOffset) {
            if (aircraftUuid == null || slotIndex < 0 || role == null
                    || !Double.isFinite(sideOffset) || !Double.isFinite(altitudeOffset)
                    || !Double.isFinite(rearOffset)) {
                throw new IllegalArgumentException("Invalid formation slot view");
            }
            this.aircraftUuid = aircraftUuid;
            this.slotIndex = slotIndex;
            this.role = role;
            this.sideOffset = sideOffset;
            this.altitudeOffset = altitudeOffset;
            this.rearOffset = rearOffset;
        }

        public UUID getAircraftUuid() { return aircraftUuid; }
        public int getSlotIndex() { return slotIndex; }
        public WingmanRole getRole() { return role; }
        public double getSideOffset() { return sideOffset; }
        public double getAltitudeOffset() { return altitudeOffset; }
        public double getRearOffset() { return rearOffset; }
    }
}
