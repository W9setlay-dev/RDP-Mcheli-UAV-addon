# Machine-vision audit

## Scope

This audit covers only the client-local McHeli UAV-feed pixel pipeline and its
read-only presentation beside mission telemetry. Mission dispatch, movement
authority, control leases, and mission-state mutation are outside scope.

## Verified baseline

- `build.gradle` targets Minecraft 1.12.2, Forge 14.23.5.2859, Java 8,
  McHeli CE 1.5.1, and ModularUI 3.0.5.
- The feed render hook is entered only while
  `WidgetUavCameraFeed.RENDERING_FEED` is true and local perception is enabled.
- The McHeli CE 1.5.1 widget render path was inspected in its matching jar:
  it binds a display-sized feed framebuffer during the camera render and
  restores Minecraft's framebuffer after rendering.
- `AsyncPixelCapture` downsamples that bound read framebuffer and performs
  asynchronous RGBA PBO readback. A bounded worker queue processes owned pixel
  buffers, then publishes immutable detections/tracks.
- Assist rendering is attached to ModularUI post-render and locates the active
  feed widget. Diagnostics render at the global HUD `ALL` event, not inside the
  feed widget.
- Capture and diagnostics default to disabled. F7 changes presentation mode;
  it does not enable capture or diagnostics.
- The detector is a narrow magenta-beam feature matcher. Its score is not a
  probability, target identity, or general-purpose object classification.

## Evidence and diagnosis

The supplied F7 mode messages prove only that the presentation mode changed.
They do not prove that the feed render callback fired, pixels were captured,
the worker published a result, or an overlay was drawn. The repository-local
`run/logs/latest.log` and `debug.log` inspected for this task contain no
vision-stage event sequence. Therefore the runtime cause of the reported
missing presentation remains **unknown**; it is not attributed to framebuffer
binding or ModularUI ordering without a matching live trace.

The available crash report identifies a separate client-launch failure in
`com.norwood.mcheli.core.VehicleRenderHook.<clinit>`:
`NoSuchMethodError: Minecraft.func_71410_x()`. That stack does not pass through
the vision subsystem and does not establish a vision defect. A later local
startup log shows Java 8 and mod initialization, but not a successful vision
capture/presentation scenario.

The original diagnostics showed pipeline state, tracks, capture rate/P95,
frame age, and drops, but did not correlate capture, processing, and overlay
stages by a local observation-session identifier and frame sequence. Mission
telemetry is a separate server snapshot; the client had no observation-status
association in the mission monitor.

## Changes in this audit

- Add a bounded, local-only observation-session status with captured,
  processed, and presented sequence numbers.
- Emit debug-gated lifecycle events for session start/stop, capture, result
  publication, and overlay presentation. Capture/result/presentation events
  are sampled to the first event and then each 25th sequence; failures and
  session boundaries are retained.
- Display the local status in the diagnostics overlay and mission monitor.
  The mission monitor explicitly identifies this as context only, not server
  telemetry or mission evidence.
- Add deterministic tests for the lifecycle-status transitions and rejection
  of results from a mismatched camera or an uncaptured sequence.

## Authority and limits

Observation status stays on the client. It is not included in packets, scan
reports, mission state, target locks, or server decisions. The mission monitor
shows the current local feed session next to the current mission telemetry
only as operator context; no causal or aircraft-to-feed correlation is
asserted by the server.

The detector's behavior on actual game imagery, correct framebuffer pixels,
render ordering on the user's configuration, overlay visibility, and runtime
performance still require live-client evidence.
