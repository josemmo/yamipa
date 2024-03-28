package io.josemmo.bukkit.plugin.storage;

import com.sun.nio.file.ExtendedWatchEventModifier;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for detecting file events inside a given directory.
 * <p>
 * It supports recursive storage (<i>e.g.,</i> nested directories) and watches for file system changes in realtime
 * when supported by the OS.
 */
public abstract class FileSystemWatcher {
    private static final int MAX_DEPTH = 32;
    private static final int POLLING_INTERVAL = 4000;
    private static final String PROBE_FILENAME = ".inotify_test";
    private static final Logger LOGGER = Logger.getLogger("FileSystemWatcher");
    protected final Path basePath;
    /** Map of existing directories with the files they contain and their last modification timestamps */
    private final SortedMap<Path, Map<Path, Long>> fileTree = new TreeMap<>();
    private @Nullable Thread watcherThread;

    /**
     * Class constructor
     * @param basePath Base path
     */
    public FileSystemWatcher(@NotNull Path basePath) {
        this.basePath = basePath;
    }

    /**
     * Start watcher
     * @throws RuntimeException if failed to start
     */
    protected void start() throws RuntimeException {
        // Prevent initializing more than once
        if (watcherThread != null) {
            throw new RuntimeException("File system watcher is already running");
        }

        // Perform initial scan
        scan();

        // Start watching files
        watcherThread = new WatcherThread();
        watcherThread.start();
    }

    /**
     * Stop watcher
     */
    protected void stop() {
        if (watcherThread != null) {
            watcherThread.interrupt();
            watcherThread = null;
        }
    }

    /**
     * On file created
     * @param path File path
     */
    protected abstract void onFileCreated(@NotNull Path path);

    /**
     * On file modified
     * @param path File path
     */
    protected abstract void onFileModified(@NotNull Path path);

    /**
     * On file deleted
     * @param path File path
     */
    protected abstract void onFileDeleted(@NotNull Path path);

    /**
     * Scan base directory recursively
     */
    private void scan() {
        synchronized (fileTree) {
            // Assume all directories and files have been deleted, will discard existing items afterward
            Set<Path> deletedDirectories = new HashSet<>(fileTree.keySet());
            Set<Path> deletedFiles = fileTree.values().stream()
                .map(Map::keySet)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

            // Traverse file tree
            Set<FileVisitOption> options = EnumSet.noneOf(FileVisitOption.class);
            try {
                Files.walkFileTree(basePath, options, MAX_DEPTH, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                        fileTree.putIfAbsent(path, new HashMap<>());
                        deletedDirectories.remove(path);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        Map<Path, Long> subtree = fileTree.get(path.getParent());
                        Long oldModifiedAt = subtree.get(path);
                        long newModifiedAt = attrs.lastModifiedTime().toMillis();
                        if (oldModifiedAt == null) {
                            subtree.put(path, newModifiedAt);
                            onFileCreated(path);
                        } else if (newModifiedAt > oldModifiedAt) {
                            subtree.put(path, newModifiedAt);
                            onFileModified(path);
                        }
                        deletedFiles.remove(path);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                LOGGER.severe("Failed to list files in directory", e);
            }

            // Process deleted files and directories
            for (Path path : deletedFiles) {
                fileTree.get(path.getParent()).remove(path);
                onFileDeleted(path);
            }
            for (Path path : deletedDirectories) {
                fileTree.remove(path);
            }
        }
    }

    private class WatcherThread extends Thread {
        private final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

        @Override
        public void run() {
            if (isInotifySupported()) {
                runWithFileSystemEvents();
            } else {
                LOGGER.warning("Device does not support inotify, detection of file changes will be slower");
                runWithPolling();
            }
        }

        /**
         * Is inotify supported
         * @return Whether inotify is supported
         */
        @SuppressWarnings("ResultOfMethodCallIgnored")
        private boolean isInotifySupported() {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                // Start listening for events
                basePath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                );

                // Create and delete probe directory
                File testFile = basePath.resolve(PROBE_FILENAME).toFile();
                testFile.mkdir();
                testFile.delete();

                // Check that at least one event was emitted
                WatchKey watchKey = watchService.poll();
                return (watchKey != null && !watchKey.pollEvents().isEmpty());
            } catch (IOException __) {
                return false;
            }
        }

        /**
         * Run with polling
         */
        @SuppressWarnings({"InfiniteLoopStatement", "BusyWait"})
        private void runWithPolling() {
            try {
                while (true) {
                    Thread.sleep(POLLING_INTERVAL);
                    scan();
                }
            } catch (InterruptedException __) {
                // Silently ignore exception, this is expected when service shuts down
            }
        }

        /**
         * Run with file system events
         */
        private void runWithFileSystemEvents() {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                // Register initial directories
                synchronized (fileTree) {
                    for (Path path : fileTree.keySet()) {
                        registerDirectory(watchService, path);
                    }
                }

                // Listen for events
                WatchKey key;
                try {
                    while ((key = watchService.take()) != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            Path keyPath = (Path) key.watchable();
                            Path path = keyPath.resolve((Path) event.context());
                            handleWatchEvent(watchService, path, kind);
                        }
                        key.reset();
                    }
                } catch (ClosedWatchServiceException | InterruptedException __) {
                    // Silently ignore exception, this is expected when service shuts down
                }
            } catch (IOException e) {
                LOGGER.severe("Unexpected error at watch service", e);
            }
        }

        /**
         * Handle watch event
         * @param watchService Watch service
         * @param path         File or directory path
         * @param kind         Event kind
         */
        private void handleWatchEvent(@NotNull WatchService watchService, @NotNull Path path, @NotNull WatchEvent.Kind<?> kind) {
            synchronized (fileTree) {
                Map<Path, Long> subtree = fileTree.computeIfAbsent(path.getParent(), k -> new HashMap<>());

                // Handle deletion of files and directories
                if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    if (subtree.containsKey(path)) {
                        subtree.remove(path);
                        onFileDeleted(path);
                    } else {
                        unregisterDirectory(path);
                    }
                    return;
                }

                // Handle creation of directories
                if (path.toFile().isDirectory()) {
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        registerDirectory(watchService, path);
                    }
                    return;
                }

                // Handle creation and modification of files
                // NOTE: in Windows, some file creation events are reported as modifications
                Long oldModifiedAt = subtree.get(path);
                long newModifiedAt = path.toFile().lastModified();
                if (oldModifiedAt == null) {
                    subtree.put(path, newModifiedAt);
                    onFileCreated(path);
                } else if (newModifiedAt > oldModifiedAt) {
                    subtree.put(path, newModifiedAt);
                    onFileModified(path);
                }
            }
        }

        /**
         * Register directory
         * @param watchService Watch service
         * @param path         Directory path
         */
        private void registerDirectory(@NotNull WatchService watchService, @NotNull Path path) {
            // Windows supports listing to events in the entire file tree,
            // in that case only allow registering the listener on the base path
            if (IS_WINDOWS && !path.equals(basePath)) {
                return;
            }

            // Start watching directory for events
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
                LOGGER.fine("Started watching directory at \"" + path + "\"");
            } catch (IOException e) {
                LOGGER.severe("Failed to register directory", e);
            }
        }

        /**
         * Unregister directory
         * @param path Directory path
         */
        private void unregisterDirectory(@NotNull Path path) {
            synchronized (fileTree) {
                if (!fileTree.containsKey(path)) {
                    // Already unregistered, can skip work
                    return;
                }
                boolean foundFirst = false;
                Iterator<Map.Entry<Path, Map<Path, Long>>> iter = fileTree.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Path, Map<Path, Long>> entry = iter.next();
                    if (entry.getKey().startsWith(path)) {
                        for (Path childPath : entry.getValue().keySet()) {
                            onFileDeleted(childPath);
                        }
                        foundFirst = true;
                        iter.remove();
                    } else if (foundFirst) {
                        // We can break early because set is alphabetically sorted by key
                        break;
                    }
                }
            }
        }
    }
}
