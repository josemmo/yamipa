package io.josemmo.bukkit.plugin;

import dev.jorel.commandapi.CommandAPI;
import io.josemmo.bukkit.plugin.commands.ImageCommand;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public class YamipaPlugin extends JavaPlugin {
    private static YamipaPlugin instance;
    private ImageStorage storage;
    private ImageRenderer renderer;

    /**
     * Get plugin instance
     * @return Plugin instance
     */
    public static YamipaPlugin getInstance() {
        return instance;
    }

    /**
     * Get image storage instance
     * @return Image storage instance
     */
    public ImageStorage getStorage() {
        return storage;
    }

    /**
     * Get image renderer instance
     * @return Image renderer instance
     */
    public ImageRenderer getRenderer() {
        return renderer;
    }

    @Override
    public void onLoad() {
        instance = this;
        CommandAPI.registerCommand(ImageCommand.class);
    }

    @Override
    public void onEnable() {
        storage = new ImageStorage(getDataFolder().getPath() + "/images");
        renderer = new ImageRenderer(getDataFolder().getPath() + "/images.yml");
        try {
            storage.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize image storage", e);
        }
        renderer.start();
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        storage.stop();
        renderer.stop();
        storage = null;
        renderer = null;
    }
}
