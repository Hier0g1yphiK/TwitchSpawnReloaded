package net.programmer.igoodie.twitchspawn;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.programmer.igoodie.twitchspawn.command.RulesetNameArgumentType;
import net.programmer.igoodie.twitchspawn.command.StreamerArgumentType;
import net.programmer.igoodie.twitchspawn.command.TwitchSpawnCommand;
import net.programmer.igoodie.twitchspawn.command.serializer.RulesetNameArgumentSerializer;
import net.programmer.igoodie.twitchspawn.command.serializer.StreamerArgumentSerializer;
import net.programmer.igoodie.twitchspawn.configuration.ConfigManager;
import net.programmer.igoodie.twitchspawn.configuration.PreferencesConfig;
import net.programmer.igoodie.twitchspawn.network.NetworkManager;
import net.programmer.igoodie.twitchspawn.network.packet.StatusChangedPacket;
import net.programmer.igoodie.twitchspawn.tracer.TraceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TwitchSpawn implements ModInitializer {

    public static final String MOD_ID = "twitchspawn";
    public static final Logger LOGGER = LogManager.getLogger(TwitchSpawn.class);

    public static MinecraftServer SERVER;
    public static TraceManager TRACE_MANAGER;

    // Errors from the initial config load, shown to the player once they join a world
    public static TwitchSpawnLoadingErrors LOADING_ERRORS;

    @Override
    public void onInitialize() {
        try {
            ConfigManager.loadConfigs();

        } catch (TwitchSpawnLoadingErrors e) {
            e.logErrors();
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
                throw new RuntimeException("TwitchSpawn loading errors occurred");
            }
            LOADING_ERRORS = e;
        }

        ArgumentTypes.register("twitchspawn:streamer", StreamerArgumentType.class,
                new StreamerArgumentSerializer());
        ArgumentTypes.register("twitchspawn:ruleset", RulesetNameArgumentType.class,
                new RulesetNameArgumentSerializer());

        registerSound("pop_in");
        registerSound("pop_out");

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> TwitchSpawnCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerAboutToStart);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerLoggedIn(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerLoggedOut(handler.player));
    }

    private void registerSound(String name) {
        ResourceLocation id = new ResourceLocation(TwitchSpawn.MOD_ID, name);
        Registry.register(Registry.SOUND_EVENT, id, new SoundEvent(id));
    }

    private void onServerAboutToStart(MinecraftServer server) {
        SERVER = server;
        TRACE_MANAGER = new TraceManager();
    }

    private void onServerStarted(MinecraftServer server) {
        if (ConfigManager.PREFERENCES.autoStart == PreferencesConfig.AutoStartEnum.ENABLED) {
            LOGGER.info("Auto-start is enabled. Attempting to start tracers.");
            TRACE_MANAGER.start();
        }
    }

    private void onServerStopping(MinecraftServer server) {
        SERVER = null;

        if (TRACE_MANAGER.isRunning())
            TRACE_MANAGER.stop(null, "Server stopping");

        ConfigManager.RULESET_COLLECTION.clearQueue();
    }

    private void onPlayerLoggedIn(ServerPlayer entity) {
        String translationKey = TRACE_MANAGER.isRunning() ?
                "commands.twitchspawn.status.on" : "commands.twitchspawn.status.off";

        entity.sendMessage(new TranslatableComponent(translationKey), entity.getUUID());

        if (TRACE_MANAGER.isRunning())
            TRACE_MANAGER.connectStreamer(entity.getName().getString());

        NetworkManager.sendTo(entity, new StatusChangedPacket(TRACE_MANAGER.isRunning()));
    }

    private void onPlayerLoggedOut(ServerPlayer entity) {
        if (TRACE_MANAGER.isRunning())
            TRACE_MANAGER.disconnectStreamer(entity.getName().getString());
    }

}
