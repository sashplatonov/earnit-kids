# Firebase / FCM Setup Guide

This guide configures push notifications for the Capacitor mobile shell and the current backend implementation.

## Table of Contents
- [1. Create Firebase project](#1-create-firebase-project)
- [2. Android app setup](#2-android-app-setup)
- [3. iOS app setup](#3-ios-app-setup)
- [4. APNs for iOS delivery](#4-apns-for-ios-delivery)
- [5. Enable backend push sending](#5-enable-backend-push-sending)
- [Where to get `FCM_SERVICE_ACCOUNT_JSON`](#where-to-get-fcm_service_account_json)
- [6. Install and sync mobile project](#6-install-and-sync-mobile-project)
- [7. Verify token registration](#7-verify-token-registration)
- [8. Verify delivery scenarios](#8-verify-delivery-scenarios)
- [9. Troubleshooting](#9-troubleshooting)
- [10. iOS local dev fallback without APNs](#10-ios-local-dev-fallback-without-apns)

## 1. Create Firebase project
1. Open Firebase Console: https://console.firebase.google.com/
2. Create a new project (or use an existing one).
3. Open **Project settings**:
   - Direct link format: `https://console.firebase.google.com/project/<your-project-id>/settings/general`

## 2. Android app setup
1. In Firebase project settings, add an Android app.
2. Use your Android package ID (must match Capacitor app ID), for example:
   - `com.coins.kids`
3. Download `google-services.json`.
4. Place it here:
   - `mobile/android/app/google-services.json`

## 3. iOS app setup
1. In Firebase project settings, add an iOS app.
2. Use your iOS bundle ID (must match Capacitor app ID), for example:
   - `com.coins.kids`
3. Download `GoogleService-Info.plist`.
4. In Xcode, add it to the `App` target:
   - `mobile/ios/App/App/GoogleService-Info.plist`

## 4. APNs for iOS delivery
1. In Apple Developer account, create an APNs Authentication Key (`.p8`):
   - https://developer.apple.com/account/resources/authkeys/list
2. In Firebase Console -> Project settings -> Cloud Messaging:
   - Direct link format: `https://console.firebase.google.com/project/<your-project-id>/settings/cloudmessaging`
   - Upload APNs key
   - Set Key ID and Team ID
3. In Xcode target capabilities, enable:
   - `Push Notifications`
   - `Background Modes` -> `Remote notifications`

## 5. Enable backend push sending
Current backend uses FCM HTTP v1 with OAuth (service account).

1. In Firebase Console -> Project settings -> Service accounts, generate a service account JSON key:
   - Direct link format: `https://console.firebase.google.com/project/<your-project-id>/settings/serviceaccounts/adminsdk`
2. Recommended: store JSON as a file on server and use path env:
```env
ENABLE_PUSH_NOTIFICATIONS=true
FCM_PROJECT_ID=your_firebase_project_id
FCM_SERVICE_ACCOUNT_PATH=/absolute/path/to/service-account.json
# or standard Google env:
# GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/service-account.json
```
3. Alternative: inline JSON in env (less convenient):
```env
FCM_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"...","private_key_id":"...","private_key":"-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n","client_email":"...","client_id":"..."}'
```
4. Restart backend after updating env.

### Where to get `FCM_SERVICE_ACCOUNT_JSON`
Option A (Firebase Console):
1. Open Firebase Console: https://console.firebase.google.com/
2. Open your project -> **Project settings**.
3. Open tab **Service accounts**.
4. Click **Generate new private key**.
5. Download JSON key file.
6. Use the JSON file directly via `FCM_SERVICE_ACCOUNT_PATH` (recommended), or inline as `FCM_SERVICE_ACCOUNT_JSON`.

Option B (Google Cloud Console):
1. Open Google Cloud Console: https://console.cloud.google.com/
2. Select your Firebase-linked project.
3. Go to **IAM & Admin** -> **Service Accounts**:
   - https://console.cloud.google.com/iam-admin/serviceaccounts
4. Select/create a service account with Firebase Messaging permissions.
5. Open **Keys** -> **Add key** -> **Create new key** -> JSON.
6. Download JSON key file and reference it via `FCM_SERVICE_ACCOUNT_PATH`.

Recommended page for FCM API setup and status:
- Google Cloud APIs Library (Firebase Cloud Messaging API):
  - https://console.cloud.google.com/apis/library/fcm.googleapis.com

Optional command if you still want inline JSON:
```bash
cat service-account.json | jq -c .
```
Then paste output into:
```env
FCM_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
```

## 6. Install and sync mobile project
```bash
cd mobile
npm install
npm run sync:prod
# or local backend
npm run sync:local
```

## 7. Verify token registration
1. Login in the mobile app.
2. Backend should receive `POST /api/push/register`.
3. Check DB table:
   - `device_push_tokens`

## 8. Verify delivery scenarios
Test these events from another client (web or second device):
1. Balance changed.
2. New request created.
3. Request approved.

Expected result:
1. App in foreground: in-app notification and data refresh.
2. App in background/closed: OS-level push notification appears.

## 9. Troubleshooting
1. No notification in background:
   - Check APNs setup (iOS) and Firebase config.
   - Check app permissions for notifications.
2. No token in backend:
   - Confirm login state and `/api/push/register` request.
   - Check server logs for push registration errors.
3. Notifications disabled globally:
   - Verify `ENABLE_PUSH_NOTIFICATIONS=true`.
4. Invalid tokens:
   - Backend auto-deactivates invalid tokens after FCM responses.

## 10. iOS local dev fallback without APNs
When APNs is not configured (for example, no paid Apple Developer Program yet), the app uses an iOS fallback:
1. Polls backend every ~15 seconds.
2. Refreshes UI data automatically.
3. Shows local in-app/mobile notifications for:
   - balance changes,
   - new requests,
   - approved requests.

Limitations:
1. Works in opened app and after returning from background.
2. Does not provide true OS push delivery for fully closed app.
