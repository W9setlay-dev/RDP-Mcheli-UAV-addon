# Machine-vision test report

## Automated checks

- **PASS**: fresh focused compile/tests with `VisionObservationStatusTest` and
  `VisionProcessingWorkerTest`, using `--rerun-tasks`.
- **PASS**: fresh full `.\gradlew.bat build --no-daemon --rerun-tasks`; all
  11 tasks completed, including the full test suite, `check`, and reobfuscated
  JAR packaging.
- **PASS**: 355 JUnit tests; 0 failures, 0 errors, and 0 skipped.
- **PASS**: `git diff --check` after final source changes.
- **Artifact**: base revision `0138281eeb420234015563fb383eea1cd991d5c1`,
  `build/libs/rdp-uav-addon-0.1.0.jar`, SHA-256
  `2f6f1fb5e82f1a370e17c3e0317bba9aea834f063a4d635a8b0be5a2857fecd2`.

## Runtime checks

**NOT VERIFIED.** No reviewed runtime artifact demonstrates the full sequence
of feed activation, pixel readback, result publication, and overlay rendering.
F7 mode-selection chat output is not evidence of those pipeline stages.

The available crash report records a McHeli `VehicleRenderHook` linkage error
(`Minecraft.func_71410_x` missing); it does not identify a failure within the
vision call path. See `MACHINE_VISION_AUDIT.md`.

The crash report was inspected but not reproduced in this change. A new
`runClient` launch and live Minecraft operation were not performed; physical
client operation remains with the human operator.

## Manual acceptance procedure

1. Build the checkout and record the Git revision, produced jar SHA-256, and
   Forge/McHeli/ModularUI versions.
2. Launch Minecraft 1.12.2 with Forge 14.23.5.2859 and McHeli CE 1.5.1.
   Set `perception.enabled=true` and `perception.overlayEnabled=true` in
   `config/rdp-uav-vision.cfg`; enable RDP-UAV debug logging.
3. Open the UAV station feed and select Diagnostics with F7. Keep a magenta
   beacon beam visible in the camera view for at least 30 seconds.
4. Capture `latest.log` and a screenshot. Verify, for the same abbreviated
   session ID, session start, `FRAME_CAPTURE_ISSUED`, `RESULT_PUBLISHED`, and
   `OVERLAY_RENDERED` events with advancing sequence numbers. Confirm the HUD
   reports the same stage numbers and the mission monitor labels its status
   context-only.
5. Close the feed and wait beyond the feed timeout. Verify a stop event,
   cleared/disabled processing status, and no stale Assist result. Record any
   `UNAVAILABLE`/`DEGRADED` reason, GL error, crash, and frame-time/FPS change.

Do not mark manual runtime validation complete until those artifacts are
reviewed.
