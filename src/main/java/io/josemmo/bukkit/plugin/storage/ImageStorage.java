package io.josemmo.bukkit.plugin.storage;

import io.josemmo.bukkit.plugin.utils.Logger;
import io.josemmo.bukkit.plugin.utils.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service for keeping track of image images.
 * <p>
 * All files are indexed based on their <b>filename</b>.
 * Due to recursion, filenames can contain forward slashes (<i>i.e.,</i> "/") and act as relative paths to the base
 * directory.
 */
public class ImageStorage extends FileSystemWatcher {
    private static final Logger LOGGER = Logger.getLogger("ImageStorage");
    /** Map of registered files indexed by filename */
    private final SortedMap<String, ImageFile> files = new TreeMap<>();
    private final Path cachePath;
    private final String allowedPaths;

    /**
     * Class constructor
     * @param basePath     Path to directory containing the images
     * @param cachePath    Path to directory containing the cached image maps
     * @param allowedPaths Allowed paths pattern
     */
    public ImageStorage(@NotNull Path basePath, @NotNull Path cachePath, @NotNull String allowedPaths) {
        super(basePath);
        this.cachePath = cachePath;
        this.allowedPaths = allowedPaths;
    }

    /**
     * Get base path
     * @return Base path
     */
    public @NotNull Path getBasePath() {
        return basePath;
    }

    /**
     * Get cache path
     * @return Cache path
     */
    public @NotNull Path getCachePath() {
        return cachePath;
    }

    /**
     * Start service
     * @throws RuntimeException if failed to start watch service
     */
    @Override
    public void start() throws RuntimeException {
        // Create base directories if necessary
        if (basePath.toFile().mkdirs()) {
            LOGGER.info("Created images directory as it did not exist");
        }
        if (cachePath.toFile().mkdirs()) {
            LOGGER.info("Created cache directory as it did not exist");
        }

        // Start file system watcher
        super.start();
        LOGGER.fine("Found " + files.size() + " file(s) in images directory");
    }

    /**
     * Stop service
     */
    @Override
    public void stop() {
        super.stop();
    }

    /**
     * Get number of images
     * @return Number of images
     */
    public synchronized int size() {
        return files.size();
    }

    /**
     * Get image filenames
     * @param  sender Sender instance to filter only allowed images
     * @return        Allowed images
     */
    public synchronized @NotNull List<String> getFilenames(@NotNull CommandSender sender) {
        List<String> response = new ArrayList<>();
        for (String filename : files.keySet()) {
            if (isPathAllowed(filename, sender)) {
                response.add(filename);
            }
        }
        return response;
    }

    /**
     * Is path allowed
     * @param  path   Path instance
     * @param  sender Sender instance
     * @return        Whether sender is allowed to access path
     */
    public boolean isPathAllowed(@NotNull Path path, @NotNull CommandSender sender) {
        return isPathAllowed(pathToFilename(path), sender);
    }

    /**
     * Is path allowed
     * @param  path   Path relative to {@link ImageStorage#basePath}
     * @param  sender Sender instance
     * @return        Whether sender is allowed to access path
     */
    public boolean isPathAllowed(@NotNull String path, @NotNull CommandSender sender) {
        // Find allowed paths pattern
        String rawPattern = null;
        if (sender instanceof Player) {
            rawPattern = Permissions.getVariable("yamipa-allowed-paths", (Player) sender);
        }
        if (rawPattern == null) {
            rawPattern = allowedPaths;
        }
        if (rawPattern.isEmpty()) {
            return true;
        }

        // Replace special tokens in pattern
        if (sender instanceof Player) {
            Player player = (Player) sender;
            rawPattern = rawPattern.replaceAll("#player#", Matcher.quoteReplacement(Pattern.quote(player.getName())));
            rawPattern = rawPattern.replaceAll("#uuid#", player.getUniqueId().toString());
        } else {
            rawPattern = rawPattern.replaceAll("#player#", ".+");
            rawPattern = rawPattern.replaceAll("#uuid#", ".+");
        }

        // Perform partial match against pattern
        try {
            return Pattern.compile(rawPattern).matcher(path).find();
        } catch (PatternSyntaxException __) {
            LOGGER.warning("Invalid allowed paths pattern: " + rawPattern);
            return false;
        }
    }

    /**
     * Get image by filename
     * @param  filename Filename
     * @return          Image instance or NULL if not found
     */
    public synchronized @Nullable ImageFile get(@NotNull String filename) {
        return files.get(filename);
    }

    /**
     * Convert path to filename
     * @param  path File path
     * @return      Relative path used for indexing
     */
    private @NotNull String pathToFilename(@NotNull Path path) {
        return basePath.relativize(path).toString().replaceAll("\\\\", "/");
    }

    /**
     * On file created
     * @param path File path
     */
    protected synchronized void onFileCreated(@NotNull Path path) {
        String filename = pathToFilename(path);
        ImageFile imageFile = new ImageFile(filename, path);
        if (files.putIfAbsent(filename, imageFile) == null) {
            LOGGER.fine("Registered file \"" + filename + "\"");
        }
    }

    /**
     * On file modified
     * @param path File path
     */
    protected synchronized void onFileModified(@NotNull Path path) {
        String filename = pathToFilename(path);
        ImageFile imageFile = files.get(filename);
        if (imageFile != null) {
            imageFile.invalidate();
            LOGGER.fine("Invalidated file \"" + filename + "\"");
        }
    }

    /**
     * On file deleted
     * @param path File path
     */
    protected synchronized void onFileDeleted(@NotNull Path path) {
        String filename = pathToFilename(path);
        ImageFile imageFile = files.remove(filename);
        if (imageFile != null) {
            imageFile.invalidate();
            LOGGER.fine("Unregistered file \"" + filename + "\"");
        }
    }
}
