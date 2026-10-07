# McHeli CE API verification

## Evidence and version

The checked-in `libs/mcheli-ce-1.5.1.jar` and `libs/rdpcore-1.0.0.jar` were
inspected directly. Public signatures below were checked with `javap` against
the McHeli CE 1.5.1 jar. The upstream source checkout was not available here,
so this note does not claim source-level inspection of McHeli internals.

The addon build currently configures Forge `1.12.2-14.23.5.2859` in
`build.gradle`. That is the source-build target; deployment instructions in
other project docs may describe a different Forge version.

## Aircraft control API

`com.norwood.mcheli.aircraft.MCH_EntityAircraft` exposes the integration
methods consumed by `NativeAircraftControlAdapter`:

- `setRotYaw(float)`, `setRotPitch(float)`, `setRotRoll(float)`
- `setThrottle(double)`, `getThrottle()`, and `updateControl()`
- `getAcInfo()`, `isUAV()`, aircraft identity and movement fields

The adapter writes server-side setpoints under the active mission lease and
does not set entity position or motion. This verifies that the referenced
methods exist in the installed jar, not that a live aircraft behaves correctly.

## Camera and UAV view APIs

The installed jar does expose camera/view functionality; the earlier
assumption that no such API exists was incorrect.

Verified public surface includes:

- `MCH_EntityAircraft.camera`, `getCameraMode(EntityPlayer)`,
  `switchCameraMode(...)`, `getCameraPosInfo()`, `setCameraId(int)`,
  `updateCamera(...)`, `updateCameraRotate(...)`, and `isCameraView(Entity)`
- `MCH_Camera` position and yaw/pitch fields, plus normal, night-vision, and
  thermal-vision mode constants and mode accessors
- aircraft camera-part metadata with `yawSync` / `pitchSync` and camera
  positions with `fixRot`, `yaw`, and `pitch`
- `WidgetUavCameraFeed`, the native UAV-station feed widget

These signatures establish native camera viewing and mode support. They do
not expose a public raw image/pixel buffer or a server-authoritative sensor
observation API. Bytecode inspection also shows that `MCH_EntityAircraft`
camera-position selection consults the client player, and `updateCamera(...)`
returns on the server side. Do not call these client camera paths as server
truth or treat the thermal-vision mode as a machine-detection result.

The addon follows Minecraft's active render view for operator aim via
`Minecraft.getRenderViewEntity()`. Its FLIR toggle changes bracket presentation
only; it does not switch McHeli's thermal mode or create sensor observations.

## UAV and WingMan APIs

The installed jar contains:

- `com.norwood.mcheli.uav.MCH_UavControl`
- `com.norwood.mcheli.uav.UAVTracker`
- `com.norwood.mcheli.wingman.handler.AutonomousFlightHandler`
- `com.norwood.mcheli.wingman.mission.MissionNode` and `MissionPlan`

Their public signatures are available from the installed jar. Presence alone
does not establish lifecycle semantics or authorize replacing the addon's
existing adapter/lease boundary. Any new calls must be checked for side,
thread, and tick-phase behavior before use.

## Remaining limits

- Matching upstream source revision and commit: not inspected.
- Full internal semantics of UAV camera feed and camera rotation: not
  source-verified.
- Raw camera image access suitable for pixel-level detection: not found in the
  inspected public signatures.
- Server-authoritative camera direction/position: not established by the
  inspected API; the observed camera-position selection is client-dependent.
- Forge client/server runtime behavior: not tested in this session.

## Validation status

- Installed McHeli CE 1.5.1 API signatures: verified with `javap`.
- Addon references to aircraft control methods: source-verified and compiled
  against the installed jar.
- Native camera/view APIs: present; not a validated pixel-sensor contract.
- Live camera, thermal mode, targeting, and mission behavior: not runtime
  verified.
