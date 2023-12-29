package io.josemmo.bukkit.plugin.storage;

import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Blocking file stream implementing {@link RandomAccessFile} for reading and writing data.
 * It will lock the file for reading/writing when opened, and release the lock when closed.
 */
public class SynchronizedFileStream extends RandomAccessFile {
    /**
     * Class constructor
     * @param path     Path to file
     * @param readOnly Whether to open stream in read-only mode
     * @throws IOException if failed to acquire lock
     */
    @Blocking
    public SynchronizedFileStream(@NotNull Path path, boolean readOnly) throws IOException {
        super(path.toFile(), readOnly ? "r" : "rw");
        //noinspection ResultOfMethodCallIgnored
        getChannel().lock(0L, Long.MAX_VALUE, readOnly);
    }
}
