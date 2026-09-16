package net.vas.rdp.uav.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.vas.rdp.uav.RDPUavMod;
import net.vas.rdp.uav.mission.UavControlAccess;
import com.norwood.mcheli.aircraft.MCH_EntityAircraft;
import java.util.Objects;

public final class ServerContactSelectionHandler
        implements IMessageHandler<ContactSelection, IMessage> {
    public IMessage onMessage(final ContactSelection message, MessageContext context) {
        final EntityPlayerMP player = context.getServerHandler().player;
        Objects.requireNonNull(player.getServer()).addScheduledTask(new Runnable() {
            public void run() {
                MCH_EntityAircraft aircraft = UavControlAccess.findControlledAircraft(player);
                if (!UavControlAccess.isOperator(aircraft, player)) return;
                if (message.clear) RDPUavMod.clearContact(player.world, aircraft);
                else RDPUavMod.cycleContact(player.world, aircraft, player, message.next);
            }
        });
        return null;
    }
}
