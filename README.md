# EarnIt Kids

A modern, minimal web application for managing kids' reward coins. Parents can award coins for completed tasks, and kids can spend them in a virtual shop. The app uses a custom Node.js HTTP backend and a modular vanilla frontend.

## 🌐 Live Demo

The project is deployed and available at: [https://earnit-kids.igo.mywire.org/](https://earnit-kids.igo.mywire.org/)

## ✨ Features

-   **Dual-Role Authentication** — Password-based login for Admin (Parents) with recovery options and unique Magic Links for each Child.
-   **Multi-Child Management** — Add multiple children to a single family account, each with their own balance, limits, and magic links.
-   **Super Admin Panel** — Manage multiple families, block/unblock accounts, and manage base catalog.
-   **Daily Coin Limits** — Set individual daily earning limits for each child to keep the economy balanced.
-   **Task Management** — Create, edit, and delete tasks with reward values and usage limits.
-   **Virtual Shop** — Manage a catalog of items kids can "buy" with their earned coins. Items can have physical money equivalents and frequency limits.
-   **Analytics Dashboards** — Visual charts showing top earning tasks and spending habits, with filtering by timeframe and child.
-   **Earning & Spending** — Simple UI for awarding coins and processing purchases across all children.
-   **Coin Requests** — Children can send requests for custom coin amounts or specific items for approval (Admin).
-   **Transaction History** — Detailed log of all earnings, spendings, and approvals.
-   **Database Management** — Integrated backup and restore functionality (PostgreSQL, including automatic Telegram backups).
-   **Telegram Notifications** — Real-time alerts for server errors and scheduled database backups (see [Setup Guide](docs/telegram-setup.md)).
-   **Session Security** — Secure `HttpOnly` cookies with session management.
-   **Mobile First** — Fully responsive design optimized for phones and tablets.

## 🛒 Shop & Analytics Flow

The shop is scoped per child and supports both direct parent purchases and child purchase requests. In combination with the new Analytics Dashboards, parents get full visibility into the family's micro-economy.

-   **Family Catalog + Personal Shop**: Parents can add products from the global catalog to a selected child's shop or create personalized items.
-   **Child Purchase Requests**: Children can submit shop purchase requests (`requestType: shop_purchase`) instead of spending coins directly.
-   **Parent Approval**: Parents approve or reject requests; approved purchases are written to history as `spend` operations and deduct child balance.
-   **Money-Aware Purchases**: Items can include a `money_limit`; when set, purchase flow asks for real-money amount and validates limits.
-   **Frequency Limits**: Shop items may define `frequency` (`limit` + `period`) and are validated against purchase history.
-   **Analytics & Insights**: Parents have access to visual Dashboards tracking all activities over time (Week/Month/Year). It visually splits earnings from jobs and spending in the shop to help track financial behavioral patterns.
-   **Per-Child Data Isolation**: Tasks, shop items, requests, and history are stored with `child_id` and loaded by active child context.

## 🛠 Tech Stack

### Web Application
-   **Backend**: Pure Node.js (no external dependencies like Express for core logic).
-   **Frontend**: Vanilla HTML5, CSS3, and modern ES Modules.
-   **Database**: PostgreSQL (with automated migrations).
-   **Deployment**: Docker & Docker Compose.

### Telegram Bot
-   **Language**: Java (Maven project).
-   **Purpose**: Optional interface for notifications and task management.

## 📂 Project Structure

```
├── public/              # Static assets (css, js/client, images)
├── src/                 # Backend source code
│   ├── config/          # Global configuration and constants
│   ├── controllers/     # API and View routing logic
│   ├── middleware/      # Security, body-parser, and auth filters
│   ├── routes/          # Route dispatchers
│   ├── services/        # Business logic & data access (auth, db, services)
│   └── db/              # Database connection and initialization
├── views/               # HTML templates and components
├── migrations/          # SQL migration files
├── scripts/             # Utility scripts (data migration, etc.)
└── Dockerfile           # Web app containerization
```

## 📚 Documentation

- [Architecture](docs/architecture.md)
- [Backend Rules](docs/rules-backend.md)
- [Frontend Rules](docs/rules-frontend.md)
- [Database Rules](docs/rules-database.md)
- [Design Concept](docs/design-concept.md)
- [Telegram Setup](docs/telegram-setup.md)
- [Operational Playbook](docs/operational-playbook.md)

## 🚀 Getting Started

### Prerequisites
-   Node.js (v20+)
-   PostgreSQL
-   Docker (optional)

### Local Development
1.  Clone the repository.
2.  Create a `.env` file from `.env.example` and configure your database settings.
3.  Install dependencies:
    ```bash
    npm install
    ```
4.  Run migrations:
    ```bash
    npm run migrate
    ```
5.  Start the server:
    ```bash
    npm start
    ```
6.  Open `http://localhost:3000` in your browser.

### Automated Testing
-   Required verification:
    ```bash
    npm run lint
    npm test
    npm run build
    ```
-   Manual browser E2E run:
    ```bash
    npm run test:ui:e2e
    ```
-   Optional combined lint + coverage:
    ```bash
    npm run check
    ```
-   Install Playwright browser (first run only):
    ```bash
    npm run playwright:install
    ```

### Docker Deployment

To build and run the application in a Docker container:

```bash
export DOCKER_HOST=unix:///Users/sash/.colima/default/docker.sock
```

**Rebuild and Start with the bundled Postgres profile:**
```bash
docker compose --profile db up -d --build
```

**Rebuild and Start with an external Postgres:**
```bash
docker compose up -d --build
```

**Local dev with secondary host port:** use the override file so both `3000` and `3001` map to the container.
```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml --profile db up -d --build
```

**Stop:**
```bash
docker compose down
```

**View Logs:**
```bash
docker compose logs -f
```

The application will be available at `http://localhost:3000`.

## 📱 Mobile Shell

The static UI can be embedded inside a Capacitor wrapper for iOS/Android. The `mobile/` folder already contains `capacitor.config.json` and instructions for syncing with the web assets. The wrapper points at `https://earnit-kids.igo.mywire.org` by default, so no API changes are required.

### Prerequisites
- Node.js 20.x or 22.x (LTS recommended).
- Android Studio with Android SDK + Emulator.
- Xcode (for iOS Simulator, macOS only).

### Local test on Android/iOS without store payments
You can test both platforms locally without paying Google Play or Apple App Store fees.

1. Install mobile dependencies:
   ```bash
   cd mobile
   npm install
   ```
2. Generate/refresh native platforms:
   ```bash
   npx cap add android
   npx cap add ios
   npm run sync
   ```
   If the platform already exists, `cap add` can fail. In that case remove the old platform folder and run the command again.

### Run on Android emulator (free)
1. Start an emulator from Android Studio Device Manager.
2. Open native project:
   ```bash
   cd mobile
   npm run sync
   npm run open:android
   ```
3. In Android Studio choose the running emulator and press `Run`.

No Google Play Console account is required for emulator testing or local APK installs.

### Run on iOS simulator (free)
1. Open native iOS project:
   ```bash
   cd mobile
   npm run sync
   npm run open:ios
   ```
2. In Xcode choose an iPhone Simulator and press `Cmd+R`.

No paid Apple Developer subscription is required to run in iOS Simulator.

### Quick smoke-check after launch
- Login screen opens.
- Admin login works.
- Child switcher works.
- Task reward adds coins.
- Shop purchase request can be created and approved/rejected.
- History updates after earn/spend actions.

### Troubleshooting
- If `npx cap` fails or packages look broken, run:
  ```bash
  cd mobile
  rm -rf node_modules package-lock.json
  npm install
  ```
- If Android/iOS project is corrupted, remove the platform folder and re-add it:
  ```bash
  cd mobile
  npx cap add android
  npx cap add ios
  npm run sync
  ```
- If app shows a blank page, verify `mobile/capacitor.config.json` `server.url` is reachable.

### Deep links and release prep
Universal/App Links are controlled by `public/.well-known/apple-app-site-association` and `public/.well-known/assetlinks.json`.
Replace placeholders (`TEAMID`, `REPLACE_WITH_SHA256`) before store submission.

For detailed store workflows and signing steps, see:
- `mobile/README.md`
- `mobile/README-ios.md`
- `mobile/README-android.md`

## 📋 TODO

- [ ] **Security**: Add second-factor authentication (2FA) for Admin accounts.
- [ ] **Testing**: Implement unit and integration tests for core services and API routes.
- [ ] **Notifications**: Add email or push notifications for parents when a child submits a request.
- [x] **Analytics**: Create a dashboard for parents to track coin earning/spending trends over time.
- [ ] **UI Refinement**: Continue improving the responsiveness of the Super Admin tables.
- [x] **Backup Automation**: Implement scheduled database backups.
- [ ] **Language Support**: Add multi-language support (i18n).

## 🔐 Security

-   **Authentication**: Secure password hashing and Magic Link authentication.
-   **Security Headers**: Implements security headers (XSS Protection, Frame Options).
-   **Rate Limiting**: Protection against brute-force attacks on login.
-   **Cookie Safety**: `HttpOnly`, `SameSite=Lax` cookies for session management.
-   **Data Integrity**: Automated PostgreSQL migrations and backup system.

## 📝 License

MIT
