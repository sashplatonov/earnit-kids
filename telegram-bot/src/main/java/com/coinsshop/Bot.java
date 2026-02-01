package com.coinsshop;

import com.coinsshop.model.*;
import com.coinsshop.service.DatabaseService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Bot extends TelegramLongPollingBot {

    private final DatabaseService db;
    private final Map<Long, String> userStates = new ConcurrentHashMap<>();

    // States
    private static final String STATE_REGISTER_ROLE = "REGISTER_ROLE";
    private static final String STATE_ENTER_INVITE = "ENTER_INVITE";
    private static final String STATE_SET_PIN = "SET_PIN";
    private static final String STATE_CHILD_NAME = "CHILD_NAME";

    // Temp storage for registration flows
    private final Map<Long, Integer> tempFamilyIds = new ConcurrentHashMap<>();

    public Bot(String token, DatabaseService db) {
        super(token);
        this.db = db;
    }

    @Override
    public String getBotUsername() {
        return Config.getBotUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            try {
                handleMessage(chatId, messageText);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(long chatId, String text) throws TelegramApiException {
        String chatIdStr = String.valueOf(chatId);
        String state = userStates.getOrDefault(chatId, "");

        // 1. Is Parent?
        Integer familyId = db.getFamilyIdByParent(chatIdStr);
        if (familyId != null) {
            handleParentFlow(chatId, text, familyId);
            return;
        }

        // 2. Is Child?
        ChildData child = db.getChildByChatId(chatIdStr);
        if (child != null) {
            handleChildFlow(chatId, text, child);
            return;
        }

        // 3. Not registered
        if (text.equals("/start") || state.isEmpty()) {
            userStates.put(chatId, STATE_REGISTER_ROLE);
            sendRoleSelection(chatId);
            return;
        }

        if (state.equals(STATE_REGISTER_ROLE)) {
            if (text.equals("👨‍👩‍👧‍👦 I am a Parent")) {
                userStates.put(chatId, STATE_SET_PIN);
                sendMessage(chatId, "🔐 Please set a PIN code for Admin access:");
            } else if (text.equals("👶 I am a Child")) {
                userStates.put(chatId, STATE_ENTER_INVITE);
                sendMessage(chatId, "📩 Enter the Invite Code from your parent:");
            } else {
                sendMessage(chatId, "Please select an option.");
            }
            return;
        }

        if (state.equals(STATE_SET_PIN)) {
            int newFamilyId = db.getOrCreateFamily(chatIdStr, text.trim());
            userStates.remove(chatId);
            sendMessage(chatId, "✅ Family created! You are the Admin.");
            sendParentMenu(chatId);
            return;
        }

        if (state.equals(STATE_ENTER_INVITE)) {
            Integer famId = db.getFamilyIdByInviteCode(text.trim());
            if (famId != null) {
                userStates.put(chatId, STATE_CHILD_NAME);
                tempFamilyIds.put(chatId, famId);
                db.consumeInviteCode(text.trim()); // Consume code
                sendMessage(chatId, "✅ Code accepted! What is your name?");
            } else {
                sendMessage(chatId, "❌ Invalid code. Try again.");
            }
            return;
        }

        if (state.equals(STATE_CHILD_NAME)) {
            Integer famId = tempFamilyIds.remove(chatId);
            if (famId == null) {
                userStates.put(chatId, STATE_ENTER_INVITE);
                sendMessage(chatId, "Error. Enter code again.");
                return;
            }

            db.createChild(famId, chatIdStr, text.trim());
            userStates.remove(chatId);
            sendMessage(chatId, "👋 Welcome " + text + "! You are now linked to your family.");
            sendChildMenu(chatId);
            return;
        }
    }

    // --- PARENT FLOW ---

    private void handleParentFlow(long chatId, String text, int familyId) throws TelegramApiException {
        if ("/start".equals(text)) {
            sendParentMenu(chatId);
            return;
        }

        switch (text) {
            case "👶 Add Child":
                String code = db.createInviteCode(familyId);
                sendMessage(chatId, "🎫 Invite Code: `" + code + "`\nGive this to your child to enter in their bot.");
                break;
            case "👨‍👩‍👧‍👦 My Children":
                List<ChildData> children = db.getChildren(familyId);
                StringBuilder sb = new StringBuilder("👶 *Children:*\n");
                for (ChildData c : children) {
                    sb.append(String.format("- %s: %d 🪙\n", c.name(), c.balance()));
                }
                if (children.isEmpty())
                    sb.append("No children linked yet.");
                sendMessage(chatId, sb.toString());
                break;
            case "📋 Manage Tasks":
                // Demo: Add default tasks if empty
                if (db.getTasks(familyId).isEmpty()) {
                    db.addTask(familyId, "Clean Room", 10, "🧹");
                    db.addTask(familyId, "Homework", 20, "📚");
                    sendMessage(chatId, "Added default tasks (Clean Room, Homework).");
                } else {
                    sendMessage(chatId, "You have tasks listed.");
                }
                break;
            case "🛒 Manage Shop":
                sendMessage(chatId, "Shop management not fully implemented yet.");
                break;
            default:
                sendParentMenu(chatId);
        }
    }

    private void sendParentMenu(long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("👨‍👩‍👧‍👦 *Parent Dashboard*\nManaged Family.");
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("👶 Add Child");
        row1.add("👨‍👩‍👧‍👦 My Children");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("📋 Manage Tasks");
        row2.add("🛒 Manage Shop");

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);
        execute(message);
    }

    // --- CHILD FLOW ---

    private void handleChildFlow(long chatId, String text, ChildData child) throws TelegramApiException {
        if ("/start".equals(text)) {
            sendChildMenu(chatId);
            return;
        }

        switch (text) {
            case "💰 My Balance":
                sendMessage(chatId, "💰 Balance: *" + child.balance() + "* coins");
                break;
            case "📋 Tasks":
                sendTaskList(chatId, db.getTasks(child.familyId()));
                break;
            case "🛒 Shop":
                sendShopList(chatId, db.getShopItems(child.familyId()));
                break;
            default:
                sendMessage(chatId, "Use the menu.");
                sendChildMenu(chatId);
        }
    }

    private void sendChildMenu(long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🧒 *Kids Dashboard* (" + db.getChildByChatId(String.valueOf(chatId)).name() + ")");
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("💰 My Balance");
        row1.add("📋 Tasks");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🛒 Shop");

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);
        execute(message);
    }

    // --- HELPERS ---

    private void sendRoleSelection(long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("👋 Welcome! Who are you?");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add("👨‍👩‍👧‍👦 I am a Parent");
        row1.add("👶 I am a Child");
        keyboard.add(row1);

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);
        execute(message);
    }

    private void sendMessage(long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("Markdown");
        execute(message);
    }

    private void sendTaskList(long chatId, List<Task> tasks) throws TelegramApiException {
        if (tasks == null || tasks.isEmpty()) {
            sendMessage(chatId, "No tasks available.");
            return;
        }
        StringBuilder sb = new StringBuilder("📋 *Tasks:*\n\n");
        for (Task task : tasks) {
            sb.append(String.format("• %s *%s* - %d 🪙\n",
                    task.icon() != null ? task.icon() : "🔹",
                    task.title(),
                    task.reward()));
        }
        sendMessage(chatId, sb.toString());
    }

    private void sendShopList(long chatId, List<ShopItem> items) throws TelegramApiException {
        if (items == null || items.isEmpty()) {
            sendMessage(chatId, "Shop is empty.");
            return;
        }
        StringBuilder sb = new StringBuilder("🛒 *Shop:*\n\n");
        for (ShopItem item : items) {
            sb.append(String.format("• %s *%s* - %d 🪙\n",
                    item.icon() != null ? item.icon() : "🎁",
                    item.title(),
                    item.price()));
        }
        sendMessage(chatId, sb.toString());
    }
}
