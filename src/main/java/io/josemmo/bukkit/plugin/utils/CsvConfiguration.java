package io.josemmo.bukkit.plugin.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CsvConfiguration {
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
        Stream<String> stream = Files.lines(Paths.get(path));
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
        FileWriter writer = new FileWriter(path);
        for (String[] row : getRows()) {
            writer.write(String.join(COLUMN_DELIMITER, row) + "\n");
        }
        writer.close();
    }
}
