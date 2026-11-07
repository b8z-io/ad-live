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

LATTICE · SMEAR · ECHO · DOME · NEON · DRIP · MAP.

### MAP — painting onto surfaces

The first step towards automatic projection mapping. Hold up cards, paper, a box or any bright object
(front or rear camera) and MAP paints generative media onto up to **four surfaces** at once:

- Every frame the camera is read back at ~160 px wide and lightly blurred; Otsu picks a brightness
  threshold (nudged by the sensitivity control).
- **Facets:** inside the bright area, regions grow only across small brightness steps (≤ 9/255 between
  neighbours). The faces of a box are lit differently, so they separate at their creases; shading across one
  face doesn't split it. Separate cards are separate regions anyway. The four biggest are kept.
- Each region's convex hull is reduced to its largest inscribed quadrilateral. If that covers > 86 % of the
  hull it's a **surface**: corners smoothed with a One-Euro filter, kept in a stable order, and a homography
  maps the pattern on in perspective at the surface's aspect. Otherwise it's a **shape**: the pattern fills
  its own mask and follows its centroid and orientation.
- Regions are matched to four tracking slots by centroid, so each face keeps its smoothing, corner order and
  colour offset from frame to frame. Every surface gets an edge trace.
- Anything darker than a surface (fingers, shadows) stays in front; the surface's own shading shows through.
- Drag up/down picks the pattern: CONTOURS · CAUSTICS · TRUCHET · LATTICE · OP-ART · JELLYFISH (the orange
  Voronoi mesh from [dumpling-launcher](https://github.com/b8z-io/dumpling-launcher) / the ISE 2027 saver).

Tested on rendered clips with known corners: a single card swinging ±38° with a finger over one edge (locked on
every sample, median corner error ~7 px on 480×854), and a turning box (2–3 faces) plus a separate card (82 % of
faces found as separate surfaces, median error 7 px, p90 11 px). Faces lit almost identically merge into one shape.

Next steps toward projector mapping: multiple surfaces, then camera → projector calibration
(structured light) so the output can be thrown back onto the real object over NDI. Swipe left/right to change, drag up/down to shape,
hold to hyperfocus on one colour, double-tap to flip camera, tap to hide controls.
