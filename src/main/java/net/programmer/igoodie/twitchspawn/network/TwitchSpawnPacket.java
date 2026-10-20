package net.programmer.igoodie.twitchspawn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public interface TwitchSpawnPacket {

    ResourceLocation getId();

    void encode(FriendlyByteBuf buffer);

}
