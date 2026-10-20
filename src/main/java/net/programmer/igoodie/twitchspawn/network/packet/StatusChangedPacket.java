package net.programmer.igoodie.twitchspawn.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.programmer.igoodie.twitchspawn.client.gui.StatusIndicatorOverlay;
import net.programmer.igoodie.twitchspawn.network.NetworkManager;
import net.programmer.igoodie.twitchspawn.network.TwitchSpawnPacket;

public class StatusChangedPacket implements TwitchSpawnPacket {

    @Override
    public ResourceLocation getId() {
        return NetworkManager.STATUS_CHANGED;
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(status);
    }

    public static StatusChangedPacket decode(FriendlyByteBuf buffer) {
        return new StatusChangedPacket(buffer.readBoolean());
    }

    public static void handle(final StatusChangedPacket packet) {
        StatusIndicatorOverlay.setRunning(packet.status);
    }

    /* ---------------------------- */

    private boolean status;

    public StatusChangedPacket(boolean status) {
        this.status = status;
    }

}
