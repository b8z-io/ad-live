# Another Distraction · Live

Fullscreen Android app: the phone camera becomes real-time GPU effects, and the effects generate a soundscape.

The whole engine is one page, `app/src/main/assets/index.html` (WebGL2 + Web Audio). The app is a thin
native shell (`MainActivity.java`) that serves the page from `https://appassets.androidplatform.net/`
(a secure context, so `getUserMedia` works), grants the camera, handles the file / record buttons,
keeps the screen on and runs immersive fullscreen.

## NDI® output

Tap **NDI** in the controls, pick 720p or 1080p and landscape or portrait. The effect then renders at
exactly that size; the phone shows a letterboxed preview of the frame being sent, and the on-screen
controls are never in the feed. The soundscape goes out with the picture (muting the phone's speaker
doesn't mute the feed). The source appears on the local network as **AD Live**. If 1080p can't hold
30 fps for three seconds, it steps down to 720p.

How it works: the final frame is packed to UYVY (BT.709) on the GPU, read back asynchronously and sent
over a loopback WebSocket to `FrameBridge.java`, which hands it to `libadndi.so`, a small JNI wrapper
around the NDI send API. Audio goes the same way as planar float from an AudioWorklet.

The NDI SDK is **not** in this repo. `tools/fetch_ndi_sdk.sh` downloads the official NDI SDK for Android
from NDI and unpacks the headers and `libndi.so` into `app/ndi-sdk/` (git-ignored) without running the
installer; CI runs it before every build. Using it means accepting the NDI SDK License Agreement, which
is copied alongside.

NDI® is a registered trademark of Vizrt NDI AB. https://ndi.video/

## Build

Every push to `main` builds the APK in GitHub Actions and publishes it as the **latest** release,
so the phone can download it straight from the Releases page.

Locally: `./tools/fetch_ndi_sdk.sh && gradle assembleDebug` (Gradle 8.14, JDK 17+, Android SDK 35, NDK 28.2, CMake 3.22) → `app/build/outputs/apk/debug/app-debug.apk`.

`keystore/debug.keystore` is a throwaway debug key kept in the repo on purpose: every build is signed
with the same key, so a new APK installs over the old one.

## Debug on the phone

Plug the phone into a desktop with USB debugging on, open `chrome://inspect` in Chrome, and inspect the
app's WebView for the console, performance and live edits.

## Effects

LATTICE · SMEAR · ECHO · DOME · NEON · DRIP. Swipe left/right to change, drag up/down to shape,
hold to hyperfocus on one colour, double-tap to flip camera, tap to hide controls.
