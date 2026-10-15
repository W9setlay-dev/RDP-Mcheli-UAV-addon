package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/** Server-to-client notice that a predicted separation conflict remains unresolved. */
public final class PacketDeconflictionAlert implements IMessage {
    private UUID aircraftA;
    private UUID aircraftB;
    private String reason = "";
    private int resolutionAttempts;

    public PacketDeconflictionAlert() { }

    /** Creates a bounded alert for presentation; it does not command either aircraft. */
    public PacketDeconflictionAlert(UUID aircraftA, UUID aircraftB,
                                    String reason, int resolutionAttempts) {
        if (aircraftA == null || aircraftB == null || aircraftA.equals(aircraftB)
                || reason == null || reason.length() > 256 || resolutionAttempts < 0) {
            throw new IllegalArgumentException("Invalid deconfliction alert");
        }
        this.aircraftA = aircraftA;
        this.aircraftB = aircraftB;
        this.reason = reason;
        this.resolutionAttempts = resolutionAttempts;
    }

    public UUID getAircraftA() { return aircraftA; }
    public UUID getAircraftB() { return aircraftB; }
    public String getReason() { return reason; }
    public int getResolutionAttempts() { return resolutionAttempts; }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (aircraftA == null || aircraftB == null || reason == null
                || reason.length() > 256 || resolutionAttempts < 0) {
            throw new IllegalStateException("Invalid deconfliction alert");
        }
        writeUuid(buffer, aircraftA);
        writeUuid(buffer, aircraftB);
        ByteBufUtils.writeUTF8String(buffer, reason);
        buffer.writeInt(resolutionAttempts);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        aircraftA = readUuid(buffer);
        aircraftB = readUuid(buffer);
        reason = ByteBufUtils.readUTF8String(buffer);
        resolutionAttempts = buffer.readInt();
        if (aircraftA.equals(aircraftB) || reason.length() > 256 || resolutionAttempts < 0) {
            throw new IllegalArgumentException("Invalid deconfliction alert");
        }
    }

    private static void writeUuid(ByteBuf buffer, UUID uuid) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }
}
