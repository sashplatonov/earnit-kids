package com.coinsshop;

import com.coinsshop.service.DatabaseService;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            // Validate Token
            String botToken = Config.getBotToken();
            if (botToken == null || botToken.isEmpty()) {
                System.err.println("ERROR: BOT_TOKEN environment variable is not set.");
                System.err.println("Usage: export BOT_TOKEN=... && mvn exec:java");
                System.exit(1);
            }

            // Init DatabaseService
            String dbPath = Config.getDbPath();
            System.out.println("Using SQLite database: " + dbPath);
            DatabaseService dbService = new DatabaseService(dbPath);

            // Register Bot
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new Bot(botToken, dbService));

            System.out.println("CoinsShop Telegram Bot started successfully! (DB Mode)");

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
