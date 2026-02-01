# 🪙 Kids Coin Shop - Telegram Bot

A standalone Telegram Bot for the Kids Coin Shop, built with **Java 25** and **SQLite**.
This bot allows parents to manage rewards/tasks and children to view their balance and shop items.

## Features
- **Multi-User Support**: Multiple families (parents/children) can use the same bot.
- **Parent Mode**:
  - Secure PIN protection.
  - Add Children (generate Invite Codes).
  - Manage Tasks (coming soon).
- **Child Mode**:
  - Link to parent via Invite Code.
  - Check Balance.
  - View Tasks and Shop.
- **SQLite Database**: Data is persisted in a local `shop.db` file.

## 🚀 Getting Started

### Prerequisites
- Java 25 (or JDK 25 Preview enabled)
- Maven

### 1. Create a Telegram Bot
You need a Bot Token from Telegram.

1. Open Telegram and search for **[@BotFather](https://t.me/BotFather)**.
2. Send the command `/newbot`.
3. Follow the instructions:
   - **Name**: e.g., `My Coin Shop`
   - **Username**: e.g., `my_coin_shop_bot` (must end in `bot`).
4. BotFather will give you a **HTTP API Token**. Copy it.

### 2. Configure the Project
You must configure the bot using Environment Variables.

**Required Variables:**
- `BOT_TOKEN`: Your Telegram Bot Token.

**Optional Variables:**
- `BOT_USERNAME`: Your bot's username (default: `CoinsShopBot`).
- `DB_PATH`: Path to SQLite database (default: `shop.db`).

See `.env.example` for a reference.

### 3. Run the Bot
You can run the bot using Maven:

```bash
# Linux/macOS
export BOT_TOKEN="your_token_here"
mvn clean compile exec:java

# Windows (PowerShell)
$env:BOT_TOKEN="your_token_here"
mvn clean compile exec:java
```

## Usage Guide

### For Parents
1. Start the bot (`/start`).
2. Select **"👨‍👩‍👧‍👦 I am a Parent"**.
3. Set a **PIN Code** (remember this!).
4. You are now the Admin.
   - Click **"👶 Add Child"** to get an Invite Code.
   - Send this code to your child.

### For Children
1. Start the bot on the child's device.
2. Select **"👶 I am a Child"**.
3. Enter the **Invite Code** shared by the parent.
4. Enter your **Name**.
5. Done! You can now check your balance.

## Project Structure
- `com.coinsshop.model`: Data records (Family, Child, Task...).
- `com.coinsshop.service.DatabaseService`: SQLite connection and queries.
- `com.coinsshop.Bot`: Main bot logic and menu handling.
