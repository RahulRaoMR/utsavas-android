# UTSAVAS Android WebView App

This folder contains a native Android wrapper for the UTSAVAS web app.

## What is already configured

- App name: `UTSAVAS`
- Package name: `com.talme.utsavas`
- Release web URL: `https://utsavas.com`
- Debug web URL: configurable for local or staging builds
- Play-ready target SDK: `35`
- Features included:
  - WebView loading for the main Utsavas site
  - Back button navigation inside the WebView
  - Pull to refresh
  - Offline fallback screen with retry
  - External app/browser opening for links like phone, email, maps, social media, and app intents
  - File upload support for HTML file inputs
  - Runtime location permission bridge for website geolocation
  - Launcher and splash assets generated from the existing website logo

## Open In Android Studio

1. Install Android Studio.
2. Open the `utsavas-android` folder.
3. Let Android Studio sync the Gradle project and install any missing SDK components.
4. If Android Studio asks to finish Gradle setup locally, allow it.
5. Run the app on an Android phone or emulator and test:
   - home page loading
   - login/register
   - hall browsing
   - booking flow
   - payment flow
   - file uploads
   - location access

## Build The Play Store Bundle

1. In Android Studio, open `Build > Generate Signed App Bundle / APK`.
2. Choose `Android App Bundle`.
3. Create or select your upload key.
4. Build the release bundle.
5. The output will be an `.aab` file for Play Console upload.

## Before Publishing

Update these values if needed before your first Play Store release:

- `app/build.gradle.kts`
  - `applicationId`
  - `versionCode`
  - `versionName`
  - `UTSAVAS_WEB_URL`

## Sync Android With Web Updates

The Android app is a WebView wrapper, so deployed frontend and backend changes already appear in the app automatically when the site URL stays the same.

If you want Android debug builds to point at your latest local web changes too, set one of these in `local.properties` or as an environment variable before opening the project in Android Studio:

- `UTSAVAS_WEB_URL=https://utsavas.com`
- `UTSAVAS_DEBUG_WEB_URL=http://10.0.2.2:3000`

Notes:

- Use `http://10.0.2.2:3000` for the Android emulator when your Next.js app runs on your computer.
- For a physical phone, use your computer's LAN IP instead, for example `http://192.168.1.10:3000`.
- The Android manifest now enables cleartext traffic automatically only when the configured URL starts with `http://`.

## Play Console Notes

- Google Play says new apps and app updates must target an Android API level within one year of the latest major Android release: https://support.google.com/googleplay/android-developer/answer/16561298
- Google Play release uploads use Android App Bundles for new apps: https://support.google.com/googleplay/android-developer/answer/9859348
- If your developer account is a personal account created after November 13, 2023, Google currently requires a closed test with at least 12 opted-in testers for 14 continuous days before production access: https://support.google.com/googleplay/android-developer/answer/14151465

## Important Limitation From This Environment

This workspace did not have Java, Gradle, or Android Studio installed, so the project files were created here but the app bundle was not built in this environment. Open the project in Android Studio on your machine to sync dependencies, generate the signed `.aab`, and upload it to Play Console.
