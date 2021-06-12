package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.jetbrains.annotations.NotNull;

public class ActionBarPacket extends PacketContainer {
    public ActionBarPacket() {
        super(PacketType.Play.Server.TITLE);
        getTitleActions().write(0, EnumWrappers.TitleAction.ACTIONBAR);
    }

    public @NotNull ActionBarPacket setText(@NotNull String text) {
        getChatComponents().write(0, WrappedChatComponent.fromText(text));
        return this;
    }
}
