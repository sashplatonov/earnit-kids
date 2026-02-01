package com.coinsshop;

import com.coinsshop.model.AppData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileRepository {
    private final Path dataPath;
    private final ObjectMapper mapper;

    public FileRepository(String filePath) {
        this.dataPath = Path.of(filePath);
        this.mapper = new ObjectMapper();
    }

    public synchronized AppData load() {
        if (!Files.exists(dataPath)) {
            System.err.println("Data file not found at: " + dataPath.toAbsolutePath());
            return null;
        }

        try {
            String json = Files.readString(dataPath);
            return mapper.readValue(json, AppData.class);
        } catch (IOException e) {
            System.err.println("Failed to read data file: " + e.getMessage());
            return null;
        }
    }

    public synchronized boolean save(AppData data) {
        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(dataPath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to write data file: " + e.getMessage());
            return false;
        }
    }
}
