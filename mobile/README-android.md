# Android Publishing Guide

Follow this guide to publish the Capacitor shell on Google Play and to test without spending $25.

## 1. Requirements
- Google Play Console account: creating one costs $25 (one-time). For internal testing without publishing you can skip it entirely and install APKs directly.  
- Android Studio + Java SDK installed.  
- `mobile/capacitor.config.json` should contain `appId = com.yourcompany.coinskids`.

## 2. Prepare Capacitor Android project
1. `cd mobile && npm run sync` (keep `public/` assets in sync).  
2. Add Android platform if missing: `npx cap add android`.  
3. Open Android Studio: `npm run open:android`.

## 3. Configure manifest & deep links
1. In Android Studio open `android/app/src/main/AndroidManifest.xml`.  
2. Under the main `<activity>` add an `<intent-filter>` to handle https links:
```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https"
          android:host="coins-kids-shop.onrender.com"
          android:pathPrefix="/login-child" />
</intent-filter>
```
3. Android will verify the domain against `assetlinks.json`; place the real `sha256_cert_fingerprints` there before upload.

## 4. Signing & build flavor
1. Create or reuse a keystore:
   - Run `keytool -genkeypair -v -keystore release.keystore -alias coins -keyalg RSA -keysize 2048 -validity 10000`.  
   - Store keystore files securely.  
2. Update `android/app/build.gradle` with your signing config and reference it in the `release` block.  
3. Keep `minSdkVersion`/`targetSdkVersion` aligned with Google Play requirements (currently min 26, target latest stable).

## 5. Test without Google Play account
1. Build a debug APK: `./gradlew assembleDebug` (or use Android Studio Run).  
2. Connect a device with USB debugging or launch an emulator.  
3. Install locally: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.  
4. Alternatively, build a release APK signed with your debug keystore and install it the same way.  
5. There’s no cost: this bypasses Play Console entirely.  
6. For deep link tests, open Safari/Chrome and navigate to `https://coins-kids-shop.onrender.com/login-child/<token>`; Android should offer to open via the installed app.

## 6. Publish to Google Play
1. Build a release AAB: `./gradlew bundleRelease` (run this from the `mobile/android/` directory; the Gradle wrapper lives there).  
2. In Google Play Console, create an application using your package name.  
3. Upload the AAB to the Internal test track first; fill store listing fields (title, description, screenshots, privacy policy URL, contact details).  
4. Provide `assetlinks.json` on your production domain with the release certificate fingerprint so Play verifies links (`android.intent` with `autoVerify`).  
5. Promote the internal test build to production after verifying functionality. Updates require repeating `npm run sync` → `bundleRelease` → upload new AAB.

## 7. Commands summary
Use these commands every time you refresh the web assets or rebuild the native bundle:
```
cd mobile
npm run sync                 # copy latest `public/` build into Capacitor
npx cap copy android         # ensure native project has the latest assets
npx cap open android         # open Android Studio for manual tweaks
```
Then build the release bundle from inside the generated Android folder:
```
cd android
./gradlew bundleRelease      # produces app-release.aab in android/app/build/outputs/bundle/release/
```
If you prefer the full Android Studio workflow, skip the last two commands and run the bundle build from the IDE (Build → Build Bundle(s) / APK(s)).

When you’re ready for stores, follow the dedicated checklist above. For quick testing or demos, rely on local APK installs so you don’t need a Play Console account.
