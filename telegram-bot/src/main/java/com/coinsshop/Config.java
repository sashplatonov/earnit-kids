package com.coinsshop;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {

    private static final Dotenv dotenv;

    static {
        String userDir = System.getProperty("user.dir");
        Dotenv tempDotenv = null;
        try {
            // Check if .env is readable to avoid FileSystemException in restricted
            // environments
            java.io.File envFile = new java.io.File(userDir, ".env");
            if (envFile.exists() && envFile.canRead()) {
                tempDotenv = Dotenv.configure()
                        .directory(userDir)
                        .ignoreIfMissing()
                        .load();
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load .env file: " + e.getMessage());
        }
        dotenv = tempDotenv;
    }

    public static String get(String key) {
        String val = null;
        if (dotenv != null) {
            val = dotenv.get(key);
        }
        if (val == null || val.isEmpty()) {
            val = System.getenv(key);
        }
        return val;
    }

    public static String getBotToken() {
        return get("BOT_TOKEN");
    }

    public static String getBotUsername() {
        String username = get("BOT_USERNAME");
        return username != null ? username : "CoinsShopBot";
    }

    public static String getDbPath() {
        String dbPath = get("DB_PATH");
        return dbPath != null ? dbPath : "shop.db";
    }

    // Fallback constants if needed, but methods above prefer Env Vars
    public static final String DATA_FILE_PATH = "../data.json";
}
