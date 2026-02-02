# 🪙 Kids Coin Shop

A modern, minimal web application for managing kids' reward coins. Parents can award coins for completed tasks, and kids can spend them in a virtual shop. Featuring a zero-dependency Node.js backend and a modular, component-based frontend.

## ✨ Features

-   **Dual-Role Authentication** — Separate PIN-based logins for Admin (Parents) and Children.
-   **Task Management** — Create, edit, and delete tasks with reward values (Admin).
-   **Virtual Shop** — Manage a catalog of items kids can "buy" with their earned coins (Admin).
-   **Earning & Spending** — Simple UI for awarding coins and processing purchases.
-   **Coin Requests** — Children can send requests for custom coin amounts for approval (Admin).
-   **Transaction History** — Detailed log of all earnings, spendings, and approvals.
-   **Quick Import** — Bulk import tasks or shop items using a simple pipe-separated format.
-   **Session Security** — Secure `HttpOnly` cookies with a 24-hour session duration and rate-limited login attempts.
-   **Mobile First** — Fully responsive design optimized for phones and tablets.

## 🛠 Tech Stack

### Web Application
-   **Backend**: Pure Node.js (no external dependencies like Express).
-   **Frontend**: Vanilla HTML5, CSS3, and modern ES Modules.
-   **Storage**: Local `data.json` file for simplicity and portability.
-   **Deployment**: Docker & Docker Compose support.

### Telegram Bot
-   **Language**: Java (Maven project).
-   **Purpose**: Provides an alternative interface for managing tasks and notifications via Telegram.

## 📂 Project Structure

```text
├── js/modules/          # Modular frontend logic (state, ui, api, actions)
├── src/                 # Backend logic (API handlers, data services)
├── views/               # UI components and templates
│   └── components/      # Partial HTML files assembled by the server
├── telegram-bot/        # Java source code for the Telegram bot
├── data.json            # Flat-file database
├── server.js            # Main entry point (HTTP server)
└── Dockerfile           # Web app containerization
```

## 🚀 Getting Started

### Prerequisites
-   Node.js (v14+)
-   Docker (optional)

### Local Development
1.  Clone the repository.
2.  Create a `.env` file from `.env.example`.
3.  Start the server:
    ```bash
    npm start
    ```
4.  Open `http://localhost:3000` in your browser.

### Docker Deployment
```bash
docker compose up -d --build
```
The application will be available at `http://localhost:3000`. Data is persisted via a volume mapping to `data.json`.

## 🔐 Security

-   **PIN Protection**: The site requires a 6-digit PIN for access. Default PINs are `000000` for both Admin and Child.
-   **Rate Limiting**: The API blocks IPs after multiple failed login attempts to prevent brute-force attacks.
-   **Cookie Safety**: Authentication is handled via `HttpOnly` cookies, preventing XSS-based token theft.

## 🤖 Telegram Bot

The project includes a Telegram bot located in the `/telegram-bot` directory. It is built with Java and Maven.
-   **Configuration**: Requires `BOT_TOKEN` in the `.env` file.
-   **Build**: Use `mvn clean compile exec:java` inside the `telegram-bot` directory.

## 📝 License

MIT
