# Physical device acceptance checks

Target: Samsung Galaxy S23 Ultra SM-S918B, Android 16, ARM64. These checks require the physical device; CI emulator success does not verify Qualcomm hardware or human-pose accuracy.

- Install the debug APK and launch in airplane mode **before first use**. Select a local MP4 through the system picker. Confirm the pose model runs with no downloads.
- Record ten clearly visible full push-ups from front, oblique, rear and side views with a static camera. Compare count to manual annotation and phase times to frame timestamps (algorithm resolution 100 ms; model error may be larger). Repeat with different clothing colors, slow reps and short pauses.
- Try portrait, landscape, rotated metadata, variable frame rate, HEVC and HDR camera footage. Confirm the output is upright, fitted without stretching, approximately source duration, with readable synchronized overlays/graphs. Export intentionally converts to SDR H.264 at 30 fps.
- Verify AAC sound remains synchronized near both start and end. Non-AAC sources must show the omission warning.
- Find the playable MP4 in Samsung Gallery under Movies/PushupAnalyzer. Verify opening it through the app, seeking and sharing.
- Background the activity, rotate the phone and turn the screen off during processing; return to the same progress. Allow notifications to see progress/cancellation outside the app. Denying notification permission must not prevent foreground processing.
- Cancel during analysis, encoding and gallery copy. No incomplete visible gallery file should remain. Run another job afterward.
- Try no person, occlusion, partial reps, multiple people, poor lighting and a moving camera. Confirm the coverage error/warning is understandable; manually assess false positives/undercounts.
- Try a 10-minute input and low storage. Measure time, peak memory, battery and heat. Inputs over 10 minutes must be rejected explicitly.
- Force-stop/reboot during a job. Reopen and select again; jobs are not resumed automatically. Check no partial gallery output is visible.
- Check device page size using `adb shell getconf PAGE_SIZE`; verify native model loading if the device uses 16 KB pages.

Record source codec, dimensions, frame rate, duration, manual count, measured phase times, output count/times, handset firmware, elapsed processing time and observed failures. Never claim accuracy from the generated color-bar test alone.
