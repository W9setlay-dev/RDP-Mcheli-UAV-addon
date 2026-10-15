package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.vas.rdp.uav.path.OrbitPrimitive;

import java.util.UUID;

/** Server-to-client orbit lifecycle event for read-only mission presentation. */
public final class PacketOrbitStatus implements IMessage {
    private UUID aircraftUuid;
    private OrbitPrimitive primitive;
    private boolean entering;
    private long serverTick;

    public PacketOrbitStatus() { }

    /** Creates a validated orbit transition event. */
    public PacketOrbitStatus(UUID aircraftUuid, OrbitPrimitive primitive,
                             boolean entering, long serverTick) {
        if (aircraftUuid == null || primitive == null || serverTick < 0L) {
            throw new IllegalArgumentException("Invalid orbit status");
        }
        this.aircraftUuid = aircraftUuid;
        this.primitive = primitive;
        this.entering = entering;
        this.serverTick = serverTick;
    }

    public UUID getAircraftUuid() { return aircraftUuid; }
    public OrbitPrimitive getPrimitive() { return primitive; }
    public boolean isEntering() { return entering; }
    public long getServerTick() { return serverTick; }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (aircraftUuid == null || primitive == null || serverTick < 0L) {
            throw new IllegalStateException("Invalid orbit status");
        }
        buffer.writeLong(aircraftUuid.getMostSignificantBits());
        buffer.writeLong(aircraftUuid.getLeastSignificantBits());
        buffer.writeByte(primitive.ordinal());
        buffer.writeBoolean(entering);
        buffer.writeLong(serverTick);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        aircraftUuid = new UUID(buffer.readLong(), buffer.readLong());
        int ordinal = buffer.readUnsignedByte();
        if (ordinal >= OrbitPrimitive.values().length) {
            throw new IllegalArgumentException("Invalid orbit primitive");
        }
        primitive = OrbitPrimitive.values()[ordinal];
        entering = buffer.readBoolean();
        serverTick = buffer.readLong();
        if (serverTick < 0L) throw new IllegalArgumentException("Invalid server tick");
    }
}
