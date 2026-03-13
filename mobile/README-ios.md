# iOS Publishing Guide

This document walks through every step needed to publish (or test without paying) the App Store build based on the Capacitor shell.

## Table of Contents
- [1. Setup / requirements](#1-setup--requirements)
- [2. Prepare the Capacitor iOS project](#2-prepare-the-capacitor-ios-project)
- [3. Signing & capabilities](#3-signing--capabilities)
- [4. Test without paying (free Apple ID)](#4-test-without-paying-free-apple-id)
- [5. Archive & upload (paid account)](#5-archive--upload-paid-account)
- [6. Deep links & associated domains](#6-deep-links--associated-domains)
- [7. Useful commands](#7-useful-commands)
- [8. Local iOS run with Docker backend (`http://localhost:3001`)](#8-local-ios-run-with-docker-backend-httplocalhost3001)
- [9. Quick Start One-liner](#9-quick-start-one-liner)

## 1. Setup / requirements
- Apple Developer Program: required only for App Store publishing, not for simulator testing.  
- Apple ID: any Apple ID works for local builds and simulator usage; for App Store uploads you need an account tied to the Developer Program.  
- Tools: install Xcode (latest stable release) and the `npm` toolchain inside `mobile/`.
- Make sure `capacitor.config.json` uses the bundle ID you plan to ship (for testing you can use `com.yourcompany.coinskids`).

## 2. Prepare the Capacitor iOS project
1. `cd mobile`  
2. Run `npm run sync` to copy the latest `public/` build into the Capacitor `ios/App/public` folder.  
3. If not already added, run `npx cap add ios`.  
4. Open Xcode with `npm run open:ios`.

## 3. Signing & capabilities
1. In Xcode → select the `App` target → `Signing & Capabilities`.  
2. Choose your Team (use your Apple ID for free testing; switching to a paid account later is easy).  
3. Confirm the Bundle Identifier matches `appId` in `capacitor.config.json`.  
4. Add the **Associated Domains** capability and enter `applinks:earnit-kids.igo.mywire.org` (or your production domain).  
5. Set the deployment target to iOS 16+ (or the version you support).

## 4. Test without paying (free Apple ID)
1. Launch an iOS Simulator in Xcode (this path is fully free).  
2. Run the project (`Cmd+R`).  
3. Optional physical-device test: use a free Apple ID for signing in Xcode.  
4. Free Apple ID limitation (device install only): builds expire every 7 days; rerun `npm run sync` and rebuild to refresh.  
5. For extra safety turn on `Product → Scheme → Edit Scheme → Build Configuration = Debug` before running.

## 5. Archive & upload (paid account)
1. After adding a paid Apple Developer account, set the scheme to `Release`.  
2. Build → Product → Archive.  
3. When the Organizer opens, upload the archive to App Store Connect.  
4. In App Store Connect, create the app (same bundle ID) and fill metadata (description, screenshots, privacy url, pricing).  
5. Attach the uploaded build to a new version and submit for review.  
6. Repeat `npm run sync` → `Archive` → `Upload` for updates.

## 6. Deep links & associated domains
- The app already serves `public/.well-known/apple-app-site-association`. Replace `TEAMID` in that file with your actual Team ID before upload.  
- Make sure the file is served over HTTPS (same domain configured in `capacitor.config.json`).  
- When running locally, Associated Domains are ignored; deep links work via `server.url` when the file exists on the production domain.

## 7. Useful commands
```
cd mobile
npm run sync         # copy latest web assets
npx cap copy ios     # optional: copy assets without syncing
npx cap open ios     # open Xcode project
```

## 8. Local iOS run with Docker backend (`http://localhost:3001`)
From repository root:
```bash
docker compose --profile db up -d --build
```

Then in mobile folder:
```bash
cd mobile
npm run sync:local
npm run open:ios
```

## 9. Quick Start One-liner
Run this command from the repository root to initialize everything and open the project in Xcode:
```bash
cd mobile && npm install && (npx cap add ios || echo "Ok") && npm run sync:local && npm run open:ios
```
