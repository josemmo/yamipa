package io.josemmo.bukkit.plugin;

import io.josemmo.bukkit.plugin.commands.ImageCommandBridge;
import io.josemmo.bukkit.plugin.renderer.*;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.Color;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.logging.Level;

public class YamipaPlugin extends JavaPlugin {
    public static final int BSTATS_PLUGIN_ID = 10243;
    private static YamipaPlugin instance;
    private boolean verbose;
    private ImageStorage storage;
    private ImageRenderer renderer;
    private ItemService itemService;
    private ScheduledExecutorService scheduler;

    /**
     * Get plugin instance
     * @return Plugin instance
     */
    public static @NotNull YamipaPlugin getInstance() {
        return instance;
    }

    /**
     * Get image storage instance
     * @return Image storage instance
     */
    public @NotNull ImageStorage getStorage() {
        return storage;
    }

    /**
     * Get image renderer instance
     * @return Image renderer instance
     */
    public @NotNull ImageRenderer getRenderer() {
        return renderer;
    }

    /**
     * Get internal tasks scheduler
     * @return Tasks scheduler
     */
    public @NotNull ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        // Initialize logger
        verbose = getConfig().getBoolean("verbose", false);
        if (verbose) {
            info("Running on VERBOSE mode");
        }

        // Register plugin commands
        ImageCommandBridge.register(this);

        // Read plugin configuration paths
        Path basePath = getDataFolder().toPath();
        String imagesPath = getConfig().getString("images-path", "images");
        String cachePath = getConfig().getString("cache-path", "cache");
        String dataPath = getConfig().getString("data-path", "images.dat");

        // Create image storage
        storage = new ImageStorage(
            basePath.resolve(imagesPath).toAbsolutePath(),
            basePath.resolve(cachePath).toAbsolutePath()
        );
        try {
            storage.start();
        } catch (Exception e) {
            log(Level.SEVERE, "Failed to initialize image storage", e);
        }

        // Create image renderer
        boolean animateImages = getConfig().getBoolean("animate-images", true);
        FakeImage.configure(animateImages);
        info(animateImages ? "Enabled image animation support" : "Image animation support is disabled");
        renderer = new ImageRenderer(basePath.resolve(dataPath).toString());
        renderer.start();

        // Create image item service
        itemService = new ItemService();
        itemService.start();

        // Create thread pool
        scheduler = Executors.newScheduledThreadPool(6);

        // Warm-up plugin dependencies
        fine("Waiting for ProtocolLib to be ready...");
        scheduler.execute(() -> {
            FakeEntity.waitForProtocolLib();
            fine("ProtocolLib is now ready");
        });
        fine("Triggered map color cache warm-up");
        FakeMap.pixelToIndex(Color.RED.getRGB()); // Ask for a color index to force cache generation

        // Initialize bStats
        Function<Integer, String> toStats = number -> {
            if (number >= 1000) return "1000+";
            if (number >= 500) return "500-999";
            if (number >= 100) return "100-499";
            if (number >= 50) return "50-99";
            if (number >= 10) return "10-49";
            return "0-9";
        };
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("animate_images", () -> FakeImage.isAnimationEnabled() ? "true" : "false"));
        metrics.addCustomChart(new SimplePie("number_of_image_files", () -> toStats.apply(storage.size())));
        metrics.addCustomChart(new SimplePie("number_of_placed_images", () -> toStats.apply(renderer.size())));
    }

    @Override
    public void onDisable() {
        // Stop plugin components
        storage.stop();
        renderer.stop();
        itemService.stop();
        storage = null;
        renderer = null;
        itemService = null;

        // Stop internal scheduler
        scheduler.shutdownNow();
        scheduler = null;

        // Remove Bukkit listeners and tasks
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
    }

    /**
     * Log message
     * @param level   Record level
     * @param message Message
     * @param e       Optional throwable to log
     */
    private void log(@NotNull Level level, @NotNull String message, @Nullable Throwable e) {
        // Fix log level
        if (level.intValue() < Level.INFO.intValue()) {
            if (!verbose) return;
            level = Level.INFO;
        }

        // Proxy record to real logger
        if (e == null) {
            getLogger().log(level, message);
        } else {
            getLogger().log(level, message, e);
        }
    }

    /**
     * Log severe message
     * @param message Message
     * @param e       Throwable to log
     */
    public void severe(@NotNull String message, @NotNull Throwable e) {
        log(Level.SEVERE, message, e);
    }

    /**
     * Log warning message
     * @param message Message
     * @param e       Throwable to log
     */
    public void warning(@NotNull String message, @NotNull Throwable e) {
        log(Level.WARNING, message, e);
    }

    /**
     * Log warning message
     * @param message Message
     */
    public void warning(@NotNull String message) {
        log(Level.WARNING, message, null);
    }

    /**
     * Log info message
     * @param message Message
     */
    public void info(@NotNull String message) {
        log(Level.INFO, message, null);
    }

    /**
     * Log fine message
     * @param message Message
     */
    public void fine(@NotNull String message) {
        log(Level.FINE, message, null);
    }
}
