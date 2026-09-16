# RDP UAV Missions

## RDP hostile-world mission utility

This addon is intended to behave as a persistent autonomous support system for a dangerous, dynamic RDP world. In a hostile environment, the highest-value behaviors are not generic aircraft movement tricks but practical mission utility:

- persistent reconnaissance around bases, roads, depots, and anomaly zones
- hostile-target filtering for parasite, military, bandit, and modded mob classes
- route-based patrol and area coverage
- persistent threat monitoring and target reacquisition
- mission recovery after target loss, low fuel, or interruption
- multi-UAV coordination that avoids duplicate coverage and supports reassignment
- operator-visible reasoning before a dangerous action is committed

The architecture and audit notes for this rework are recorded in `docs/`:

- `docs/baseline-report.md`
- `docs/audit-report.md`
- `docs/current-architecture.md`
- `docs/implementation-source-matrix.md`
- `docs/proposed-architecture.md`
- `docs/dependency-diagram.md`
- `docs/api-verification-notes.md`
- `docs/runtime-test-matrix.md`
- `docs/bug-reproduction-matrix.md`
- `docs/changelog.md`

This keeps the project grounded in the actual McHeliCE/WingMan mission boundary and makes the hostile-world utility case explicit rather than relying on unsupported AI rhetoric.

## Operator-triggered kamikaze run

For a locked attack mission, the pilot can explicitly press `N` or use the
`Nose-dive [N]` GUI button. The server validates ownership, pilot seat,
target lock, and supported Switchblade/Shahed aircraft before arming the
35-degree WingMan dive approach. WingMan remains responsible for steering and
available-speed control; the addon does not teleport the aircraft or force a
fake impact. Ordinary aircraft are rejected, and live collision, damage, and
self-destruct behavior must be verified in the target instance.

This is the Forge 1.12.2 addon project for player-configured reconnaissance
and target missions. It targets the exact installed runtime:

* Minecraft 1.12.2
* Forge 14.23.5.2864
* McHeli CE 1.5.1 with merged WingMan classes
* RDP Core 1.0.0
* Java source/target compatibility 8

Place the verified runtime jars at `libs/mcheli-ce-1.5.1.jar` and
`libs/rdpcore-1.0.0.jar` before running Gradle, or set `RDP_MCHELI_JAR` and
`RDP_CORE_JAR` to the installed instance jar paths. The jars are intentionally
not copied into source control.

The addon includes a tactical mission-control GUI and player-owned UAV mission
commands. While piloting a McHeli UAV, authorize it with
`/rdpuav authorize`, then create a manual-target reconnaissance mission with
`/rdpuav mission create <namespace:id>` (append `attack` only when an attack
mission was intentionally configured). Use `/rdpuav mission gui` to monitor
missions. The screen receives only the current
player's mission summaries from the server; select a mission and use
`Abort selected` to safely stop a non-terminal mission, Refresh requests a new
snapshot, and Close exits the screen. The dashboard provides an ALL/ACTIVE/ALERTS
mission filter, free-text filtering, selected-mission details, and a live
target-telemetry pane. `Lock crosshair [L]` sends a single-press, server-validated exact target-lock
request for the entity or McHeli vehicle under the crosshair. `J`, or
**Clear lock** in the dashboard, requests lock cancellation and normal WingMan
recovery. Locking does not require a destructive weapon and does not bypass
aircraft ownership or target validation. Abort requests are checked server-side against the player's UUID,
mission ownership, and terminal state. Authorization requires the player to
be the McHeli UAV pilot; another player cannot create a mission for that UAV.
Attack-intent missions begin in reconnaissance mode and automatically arm
WingMan attack mode after the server reaches `TARGET_LOCKED`. `Authorize
attack` remains available as a manual operator override. The server still
checks ownership, UAV status, target coordinates, and mission state before
arming the attack adapter. Dispatch starts on the server world tick and is
handed to WingMan through the adapter.

### Mission execution keys

| Key | Action | Server-side safety boundary |
| --- | --- | --- |
| `U` | Open mission control | Opens the dashboard and requests only the player's mission data |
| `P` | Point task | Sends a camera-point request; the server ray-traces and validates the controlled, authorized UAV |
| `R` | Recon mission | Creates a non-destructive exact registry-target request |
| `K` | Attack intent | Creates an attack mission intent; it does not arm weapons |
| `A` | Authorize attack | Requires owner, operator, `TARGET_LOCKED`, live target, and a valid WingMan attack route |
| `N` | Kamikaze/nose-dive | Explicit operator action; server requires a supported aircraft, target, payload, and route |
| `H` | Fire SelfDestruct payload | Requires operator control, supported aircraft, destructive weapon, and ammunition |
| `L` | Exact target lock | Locks only a loaded, hostile, non-vehicle contact under the crosshair; no destructive payload is implied |
| `J` | Clear target lock | Requests lock cancellation and normal WingMan recovery |
| `X` | Abort selected mission | Requires a server-owned mission and releases the aircraft after aborting |
| `F` | Refresh missions | Requests a server-filtered snapshot; it does not mutate mission state |

`K`, `N`, and `H` are intentionally distinct. `K` submits an attack plan,
`N` arms a destructive kamikaze mode after the explicit operator action, and `H` directly
fires the validated SelfDestruct weapon. The client key names are configurable
through Minecraft's Controls menu.

For an autonomous AI task, use `/rdpuav mission ai <namespace:id> [attack]`.
The command evaluates the controlled, authorized UAV through the deterministic
AI tasking service and submits a mission only when the aircraft is available
and attack capability is present for an attack request.
Normal AI attacks use WingMan's regular attack node; a destructive weapon does
not cause an automatic kamikaze dive. Kamikaze remains an explicit operator
action through the mission controls. Manual AI targets are resolved from
loaded entities at any distance inside the mission's 5000-block envelope, so
they are not incorrectly rejected by the old 256-block acquisition search.
Long-range/reusable profiles, including MQ-9, additionally require explicit
destructive authorization before any kamikaze route. One-way/FPV square
strikes are supported and use their configured terminal payload behavior
(default self-destruct), while reusable square strikes return to base.

For a survival-safe point attack, look at a block or structure from the
piloted UAV and run `/rdpuav mission attack-point`. The server ray-traces the
player camera out to 256 blocks, stores the finite world coordinates, and
queues a WingMan reconnaissance flight. An attack node is armed automatically
after a valid target lock, or manually through the GUI override. Entity missions resolve
registered entity IDs in the same radius, including modded IDs such as
`srparasites:*`, `lycanitesmobs:*`, `scp:*`, and `techguns:*` when those
entities are loaded in the world. The addon does not infer a structure's
identity; structures are safely targeted by their selected world point.
No live weapon damage or self-destruct result is inferred by this addon;
ordinary UAV missions are never forced into an attack.

Guided weapons are selected from the server registry by exact McHeli
definition/type. UAVs use native WingMan attack nodes for coordinate/TV
weapons and McHeli's guidance system for entity seekers. A piloted non-UAV
aircraft may use the same supported loaded weapon through the native
`MCH_WeaponSet.use` boundary; unsupported definitions and objective
combinations return deterministic rejection reasons rather than silently
falling back to an arbitrary weapon. The release gate performs a bounded,
10-tick cached terrain corridor check, an optional line-of-sight check, and
configured close/high-altitude envelope checks. It never changes projectile
velocity, projectile position, or aircraft motion. Entity seeker targets
remain UUID-authoritative and are resolved from the live server entity.

The flight adapter also exposes a validated coordinate-square strike pattern.
It generates alternating native WingMan `flyTo` and `attack` nodes, preserving
the real McHeli/WingMan movement and weapon boundaries. Node creation is not
treated as proof of a completed pass, weapon release, impact, or damage; a
server mission command and live runtime verification are still required before
this capability can be considered production-ready.

Square strikes accept a target center at most 5000 blocks from the controlled
UAV. The area remains bounded to 16-512 blocks, and the mission envelope uses
the 5000-block dispatch limit so a valid square target is not shown as out of
range by the HUD. This limit does not claim that chunk loading, steering,
weapon release, or damage have been verified in a live server.

**Implementation boundary:** server ticks now dispatch queued missions, track
loaded targets with reacquisition, refresh bounded native WingMan approach and
orbit routes as targets move, persist scan metadata, and request WingMan
return/parking. Point and entity missions do not advance merely because a
route was created: the lifecycle waits for native WingMan route progress and
the observation envelope. Mission failures are logged with the
mission/aircraft IDs, state, target, dimension, tick age, and reason; the
mission list and target HUD also expose the persisted failure reason and
current WingMan state/node. This remains source/API validated only: actual
landing, scanner contents, weapon effects, and self-destruct require a live
Forge server test. Do not treat a successful build or JAR deployment as proof
that those behaviors work in-game.

Automatic selection ranks valid candidates by target priority and then distance;
manual selection only returns the explicitly requested registry ID. The
dependency-free core test can be run with:

```text
javac -source 8 -target 8 ...
java net.vas.rdp.uav.CoreMissionTest
```

The Gradle test task runs the JUnit contract suites in
`src/test/java/net/vas/rdp/uav/`. They cover mission transition guards,
mission NBT persistence, exact target normalization and selection, target-lock
ordering and packet identity, assignment conflicts and reassignment, corridor
safety decisions, RF energy bounds and persistence, scan-report persistence,
and square-strike native-node geometry:

```text
gradlew.bat test --no-daemon
```

These tests are source/API validation only. They do not prove live McHeli
steering, fixed-wing turning/orbiting, moving-target tracking, vehicle lock
acceptance, weapon release, projectile impact, target damage, self-destruct,
landing, parking, or HUD rendering. Those behaviors require a controlled
Minecraft client/server test with the installed McHeli and WingMan runtime.

## Live debug logging

The addon includes a server-side diagnostic logger that is disabled by default.
It writes tagged entries to the normal Forge/Minecraft log (`logs/latest.log`)
so it works on dedicated servers and clients without an additional logging
installation. The generated configuration is:

```text
<minecraft>/config/rdp-uav-debug.cfg
```

Use the operator command while the server is running:

```text
/rdpuav debug on
/rdpuav debug off
/rdpuav debug status
/rdpuav debug reload
```

`on` and `off` persist immediately; `reload` rereads the configuration file.
Debug entries use the searchable format
`[RDP-UAV/Debug] [DEBUG][Subsystem]`. Lifecycle, WingMan dispatch/return,
commands, and network telemetry include mission, aircraft, target, state, and
dimension context where available. Debug logging is intentionally verbose, so
leave it off during normal operation and attach the relevant `latest.log`
after reproducing a failure.

## Kamikaze impact detonation

Kamikaze missions can be armed only when the UAV has a McHeli weapon whose
definition has `SelfDestruct: true` and available ammunition. While the mission
is in `KAMIKADZE_TARGET`, the server checks the forward nose sweep each tick.
If the nose intersects a solid block or collidable entity/vehicle at or above
the configured speed, the addon fires that native McHeli weapon and records
`KAMIKADZE_DETONATED`. It does not detonate from a slow scrape, a nearby
non-collidable entity, or a normal reconnaissance/attack mission.

McHeliCE 1.5.1 currently applies the native `SelfDestruct` weapon branch only
to helicopter entities. For plane and other aircraft UAVs, the addon mirrors
the same server-side explosion and aircraft destruction after the loaded
self-destruct weapon fires, so the payload is not silently consumed.
Attack missions with a loaded self-destruct weapon are promoted to an armed
kamikaze run instead of allowing WingMan to fire the payload when the target
is merely acquired. The addon detonates when the target point, entity, or
vehicle enters `detonationRadius` (8 blocks by default), or on a qualifying
high-speed nose impact. Both paths require the aircraft motion speed to reach
`minimumImpactSpeed`; the armed UAV therefore continues its approach instead
of detonating immediately when a point target is acquired.

The operator can also fire the selected UAV's SelfDestruct payload directly
with the `RDP UAV fire SelfDestruct` key (default `H`), just as with a
helicopter SelfDestruct weapon. This is an explicit manual override and does
not require a mission target or kamikaze approach. The server still validates
that the player operates the aircraft, the payload is configured with
`SelfDestruct: true`, and ammunition is available.

The generated configuration is:

```text
<minecraft>/config/rdp-uav-kamikaze.cfg
```

`minimumImpactSpeed` defaults to `0.35` blocks/tick and
`impactSweepPadding` defaults to `0.35` blocks. These values can be adjusted
for the installed aircraft pack; lower values make impact triggering more
sensitive. `detonationRadius` defaults to `8` blocks and controls the AI
proximity detonation distance. The trigger is implemented in `rdp-uav-addon`,
not RDPMixins.

## Operator target lock

When piloting or operating a UAV, aim the normal Minecraft crosshair at an exact
loaded entity or vehicle and press the configurable `RDP UAV AI target lock`
key (default `L`). The client sends only that entity UUID; the server
revalidates the dimension, identity, collision state, and operator ownership.
The server creates a non-destructive exact-target tracking mission, so ordinary
UAVs can approach, observe, and track targets without carrying a self-destruct
weapon.

Press `J` (or use **Clear lock** in the dashboard) to clear the lock and
request a normal WingMan return. Destructive attack and kamikaze behavior
remain separate, server-gated actions and are never implied by a tracking lock.
Both keys are configurable through Minecraft's Controls menu. Locking never
substitutes a nearby entity for the exact entity selected on screen.

## AI tasking boundary

The `net.vas.rdp.uav.ai` package is a deterministic tasking layer above the
existing `MissionManager`. `AiTaskRequest` represents operator intent,
`AiDecisionEngine` scores registered aircraft by availability, capability,
priority, and distance, and `AiTaskingService` separates recommendation,
operator approval, and autonomous execution. Approved or autonomous plans
are submitted through `MissionManager.create`; the AI does not mutate
mission state, ownership, weapons, or flight physics directly. The existing
`MissionLifecycle` and `WingManFlightAdapter` remain the execution authority.

This layer is source/API validated. It does not by itself prove that a live
operator GUI request reaches an in-game UAV, nor does it certify autonomous
flight or weapon effects.

## Sensor and decision boundary

The `net.vas.rdp.uav.sensor` package keeps world evidence separate from
operator-reported intent. `SensorObservation` records the source, knowledge
kind (`KNOWN`, `REPORTED`, or `INFERRED`), identity, position, confidence,
and observation tick. `SensorFusion` selects the strongest observation while
explicitly lowering confidence and exposing an explanation when sources
disagree. `AiDecision` now also carries a bounded confidence value and a
`FlightIntent`; the existing five-argument constructor remains compatible with
callers that have no sensor evidence. These are decision inputs only: WingMan
and `MissionLifecycle` remain the authority for physical execution and mission
state transitions.

## Guided weapon boundary

Copy `config/rdp-uav-guided-weapons.cfg.example` to the instance config
directory to authorize McHeli weapon definition/type pairs. `aamissile` and
`atmissile` are entity-seeker weapons; `asmissile` and `tvmissile` accept
coordinate/TV/laser square objectives. Unsupported combinations are rejected
by the server. The addon never creates or steers projectile entities; native
McHeli/WingMan guidance remains authoritative.

## Square strike constraint boundary

`SquareArea` is the authoritative bounded geometry for area missions; it
provides containment and min/max edges instead of deriving boundaries from
waypoints. `SquareStrikeConstraints` keeps altitude, search pattern, target
policy, weapon policy, priority, timeout, engagement/target caps, return
policy, and RTB threshold together with validation helpers. The existing
`SquareStrikePlan` remains compatible and now exposes its hard area boundary.
These constraints are intentionally separate from tactical decisions and
WingMan node execution, so later re-tasking and operator overrides can change
the plan without weakening the safety boundary.

`SquareStrikeStateMachine` supplies the area mission lifecycle independently of
the legacy generic mission state: validation, assignment, launch, transit,
bounded search, target validation, engagement assessment, resume, RTB, and
completion. Operator override, pause, abort, and emergency transitions are
explicitly interruptible and invalid shortcuts are rejected.

ForgeGradle configuration was verified with the local ForgeGradle 2.3
installation. A full `test`/`build` additionally requires the exact
`net.minecraftforge:forge:1.12.2-14.23.5.2864` userdev artifact; only
`14.23.5.2859` is cached in the current environment, so no version-mismatched
Forge build is substituted.

## Exact Forge 14.23.5.2859 setup requested for this GUI

The checked-in `build.gradle` still targets the previously validated
`14.23.5.2864` environment. To build this small GUI addon against the exact
environment requested for deployment, make the following one-line change
before running Gradle:

```groovy
minecraft {
    version = '1.12.2-14.23.5.2859'
}
```

Then:

1. Install Java `1.8.0_492` and make it the `JAVA_HOME` used by Gradle.
2. Put the exact McHeli CE `1.5.1` jar at
   `rdp-uav-addon/libs/mcheli-ce-1.5.1.jar`.
3. Put the matching RDP Core jar at
   `rdp-uav-addon/libs/rdpcore-1.0.0.jar`.
4. Use ForgeGradle 2.3 with Maven access to
   `https://maven.minecraftforge.net`.
5. Run `gradlew setupDecompWorkspace` and `gradlew build` from
   `rdp-uav-addon`.
6. Copy `build/libs/rdp-uav-addon-0.1.0.jar` into the Minecraft 1.12.2
   `mods` directory alongside Forge `14.23.5.2859` and McHeli CE `1.5.1`.
7. Start a dedicated server and client with the same mod set, then run
   `/rdpuav mission gui`.

Do not copy the source project into `mods`, and do not mix McHeli versions.
The GUI was source-compiled against cached 1.12.2 APIs, but no Minecraft
client/server runtime test was run in this session.
## Explicit no-candidate behavior

Target search is operational when no configured entity is currently eligible. The
mission enters `WAITING_FOR_TARGET` (and telemetry reports `NO_CANDIDATE`) rather
than failing, selecting an arbitrary entity, using stale coordinates, or firing a
payload. A previously acquired target is recorded as `LOST` before entering the
waiting state. Searching resumes on the normal mission tick when a candidate
passes the complete target filters. `TARGET_LOCKED` is never reported without a
currently resolved target.

## Vehicle execution profiles

The addon does not derive mission behavior from McHeliCE's technical
`Type: helicopter` value or from display names. On startup it creates
`config/rdp-uav-vehicles.cfg`, where each exact McHeli aircraft definition can
be mapped to `ONE_WAY` or `REUSABLE_UAV` and aliases can be listed. The default
mapping covers Switchblade/Shahed-style one-way payload vehicles and an MQ-9
profile. Reusable profiles use the configured execution altitude, altitude
tolerance, and horizontal target guidance; they do not dive toward target Y or
automatically self-destruct. SelfDestruct remains a McHeliCE weapon capability,
not a universal mission classification.

`LONG_RANGE_REUSABLE_UAV` adds separate `detectionRange`, `lockRange`, and
`missionExecutionRange` settings. Detection selects eligible candidates,
locking uses authoritative 3D distance and UUID continuity, and mission
execution remains gated by the smaller execution range.
Square strike is deliberately restricted to reusable UAV profiles. ONE_WAY
FPV/Shahed-style aircraft use the point/entity attack path and must not be
sent through a repeating square route. Square altitude, heading, and pass
spacing are configured in `rdp-uav-square-strike.cfg`; the command coordinates
are the requested area center, not a hardcoded aircraft placement. The reference MQ-9
configuration can therefore establish a lock before reaching the target area
while holding its configured altitude.

Entity missions use one server-side contact tracker keyed by entity UUID. A
missed update marks the same contact `LOST` and eventually `EXPIRED`; it never
silently transfers tracking to a nearby entity. The tracker retains last-known
position, velocity, confidence, dimension, and timestamps. McHeliCE's
technical vehicle type does not select this policy; the configured UAV profile
does.

## Swarm orchestration

Swarms are server-authoritative persisted aggregates over existing aircraft and
missions. A swarm owns membership, roles, formation metadata, leadership policy,
and degraded/partial status; `MissionManager` still owns aircraft assignment,
target claims, mission queues, and lifecycle, while WingMan remains the only
flight controller. Members are keyed by the authoritative aircraft UUID, so
mixed `ONE_WAY`, `REUSABLE_UAV`, and long-range profiles can coexist without
McHeliCE type or display-name checks.

Commands are validated on the server: `/rdpuav swarm create <name>`, `add`,
`remove`, `pause`, `resume`, `reform <formation>`, `disband`, and `status`.
Telemetry is bounded and sent at the configured swarm update cadence. Formation
geometry is an objective/slot description only; it never teleports aircraft or
competes with WingMan. Operator-controlled members are excluded from automatic
allocation. Capability filtering and stable UUID ordering provide deterministic
task candidates.
