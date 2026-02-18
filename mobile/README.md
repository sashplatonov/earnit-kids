# Mobile Shell

This folder holds the Capacitor wrapper that hosts the existing web UI (`../public`). It keeps the current business logic untouched while exposing the app through native iOS/Android shells.

## Getting started
0. Recommended runtime: Node.js 20.x or 22.x (LTS).
1. `cd mobile && npm install`
2. Optionally, customize `capacitor.config.json`:
   - `server.url` should point to your production web origin (default `https://coins-kids-shop.onrender.com`). Change to `http://localhost:3000` for local dev, and set `server.cleartext` to `true` if you rely on HTTP.
   - `appId`/`appName` — replace with your bundle identifiers (e.g., `com.example.coinskids`).
3. Add platforms:
   - `npx cap add ios`
   - `npx cap add android`
4. Sync/refresh assets whenever `public/` changes: `npm run sync` or `npx cap sync`
5. Open platform projects:
   - `npm run open:ios`
   - `npm run open:android`

## Free local testing
- Android Emulator testing is free (no Google Play Console account required).
- iOS Simulator testing is free (no paid Apple Developer Program required).
- For physical iPhone installs, you can use a free Apple ID with standard 7-day limitations.

## Deep links & links settings
- The app consumes `/login-child/:token`. Configure **Associated Domains** (iOS) and **Intent Filters** (Android) for your package. Replace the placeholders in `../public/.well-known/apple-app-site-association` and `../public/.well-known/assetlinks.json` with your team/app IDs and the SHA256 fingerprint of your Android signing key.
- Both files are served directly by the existing Node.js server because they live inside `public/.well-known/`.

## Production notes
- App stores expect stable `capacitor` builds. Keep `mobile/package-lock.json` in sync with `npm install`.
- Use `npm run sync` before each native build so the latest static assets are packaged.
- The web backend must continue to set cookies with `Secure`, `HttpOnly`, and `SameSite=Lax` — see `src/controllers/apiController.js` for the logic used today.

## Publishing tips
This folder contains helper docs for both stores. Start by reading the overview below and then open the store-specific guide you need:

- `README-ios.md` – complete App Store prep and testing walkthrough (including free testing with a standard Apple ID).  
- `README-android.md` – Google Play workflow and how to test an APK/AAB without an account fee.

Follow the instructions in the respective doc before you start building your first submission. Each guide explains how to configure deep links, signing, store listings, and how to rerun `npm run sync` + rebuild for updates. Check `README-android.md` when you run `./gradlew bundleRelease`, because the Gradle wrapper lives inside the generated `android/` directory.
