# 🪙 Kids Coin Shop

A minimal web application for managing kids' reward coins. Parents can award coins for completed tasks, and kids can spend them in a virtual shop.

## Features

- **Task List** — Define tasks with coin rewards (admin only)
- **Shop** — Items kids can purchase with coins (admin only)
- **Coin Earning** — Parents award coins when tasks are completed
- **Purchasing** — Kids buy items (balance validation)
- **History** — Full transaction log for both earning and spending
- **PIN Protection** — The site is closed with a PIN code by default
- **Session Duration** — Login lasts for 24 hours (via HttpOnly cookies)

## Security

The entire application is protected by a PIN code. Upon first access, or after 24 hours, users will be prompted to enter the PIN to view the site content.

### Access Control
The PIN used for global access is the same as the parent/admin PIN stored in `data.json`.

### Cookies
The authentication state is stored in a secure `HttpOnly` cookie named `app_auth`. This prevents client-side scripts from accessing the authentication token, protecting against XSS attacks.

### Docker Port Conflict
If you encounter a "port is already allocated" error when running `docker compose up`, it's because an old container is still running. Use:
```bash
docker compose down --remove-orphans
docker compose up -d --build
```

### Other Security Options
- **VPN (Tailscale)**: The most secure way. Don't expose ports 80/443 to the public internet. Use Tailscale to access the site via a private IP.
- **Cloudflare Tunnel**: Secure access without opening ports, allows using Google/Telegram for login.
- **HTTPS**: Always use HTTPS (via Let's Encrypt/Nginx) when using passwords.

```bash
node server.js
# → http://localhost:3000
```

## Docker

```bash
docker compose up -d
# → http://localhost:3000
```

Data persists in `data.json` on host.

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
