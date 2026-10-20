package net.programmer.igoodie.twitchspawn.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.programmer.igoodie.twitchspawn.client.gui.GlobalChatCooldownOverlay;
import net.programmer.igoodie.twitchspawn.network.NetworkManager;
import net.programmer.igoodie.twitchspawn.network.TwitchSpawnPacket;

public class GlobalChatCooldownPacket implements TwitchSpawnPacket {

    @Override
    public ResourceLocation getId() {
        return NetworkManager.GLOBAL_CHAT_COOLDOWN;
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(timestamp);
    }

    public static GlobalChatCooldownPacket decode(FriendlyByteBuf buffer) {
        return new GlobalChatCooldownPacket(buffer.readLong());
    }

    public static void handle(final GlobalChatCooldownPacket packet) {
        GlobalChatCooldownOverlay.setCooldownTimestamp(packet.timestamp);
    }

    /* -------------------------------- */

    private long timestamp;

    public GlobalChatCooldownPacket(long timestamp) {
        this.timestamp = timestamp;
    }

}
