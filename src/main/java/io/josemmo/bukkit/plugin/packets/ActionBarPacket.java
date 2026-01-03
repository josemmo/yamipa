package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.jetbrains.annotations.NotNull;

public class ActionBarPacket extends PacketContainer {
    private static final boolean USE_TITLE = (Internals.MINECRAFT_VERSION < 1700);

    @SuppressWarnings("deprecation")
    public ActionBarPacket() {
        super(USE_TITLE ? PacketType.Play.Server.TITLE : PacketType.Play.Server.SET_ACTION_BAR_TEXT);
        if (USE_TITLE) {
            getTitleActions().write(0, EnumWrappers.TitleAction.ACTIONBAR);
        }
    }

    public @NotNull ActionBarPacket setText(@NotNull String text) {
        getChatComponents().write(0, WrappedChatComponent.fromText(text));
        return this;
    }
}
