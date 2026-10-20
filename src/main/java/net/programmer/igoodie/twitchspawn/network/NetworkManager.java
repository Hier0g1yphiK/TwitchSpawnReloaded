package net.programmer.igoodie.twitchspawn.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.programmer.igoodie.twitchspawn.TwitchSpawn;

public class NetworkManager {

    public static final ResourceLocation STATUS_CHANGED = new ResourceLocation(TwitchSpawn.MOD_ID, "status_changed");
    public static final ResourceLocation OS_RUN = new ResourceLocation(TwitchSpawn.MOD_ID, "os_run");
    public static final ResourceLocation GLOBAL_CHAT_COOLDOWN = new ResourceLocation(TwitchSpawn.MOD_ID, "global_chat_cooldown");

    public static void sendTo(ServerPlayer player, TwitchSpawnPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buffer);
        ServerPlayNetworking.send(player, packet.getId(), buffer);
    }

}
