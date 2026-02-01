# Kids Coin Shop - Telegram Bot (Java 25)

A lightweight Telegram Bot for the Kids Coin Shop, built with **Java 25** and minimal dependencies.

The bot reads and writes directly to the shared `data.json` file, so it works completely independently of the web server (although they share the same data).

## Prerequisites

*   **Java 25** (JDK) installed.
*   **Maven** installed (for building).
*   A **Telegram Bot Token** (from @BotFather).

## 🚀 How to Run

1.  **Open Terminal** in this `telegram-bot-java` directory.

2.  **Set your Bot Token**:
    ```bash
    export BOT_TOKEN="YOUR_TELEGRAM_BOT_TOKEN_HERE"
    ```

3.  **Run the Bot**:
    ```bash
    mvn clean compile exec:java -Dexec.mainClass="com.coinsshop.Main"
    ```

    The bot will look for `../data.json` automatically.

## 🛠 Features

*   `/start` - Welcome message.
*   `/balance` - Check current coin balance.
*   `/tasks` - List tasks. Click the generated command (e.g., `/req_123`) to request coins for a task.
*   `/requests` - View pending requests.

## 🏗 Dependencies

*   `jackson-databind` - For JSON parsing.
*   `slf4j-simple` - For logging.
*   **No other frameworks!** Uses standard Java HTTP Client and File I/O.

## 🧪 Testing Locally (Mac/Linux)

Since this bot uses **Long Polling** (`getUpdates`), you **do not** need a public IP or tools like `ngrok`. It connects outbound to Telegram's servers.

1.  **Ensure no other instances are running**:
    If you started the bot elsewhere, stop it unless you want two bots replying to you!

2.  **Open Telegram**:
    Find your bot by username (e.g., `@YourShopBot`).

3.  **Send `/start`**:
    You should see the welcome message immediately in the terminal log:
    `Msg from 123456789: /start`
    And get a reply in Telegram.

4.  **Test Data Sync**:
    *   Open the Web App locally (`http://localhost:3000`).
    *   Add a Task in the Web App.
    *   Send `/tasks` to the Bot. You should see the new task immediately.

### ❓ Troubleshooting
*   **"Error: BOT_TOKEN environment variable is not set"**: You forgot the `export` step.
*   **"Connection refused"**: This bot connects to *Telegram* (internet), not a local server port (except for reading `data.json` if you customized it). Ensure your internet is working.
*   **Bot doesn't reply**: Check the terminal. If it says `Polling error`, check your internet or Token. If it shows the message received but no reply, check the logs for errors.
