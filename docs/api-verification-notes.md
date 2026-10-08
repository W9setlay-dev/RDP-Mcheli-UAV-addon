# API verification notes

## `MCH_EntityAircraft`

- Source location: McHeliCE runtime classes available in `libs/mcheli-ce-1.5.1.jar`
- Verified signature: aircraft entity object with `isUAV()`, `isPilot(...)`, `getUniqueID()`, `getAcInfo()`, `getWeapons()`, and dimension metadata
- Constraints: server-side ownership and world validity still matter; the addon must validate before using a craft
- Decision: accepted as the authoritative aircraft identity source for mission assignment

## `WingmanEntry`

- Source location: WingMan classes bundled with McHeliCE runtime
- Verified signature: mission controller entry used for native WingMan mission nodes and state tracking
- Constraints: execution is runtime- and version-sensitive; do not treat the entry as a general-purpose arbitrary mission object
- Decision: kept behind `WingManFlightAdapter`

## `MissionState` and `RDPMission`

- Source location: project source under `src/main/java/net/vas/rdp/uav/mission`
- Verified signature: explicit state transitions, tick monotonicity, legal transition guards, queue flags, persistence metadata
- Constraints: all state mutation must pass through `RDPMission.transitionTo(...)`
- Decision: authoritative mission state must remain in the addon’s own domain model

## `TargetResolver`

- Source location: `src/main/java/net/vas/rdp/uav/target/TargetResolver.java`
- Verified signature: searches `world.loadedEntityList` by a radius box and optional UUID/registry ID
- Constraints: works only within currently loaded entities and server dimension
- Decision: usable for source-level and in-world target discovery, but not a replacement for actual threat classification or structure-model intelligence

## `TargetSelector`

- Source location: `src/main/java/net/vas/rdp/uav/target/TargetSelector.java`
- Verified signature: chooses best valid candidate by priority then distance
- Constraints: does not infer attackability beyond the candidate list supplied by the caller
- Decision: accepted as a deterministic selection layer

## `RdpUavNetwork`

- Source location: project source under `src/main/java/net/vas/rdp/uav/network`
- Verified signature: custom packet registration and server/client synchronization path
- Constraints: server-side validation remains required; packets do not grant authority by themselves
- Decision: accepted as the operator synchronization boundary

## `RdpUavDebug`

- Source location: `src/main/java/net/vas/rdp/uav/RdpUavDebug.java`
- Verified signature: reads configuration file and writes tagged debug entries to the log
- Constraints: intended only for diagnostics; leave disabled under normal operations
- Decision: accepted as a low-risk monitoring utility

## Unsupported or uncertain operations

- McHeliCE 1.5.1 exposes native `MCH_WeaponSet.use(...)`,
  `MCH_WeaponBase.getGuidanceSystem()`, and
  `MCH_WeaponGuidanceSystem.setTarget(Entity)`. Entity seekers use that
  public target-before-launch contract when the weapon's current native
  implementation exposes a guidance system. Coordinate/TV/laser weapons
  continue through native WingMan coordinate attack nodes. RDP never writes
  projectile motion or steers a spawned bullet itself; if the public
  guidance system is unavailable, the seeker decision is explicitly
  unsupported.
- McHeli CE 1.5.1 exposes camera-view state, camera-position metadata,
  night/thermal display modes, and a UAV camera-feed widget. The inspected
  public surface does not establish raw image pixels or a server-authoritative
  camera pose/detection contract. The addon uses Minecraft's active client
  render-view entity for aim requests, while the server sightline remains a
  conservative aircraft/target bounds ray. This is not proof of camera/HUD
  projection parity; see `mcheli-api-verification.md`.
- `MissionNode` has no speed field and `WingmanEntry` has no speed setter in
  McHeliCE 1.5.1. The addon flight computer derives a throttle setpoint from
  the configured profile and observed velocity; these profile envelopes are
  provisional and require per-aircraft live calibration.
- The bundled `libs/mcheli-ce-1.5.1.jar` was independently inspected with
  `javap`. It exposes public `MCH_EntityAircraft.setRotYaw(float)`,
  `setRotPitch(float)`, `setRotRoll(float)`, `setThrottle(double)`, and
  `setCurrentThrottle(double)`, and `updateControl()`. Source integration uses
  bounded, rate-limited setpoints through these methods before MCheliCE's normal
  entity physics tick. `RdpUavTransformer` suppresses the matching WingMan
  movement helpers under the RDP control lease. Bytecode signatures and
  regression tests establish method wiring and ownership guards; they do not
  establish the aircraft's observed trajectory or per-profile performance.

- exact structure-model destruction (for example specific parasite nests) is not yet a first-class mission type
- dynamic military or bandit role semantics are not yet implemented as a validated runtime feature
- direct hostile-vehicle targeting is still conceptual and should be implemented only after a world-facing validation step

## Test method decision

The project uses dependency-free Java tests to validate mission-state transitions, target selection, persistence, and queue logic. Real root-tier McHeli behavior still requires a live 1.12.2 Forge client/server runtime.

## Runtime smoke boundary

On 2026-09-24, `.\gradlew.bat runServer --no-daemon` reached the dedicated
server `Done` state with McHeliCE, WingMan, RDP Core, and the addon coremod
loaded. The run also reproduced an RDP Core world-data error:
`RDPWorldSavedData.<init>(String)` is missing during world load/save. The
server remained alive, but this is a runtime integration blocker for claiming
persistent mission execution. No aircraft was spawned and no flight,
payload, impact, or damage result was observed.

This is a historical runtime record, not evidence for the current checkout.
The saved 2026-09-27 client log fails during McHeliCE model initialization
before a world is opened; no current server-ready mission run is available.
The historical constructor report therefore remains unverified against a
successful current world-load run.

## Guided weapon boundary

`GuidedWeaponRegistry` is configuration-driven and keyed by the McHeli
weapon-set definition and `MCH_WeaponInfo.type`. The verified native mapping is
`aamissile`/`atmissile` entity seekers and `asmissile` coordinate/ballistic.
`atmissile` is entity-only until a native square-coordinate contract is
verified. The bundled McHeliCE 1.5.1 `MCH_EntityTvMissile` exposes
`onUpdateMotion()V`, `targetPosX/Y/Z`, and `onLaserGuide()V`. Bytecode inspection
shows its ordinary update derives aim from native wire or camera/laser control,
which previously overwrote the addon's end-of-world-tick coordinate update.
The addon now transforms the verified `onUpdateMotion` entry: an active server
AI assignment applies its authoritative target first and delegates steering
back to `onLaserGuide`; unassigned and player-guided missiles keep the native
path. This makes camera mouse aim non-authoritative for AI-assigned TV
projectiles without globally disabling player mouse input. `GuidedWeaponAdapter` validates ammunition,
objective form, speed policy, and server safety before dispatch. Entity seekers
resolve the locked UUID in the loaded server world, assign it through the
native guidance system, and invoke `MCH_WeaponSet.use(...)`; a missing lock is
rejected as `GUIDED_TARGET_LOST`. Unsupported definition/type/objective
combinations are rejected rather than presented as working autonomous
guidance. The adapter does not create, steer, teleport, or mutate projectiles.
The transformer regression test checks the exact class/method from the bundled
1.5.1 JAR. Physical in-game TV convergence and camera presentation remain
unverified until a controlled Forge world launch.
## Rework safety boundaries

The addon now keeps target filtering server-authoritative: vehicle-classified
contacts are not hostile candidates, and deeply ground-embedded contacts are
rejected according to `rdp-uav-targets.cfg`. The policy only rejects a
contact; it never teleports or otherwise mutates entities. One-way attack
missions terminate with `ONE_WAY_TARGET_MISSED` when their locked contact
disappears rather than retaining a native loiter route indefinitely.

## McHeli fixed-wing and VTOL API audit — 2026-10-03

Inspected the bundled `libs/mcheli-ce-1.5.1.jar` with Java 8 `javap`.
SHA-256: `740B4DF7C6AE8FF66C2E54ADD422AF4F4495EEEF45DDF5815AF7FDF7FF49015B`.

Verified signatures and type relationships:

- `MCH_EntityPlane` extends `MCH_EntityAircraft`.
- `MCH_EntityPlane` exposes `getMaxSpeed()`, `getPlaneInfo()`,
  `canSwitchVtol()`, `getVtolMode()`, and the native method spelled
  `swithVtolMode(boolean)`. It overrides the yaw/pitch/roll factor and update
  methods used by its native control path.
- `MCH_PlaneInfo` exposes `isEnableVtol`, `isDefaultVtol`, `vtolYaw`, and
  `vtolPitch`. The `canSwitchVtol` bytecode checks plane information and native
  transition preconditions. The inspected `swithVtolMode` bytecode shows
  server-side nozzle-part status handling. This establishes an API surface,
  not safe autonomous transition timing or successful hovering.
- `MCH_EntityAircraft` exposes `setRotYaw(float)`, `setRotPitch(float)`,
  `setRotRoll(float)`, `setThrottle(double)`, and `updateControl()`; the
  current addon adapter uses these generic controls.

Current addon mapping:

- `AirframeController.forProfile` selects `HelicopterFlightController` for a
  native `MCH_EntityHeli` or `HELICOPTER_ONE_WAY_FPV` profile. It selects
  `FixedWingFlightController` for `ONE_WAY`, `REUSABLE_UAV`, and
  `LONG_RANGE_REUSABLE_UAV` profiles.
- The current profile enum distinguishes mission/execution policies, not
  fixed-wing, VTOL, or rotary vehicle geometry. Controller selection does not
  inspect `MCH_PlaneInfo.isEnableVtol` or current `getVtolMode`. The addon
  adapter does not issue an explicit VTOL transition.
- Built-in profile aliases include `mq9`/`reaper`, `switchblade`, `shahed136`,
  `fpvdrone`, and `zala421`; those aliases do not establish that the matching
  runtime McHeli definitions are planes or whether VTOL is enabled.

Unknown: the exact aircraft definition files loaded with the gameplay
content, their VTOL configuration, and their observed transition/flight
behavior. The bundled jar inspection found the plane implementation classes,
not the deployment's vehicle definitions. Any VTOL controller policy requires
an explicit architecture choice and later live trajectory validation.
