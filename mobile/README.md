# Mobile Shell

This folder holds the Capacitor wrapper that hosts the existing web UI (`../public`). It keeps the current business logic untouched while exposing the app through native iOS/Android shells.

## Table of Contents
## Table of Contents
- [Getting started](#getting-started)
- [Local run (Docker Compose backend at `http://localhost:3001`)](#local-run-docker-compose-backend-at-httplocalhost3001)
- [Free local testing](#free-local-testing)
- [Deep links & links settings](#deep-links--links-settings)
- [Background push notifications (closed app)](#background-push-notifications-closed-app)
- [Production notes](#production-notes)
- [Publishing tips](#publishing-tips)

## Getting started
0. Recommended runtime: Node.js 20.x or 22.x (LTS).
1. `cd mobile && npm install`
2. Config selection:
- `npm run sync:prod` uses `https://earnit-kids.igo.mywire.org`.
   - `npm run sync:local` uses `http://localhost:3001` (Docker Compose backend).
   - `appId`/`appName` can be adjusted in config files if you need custom bundle identifiers.
3. Add platforms:
   - `npx cap add ios`
   - `npx cap add android`
4. Sync/refresh assets whenever `public/` changes:
   - production: `npm run sync` (alias of `npm run sync:prod`)
   - local: `npm run sync:local`
5. Open platform projects:
   - `npm run open:ios`
   - `npm run open:android`

## Local run (Docker Compose backend at `http://localhost:3001`)
Run these commands from the repository root:
```bash
docker compose up -d --build
```

Then run mobile commands:
```bash
cd mobile
npm run sync:local
npm run reverse      # (Android only) forward localhost:3001 via USB
npm run open:ios
# or
npm run open:android
```

If your simulator/emulator does not pick up recent web changes, re-run:
```bash
cd mobile
npm run sync:local
```

## Free local testing
- Android Emulator testing is free (no Google Play Console account required).
- iOS Simulator testing is free (no paid Apple Developer Program required).
- For physical iPhone installs, you can use a free Apple ID with standard 7-day limitations.

## Deep links & links settings
- The app consumes `/login-child/:token`. Configure **Associated Domains** (iOS) and **Intent Filters** (Android) for your package. Replace the placeholders in `../public/.well-known/apple-app-site-association` and `../public/.well-known/assetlinks.json` with your team/app IDs and the SHA256 fingerprint of your Android signing key.
- Both files are served directly by the existing Node.js server because they live inside `public/.well-known/`.

## Background push notifications (closed app)
1. Install/update mobile dependencies:
```bash
cd mobile
npm install
```
2. Sync native projects:
```bash
npm run sync:prod
# or local:
npm run sync:local
```
3. Configure push providers:
   - Android: Firebase Cloud Messaging (FCM) project + `google-services.json` in Android app.
   - iOS: APNs key/cert + Apple capability `Push Notifications`, and Firebase setup if using FCM delivery.
4. Backend env (server):
```bash
ENABLE_PUSH_NOTIFICATIONS=true
FCM_PROJECT_ID=your_firebase_project_id
FCM_SERVICE_ACCOUNT_PATH=/absolute/path/to/service-account.json
# or GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/service-account.json
```
5. After app login on device, the app registers its push token in backend (`/api/push/register`).

## Production notes
- App stores expect stable `capacitor` builds. Keep `mobile/package-lock.json` in sync with `npm install`.
- Use `npm run sync` before each native build so the latest static assets are packaged.
- The web backend must continue to set cookies with `Secure`, `HttpOnly`, and `SameSite=Lax` — see `src/controllers/apiController.js` for the logic used today.

## Publishing tips
This folder contains helper docs for both stores. Start by reading the overview below and then open the store-specific guide you need:

- `README-ios.md` – complete App Store prep and testing walkthrough (including free testing with a standard Apple ID).  
- `README-android.md` – Google Play workflow and how to test an APK/AAB without an account fee.
- `README-firebase-fcm.md` – Firebase/FCM and APNs setup for push notifications (including background/closed app delivery).

Follow the instructions in the respective doc before you start building your first submission. Each guide explains how to configure deep links, signing, store listings, and how to rerun `npm run sync` + rebuild for updates. Check `README-android.md` when you run `./gradlew bundleRelease`, because the Gradle wrapper lives inside the generated `android/` directory.
