# Machine vision and operator presentation

## Authority and limits

The server owns mission identity, target UUID, target validity, contact
observations, and confidence. The client uses McHeli's active render camera to
request an exact target and renders only a projection of server-authoritative
mission data. Rendering never confirms or mutates a lock.

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

## Not implemented / not claimed

- pixel-level image processing or automatic visual classification
- access to McHeli's rendered camera-feed pixels
- a server-authoritative thermal detector or camera gimbal
- client-generated confidence, target identity, or mission authority
- a screen-space marker that can ignore world occlusion

Adding actual image processing requires a verified image-buffer contract and
bounded client-side processing path. McHeli's camera feed widget and thermal
display mode alone do not establish either.

## Validation status

- API signatures: camera/view methods and feed widget verified in the local
  McHeli CE 1.5.1 jar.
- Source behavior: camera-ray target selection, server validation, and
  world-space marker/bracket rendering are implemented in the addon.
- Automated tests: source compilation and existing mission/target tests pass;
  GL output is not exercised by unit tests.
- Runtime behavior: not attempted in a matching Forge client/server session.
