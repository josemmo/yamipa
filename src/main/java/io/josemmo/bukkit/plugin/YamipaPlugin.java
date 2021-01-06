package io.josemmo.bukkit.plugin;

import dev.jorel.commandapi.CommandAPI;
import io.josemmo.bukkit.plugin.commands.ImageCommand;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public class YamipaPlugin extends JavaPlugin {
    private static YamipaPlugin instance;
    private ImageStorage storage;

    /**
     * Get plugin instance
     * @return Plugin instance
     */
    public static YamipaPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        CommandAPI.registerCommand(ImageCommand.class);
    }

    @Override
    public void onEnable() {
        storage = new ImageStorage(getDataFolder().getPath() + "/images");
        try {
            storage.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize image storage", e);
        }
    }

    @Override
    public void onDisable() {
        storage.stop();
        storage = null;
    }
}
