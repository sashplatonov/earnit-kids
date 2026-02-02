# 🪙 Kids Coin Shop

A modern, minimal web application for managing kids' reward coins. Parents can award coins for completed tasks, and kids can spend them in a virtual shop. Featuring a zero-dependency Node.js backend and a modular, component-based frontend.

## ✨ Features

-   **Dual-Role Authentication** — Separate password-based logins for Admin (Parents) and Children.
-   **Super Admin Panel** — Manage multiple families, block/unblock accounts, and manage base catalog.
-   **Task Management** — Create, edit, and delete tasks with reward values (Admin).
-   **Virtual Shop** — Manage a catalog of items kids can "buy" with their earned coins (Admin).
-   **Earning & Spending** — Simple UI for awarding coins and processing purchases.
-   **Coin Requests** — Children can send requests for custom coin amounts for approval (Admin).
-   **Transaction History** — Detailed log of all earnings, spendings, and approvals.
-   **Session Security** — Secure `HttpOnly` cookies with a 24-hour session duration and rate-limited login attempts.
-   **Mobile First** — Fully responsive design optimized for phones and tablets.

## 🛠 Tech Stack

### Web Application
-   **Backend**: Pure Node.js (no external dependencies like Express).
-   **Frontend**: Vanilla HTML5, CSS3, and modern ES Modules.
-   **Storage**: Flat-file database in `data/` directory.
-   **Deployment**: Docker & Docker Compose support.

### Telegram Bot
-   **Language**: Java (Maven project).
-   **Purpose**: Provides an alternative interface for managing tasks and notifications via Telegram.

## 📂 Project Structure

├── public/              # Static assets (css, js/client, images)
├── src/                 # Backend source code
│   ├── config/          # Global configuration and constants
│   ├── controllers/     # API and View routing logic
│   ├── middleware/      # Security, body-parser, and auth filters
│   ├── routes/          # Route dispatchers (logic-less)
│   └── services/        # Business logic & data access (auth, email, data)
├── views/               # HTML templates and components
│   └── components/      # UI pieces assembled by the server
├── data/                # Local JSON database storage
└── Dockerfile           # Web app containerization
```

## 🚀 Getting Started

### Prerequisites
-   Node.js (v20+)
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
The application will be available at `http://localhost:3000`. Data is persisted via a volume mapping to the `data/` directory.

## 🔐 Security

-   **Authentication**: The site requires passwords for access (minimum 6 characters).
-   **Security Headers**: Implements `Helmet`-like security headers (XSS Protection, Frame Options, CSP-ready).
-   **Rate Limiting**: The API blocks IPs after multiple failed login attempts to prevent brute-force attacks.
-   **Cookie Safety**: Authentication is handled via `HttpOnly`, `SameSite=Lax` cookies.
-   **Data Isolation**: Client-side JS is strictly separated from server-side logic in `/public`.
-   **Input Sanitization**: Basic protection against directory traversal and JSON injection.

## 🤖 Telegram Bot

The project includes a Telegram bot located in the `/telegram-bot` directory. It is built with Java and Maven.
-   **Configuration**: Requires `BOT_TOKEN` in the `.env` file.
-   **Build**: Use `mvn clean compile exec:java` inside the `telegram-bot` directory.

## 📝 License

MIT
