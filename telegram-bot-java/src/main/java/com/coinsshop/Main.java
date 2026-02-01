package com.coinsshop;

import com.coinsshop.model.AppData;
import com.coinsshop.model.Request;
import com.coinsshop.model.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {
    private static String BOT_TOKEN = System.getenv("BOT_TOKEN");
    // Default to sibling directory's data.json if not set
    private static final String DATA_FILE = System.getenv("DATA_FILE") != null
            ? System.getenv("DATA_FILE")
            : Path.of("..", "data.json").toString();

    private static final String BASE_URL = "https://api.telegram.org/bot";

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final FileRepository repo = new FileRepository(DATA_FILE);

    private static long lastUpdateId = 0;

    public static void main(String[] args) {
        if (BOT_TOKEN == null) {
            System.err.println("Error: BOT_TOKEN environment variable is not set.");
            System.exit(1);
        }

        System.out.println("🤖 Bot started using Java 25");
        System.out.println("Reading data from: " + DATA_FILE);

        // Polling loop
        while (true) {
            try {
                getUpdates();
                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                System.err.println("Polling error: " + e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static void getUpdates() throws IOException, InterruptedException {
        String url = BASE_URL + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode result = root.get("result");

            if (result.isArray()) {
                for (JsonNode update : result) {
                    long updateId = update.get("update_id").asLong();
                    lastUpdateId = Math.max(lastUpdateId, updateId);

                    if (update.has("message")) {
                        handleMessage(update.get("message"));
                    } else if (update.has("callback_query")) {
                        handleCallback(update.get("callback_query"));
                    }
                }
            }
        }
    }

    private static void handleMessage(JsonNode message) {
        if (!message.has("text"))
            return;

        long chatId = message.get("chat").get("id").asLong();
        String text = message.get("text").asText();

        System.out.println("Msg from " + chatId + ": " + text);

        AppData data = repo.load(); // Reload data every time to ensure freshness
        if (data == null) {
            sendMessage(chatId, "⚠️ Error: Could not read data file.");
            return;
        }

        // Modern switch expression
        switch (text) {
            case "/start" -> {
                String welcome = """
                        👋 Welcome to Kids Coin Shop Bot!

                        Commands:
                        /balance - 💰 Check Balance
                        /tasks - 📋 View Tasks
                        /requests - 📩 View Requests
                        /shop - 🛒 View Shop (ToDo)
                        """;
                sendMessage(chatId, welcome);
            }
            case "/balance" -> {
                int balance = data.balance();
                sendMessage(chatId, "💰 Current Balance: *" + balance + " 🪙*");
            }
            case "/tasks" -> {
                StringBuilder sb = new StringBuilder("📋 *Tasks:*\n\n");
                var tasks = data.tasks();
                if (tasks.isEmpty())
                    sb.append("No tasks available.");

                // We send text list first, can implement buttons later
                for (Task t : tasks) {
                    sb.append("• ").append(t.name()).append(" (+").append(t.coins()).append(" 🪙)\n");
                    sb.append("  _/req_").append(t.id()).append("_\n\n");
                }
                sendMessage(chatId, sb.toString());
            }
            case "/requests" -> {
                StringBuilder sb = new StringBuilder("📩 *Pending Requests:*\n\n");
                var reqs = data.requests().stream().filter(r -> "pending".equals(r.status())).toList();

                if (reqs.isEmpty())
                    sb.append("No pending requests.");

                for (Request r : reqs) {
                    sb.append("• ").append(r.taskName()).append(" (+").append(r.coins()).append(")\n");
                }
                sendMessage(chatId, sb.toString());
            }
            default -> {
                if (text.startsWith("/req_")) {
                    handleTaskRequest(chatId, text, data);
                } else {
                    sendMessage(chatId, "Unknown command.");
                }
            }
        }
    }

    private static void handleTaskRequest(long chatId, String command, AppData data) {
        try {
            long taskId = Long.parseLong(command.substring(5));
            Task task = data.tasks().stream().filter(t -> t.id() == taskId).findFirst().orElse(null);

            if (task == null) {
                sendMessage(chatId, "❌ Task not found.");
                return;
            }

            Request newReq = new Request(
                    System.currentTimeMillis(),
                    task.id(),
                    task.name(),
                    task.coins(),
                    java.time.Instant.now().toString(),
                    "pending");

            // AppData is immutable (record), so we reconstruct logic manually or modify
            // list structure
            // Since records are immutable, we cannot add to data.requests() if
            // unmodifiable.
            // But List in JSON mapping usually ArrayList. Let's try adding.
            List<Request> newRequests = new ArrayList<>(data.requests());
            newRequests.add(newReq);

            AppData updated = new AppData(data.pin(), data.balance(), data.tasks(), data.shop(), newRequests);

            if (repo.save(updated)) {
                sendMessage(chatId, "✅ Request sent for: " + task.name());
            } else {
                sendMessage(chatId, "❌ Failed to save request.");
            }

        } catch (NumberFormatException e) {
            sendMessage(chatId, "Invalid ID");
        }
    }

    private static void handleCallback(JsonNode query) {
        // To implement inline buttons logic
    }

    private static void sendMessage(long chatId, String text) {
        try {
            ObjectNode json = mapper.createObjectNode();
            json.put("chat_id", chatId);
            json.put("text", text);
            json.put("parse_mode", "Markdown");

            String body = mapper.writeValueAsString(json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + BOT_TOKEN + "/sendMessage"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("Send Error: " + e.getMessage());
        }
    }
}
