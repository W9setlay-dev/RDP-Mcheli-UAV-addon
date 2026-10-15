package net.vas.rdp.uav.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.vas.rdp.uav.network.PacketDeconflictionAlert;
import net.vas.rdp.uav.network.PacketFormationSync;
import net.vas.rdp.uav.network.PacketOrbitStatus;

/** Client-thread handlers for server-owned formation and path status. */
public final class FormationStatusClientHandler {
    private FormationStatusClientHandler() { }

    public static final class Formation
            implements IMessageHandler<PacketFormationSync, IMessage> {
        @Override
        public IMessage onMessage(final PacketFormationSync message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    FormationStatusStore.replaceFormation(message.getSlots());
                }
            });
            return null;
        }
    }

    public static final class Alert
            implements IMessageHandler<PacketDeconflictionAlert, IMessage> {
        @Override
        public IMessage onMessage(final PacketDeconflictionAlert message,
                                  MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    FormationStatusStore.setLastAlert(message);
                }
            });
            return null;
        }
    }

    public static final class Orbit
            implements IMessageHandler<PacketOrbitStatus, IMessage> {
        @Override
        public IMessage onMessage(final PacketOrbitStatus message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    FormationStatusStore.setLastOrbitStatus(message);
                }
            });
            return null;
        }
    }
}
