# Pushup Analyzer

Native Kotlin Android application for **Samsung Galaxy S23 Ultra (SM-S918B), Android 16, ARM64**. Android 10+ is supported. All video analysis and export run on the device. The APK includes the ML Kit pose model and deliberately has **no INTERNET permission**; it needs no login, server, runtime model download, or broad gallery access.

## Install and use

Download `PushupAnalyzer-debug-apk` from a successful [Android Actions run](https://github.com/matlab-physicist/PushupAnalyzer/actions/workflows/android.yml), unzip it, and install `app-debug.apk` (allow installation from your browser/file manager). This is a debug-signed APK, not a Play Store release.

1. Film one person side-on, with shoulder, elbow, wrist and hip clearly visible and a stationary camera. Use a local MP4 between 0.5 seconds and 10 minutes. Landscape and portrait videos are fitted without stretching.
2. Tap **Select MP4 from gallery**. Android's system document picker can browse local gallery/video providers; no storage permission is required.
3. Analysis uses a bundled pose model at 10 samples/second. Full extended–bent–extended elbow cycles are counted. The app chooses the more visible arm for the whole clip, filters jitter, and rejects cycles containing tracking gaps. No clothing-color assumptions.
4. A foreground service renders a **960 × 960, 30 fps H.264 MP4**, with the original image fitted into the upper 960 × 640 area. Lower graphs show UP and DOWN durations in seconds, against video elapsed time. A moving playhead, current/last phase durations, completed rep count and elapsed time are synchronized to media timestamps, independent of rendering speed. AAC audio is copied; other audio formats are omitted with a completion warning.
5. Output is published to **Movies/PushupAnalyzer** in the phone gallery, using a pending MediaStore item so failed/cancelled saves are removed. Tap **Open saved video** to play it. Processing can continue with the activity in the background; the notification and app have Cancel buttons. Temporary output is removed on completion/failure/cancellation.

## Measurement limits

These are estimated movement timings, not a medical or competition form assessment. Arms must extend to at least 150° and bend to at most 110°. Partial cycles at clip boundaries, shallow bends, long tracking gaps and phases outside 0.15–15 seconds are excluded. A median filter and 8° reversal hysteresis reduce jitter. DOWN is measured from the last extended-arm sample to the bottom; UP ends at the first extended-arm sample. Bottom holds are included in DOWN. Results have 0.1 second sampling resolution plus pose-estimation error. Poor lighting, occlusion, oblique views or multiple people can undercount or miscount; review the exported video. Low coverage is reported instead of filling long gaps with fabricated movement.

Export resamples source video at 30 fps (including VFR footage), preserves aspect ratio, and produces SDR H.264. It does not retain HDR metadata, original resolution, or non-AAC audio. Processing can take longer than the source duration and uses temporary storage plus a gallery copy; the app checks available space before export. No silent duration truncation. Jobs are not automatically resumed after Android kills the app or the phone reboots; select the video again. Output timing uses EGL presentation timestamps, not wall-clock time or unsupported Canvas drawing on codec surfaces.

## Build and test

Requires JDK 17 and Android SDK platform 36/build-tools 35.0.0. The checked-in Gradle 8.13 wrapper verifies its distribution SHA-256.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
# With an Android 16 emulator or a connected device:
./gradlew connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Includes ARM64 and x86_64 native libraries (the latter for emulator testing). AGP packages uncompressed native libraries with 16 KB ZIP page alignment. CI also verifies the APK signature and ZIP alignment.

[GitHub Actions](.github/workflows/android.yml) runs unit tests, lint and compilation on every push/PR and manually. A second job runs instrumentation tests on an Android 16 x86_64 emulator. Test reports and the APK are downloadable artifacts; check both job results.

- JVM tests: full/partial/repeated/slow cycles, separate phase durations, overlay timeline values, noisy/shallow motion, pose dropouts, timestamp gaps and angle geometry.
- Android instrumentation: a generated 2-second red/blue MP4 with AAC audio is decoded, composited, encoded, demuxed and saved/read/deleted through MediaStore. Checks frame count, monotonically spaced media timestamps, duration, image orientation, graph playhead and retained audio. Also initializes the bundled model without INTERNET permission and checks empty-scene rejection and export cancellation.
- Synthetic fixtures do **not** establish accuracy on human push-up videos. Physical S23 Ultra validation is required for Samsung Gallery picker/save behavior, Qualcomm hardware codecs, portrait/HDR/HEVC sources, thermal/battery behavior, and accuracy on representative real exercise videos. See [device checklist](docs/DEVICE_TESTING.md).

The model and transitive libraries are distributed under their respective SDK/licenses. ML Kit pose detection is a beta API. Dependency versions are pinned in Gradle.
