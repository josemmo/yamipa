package io.josemmo.bukkit.plugin.storage;

import com.sun.nio.file.ExtendedWatchEventModifier;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/**
 * A service whose purpose is to keep track of all available image files in a given directory.
 * It supports recursive storage (<i>e.g.</i>, nested directories) and watches for file system changes in realtime.
 * <p>
 * All files are indexed based on their <b>filename</b>.
 * Due to recursion, filenames can contain forward slashes (<i>i.e.</i>, "/") and act as relative paths to the base
 * directory.
 */
public class ImageStorage {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    /** Map of registered files indexed by filename */
    private final SortedMap<String, ImageFile> files = new TreeMap<>();
    private final Path basePath;
    private final Path cachePath;
    private WatchService watchService;
    private Thread watchServiceThread;

    /**
     * Class constructor
     * @param basePath  Path to directory containing the images
     * @param cachePath Path to directory containing the cached image maps
     */
    public ImageStorage(@NotNull Path basePath, @NotNull Path cachePath) {
        this.basePath = basePath;
        this.cachePath = cachePath;
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
     * @throws IOException if failed to start watch service
     */
    public void start() throws IOException {
        // Create base directories if necessary
        if (basePath.toFile().mkdirs()) {
            plugin.info("Created images directory as it did not exist");
        }
        if (cachePath.toFile().mkdirs()) {
            plugin.info("Created cache directory as it did not exist");
        }

        // Start watching files
        watchService = FileSystems.getDefault().newWatchService();
        watchServiceThread = new WatcherThread();
        watchServiceThread.start();
        registerDirectory(basePath, true);
    }

    /**
     * Stop service
     */
    public void stop() {
        // Interrupt watch service thread
        if (watchServiceThread != null) {
            watchServiceThread.interrupt();
            watchServiceThread = null;
        }

        // Close watch service
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                plugin.log(Level.WARNING, "Failed to close watch service", e);
            }
            watchService = null;
        }
    }

    /**
     * Get number of images
     * @return Number of images
     */
    public synchronized int size() {
        return files.size();
    }

    /**
     * Get all image filenames
     * @return Sorted array of filenames
     */
    public synchronized @NotNull String[] getAllFilenames() {
        return files.keySet().toArray(new String[0]);
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
     * Register directory
     * @param path   Path to directory
     * @param isBase Whether is base directory or not
     */
    private synchronized void registerDirectory(@NotNull Path path, boolean isBase) {
        // Validate path
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            plugin.warning("Cannot list files in \"" + path.toAbsolutePath() + "\" as it is not a valid directory");
            return;
        }

        // Do initial directory listing
        for (File child : Objects.requireNonNull(path.toFile().listFiles())) {
            if (child.isDirectory()) {
                registerDirectory(child.toPath(), false);
            } else {
                registerFile(child.toPath());
            }
        }

        // Start watching for files changes
        if (!IS_WINDOWS || isBase) {
            try {
                WatchEvent.Kind<?>[] events = new WatchEvent.Kind[]{
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                };
                WatchEvent.Modifier[] modifiers = IS_WINDOWS ?
                    new WatchEvent.Modifier[]{ExtendedWatchEventModifier.FILE_TREE} :
                    new WatchEvent.Modifier[0];
                path.register(watchService, events, modifiers);
                plugin.fine("Started watching directory at \"" + path.toAbsolutePath() + "\"");
            } catch (IOException e) {
                plugin.log(Level.SEVERE, "Failed to register directory", e);
            }
        }
    }

    /**
     * Register file
     * @param path Path to file
     */
    private synchronized void registerFile(@NotNull Path path) {
        // Validate path
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            plugin.warning("Cannot register \"" + path.toAbsolutePath() + "\" as it is not a valid file");
            return;
        }

        // Add file to map
        String filename = getFilename(path);
        ImageFile imageFile = new ImageFile(filename, path);
        if (files.putIfAbsent(filename, imageFile) == null) {
            plugin.fine("Registered file \"" + filename + "\"");
        }
    }

    /**
     * Unregister directory
     * @param filename Filename to directory
     */
    private synchronized void unregisterDirectory(@NotNull String filename) {
        boolean foundFirst = false;
        Iterator<Map.Entry<String, ImageFile>> iter = files.entrySet().iterator();
        while (iter.hasNext()) {
            String entryKey = iter.next().getKey();
            if (entryKey.startsWith(filename+"/")) {
                foundFirst = true;
                iter.remove();
                plugin.fine("Unregistered file \"" + entryKey + "\"");
            } else if (foundFirst) {
                // We can break early because set is alphabetically sorted by key
                break;
            }
        }
    }

    /**
     * Unregister file
     * @param filename Filename to file
     */
    private synchronized void unregisterFile(@NotNull String filename) {
        ImageFile imageFile = files.remove(filename);
        if (imageFile != null) {
            imageFile.invalidate();
            plugin.fine("Unregistered file \"" + filename + "\"");
        }
    }

    /**
     * Invalidate file
     * @param filename Filename to file
     */
    private synchronized void invalidateFile(@NotNull String filename) {
        ImageFile imageFile = files.get(filename);
        if (imageFile != null) {
            imageFile.invalidate();
        }
    }

    /**
     * Handle watch event
     * @param path Path to file or directory
     * @param kind Event kind
     */
    private synchronized void handleWatchEvent(@NotNull Path path, WatchEvent.Kind<?> kind) {
        // Check whether file currently exists in file system (for CREATE and UPDATE events)
        // or is registered in the file list (for DELETE event)
        String filename = getFilename(path);
        boolean isFile = path.toFile().isFile() || files.containsKey(filename);

        // Handle creation event
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            if (isFile) {
                registerFile(path);
            } else {
                registerDirectory(path, false);
            }
            return;
        }

        // Handle deletion event
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            if (isFile) {
                unregisterFile(filename);
            } else {
                unregisterDirectory(filename);
            }
            return;
        }

        // Handle modification event
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY && isFile) {
            invalidateFile(filename);
        }
    }

    /**
     * Get filename from path
     * @param  path Path to file
     * @return      Relative path used for indexing
     */
    private @NotNull String getFilename(@NotNull Path path) {
        return basePath.relativize(path).toString().replaceAll("\\\\", "/");
    }

    private class WatcherThread extends Thread {
        @Override
        public void run() {
            try {
                WatchKey key;
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        Path keyPath = (Path) key.watchable();
                        Path path = keyPath.resolve((Path) event.context());
                        handleWatchEvent(path, kind);
                    }
                    key.reset();
                }
            } catch (InterruptedException __) {
                // Silently ignore exception, this is expected when service shuts down
            }
        }
    }
}
