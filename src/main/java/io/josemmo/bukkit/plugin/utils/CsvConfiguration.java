package io.josemmo.bukkit.plugin.utils;

import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A class for parsing simple CSV files.
 * It's used for loading and writing Yamipa's configuration file ("image.dat" by default) and does not
 * support escaping of special characters, as this is not currently needed.
 */
public class CsvConfiguration {
    public static final Charset CHARSET = StandardCharsets.UTF_8;
    public static final String COLUMN_DELIMITER = ";";
    private final List<String[]> data = new ArrayList<>();

    /**
     * Get rows
     * @return List of rows
     */
    public @NotNull List<String[]> getRows() {
        return data;
    }

    /**
     * Add row
     * @param row Row
     */
    public void addRow(@NotNull String[] row) {
        data.add(row);
    }

    /**
     * Load from file
     * @param path File path
     * @throws IOException if failed to read file
     */
    public void load(@NotNull String path) throws IOException {
        try (Stream<String> stream = Files.lines(Paths.get(path), CHARSET)) {
            stream.forEach(line -> {
                line = line.trim();

                // Ignore empty lines
                if (line.isEmpty()) {
                    return;
                }

                // Migrate legacy format
                if (!line.contains(COLUMN_DELIMITER)) {
                    line = line.replaceAll("/", COLUMN_DELIMITER);
                }

                // Parse line
                addRow(line.split(COLUMN_DELIMITER));
            });
        }
    }

    /**
     * Save to file
     * @param path File path
     * @throws IOException if failed to write file
     */
    public void save(@NotNull String path) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(Paths.get(path)), CHARSET)) {
            for (String[] row : getRows()) {
                writer.write(String.join(COLUMN_DELIMITER, row) + "\n");
            }
        }
    }
}
