package net.programmer.igoodie.twitchspawn.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.programmer.igoodie.twitchspawn.network.NetworkManager;
import net.programmer.igoodie.twitchspawn.network.TwitchSpawnPacket;
import net.programmer.igoodie.twitchspawn.tslanguage.action.OsRunAction;

public class OsRunPacket implements TwitchSpawnPacket {

    @Override
    public ResourceLocation getId() {
        return NetworkManager.OS_RUN;
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(shell.ordinal());
        buffer.writeUtf(script);
    }

    public static OsRunPacket decode(FriendlyByteBuf buffer) {
        OsRunAction.Shell shell = OsRunAction.Shell.values()[buffer.readInt()];
        String script = buffer.readUtf();

        return new OsRunPacket(shell, script);
    }

    public static void handle(final OsRunPacket packet) {
        OsRunAction.handleLocalScript(packet.shell, packet.script);
    }

    /* ------------------------------------------------ */

    private OsRunAction.Shell shell;
    private String script;

    public OsRunPacket(OsRunAction.Shell shell, String script) {
        this.shell = shell;
        this.script = script;
    }

}
