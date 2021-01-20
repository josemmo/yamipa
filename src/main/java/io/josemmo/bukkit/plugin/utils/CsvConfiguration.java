package io.josemmo.bukkit.plugin.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CsvConfiguration {
    public static final Charset CHARSET = StandardCharsets.UTF_8;
    public static final String COLUMN_DELIMITER = "/";
    private final List<String[]> data = new ArrayList<>();

    /**
     * Get rows
     * @return List of rows
     */
    public List<String[]> getRows() {
        return data;
    }

    /**
     * Add row
     * @param row Row
     */
    public void addRow(String[] row) {
        data.add(row);
    }

    /**
     * Load from file
     * @param path File path
     * @throws IOException if failed to read file
     */
    public void load(String path) throws IOException {
        Stream<String> stream = Files.lines(Paths.get(path), CHARSET);
        stream.forEach(line -> {
            line = line.trim();
            if (!line.isEmpty()) {
                addRow(line.split(COLUMN_DELIMITER));
            }
        });
    }

    /**
     * Save to file
     * @param path File path
     * @throws IOException if failed to write file
     */
    public void save(String path) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path), CHARSET)) {
            for (String[] row : getRows()) {
                writer.write(String.join(COLUMN_DELIMITER, row) + "\n");
            }
        }
    }
}
