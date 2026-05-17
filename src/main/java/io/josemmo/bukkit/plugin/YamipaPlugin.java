package io.josemmo.bukkit.plugin;

import io.josemmo.bukkit.plugin.commands.ImageCommandBridge;
import io.josemmo.bukkit.plugin.renderer.*;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import io.josemmo.bukkit.plugin.utils.Logger;
import io.josemmo.bukkit.plugin.utils.MinecraftVersion;
import io.josemmo.bukkit.plugin.utils.Scheduler;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.Color;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class YamipaPlugin extends JavaPlugin {
    public static final int BSTATS_PLUGIN_ID = 10243;
    private static final Logger LOGGER = Logger.getLogger();
    private static @Nullable YamipaPlugin INSTANCE;
    private boolean verbose;
    private @Nullable Scheduler scheduler;
    private @Nullable ImageStorage storage;
    private @Nullable ImageRenderer renderer;
    private @Nullable ItemService itemService;
    private @Nullable Metrics metrics;

    /**
     * Get plugin instance
     * @return Plugin instance
     */
    public static @NotNull YamipaPlugin getInstance() {
        Objects.requireNonNull(INSTANCE, "Cannot get plugin instance if plugin is not running");
        return INSTANCE;
    }

    /**
     * Get tasks scheduler
     * @return Tasks scheduler
     */
    public @NotNull Scheduler getScheduler() {
        Objects.requireNonNull(scheduler, "Cannot get scheduler instance if plugin is not running");
        return scheduler;
    }

    /**
     * Get image storage instance
     * @return Image storage instance
     */
    public @NotNull ImageStorage getStorage() {
        Objects.requireNonNull(storage, "Cannot get storage instance if plugin is not running");
        return storage;
    }

    /**
     * Get image renderer instance
     * @return Image renderer instance
     */
    public @NotNull ImageRenderer getRenderer() {
        Objects.requireNonNull(renderer, "Cannot get renderer instance if plugin is not running");
        return renderer;
    }

    /**
     * Is verbose
     * @return Whether plugin is running in verbose mode
     */
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void onLoad() {
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        // Initialize configuration
        FileConfiguration config = getConfig();
        config.addDefault("command-name", "yamipa");
        config.addDefault("command-aliases", Arrays.asList("image", "images"));
        config.addDefault("verbose", false);
        config.addDefault("animate-images", true);
        config.addDefault("images-path", "images");
        config.addDefault("cache-path", "cache");
        config.addDefault("data-path", "images.dat");
        config.addDefault("allowed-paths", null);
        config.addDefault("max-image-dimension", 30);
        config.options().copyDefaults(true);
        saveConfig();

        // Initialize logger
        verbose = config.getBoolean("verbose");
        if (verbose) {
            LOGGER.fine("Running on VERBOSE mode");
            LOGGER.fine("Detected server with Minecraft " + MinecraftVersion.CURRENT);
        }

        // Create scheduler
        scheduler = new Scheduler();

        // Register plugin commands
        String commandName = Objects.requireNonNull(config.getString("command-name"));
        List<String> commandAliases = config.getStringList("command-aliases");
        ImageCommandBridge.register(commandName, commandAliases);

        // Read plugin configuration paths
        Path basePath = getDataFolder().toPath();
        String imagesPath = Objects.requireNonNull(config.getString("images-path"));
        String cachePath = Objects.requireNonNull(config.getString("cache-path"));
        String dataPath = Objects.requireNonNull(config.getString("data-path"));

        // Create image storage
        String allowedPaths = config.getString("allowed-paths", "");
        storage = new ImageStorage(
            basePath.resolve(imagesPath).toAbsolutePath().normalize(),
            basePath.resolve(cachePath).toAbsolutePath().normalize(),
            allowedPaths
        );
        try {
            storage.start();
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize image storage", e);
        }

        // Create image renderer
        boolean animateImages = config.getBoolean("animate-images");
        LOGGER.info(animateImages ? "Enabled image animation support" : "Image animation support is disabled");
        int maxImageDimension = config.getInt("max-image-dimension");
        renderer = new ImageRenderer(basePath.resolve(dataPath), animateImages, maxImageDimension);
        renderer.start();

        // Create image item service
        itemService = new ItemService();
        itemService.start();

        // Warm-up plugin dependencies
        LOGGER.fine("Triggered map color cache warm-up");
        FakeMap.pixelToIndex(Color.RED.getRGB()); // Ask for a color index to force cache generation
        FakeEntity.initialize();

        // Initialize bStats
        Function<Integer, String> toStats = number -> {
            if (number >= 1000) return "1000+";
            if (number >= 500) return "500-999";
            if (number >= 100) return "100-499";
            if (number >= 50) return "50-99";
            if (number >= 10) return "10-49";
            return "0-9";
        };
        metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("animate_images", () -> Boolean.toString(animateImages)));
        metrics.addCustomChart(new SimplePie("number_of_image_files", () -> toStats.apply(storage.size())));
        metrics.addCustomChart(new SimplePie("number_of_placed_images", () -> toStats.apply(renderer.size())));
    }

    @Override
    public void onDisable() {
        // Stop metrics
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }

        // Stop item service
        if (itemService != null) {
            itemService.stop();
            itemService = null;
        }

        // Stop image renderer
        if (renderer != null) {
            renderer.stop();
            renderer = null;
        }

        // Stop image storage
        if (storage != null) {
            storage.stop();
            storage = null;
        }

        // Stop scheduler
        if (scheduler != null) {
            scheduler.stop();
            scheduler = null;
        }

        // Remove Bukkit listeners
        HandlerList.unregisterAll(this);

        // Unlink reference to instance
        INSTANCE = null;
    }
}
