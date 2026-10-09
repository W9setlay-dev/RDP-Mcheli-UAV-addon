# Machine vision and operator presentation

## Authority and limits

The server owns mission identity, target UUID, target validity, contact
observations, and confidence. The client uses McHeli's active render camera to
request an exact target and renders only a projection of server-authoritative
mission data. Rendering never confirms or mutates a lock. The new pixel
perception path is client-local diagnostic output; it does not send detections
to the server or affect mission/target authority.

McHeli CE 1.5.1 exposes camera viewing, night-vision/thermal-vision modes, and a
UAV camera-feed widget. The inspected public API does not expose raw camera
pixels or an authoritative server sensor result. The addon's `FLIR` option is
therefore a presentation style only; it does not claim to detect heat or
perform pixel-level image recognition.

## Implemented presentation

- `ClientAimRayTracer` traces from `Minecraft.getRenderViewEntity()`, so an
  operator in a McHeli view uses the active render camera rather than a stale
  cached crosshair entity. It traces blocks first and accepts only an exact
  entity AABB intersection before the obstruction.
- The client sends the selected entity UUID as intent. The server resolves
  that UUID in the operator's current world and performs ownership, profile,
  policy, range, and line-of-sight checks before mutating the mission.
- `RdpUavTargetHud` draws entity brackets from the loaded entity's actual
  interpolated AABB in `RenderWorldLastEvent`. Point targets use a 3D,
  world-anchored cross only when the mission marker is visible and the camera
  has an unobstructed view.
- HUD telemetry is display-only. The server remains authoritative for target
  state, mission state, contact metadata, and marker visibility.

No flat screen-space square or canned reticle is used for target position.
The world renderer supplies the camera projection for the world-space lines.

## Server observations and contacts

`TargetLock` and `ContactTracker` are mission/runtime models, not camera image
processors. Contact bounds, positions, motion, confidence, visibility, and
lifecycle come from server-side entity observations. A committed mission
target remains the UUID in `RDPMission.targetId`; a lost target is reacquired
by that UUID only, not by selecting a nearby candidate.

The mission NBT codec persists the target UUID. Runtime lock observations and
contact history are not replayed as current evidence after a restart; the
server must resolve and observe the same UUID again.

## Client pixel-perception experiment

When the client-side `perception.enabled` option in `rdp-uav-vision.cfg` is
enabled, `VisionClientEvents` listens for `RenderWorldLastEvent` only while
McHeli's public `WidgetUavCameraFeed.RENDERING_FEED` flag is set. It reads the
currently bound feed framebuffer without reflection, blits a downscaled image
into a small addon-owned framebuffer, and issues asynchronous RGBA readback
through a three-slot pixel-buffer-object ring with nonblocking sync polling.
The render hook restores framebuffer, pixel-pack-buffer, and pack-layout state.
Frames are processed by one daemon worker with a one-frame latest-value queue
and a three-array bounded pixel pool. World unload, feed timeout, and config
disable clear the active source and schedule/perform GL resource cleanup on the
client thread. Capture and the diagnostic overlay default to disabled.

The current detector is deliberately narrow: it scores tall saturated magenta
pixel columns as `minecraft:beacon_beam` candidates. Its score is a feature
match score, not a probability or semantic classification. `PerceptionTracker`
associates detections in normalized image space and publishes tentative,
confirmed, stale, and lost visual tracks. These tracks have no entity UUID,
world position, or authority outside this client pipeline.

The optional overlay reports state, track count, capture rate, render-thread
readback submission P95, frame age, and dropped-frame totals. That P95 measures
CPU time to issue the GPU blit/readback/fence commands; it is not GPU completion
latency or an end-to-end benchmark. The overlay is drawn after capture and
therefore is not part of detector input.

Failure states are exposed as `UNAVAILABLE` or `DEGRADED` with a reason for
unsupported GL capabilities, an absent feed framebuffer, framebuffer setup,
fence, mapping, or worker-processing failures. No client-generated detection
changes server state.

## Not implemented / not claimed

- general-purpose pixel-level object recognition or semantic classification
- a server-authoritative thermal detector or camera gimbal
- client-generated confidence, target identity, or mission authority
- a screen-space marker that can ignore world occlusion

The feed framebuffer binding is inferred from the verified McHeli widget
render path and the matching Forge `RenderWorldLastEvent` call order; it still
requires an in-game runtime capture to establish that this hook observes the
correct FBO on the supported client configuration.

## Manual runtime validation procedure

1. Build the current checkout with `.\gradlew.bat build`; record the Git commit
   and resulting jar SHA-256 as the build identifier.
2. In a matching Minecraft 1.12.2 / Forge 14.23.5.2859 client with McHeliCE
   1.5.1, set `perception.enabled=true` and `perception.overlayEnabled=true`
   in `config/rdp-uav-vision.cfg`. Use a beacon with a magenta/purple stained
   glass tint visible in the UAV feed; keep the diagnostic overlay outside the
   feed widget.
3. Open the McHeli UAV station and display the feed for at least 30 seconds.
   Confirm the overlay reaches `OPERATIONAL`, image frames advance, and a
   sufficiently tall magenta beam appears as a track. Close the feed and
   verify the state returns to `DISABLED`/waiting after the feed timeout.
4. Capture `latest.log`, the config file, an overlay screenshot, the build
   commit/hash, and the observed FPS/frame-time before and during capture.
   Report any `UNAVAILABLE`/`DEGRADED` reason, GL error, client crash, or
   sustained frame-time change. Do not report successful runtime validation
   until those artifacts are reviewed.

## Validation status

- API signatures: camera/view methods and feed widget verified in the local
  McHeli CE 1.5.1 jar.
- Client capture, detector, tracking, lifecycle, and diagnostics: compiled;
  synthetic-pixel tests cover detection/tracking; worker tests cover queue
  replacement/drop counts, camera-source reset, and explicit failure state.
- Source behavior: camera-ray target selection, server validation, and
  world-space marker/bracket rendering are implemented in the addon.
- Runtime behavior: **not verified**; no matching Minecraft client capture,
  frame-time, accuracy, precision/recall, or GL-state evidence is available.
- Accuracy against real UAV-feed imagery: **not measured**. Synthetic pixel
  fixtures establish only deterministic algorithm behavior.
