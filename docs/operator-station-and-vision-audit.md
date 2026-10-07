# Operator station and vision audit

## Current limitation list

### 1. Mission target identity is persisted; live observation history is not

`RDPMission.targetId` is the committed entity UUID and is persisted by
`MissionNbtCodec`. `TargetLock` holds runtime identity, dimension, motion,
confidence, and validation data; `ContactTracker` holds runtime lifecycle
snapshots. Those observation histories are not restored as fresh evidence.
After reload the server must resolve the same UUID and observe that entity
again; it must not choose a nearby replacement.

### 2. Camera-aware target presentation exists, but it is not pixel-level vision

`ClientAimRayTracer` traces from `Minecraft.getRenderViewEntity()` and checks
blocks before entity bounding boxes. `RdpUavTargetHud` renders world-space lines
around a synchronized entity's actual interpolated AABB, and now renders point
markers as world-space 3D crosses. It does not draw the target lock as a
screen-space square.

These are camera-aware selection and presentation features, not image
classification. `GuiMissionControl` remains the mission/telemetry dashboard;
the target overlay is implemented in the separate client HUD renderer.

### 3. McHeli exposes camera viewing, not a verified pixel-sensor contract

The installed McHeli CE 1.5.1 jar exposes camera state, camera positions,
normal/night-vision/thermal-vision modes, and a UAV camera-feed widget.
However, the inspected public surface does not provide raw camera pixels or a
server-authoritative detection API. Its camera-position selection is
client-dependent. The addon's FLIR styling does not switch native thermal
vision or create observations.

### 4. Movement lease release must distinguish terminal missions from active route states

Evidence from the code shows that the native control path owns a single flight-control boundary:
- `NativeAircraftControlAdapter.apply(...)` checks the active mission ownership and `MissionMovementAuthority.claimControl(...)` before writing setpoints.
- `MissionLifecycle` owns dispatch and mission state transitions.
- `MissionMovementAuthority.suppressOperatorFlightInput(...)` filters native pilot axes only while `nativeWingManRequired(mission)` is true.

Terminal states map to `ControlOwner.SAFETY`, so addon movement is no longer required and pilot flight axes are no longer filtered. Active states such as `HOLDING` still map to `ControlOwner.RECOVERY` and must retain the mission lease. `DIRECT_LAUNCH_COMPLETE` is a legacy, nonterminal observation state and also remains under mission control. Releasing leases at those active states would create competing or missing movement writers rather than restore operator control.

### 5. Vehicle profiles are explicit and profile-specific

`UavVehicleProfiles` defines a clear mapping between McHeli definitions and `UavExecutionProfile` values such as:
- `ONE_WAY`
- `HELICOPTER_ONE_WAY_FPV`
- `REUSABLE_UAV`
- `LONG_RANGE_REUSABLE_UAV`

The profile class stores execution altitude, lock range, detection range, attack envelope, holding behavior, and yaw/pitch correction limits. This is much more specific than a universal UAV flag and is exactly the right place to encode capability differences.

## Root cause and authority boundary summary

| Limitation | Root cause in code | Authority boundary | Minimal correct fix |
|---|---|---|---|
| No pixel-level image detection | native public camera API does not expose image pixels or server detection results | client presentation / server contact | do not claim detection until a supported image-buffer contract exists |
| Contact history is runtime-only | `ContactTracker` observations are not persisted as fresh evidence | server-authoritative mission state | reacquire only the committed UUID after reload |
| Malformed empty lock request could clear a mission | server treated a missing UUID as an implicit clear | server packet boundary | require the explicit clear flag; reject empty non-clear requests |
| Crosshair lock could use a stale cached target | GUI/key paths read `objectMouseOver` or an old captured UUID | client intent, server validates | trace the active render-view camera and send only the current exact hit UUID |
| Suggestion assignment could substitute another same-type entity | selected mission suggestions sent a registry ID resolved to the first loaded matching entity | server packet boundary | send and resolve the selected entity UUID; apply the same policy, range, and bounds sightline checks |
| Lock acceptance lacked a server block sightline test | target checks covered identity, policy, range, and profile but not obstruction | server packet boundary | reject when the aircraft-to-target bounds ray is blocked |
| Operator station stuck in AI control | terminal transitions did not guarantee release of the mission-owned movement lease | `RDPMission` + `MissionMovementAuthority` | release the matching lease only on terminal transitions; active route states retain it |

## Validation path

- Source validation: installed camera APIs, lock request checks, world-space rendering, terminal lease release, and the pilot-axis suppression gate are verified in source/jar.
- Automated validation: mission lease and malformed lock request contracts are covered by focused tests.
- Runtime validation: blocked, because the exact Forge client/server environment was not launched in this session.
- Final result: source/API and unit checks do not prove camera-feed projection, GL output, sightline decisions, packet timing, or in-game pilot-input restoration.
