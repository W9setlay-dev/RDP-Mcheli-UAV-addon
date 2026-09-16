package net.vas.rdp.uav.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class ContactSelection implements IMessage {
    public boolean next;
    public boolean clear;
    public ContactSelection() { }
    public ContactSelection(boolean next, boolean clear) {
        this.next = next; this.clear = clear;
    }
    public void fromBytes(ByteBuf buffer) {
        next = buffer.readBoolean(); clear = buffer.readBoolean();
    }
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(next); buffer.writeBoolean(clear);
    }
}
