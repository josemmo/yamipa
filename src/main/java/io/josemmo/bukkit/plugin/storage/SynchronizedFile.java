package io.josemmo.bukkit.plugin.storage;

import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An instance of a file stored in a shared filesystem (<i>e.g.,</i> a NFS drive).
 * All operations are performed in a blocking way.
 * That is, the instance will acquire a read or write lock before reading or modifying the file.
 * If the file is already locked by another resource, it will wait until the lock is released
 * <b>while blocking the thread</b>.
 */
public class SynchronizedFile {
    protected final Path path;

    /**
     * Class constructor
     * @param path Path to file
     */
    public SynchronizedFile(@NotNull Path path) {
        this.path = path;
    }

    /**
     * Check file exists
     * @return Whether file exists or not
     */
    public boolean exists() {
        return Files.exists(path);
    }

    /**
     * Get image last modified time
     * @return Last modified time in milliseconds or <code>0</code> in case of error
     */
    public long getLastModified() {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception __) {
            return 0L;
        }
    }

    /**
     * Make directories
     * <p>
     * Creates the directory containing this file and its parents if they don't exist.
     */
    public void mkdirs() {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException __) {
            // Silently ignore exception
        }
    }

    /**
     * Get file reader
     * @return Readable stream of the file
     * @throws IOException if failed to acquire lock
     */
    public RandomAccessFile read() throws IOException {
        return new SynchronizedFileStream(path, true);
    }

    /**
     * Get file writer
     * @return Writable stream of the file
     * @throws IOException if failed to acquire lock
     */
    public RandomAccessFile write() throws IOException {
        return new SynchronizedFileStream(path, false);
    }
}
