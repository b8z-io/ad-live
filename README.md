# Another Distraction · Live

Fullscreen Android app: the phone camera becomes real-time GPU effects, and the effects generate a soundscape.

The whole engine is one page, `app/src/main/assets/index.html` (WebGL2 + Web Audio). The app is a thin
native shell (`MainActivity.java`) that serves the page from `https://appassets.androidplatform.net/`
(a secure context, so `getUserMedia` works), grants the camera, handles the file / record buttons,
keeps the screen on and runs immersive fullscreen.

## Build

Every push to `main` builds the APK in GitHub Actions and publishes it as the **latest** release,
so the phone can download it straight from the Releases page.

Locally: `gradle assembleDebug` (Gradle 8.14, JDK 17+, Android SDK 35) → `app/build/outputs/apk/debug/app-debug.apk`.

`keystore/debug.keystore` is a throwaway debug key kept in the repo on purpose: every build is signed
with the same key, so a new APK installs over the old one.

## Debug on the phone

Plug the phone into a desktop with USB debugging on, open `chrome://inspect` in Chrome, and inspect the
app's WebView for the console, performance and live edits.

## Effects

LATTICE · SMEAR · ECHO · DOME · NEON · DRIP. Swipe left/right to change, drag up/down to shape,
hold to hyperfocus on one colour, double-tap to flip camera, tap to hide controls.
