# 🤖 Telegram Alerts & Backups Setup

The system supports automated server error notifications and regular database backups via a Telegram bot.

## 📋 What does it provide?

1.  **Alerts**: If a critical server error (500) occurs or the server fails to start, the bot instantly sends a message with error details and stack trace.
2.  **Backups**: Once a day (or at a configured interval), the bot sends a database dump file (`.sql`). This ensures your data is safe even in case of total server failure.

---

## 🛠 How to setup (Step-by-Step)

### 1. Create a Bot and get the Token
1.  Find the **@BotFather** bot in Telegram.
2.  Send the `/newbot` command.
3.  Follow the instructions: choose a name and a unique username for your bot.
4.  You will receive an **API Token** (looks like `12345678:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`).
5.  **Save it — this is your `TELEGRAM_BOT_TOKEN`**.

### 2. Get your Chat ID
You need to find out the ID of the chat where the bot will send messages. This can be your private chat or a group.

**Option A (Private Chat):**
1.  Send any message to your new bot.
2.  Go to the following URL in your browser: `https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates`.
3.  Find the `"chat":{"id":XXXXXXXXX, ...}` object in the JSON response.
4.  The number `XXXXXXXXX` is your **`TELEGRAM_CHAT_ID`**.

**Option B (Group):**
1.  Add the bot to a group.
2.  Promote it to Admin (optional, but recommended).
3.  Post any message in the group.
4.  Visit the URL from Option A and find the group ID (usually starts with a `-`).

---

## ⚙️ Environment Variables (.env)

Add the following lines to your `.env` file:

```env
# Enable notifications
ENABLE_TELEGRAM_ALERTS=true

# Bot Token from BotFather
TELEGRAM_BOT_TOKEN=your_token_here

# ID of the chat or group
TELEGRAM_CHAT_ID=your_chat_id_here

# Backup interval in hours (optional, defaults to 24)
BACKUP_INTERVAL_HOURS=24
```

---

## 🛡 Security and Important Notes

*   **Never share your bot token.** If the token is leaked, revoke it via `@BotFather` using the `/revoke` command.
*   **Write Permissions**: The server needs permissions to create a `backups` folder inside the `data` directory (usually handled automatically).
*   **pg_dump**: The `pg_dump` utility must be installed on the server (or inside the Docker container) for backups to work. It is already pre-installed in our official Docker image.
*   **File Size**: Telegram allows bots to send files up to 50MB. For a small home application database, this will be sufficient for a very long time.
