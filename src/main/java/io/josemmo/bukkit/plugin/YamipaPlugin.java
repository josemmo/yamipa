package io.josemmo.bukkit.plugin;

import dev.jorel.commandapi.CommandAPI;
import io.josemmo.bukkit.plugin.commands.ImageCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class YamipaPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        CommandAPI.registerCommand(ImageCommand.class);
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabled YamipaPlugin");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled YamipaPlugin");
    }
}
