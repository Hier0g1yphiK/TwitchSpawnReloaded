package net.programmer.igoodie.twitchspawn.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.programmer.igoodie.twitchspawn.TwitchSpawn;
import net.programmer.igoodie.twitchspawn.TwitchSpawnLoadingErrors;
import net.programmer.igoodie.twitchspawn.client.gui.GlobalChatCooldownOverlay;
import net.programmer.igoodie.twitchspawn.client.gui.StatusIndicatorOverlay;
import net.programmer.igoodie.twitchspawn.network.NetworkManager;
import net.programmer.igoodie.twitchspawn.network.packet.GlobalChatCooldownPacket;
import net.programmer.igoodie.twitchspawn.network.packet.OsRunPacket;
import net.programmer.igoodie.twitchspawn.network.packet.StatusChangedPacket;
import net.programmer.igoodie.twitchspawn.udl.NotepadUDLUpdater;

import java.util.function.Consumer;
import java.util.function.Function;

public class TwitchSpawnClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NotepadUDLUpdater.attemptUpdate();

        registerReceiver(NetworkManager.STATUS_CHANGED, StatusChangedPacket::decode, StatusChangedPacket::handle);
        registerReceiver(NetworkManager.OS_RUN, OsRunPacket::decode, OsRunPacket::handle);
        registerReceiver(NetworkManager.GLOBAL_CHAT_COOLDOWN, GlobalChatCooldownPacket::decode, GlobalChatCooldownPacket::handle);

        HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> {
            if (Minecraft.getInstance().options.hideGui) return;
            StatusIndicatorOverlay.render(matrixStack);
            GlobalChatCooldownOverlay.render(matrixStack);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            TwitchSpawnLoadingErrors errors = TwitchSpawn.LOADING_ERRORS;
            if (errors == null) return;
            errors.toWarningMessages().forEach(client.gui.getChat()::addMessage);
            TwitchSpawn.LOADING_ERRORS = null;
        }));
    }

    private static <T> void registerReceiver(ResourceLocation id,
                                             Function<FriendlyByteBuf, T> decoder,
                                             Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(id, (client, listener, buffer, responseSender) -> {
            T packet = decoder.apply(buffer);
            client.execute(() -> handler.accept(packet));
        });
    }

}
