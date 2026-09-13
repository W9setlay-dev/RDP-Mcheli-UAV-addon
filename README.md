# RDP UAV Missions

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

The addon includes a small mission-control GUI and player-owned UAV mission
commands. While piloting a McHeli UAV, authorize it with
`/rdpuav authorize`, then create a manual-target reconnaissance mission with
`/rdpuav mission create <namespace:id>` (append `attack` only when an attack
mission was intentionally configured). Use `/rdpuav mission gui` to monitor
missions. The screen receives only the current
player's mission summaries from the server; select a mission and use
`Abort selected` to safely stop a non-terminal mission, Refresh requests a new
snapshot, Operator help explains the AI pilot workflow, and Close exits the
screen. Abort requests are checked server-side against the player's UUID,
mission ownership, and terminal state. Authorization requires the player to
be the McHeli UAV pilot; another player cannot create a mission for that UAV.
Attack-intent missions begin in reconnaissance mode and automatically arm
WingMan attack mode after the server reaches `TARGET_LOCKED`. `Authorize
attack` remains available as a manual operator override. The server still
checks ownership, UAV status, target coordinates, and mission state before
arming the attack adapter. Dispatch starts on the server world tick and is
handed to WingMan through the adapter.

For an autonomous AI task, use `/rdpuav mission ai <namespace:id> [attack]`.
The command evaluates the controlled, authorized UAV through the deterministic
AI tasking service and submits a mission only when the aircraft is available
and attack capability is present for an attack request.
Normal AI attacks use WingMan's regular attack node; a destructive weapon does
not cause an automatic kamikaze dive. Kamikaze remains an explicit operator
action through the mission controls. Manual AI targets are resolved from
loaded entities at any distance inside the mission's 5000-block envelope, so
they are not incorrectly rejected by the old 256-block acquisition search.

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
loaded targets with reacquisition, persist scan metadata, and request WingMan
return/parking. This remains source/API validated only: actual autonomous
steering, landing, scanner contents, weapon effects, and self-destruct are not
verified in a live Forge server. Do not treat a successful build or JAR
deployment as proof that those behaviors work in-game.

Automatic selection ranks valid candidates by target priority and then distance;
manual selection only returns the explicitly requested registry ID. The
dependency-free core test can be run with:

```text
javac -source 8 -target 8 ...
java net.vas.rdp.uav.CoreMissionTest
```

The Gradle test task also runs the JUnit core suite in
`src/test/java/net/vas/rdp/uav/RdpUavCoreTest.java`. It covers mission
transition guards, mission NBT persistence, target normalization and
selection, target-lock ordering, assignment conflicts, RF energy bounds and
persistence, and square-strike native-node geometry:

```text
gradlew.bat test --no-daemon
```

These tests are source/API validation only. They do not prove live McHeli
steering, weapon release, projectile impact, target damage, self-destruct,
landing, parking, or HUD rendering. Those behaviors require a controlled
Minecraft client/server test with the installed McHeli and WingMan runtime.

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
