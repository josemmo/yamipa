package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import io.josemmo.bukkit.plugin.utils.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

public class ActionBarPacket extends PacketContainer {
    private static final boolean USE_ACTIONBAR = MinecraftVersion.CURRENT.isAtLeast(MinecraftVersion.V1_17);

    @SuppressWarnings("deprecation")
    public ActionBarPacket() {
        super(USE_ACTIONBAR ? PacketType.Play.Server.SET_ACTION_BAR_TEXT : PacketType.Play.Server.TITLE);
        if (!USE_ACTIONBAR) {
            getTitleActions().write(0, EnumWrappers.TitleAction.ACTIONBAR);
        }
    }

    public @NotNull ActionBarPacket setText(@NotNull String text) {
        getChatComponents().write(0, WrappedChatComponent.fromText(text));
        return this;
    }
}
