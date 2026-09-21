# Guided Missile Lifecycle and Development Guide

This document is the implementation reference for the RDP UAV guided-weapon
path. It describes the complete flow from a client action to native McHeli
projectile flight, the ownership of each subsystem, the important failure
states, and the safe places to modify the code.

The guide applies to the current target environment:

* Minecraft 1.12.2
* Forge 14.23.x
* McHeli CE 1.5.1
* WingMan supplied by McHeli CE
* Java 8

## 1. Design boundary

The addon is a mission and target-orchestration layer. McHeli remains
authoritative for projectile creation and physics.

| Responsibility | Owner |
|---|---|
| GUI controls and key bindings | `client/GuiMissionControl`, `client/RdpUavClientKeys` |
| Packet transport | `network/RdpUavNetwork` |
| Permission, ownership, and packet validation | Server packet handlers |
| Mission persistence and lifecycle | `mission/*`, `MissionLifecycle` |
| Weapon capability and release gates | `weapon/GuidedWeaponRegistry`, `GuidedWeaponAdapter` |
| Native weapon launch | `MCH_WeaponSet.use(MCH_WeaponParam)` or WingMan |
| Projectile motion, steering physics, gravity, collision, fuse | McHeli CE |
| Addon target updates for coordinate missiles | `MissileGuidanceController` |
| Per-projectile addon state and diagnostics | `ProjectileGuidanceMission` |

Do not implement a second missile physics engine in the addon. In particular,
do not directly write `motionX`, `motionY`, `motionZ`, gravity, acceleration,
collision, or fuse fields to make a missile “track.” The supported integration
is to validate the mission and write the native target fields exposed by
McHeli.

## 2. End-to-end lifecycle

```text
Client GUI/key/command
    |
    v
RdpUavNetwork packet
    |
    v
Server handler validates player, aircraft, mission, target, and ownership
    |
    +--> Mission path: MissionLifecycle.dispatch/updateActive
    |       |
    |       +--> GuidedWeaponAdapter.arm(...)
    |
    +--> Direct guided path: GuidedWeaponAdapter.arm(...)
    |
    v
Capability lookup and release safety gates
    |
    v
Native McHeli/WingMan launch
    |
    v
MissileGuidanceController discovers the loaded projectile
    |
    v
Target coordinates/entity are refreshed each server world tick
    |
    v
McHeli native projectile update consumes the target
    |
    v
Impact, target loss, abort, timeout, or projectile unload cleanup
```

### 2.1 Client request

The client never creates or steers a projectile. It sends an intent packet:

* `RdpUavNetwork.createMission(...)` sends `CreateMission`.
* `RdpUavNetwork.createSquareStrike(...)` sends `SquareStrike`.
* `RdpUavNetwork.authorizeAttack(...)` sends `AuthorizeAttack`.
* `RdpUavNetwork.lockAiTarget(...)` sends `AiTargetLock`.
* `RdpUavNetwork.adoptLaunchedProjectile()` sends `AdoptProjectile`.
* `RdpUavNetwork.reassignGuidedMission(...)` sends `ReassignGuided`.

Packet definitions and handlers are in
`src/main/java/net/vas/rdp/uav/network/RdpUavNetwork.java`. Search for the
corresponding `IMessage` class and `Server...Handler` class before changing a
request.

Client values are untrusted. A packet may contain a mission ID, weapon name,
target UUID, square coordinates, or swarm ID, but the server must resolve and
validate every value again.

### 2.2 Server request validation

The server handler resolves the player-controlled aircraft and checks
authorization before changing mission state or firing:

1. The sender is a valid server-side player.
2. The player controls or is authorized to use the aircraft.
3. The aircraft and target belong to the active dimension.
4. The mission exists and is owned by the sender.
5. The objective is authorized and in `AUTHORIZED` or `TRACKING` state.
6. The requested weapon exists on the aircraft and has a registered capability.
7. Ammunition and aircraft readiness are valid.

The attack packet path is in
`RdpUavNetwork.ServerAuthorizeAttackHandler` around the call to
`GUIDED_ADAPTER.arm(...)`. Square strike handling is in
`ServerSquareStrikeHandler`. Mission-created attacks enter through
`MissionLifecycle.dispatch(...)`.

### 2.3 Mission objective construction

`GuidedStrikeObjective` is the immutable identity and mutable tracking
contract for one strike:

* `ENTITY` objectives retain a target UUID and refresh its coordinates.
* `SQUARE` objectives retain a world coordinate and do not require an entity.
* `authorize()` moves a created objective into the release-eligible state.
* `beginTracking()` enables live contact tracking.
* `loseContact()` marks an entity contact unavailable.
* `switchContact(...)` changes the selected entity without replacing the
  objective UUID.

Do not use a new UUID for every contact update. The objective UUID identifies
the mission decision; the contact UUID identifies the current entity.

### 2.4 Release gate and native launch

`GuidedWeaponAdapter.arm(...)` is the release boundary. Its order is
intentional:

1. Resolve the selected weapon from `MCH_EntityAircraft.getWeapons()`.
2. Call `validate(...)` for objective state, capability, speed, and ammo.
3. Resolve the capability using definition name and native McHeli type.
4. Record `compatibility_evaluated` diagnostics.
5. Reject unsupported native guidance behavior.
6. Compute McHeli chunk capability and unloaded-target policy.
7. Run `GuidedSafetyPolicy.evaluate(...)`.
8. Analyze terrain and line of sight when required.
9. Launch through the native path.

Entity seekers use `fireNativeEntitySeeker(...)`:

* Build `MCH_WeaponParam`.
* Set `MCH_WeaponGuidanceSystem.setTarget(target)`.
* Call `MCH_WeaponSet.use(parameter)`.
* Clear the native target if the shot is rejected.

Coordinate weapons use either:

* `WingManFlightAdapter.armAttack(...)` for an autonomous UAV when
  `armDirect` is false; or
* `fireNativeCoordinateWeapon(...)` for a direct native release.

The addon does not instantiate `MCH_EntityTvMissile` or
`MCH_EntityASMissile` itself. McHeli creates the projectile.

Successful release emits `native_launch_accepted`, then authorizes the
aircraft in `MissileGuidanceController`. This authorization is the bridge
between the aircraft-level release and projectile-level tracking.

### 2.5 Projectile discovery and association

`MissileGuidanceController.tick(World)` runs at the end of every server world
tick from `RDPUavMod.ServerTickHandler`.

For each loaded `MCH_EntityBaseBullet`, it:

1. Finds `shootingAircraft`, falling back to `shootingEntity`.
2. Looks up the aircraft assignment.
3. Checks that the assignment is still in `AI_GUIDANCE` mode.
4. Matches the configured weapon to the projectile's McHeli definition.
5. Creates a `ProjectileGuidanceMission` keyed by projectile UUID.
6. Updates its lifecycle state to `NATIVE_GUIDANCE_ACTIVE`.
7. Applies the objective target.

The maps in `MissileGuidanceController` are runtime state only. They are
cleaned when a projectile UUID is no longer present in the loaded entity list.
They are deliberately not persistent world data.

`adopt(World, aircraftId, projectileId, objective)` is the recovery path for a
projectile launched before the addon was authorized. It searches loaded
projectiles, verifies the shooter, and creates an assignment.

### 2.6 Per-tick target application

`MissileGuidanceController.apply(...)` determines the current target:

* For `ENTITY`, it resolves the contact UUID from the loaded world and writes
  the entity's current position into the objective.
* If the contact is absent, it records `TARGET_LOST` and does not write a
  stale coordinate.
* For `SQUARE`, it uses the stored objective coordinate.

It then calls `chooseAimPoint(...)`.

For TV and AS coordinate projectiles:

1. Existing avoidance plans are reused while valid.
2. The direct route is checked.
3. Bounded detour candidates are evaluated.
4. The selected point is passed through `normalizeNativeTarget(...)`.
5. AS correction limits are applied by `applyCoordinatePolicy(...)`.
6. Native target fields are written.

The native fields are:

```java
MCH_EntityTvMissile.targetPosX
MCH_EntityTvMissile.targetPosY
MCH_EntityTvMissile.targetPosZ

MCH_EntityASMissile.targetPosX
MCH_EntityASMissile.targetPosY
MCH_EntityASMissile.targetPosZ
```

These writes are the only steering input owned by this addon. McHeli reads
them in its own projectile update code.

If no safe detour exists, `chooseAimPoint(...)` now emits
`native_guidance_fallback` and returns the requested point. It must not return
`null` merely because addon avoidance failed: that would skip the native target
write and cause a TV missile to continue on its launch vector.

### 2.7 Native McHeli update

McHeli owns the actual projectile tick. In McHeli CE 1.5.1:

* `MCH_EntityTvMissile.onUpdateMotion()` chooses wire or laser guidance and
  eventually consumes the native target point.
* `MCH_EntityTvMissile.onLaserGuide()` checks the target `BlockPos` and steers
  when the native target resolves to a movement-blocking block.
* `MCH_EntityASMissile` consumes its target fields using its native ballistic
  behavior.
* `MCH_EntityATMissile` and `MCH_EntityAAMissile` remain native seeker/entity
  families; the addon must not pretend coordinate fields exist for them.

When a TV target is an air coordinate, `normalizeNativeTarget(...)` searches
downward for a loaded solid block. This adapts the addon objective point to
McHeli's target semantics without changing projectile motion.

### 2.8 Terminal and recovery behavior

`ProjectileGuidanceMission` records addon-observed state:

```text
ASSIGNED
  -> NATIVE_GUIDANCE_ACTIVE
  -> AVOIDANCE_ACTIVE
  -> TARGET_LOST
  -> OBSTACLE_UNAVAILABLE
  -> IMPACTED
  -> ABORTED
  -> TIMED_OUT
```

`IMPACTED`, `ABORTED`, and `TIMED_OUT` are terminal and cannot be reactivated.
The projectile state is separate from the carrier aircraft mission state.
Aircraft recovery, return-to-base, and mission persistence are handled by
`MissionLifecycle`, not by `ProjectileGuidanceMission`.

## 3. Failure diagnostics

Use the first relevant event in the log, not only the final symptom.

| Log event/reason | Meaning | Inspect |
|---|---|---|
| `compatibility_rejected` | Weapon/objective matrix mismatch | `GuidedWeaponRegistry`, config |
| `validation_rejected` | No weapon or unsupported guidance | `GuidedWeaponAdapter.validate` |
| `compatibility_evaluated` | Resolved definition/type and chunk facts | live weapon config and `MCH_WeaponInfo` |
| `safety_rejected` + `GUIDED_HOLD_TERRAIN` | Loaded target, but corridor/altitude safety failed | `GuidedTerrainAnalyzer` |
| `GUIDED_WAIT_TARGET_CHUNK` | Target region is not loaded and execution is not allowed | `GuidedChunkCapability`, weapon config |
| `native_launch_rejected` | McHeli/WingMan refused the actual fire call | `GuidedWeaponAdapter`, native weapon state |
| `projectile_state ... TARGET_LOST` | Entity UUID is not currently loaded/alive | target resolver and world dimension |
| `projectile_state ... OBSTACLE_UNAVAILABLE` | Legacy/explicit obstacle terminal state | `MissileGuidanceController.chooseAimPoint` |
| `native_guidance_fallback` | No addon detour was safe; native target was still submitted | native McHeli behavior and collision |
| `native_target_normalized` | Air target was converted to a solid block below | target Y and loaded terrain |
| `native_target_update` | Addon wrote a coordinate target | projectile class and weapon mapping |

For the known straight-line failure, the required chain is:

```text
native_launch_accepted
  -> projectile_state ... NATIVE_GUIDANCE_ACTIVE
  -> native_target_update
```

If `native_target_update` is absent, the addon did not hand off a target.
If it is present but the projectile remains straight, inspect McHeli's native
weapon definition, `laserGuidance`, `turningFactor`, acceleration, target
block validity, and whether the projectile is being updated server-side.

## 4. How to modify the system safely

### Add or change a weapon capability

1. Add or change the entry in
   `config/rdp-uav-guided-weapons.cfg.example`.
2. Update the corresponding built-in default in
   `GuidedWeaponRegistry.register(...)` if the change is intended to be the
   default.
3. Confirm the live config in the Minecraft instance.
4. Add a capability test in `GuidedWeaponTest`.
5. Verify resolved `definition`, `type`, `behavior`, and objective set in the
   `compatibility_evaluated` event.

Do not infer capability from a display name. McHeli definition names and
`MCH_WeaponInfo.type` are the authoritative identifiers.

### Change objective or target behavior

Modify `GuidedStrikeObjective` and target selection classes first. Keep UUID
identity, dimension, and objective state rules intact. Do not put entity
search logic in the client GUI.

### Change release validation

Modify `GuidedWeaponAdapter.validate(...)` or
`GuidedSafetyPolicy.evaluate(...)`. Keep these gates before any native
`weapon.use(...)` call. Add a test for every new rejection reason and ensure
the reason is visible in `RdpUavDebug`.

### Change TV/AS steering handoff

Modify only `MissileGuidanceController`:

* `apply(...)` selects the current objective point.
* `chooseAimPoint(...)` performs optional addon avoidance.
* `normalizeNativeTarget(...)` adapts coordinates to McHeli target semantics.
* `applyCoordinatePolicy(...)` smooths AS coordinate updates.
* The `MCH_EntityTvMissile` and `MCH_EntityASMissile` assignments write the
  native target fields.

Do not change projectile motion to compensate for a missing target handoff.
First prove that `native_target_update` is emitted and that the target point
is valid in the loaded world.

### Add a new projectile family

1. Inspect the actual McHeli CE 1.5.1 class with `javap` or decompiled source.
2. Confirm how the class receives targets and whether it exposes a public API.
3. Add a `GuidedNativeBehavior` only if the native behavior is understood.
4. Add registry/config capability data.
5. Add matching logic in `MissileGuidanceController.matches(...)`.
6. Add a behavior branch in `behaviorFor(...)`.
7. Add release and lifecycle tests.
8. Add runtime log evidence before enabling it by default.

If a class has no supported target handoff, mark it
`ENTITY_SEEKER`/native-only or `UNSUPPORTED`; do not write guessed fields.

### Change mission recovery

Modify `MissionLifecycle`, `MissionManager`, or `RDPMission` rather than
projectile physics. Preserve persisted mission state transitions and retry
deferral. A recoverable safety failure should usually defer or replan, not
silently fail or fire repeatedly every tick.

## 5. Validation workflow

Run the smallest relevant checks first:

```text
./gradlew test
./gradlew build -x test
git diff --check
```

For a TV/AS runtime test, capture:

1. `compatibility_evaluated`
2. `native_launch_accepted`
3. `native_target_update`
4. Projectile position over multiple server ticks
5. `projectile_state` terminal event

Test at least:

* TV entity target, loaded and moving.
* TV square target with a solid block below the coordinate.
* AS square target from altitude.
* Target lost after release.
* Blocked corridor where `native_guidance_fallback` must preserve steering.
* Unloaded target with both allowed and disallowed chunk execution.
* Abort/reassign/adopt paths.

## 6. Source map

| Area | Primary file |
|---|---|
| Forge setup and server tick order | `src/main/java/net/vas/rdp/uav/RDPUavMod.java` |
| Client packet send methods and server handlers | `src/main/java/net/vas/rdp/uav/network/RdpUavNetwork.java` |
| Mission dispatch and recovery | `src/main/java/net/vas/rdp/uav/mission/MissionLifecycle.java` |
| Native release boundary | `src/main/java/net/vas/rdp/uav/weapon/GuidedWeaponAdapter.java` |
| Capability matrix | `src/main/java/net/vas/rdp/uav/weapon/GuidedWeaponRegistry.java` |
| Objective identity/state | `src/main/java/net/vas/rdp/uav/weapon/GuidedStrikeObjective.java` |
| Terrain and LOS gates | `GuidedSafetyPolicy.java`, `GuidedTerrainAnalyzer.java` |
| Projectile association and target writes | `MissileGuidanceController.java` |
| Per-projectile state | `ProjectileGuidanceMission.java`, `ProjectileGuidanceState.java` |
| Unit contracts | `src/test/java/net/vas/rdp/uav/GuidedWeaponTest.java` |
| Runtime capability configuration | `config/rdp-uav-guided-weapons.cfg.example` |

When debugging, start at the first missing lifecycle event and follow this
map forward. Do not start by changing McHeli physics unless the addon has
already proven that the native target handoff is correct.
