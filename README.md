# 🪙 Kids Coin Shop

A minimal web application for managing kids' reward coins. Parents can award coins for completed tasks, and kids can spend them in a virtual shop.

## Features

- **Task List** — Define tasks with coin rewards (admin only)
- **Shop** — Items kids can purchase with coins (admin only)
- **Coin Earning** — Parents award coins when tasks are completed
- **Purchasing** — Kids buy items (balance validation)
- **History** — Full transaction log for both earning and spending
- **PIN Protection** — Simple admin authentication

## Quick Start

```bash
# Clone and run
node server.js

# Open in browser
open http://localhost:3000
```

## Deployment (VPS with nginx)

### 1. Copy files to server
```bash
scp -r * user@your-server:/var/www/coins/
```

### 2. Run with PM2 (auto-restart)
```bash
npm install -g pm2
cd /var/www/coins
pm2 start server.js --name coins
pm2 save && pm2 startup
```

### 3. Configure nginx reverse proxy
```nginx
server {
    listen 80;
    server_name coins.example.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 4. Reload nginx
```bash
sudo nginx -t && sudo systemctl reload nginx
```

## Data Storage

All data is stored in `data.json`:
- Admin PIN
- Current balance
- Tasks list
- Shop items
- Transaction history

## Tech Stack

- **Frontend**: Vanilla HTML/CSS/JS
- **Backend**: Node.js (no dependencies)
- **Storage**: JSON file

## License

MIT
