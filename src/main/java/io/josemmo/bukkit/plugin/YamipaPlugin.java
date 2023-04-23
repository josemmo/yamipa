package io.josemmo.bukkit.plugin;

import io.josemmo.bukkit.plugin.commands.ImageCommandBridge;
import io.josemmo.bukkit.plugin.renderer.FakeEntity;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.renderer.ItemService;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;	
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.logging.Level;
import java.lang.StringBuilder;

public class YamipaPlugin extends JavaPlugin {
    public static final int BSTATS_PLUGIN_ID = 10243;
    private static YamipaPlugin instance;
    private boolean verbose;
    private int maxWidth;
    private int maxHeight;
    private ImageStorage storage;
    private ImageRenderer renderer;
    private ItemService itemService;
    private ScheduledExecutorService scheduler;
    private HashSet<String> disabledPlayers = new HashSet<String>();

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

    public int getMaxWidth() {
	return maxWidth;
    }

    public int getMaxHeight() {
	    return maxHeight;
    }


    public void setPlayer(Player p, boolean display) {
	    if (!display) {
		    disabledPlayers.add(p.getName());
	    } else {
		    disabledPlayers.remove(p.getName());
	    }
    }


    public boolean playerDisabled(Player p) {
	    return disabledPlayers.contains(p.getName());
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
            basePath.resolve(imagesPath).toString(),
            basePath.resolve(cachePath).toString()
        );
        try {
            storage.start();
        } catch (Exception e) {
            log(Level.SEVERE, "Failed to initialize image storage", e);
        }

	maxWidth = getConfig().getInt("max-width", 5);
	maxHeight = getConfig().getInt("max-height", 5);

	// read in disabled players
	Path playersPath = basePath.resolve(getConfig().getString("players-path", "players.dat"));
	try {
		List<String> data = Files.readAllLines(playersPath);
		disabledPlayers.addAll(data);
	} catch (Exception e) {
		info("error reading players.dat");
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

        // Warm-up ProtocolLib
        fine("Waiting for ProtocolLib to be ready...");
        scheduler.execute(() -> {
            FakeEntity.waitForProtocolLib();
            fine("ProtocolLib is now ready");
        });

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
        Path basePath = getDataFolder().toPath();
	Path playersPath = basePath.resolve(getConfig().getString("players-path", "players.dat"));
	StringBuilder data = new StringBuilder();
	for (String s : disabledPlayers) {
		data.append(s + '\n');
	}

	try {
	Files.write(playersPath, data.toString().getBytes());
	} catch (Exception e) {
		info("error writing players.dat");
	}

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
     * @param e       Throwable instance, NULL to ignore
     */
    public void log(@NotNull Level level, @NotNull String message, @Nullable Throwable e) {
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
     * Log message
     * @param level   Record level
     * @param message Message
     */
    public void log(@NotNull Level level, @NotNull String message) {
        log(level, message, null);
    }

    /**
     * Log warning message
     * @param message Message
     */
    public void warning(@NotNull String message) {
        log(Level.WARNING, message);
    }

    /**
     * Log info message
     * @param message Message
     */
    public void info(@NotNull String message) {
        log(Level.INFO, message);
    }

    /**
     * Log fine message
     * @param message Message
     */
    public void fine(@NotNull String message) {
        log(Level.FINE, message);
    }
}
