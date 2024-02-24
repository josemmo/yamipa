package io.josemmo.bukkit.plugin.storage;

import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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
    public SynchronizedFileStream(@NotNull Path path, boolean readOnly) throws IOException {
        super(path.toFile(), readOnly ? "r" : "rw");
        waitForLock(readOnly);
    }

    /**
     * Wait for lock to be released
     * @param readOnly Whether to acquire read-only (shared) lock
     */
    @Blocking
    private void waitForLock(boolean readOnly) throws IOException {
        FileChannel channel = getChannel();
        FileLock lock = null;
        while (lock == null) {
            try {
                lock = channel.lock(0L, Long.MAX_VALUE, readOnly);
            } catch (OverlappingFileLockException e) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException __) {
                    throw e;
                }
            }
        }
    }
}
