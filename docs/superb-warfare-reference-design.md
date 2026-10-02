# UAV Warfare Feature Reference: Evidence and Safe Integration Design

## Purpose and scope

This document translates the requested Superb Warfare-inspired ideas into a
repository-aware design reference for `rdp-uav-addon`. It is not a port of
another mod, a promise of feature parity, or proof that the suggested mechanics
work in this runtime.

The first prioritized user-facing slice is **A + B**:

- **A — Operator control:** Keep the existing Mission Control GUI as the
  control surface. Do not add a physical radio item or make item NBT a source
  of aircraft authority.
- **B — Entity designation:** Reuse the existing crosshair target-lock action
  and exact entity UUID. Do not add another laser-pointer item or a competing
  target-selection packet. Block/coordinate designation remains the separate
  Point task.
- **Line of sight:** Preserve the current lock action and validation semantics.
  Do not claim that the server verified the crosshair ray or unobstructed line
  of sight. The client chooses the entity UUID; the server independently
  validates whether that exact entity may be locked under the current mission
  and operator policy.

The user has also approved **C. Swarm autonomy** and **E. Waypoint/loitering
flight**, using bounded server defaults. Sensor discovery, thermal sensing,
automatic weapon assignment, and physical collision avoidance are not part of
that approval.

## Evidence and compatibility

The addon targets Minecraft 1.12.2, Forge, Java 8, and McHeliCE 1.5.1/WingMan.
The cited public mod pages currently list newer Minecraft versions; their
feature descriptions are inspiration, not compatible APIs or behavioral
specifications.

| Reference | Publicly verified information | Boundary |
| --- | --- | --- |
| [Superb Warfare: Perimeter](https://modrinth.com/project/sDCjHVv3) | Modrinth project metadata resolves to `superb-warfare-perimeter`, describes drone-swarm control and enemy-tracking radar, and lists 1.20.1, 1.21, and 1.21.1. | Its metadata does not verify the precise swarm algorithms or numerical values quoted in the request. |
| [Uncomplicated FPV](https://modrinth.com/project/iS1QJ8ii) | Modrinth metadata resolves to `uncomplicated-fpv`, describes combat/reconnaissance drones, electronic warfare, and a Shahed-136, and lists 1.20.1–1.20.6. | The metadata does not establish coordinate formats, route laws, or impact behavior for this addon. |
| [[SBW] Drone Warfare](https://modrinth.com/project/sbw-tactical-drone) | Modrinth metadata resolves to `sbw-tactical-drone`, describes tactical FPV drones, LUCAS UAVs, fiber links, and radar, and lists 1.20.1 and 1.21.1. | Its version and dependency context differ from this 1.12.2 addon. |
| [Supplied SBW Drone Warfare README](https://github.com/SmartStreamLabs/-SBW-Drone-Warfare/blob/main/README.md) | The reachable README is titled `[SBW] Drone Range Config`; it describes configurable drone-control distance and optional retention of chunks while a drone is used. | It does not independently verify the waypoint/recon behaviors in the request. |

The values **16 members**, **80/20 engagement split**, **6–20 blocks AGL**,
**5-block standoff**, **24-block sweep**, and **40-second timeout** are
request-supplied examples, not approved RDP gameplay defaults or independently
verified reference behavior. Existing addon configuration currently defaults
to a swarm maximum of 8, with a configured upper bound of 64. Any future tuning
must be configurable, profile-bounded, server-validated, and justified by
gameplay tests.

## Current addon capability map

“Implemented” below means a source-level contract exists. It does not imply
that the feature has passed live flight, weapon, visual, or persistence
validation in Minecraft.

| Capability | Current implementation | Status and remaining boundary |
| --- | --- | --- |
| Operator GUI | `GuiMissionControl`, `MissionDefinition`, `MissionValidationResult`, `RdpUavNetwork` | GUI sends intent; the server validates. Patrol launch is a bounded single-aircraft route workflow. |
| Operator/aircraft binding | `UavControlAccess`, `MissionManager` | Resolves the piloted craft or UAV-station-controlled craft and checks ownership/operator status server-side. There is no new radio binding item or channel number. |
| Exact entity lock | `GuiMissionControl.lockCrosshairTarget`, `RdpUavClientKeys`, `RdpUavNetwork.ServerAiTargetLockHandler`, `RDPMission` | Sends a selected entity UUID; server checks operator/aircraft authorization, supported profile, exact loaded entity in the player's world, target policy, range, active mission, and terminal target immutability. **No server-side crosshair-ray or LOS assertion is made.** |
| Point designation | `ClientAimRayTracer`, `OperatorAimMissionService` | A separate camera-ray point task supports entity UUID or block point. The server validates operator authority, loaded/world bounds and block collision for point targets, reported hit/block consistency, range, and target policy for entities. |
| Mission and target persistence | `RDPMissionWorldData`, `MissionNbtCodec`, `RDPMission` | Mission identity/state are persisted. Locked entities retain UUID identity; unavailable identities must not be silently replaced. |
| Swarm membership and policy | `SwarmManager`, `SwarmRecord`, `SwarmConfig`, `SwarmConfiguration`, `RDPUavSwarmWorldData` | Persisted owner-bound membership, configurable size, role/capability checks, leader state, and shared target projection exist. Contact persistence is rate-limited; swarm patrol fan-out is not enabled. |
| Assignment and recovery | `SwarmTaskAllocator`, `SwarmAssignmentManager`, `SwarmRecoveryManager`, `SwarmReplanner`, `SwarmTargetRecoveryPolicy`, `SwarmSearchRoutePlanner` | Deterministic observer-role allocation and same-UUID loss recovery exist. On a loaded-chunk loss, at most one idle reusable SEARCHER receives a bounded, validated search route; it cannot authorize a strike. |
| Search and formations | `SearchCoordinator`, `FormationController`, `FormationGeometry` | Computes sectors/slots and checks configured spacing. Plans are not proof of path safety or physical collision avoidance. |
| Shared contact | `SwarmTargetRegistry`, `SwarmTargetState`, mission `TargetAuthority` | Shared target registry is coordination state; mission UUID lock and lifecycle remain authoritative. “Last seen” data must not be mistaken for a live entity. |
| Terrain and route | `TerrainRoutePlanner`, `WorldPerceptionAdapter`, `MinecraftWorldPerceptionAdapter` | Bounded non-loading terrain/airspace samples, AGL planning, and explicit hold reasons exist. This is not a global pathfinder or indoor wall follower. |
| Patrol and loiter | `PatrolRouteCodec`, `PatrolRoutePlanner`, `MissionLifecycle`, `WingManFlightAdapter` | Mission Control submits finite terrain-checked waypoint routes with bounded loiter; native WingMan executes them and receives a return request on completion. A separate one-aircraft target-loss search route uses the same planner. User-route swarm fan-out remains disabled; runtime flight, native return, and physical separation are unverified. |
| Vehicle control | `AirframeController`, `NativeAircraftControlAdapter`, `MissionMovementAuthority`, `WingManFlightAdapter` | Profile-specific setpoints go through the leased native aircraft adapter. Do not add direct entity-position/motion writes or fictional McHeli control methods. |
| HUD and target rendering | `RdpUavTargetHud`, `RdpUavHudConfig`, telemetry packets | Displays synchronized mission/target data and a world-space target bracket using a loaded UUID match. It is not a thermal sensor or general entity-highlighting engine. |
| Sensor and AI tasking | `SensorFusion`, `AiTaskingService`, `AiDecisionEngine` | Supports recommendations/approval contracts. A recommendation or client display is not authority to create an unvalidated mission. |
| Guided payload | `GuidedWeaponAdapter`, `GuidedWeaponRegistry`, mission outcome state | Native adapter owns supported release contracts; launch, tracking, impact, and confirmed effect are separate outcomes. Runtime acceptance remains a separate proof requirement. |

See the [current architecture](current-architecture.md), [mission framework
map](mission-framework-map.md), [terrain/FSM/authority notes](terrain-fsm-authority-and-trim.md),
[API verification notes](api-verification-notes.md), [Phase E UI/HUD
contract](phase-e-ui-hud-rework.md), and [runtime test
matrix](runtime-test-matrix.md) for source detail and validation status.

## A+B operator and designation contract

### Request and authority flow

```text
Mission Control GUI / existing lock key
        |
        | selected entity UUID (client-side crosshair contact)
        v
existing RdpUavNetwork lock request
        |
        v
server thread + ServerAiTargetLockHandler
        |
        +-- resolve controlled aircraft and verify pilot/station operator
        +-- verify registered owner and profile lock capability
        +-- resolve the exact UUID from loaded entities in the operator world
        +-- reject self/aircraft/unavailable/filtered/out-of-range contacts
        +-- require an active mission and preserve terminal target identity
        |
        v
RDPMission applies exact operator entity lock -> mission persistence/telemetry
```

The GUI remains the sole operator-facing command surface for this slice. The
server determines the controlled aircraft from the authenticated player and
the native seat/station relationship; a GUI field, channel value, stored UUID,
or packet parameter cannot authorize control. No `UavData` item compound is
required. If a future UI needs a selected aircraft, that identifier is only a
request and must be re-resolved against server ownership and control access.

The existing target-lock action selects an **entity UUID**. It does not submit
a laser point, block coordinate, continuous beam, or arbitrary client world
position. Block designation remains the existing Point task. The Point task
and the lock action must not be merged into one ambiguous target packet.

### Explicit line-of-sight semantics

Current UI code uses the crosshair entity when available and may fall back to a
recently captured crosshair UUID. The server resolves and validates that UUID,
but does not receive or prove the camera ray. The verified McHeliCE station
camera contract does not provide a server-side ray origin/direction in the
current integration notes.

Accordingly:

- Preserve the existing request and validation behavior; do not add a second
  laser-designator action or promise LOS rejection.
- Treat “crosshair-selected” as the **client's selection method**, not as a
  server-verifiable visibility guarantee.
- Never trust a client-provided ray, position, target class, or authorization
  as a replacement for server checks.
- Keep exact UUID identity: if the entity is unavailable, reject/report loss;
  do not substitute a nearby entity or matching registry ID.
- If server-observed LOS becomes a future requirement, first verify a stable
  server camera/station pose and define packet freshness, ray sampling, block
  transparency, unloaded-block, and rejection behavior. Do not silently
  downgrade to range-only behavior when that proof is unavailable.

### Expected outcomes and reporting

| Condition | Server outcome | Operator-visible outcome |
| --- | --- | --- |
| Authorized operator selects a valid supported loaded contact in range with an active mission | Apply the exact UUID lock to the active mission and persist world mission data. | Accepted lock response and refreshed mission/target telemetry. |
| UUID invalid, unloaded, in another dimension, dead, or no longer selectable | Reject; preserve existing lock/mission unless an explicit supported transition says otherwise. | Explain that the selected contact is unavailable or filtered. |
| No active mission, unsupported vehicle profile, unauthorized operator, out-of-range contact, or terminal target replacement attempt | Reject with an explicit reason. | Report the corresponding rejection; do not present a lock as accepted. |
| Operator invokes the existing clear-lock action | Use the existing abort/release behavior and resource cleanup, subject to terminal commitment rules. | Confirm cleared/rejected state from the authoritative server response. |
| Client crosshair ray was occluded or stale but its UUID still passes existing server gates | Current semantics may accept it; the server has not verified visibility. | Do not label this a LOS-validated “laser lock.” |

This accepted design preserves existing UX; it does not claim the stronger
server-side visibility guarantee.

## D: Presentation-only FLIR/HUD design

### Accepted feature boundary

- FLIR v1 is a **client-side presentation mode**, not a sensor or target
  acquisition capability.
- It may restyle the current server-reported mission-target bracket only when
  that exact target UUID resolves to a live, loaded client-world entity and
  existing telemetry freshness/state rules allow the marker.
- It does not scan an AABB, enumerate nearby `EntityLivingBase` instances,
  reveal targets through walls, create contacts, change target confidence,
  share swarm contacts, or influence mission decisions.
- It does not tint, grayscale, darken, or otherwise post-process the full
  terrain/world image. There is no screen-wide thermal overlay in v1.
- The existing **Mission Control GUI is the primary toggle surface**. The
  configurable **F8 convenience keybind** uses the same persisted client
  preference rather than maintaining an independent toggle state.
- FLIR defaults **off** so existing HUD presentation is unchanged until the
  operator explicitly enables the mode.
- The GUI toggle is on the `MONITOR` tab. The convenience keybind defaults to
  `F8`, is configurable in the Minecraft controls menu, and is ignored while
  another GUI is open so it does not consume text-entry input.
- The toggle is client presentation preference only. Do not send an
  enable/disable packet, alter mission NBT, change server target acquisition,
  or treat the preference as evidence that an aircraft has a thermal sensor.

### Data flow and rendering contract

```text
existing server target telemetry (mission UUID, target UUID/state, dimension)
                           |
                           v
RdpUavTargetHud freshness + mission marker-state gates
                           |
                           v
resolve only the exact loaded UUID in the current client world
                           |
                           v
FLIR preference ? thermal-style target bracket : existing bracket
```

- Keep `RdpUavTargetHud` as the rendering owner and reuse its authoritative
  telemetry, dimension guard, exact-UUID entity lookup, cache invalidation,
  stale-data timeout, and `MissionUiState.isMarkerVisible` policy. Do not add a
  second contact cache or resolve by registry ID/name.
- FLIR off (including the default) preserves the current bracket appearance.
  FLIR on uses a restrained high-contrast monochrome white-hot bracket for the
  exact existing target. Continue to distinguish lock state using the current
  lock-state semantics and accessible shape/status cues, not a claimed heat
  value. The rendered value represents “selected mission target,” not
  temperature, hostility evidence, visibility through occlusion, or sensor
  quality.
- If telemetry expires, target identity is absent, the dimension differs,
  target UUID cannot be resolved, or mission state suppresses markers, render
  no FLIR target bracket. Never fall back to a nearby entity or a stale
  coordinate marker for an entity lock.
- The setting belongs in the client HUD preference/config (the existing
  `RdpUavHudConfig` / `rdp-uav-hud.cfg` path), not `MissionDefinition`,
  `MissionNbtCodec`, `RDPMissionWorldData`, or server swarm persistence.
- Add the GUI control as a labeled presentation setting, preferably in an
  existing HUD/Monitor-oriented section after checking actual layout. Display
  its current value and explain “target bracket only; no additional sensing.”
  The later keybind toggles the same local setting and reports its new state.
  Do not register or process keybind input while text entry or another
  conflicting GUI action should own input.
- Rendering remains client-only. Preserve/restore OpenGL matrix, attribute,
  blending, depth-mask, color, line-width, and texture state on every path;
  keep work bounded to the one already-authorized target marker. Do not use
  screen-wide shader/post-processing hooks, raw entity scans, or unsupported
  camera projection matrices.

### Tests and live proof

The implementation includes focused tests for the default-off preference,
shared toggle state, and locked/unlocked bracket palette. The tests do not
exercise Forge configuration file round-trip or GUI/key event dispatch.

- Pure/client-state tests should cover preference default-off/on/off, config
  round-trip, GUI and keybind sharing the same setting, and that toggling does
  not modify mission definition or send a server packet.
- HUD contract tests should cover stale telemetry, target UUID change,
  unloaded/disappeared target, dimension mismatch, terminal/hidden marker
  states, and preservation of the existing non-FLIR bracket when disabled.
- Verify any render-state helper restores GL state even when target resolution
  or drawing exits early. Avoid relying on headless unit tests to prove
  rendered pixels.
- Live Forge client validation should verify both GUI toggle presentation and
  the keybind, no world/terrain tint, no additional entity highlighting,
  no marker with stale/missing telemetry, no input interference while typing,
  and no change to server mission/target state.
- Runtime status for this feature remains **NOT ATTEMPTED** until those live
  client checks are observed. Source tests cannot prove thermal visuals.

## Reference-inspired subsystem designs

### GUI, aircraft selection, and control

- Reuse `GuiMissionControl`, its versioned `MissionDefinition` submission,
  existing aircraft-control discovery, and server-side mission validation.
- Keep mission intent separate from authoritative mission state and persisted
  `RDPMission`. UI enablement is explanatory only; every state-changing request
  is checked on the server.
- No physical radio, inventory-stored channel, or item UUID binding is part of
  the accepted design. If one is proposed later, bind it as a usability
  selector, not a credential or owner proof.
- Report capability and rejection before dangerous execution. Keep point,
  entity lock, terminal commitment, abort, and release actions semantically
  distinct.

### Swarm organization and target sharing

- Continue to use `SwarmRecord` as the persisted swarm aggregate and individual
  `RDPMission` records as the owners of each aircraft's mission, target lock,
  and flight lifecycle.
- Clamp requested member count to the intersection of server global maximum,
  profile-specific maximum, mission maximum, actual membership, and supported
  available capabilities. Never trust client-submitted swarm size.
- Allocate deterministic roles from capabilities and availability. A
  proposed engagement/recon split is a policy, not a copied constant: ensure
  at least one observer/reserve where feasible, handle small/odd/degraded
  swarms deterministically, and allow operator override where permitted.
- Share a target **identity projection** (UUID plus dimension, observation
  tick, confidence, and status) only after server-side validation. Shared
  target state does not silently overwrite each aircraft's committed
  `RDPMission.targetId`.
- Do not describe `FormationController` slots or `SearchCoordinator` sectors
  as collision avoidance or motion; they are coordination plans. Enforce
  loaded-aircraft, profile, route, spacing, and ownership constraints before
  translating any future plan into per-aircraft missions.

### Patrol, loiter, and search

- Implemented patrol input is a finite list of absolute X/Z waypoints with
  optional bounded loiter. The server validates route length, world border,
  loaded terrain/airspace, and conservative terrain-relative altitude without
  loading chunks. Native WingMan owns route execution; route completion
  requests return to WingMan's configured home. This does not yet implement
  swarm route fan-out or prove flight behavior.
- A target loss creates one deterministic serpentine route for the first
  eligible SEARCHER only. The server requires a loaded reusable UAV with no
  active assignment and validates every leg's terrain and airspace before
  queueing a non-attack patrol mission. Search uses the same shared target UUID;
  last-known coordinates never become a replacement lock. Other members are
  not sent on overlapping routes.
- `SearchCoordinator` sector output remains planning data only. User-authored
  patrol routes are not fanned out across a swarm without validated separation.
- For target loss, preserve identity and last-known observation separately:
  `TRACKING -> LOST/SEARCHING -> REACQUIRED` only for the same UUID. Last-known
  coordinates produce a bounded search route, never a replacement lock.
  Expiry clears lost shared-contact state under the configured timeout. The
  searcher finishes its finite route and native recovery; it does not engage
  or automatically retask another member. Visible expiry reporting remains
  outstanding.
- Loiter/orbit geometry is a route request, not a promise of a stable orbit.
  Validate profile capability, terrain/airspace corridor, world bounds, and
  recovery path; physically validate native route response.

### Sensors, visual overlays, and designation

- Keep server target classification, contact identity, and mission telemetry
  authoritative. Client rendering consumes only permitted synchronized data
  and cannot acquire, promote, or replace a target.
- A future FLIR mode should be a client presentation toggle with bounded,
  freshness-limited contacts and explicit sensor/profile/range policy. Do not
  reveal all `EntityLivingBase` objects in an arbitrary client AABB or imply
  that ordinary visual detection is thermal sensing.
- Prefer existing mission/target HUD and world-space brackets before adding
  screen-space projection. The accepted FLIR v1 only restyles the existing
  world-space bracket. A general `worldToScreen` helper needs the actual 1.12.2
  camera matrices, viewport/scaling, behind-camera/clipping handling, and
  tests; no stable McHeli station camera projection API is documented.
- If using Forge render events, isolate client-only classes, bound per-frame
  work, and restore all modified GL state even on exceptional paths. No
  “Vulkan-compatible” claim is warranted for the current OpenGL-era runtime.
- Terrain tint/desaturation and thermal post-processing are separate visual
  features. Avoid global render-state leakage or shader dependencies without
  a verified 1.12.2 render pipeline.

### Flight safety and threat response

- Keep WingMan/native route state and the leased `NativeAircraftControlAdapter`
  as the established route/control boundary. Guidance is profile-specific and
  setpoint-based; native McHeliCE physics remains responsible for motion and
  collision integration.
- Terrain and water clearance use loaded world observations, profile AGL and
  climb-envelope checks, bounded 3D airspace sampling, and explicit unknown
  holds. They are not global pathfinding.
- Indoor navigation should initially fail closed with a bounded hold/replan
  when obstacle observations are unknown or blocked. Wall following requires
  its own validated perception and route-controller design.
- Projectile evasion is deferred until projectile identity/trajectory can be
  authoritatively observed with bounded tick cost and per-profile safe
  maneuver authority. A random `dodgeChance` does not establish threat
  detection, successful avoidance, or collision safety.
- Do not introduce PN/impact/detonation behavior through UI or a swarm target
  registry. Validate terminal commitment, payload capability, ammunition,
  target identity, and lifecycle outcome through existing mission/weapon
  boundaries.

## Data, network, and persistence contracts

| Data | Authoritative owner | Design constraint |
| --- | --- | --- |
| Operator identity and aircraft access | Server `EntityPlayerMP`, `UavControlAccess`, `MissionManager` | Resolve from live server pilot/station and ownership state for each mutating request. |
| Mission definition | Client drafts intent; server validates and creates mission | Versioned and bounded; GUI/NBT configuration is not runtime authority. |
| Mission state and target UUID | `RDPMission`, `RDPMissionWorldData` | Preserve UUID and dimension; terminal commitment cannot be silently replaced. |
| Swarm owner, members, roles, shared projection | `SwarmRecord`, `RDPUavSwarmWorldData` | Enforce owner and server configuration; reconcile member availability after load. |
| Live entity/terrain/airspace state | Server world and loaded entities through resolver/perception adapters | Do not force chunk loads; unknown is not clear; timestamps/confidence travel with staleable observations. |
| Client target marker and operator feedback | Server-synchronized telemetry plus client HUD | Display only; no client-side acquisition or authorization. |

Any future schema change must be versioned or backward-readable, bounded in
list/string/coordinate sizes, validated on load, and covered by migration tests.
Reject malformed UUIDs, unknown enum values, non-finite coordinates, invalid
dimensions, stale/replayed requests, and excessive packet rates through
repository-standard diagnostics and explicit client feedback. Keep logs
throttled and avoid logging unbounded data every tick.

No network or NBT change is needed for the accepted A+B decision: reuse the
current lock request and GUI workflow. If later work introduces a new packet,
include operator UUID binding, exact selected target identity, rate/freshness
policy, server scheduling, permission checks, explicit rejection reason, and
client response; do not send client-computed world rays as proof.

## Phased implementation and proof gates

The A+B, C, and E design decisions are accepted. Implemented source-level
behavior is not evidence of live flight. Continue in small reviewable slices:

| Phase | Scope | Source/test gate | Runtime gate |
| --- | --- | --- | --- |
| 0. Authority contract | Preserve GUI, no radio item, existing UUID lock, separate block Point task, no LOS claim. | Contract tests assert exact UUID, owner/operator rejection, missing-mission rejection, terminal immutability, and no nearby-target substitution. | Observe accepted and rejected operator requests in a clean 1.12.2 instance; report the existing RDP Core persistence blocker separately. |
| 1. A+B UX hardening | Reuse existing GUI/keybind and request. Improve only demonstrable stale-selection/error-reporting gaps after tracing all callers. Do not change targeting semantics without user approval. | Client selection tests and server handler tests cover current accepted/rejected paths and clear-lock cleanup. | Confirm controls in piloted and remote-station modes; confirm a block is not reported as an entity lock. |
| 2. Visibility research (optional) | Verify whether the server can observe the relevant McHeli station camera pose and ray. No gameplay promise until verified. | Bytecode/API evidence and deterministic ray policy tests, including unloaded/transparent/opaque block rules. | Confirm the server-observed ray follows the live station camera and cannot be spoofed. If impossible, retain current range/policy semantics and disclose the limitation. |
| 3. Presentation-only FLIR (implemented; runtime proof pending) | Monitor-tab GUI toggle and configurable F8 keybind share the same client preference; only the current server-reported target bracket is restyled. No discovery or terrain tint. | Focused tests cover default-off/toggle behavior and palette selection; the full Gradle test task and build pass. Config-file round-trip, GUI/key dispatch, stale-target cases, and GL-state behavior lack dedicated tests. | Forge client test for actual palette/bracket rendering, GUI and keybind input, target loss, unload/reload, GUI close/reopen, and unchanged server state remains outstanding. |
| 4. Sensor/reporting extension (separate proposal) | Add bounded freshness/confidence metadata and optional additional contacts only if a gameplay need and authoritative server source are identified. | Stale-data, UUID identity, dimension, visibility, packet-size, GL-state and rendering contract tests. | Visual/client-server test with station camera, target loss, unload/reload, and GUI close/reopen. |
| 5. Swarm autonomy (partial) | Validated same-dimension UUID contacts, deterministic non-strike roles, bounded same-UUID loss policy, and a single-searcher, terrain-checked search route exist. User patrol fan-out, multi-aircraft search allocation, and autonomous engagement remain out of scope until deconfliction and mission retasking are validated. | Deterministic allocation, degraded roster, persistence cadence, duplicate identity, bounded route, loss, and cleanup tests. | Multi-aircraft server run demonstrating contact sharing, native search route, measured separation, and recovery with telemetry. |
| 6. Waypoint/loiter flight (source implemented; runtime pending) | Mission Control submits finite patrol routes. Server validates terrain/airspace and native WingMan executes nodes, then requests configured-home recovery. | Route codec, bounds, mission persistence, and lifecycle transition tests; full build/test still required for subsequent edits. | Forge server/client run demonstrating accepted route, stable native loiter, route completion, native RTB, and parking. |
| 7. Advanced flight/response | Consider standoff or evasive behavior only after sensor, profile, terrain, route, and control contracts are proven. | Profile-specific bounded math and failure-path regression tests. | Measure native trajectories and collisions in the target runtime; do not infer success from setpoints or compile results. |

Existing runtime notes record a dedicated-server `Done` milestone but also a
separate RDP Core world-data reflection failure; no aircraft flight or payload
outcome was proven by that smoke run. Recheck
[`docs/api-verification-notes.md`](api-verification-notes.md) and
[`docs/mission-framework-map.md`](mission-framework-map.md) before reporting
runtime status.

## Decision record

**Decision:** Use the existing Mission Control GUI and existing entity UUID
crosshair-lock action for A+B; do not add a radio item, a second designator
action, or a server-verified LOS claim. Keep block point designation separate.

**Evidence:** Existing `GuiMissionControl`, `RdpUavClientKeys`,
`RdpUavNetwork.ServerAiTargetLockHandler`, `UavControlAccess`,
`TargetPolicy`, `RDPMission`, and `OperatorAimMissionService` implement
separate entity-lock and point-task paths. The lock handler validates the
server operator, aircraft, profile, exact loaded target, policy, range,
mission, and terminal target; no server-observed crosshair ray is part of that
contract.

**Alternatives rejected:** New physical radio/NBT authorization; a second
laser-pointer item/action; treating client aim coordinates as server proof;
strict LOS behavior without a verified server camera-pose contract.

**Compatibility/safety impact:** Preserves current 1.12.2 GUI, packets, target
identity, and clear-lock behavior. Makes the visibility limitation explicit;
does not widen current authority or assert physical flight/payload success.

**Validation:** Source paths and public project metadata were reviewed. The
full Gradle test task and build pass; live client/server validation remains a
separate proof gate.

**FLIR design decision:** Presentation-only current-target bracket, no
terrain tint or entity discovery; default off; monochrome white-hot bracket;
Mission Control GUI and configurable keybind share the same client HUD
preference.

**FLIR evidence:** `RdpUavTargetHud` already renders a world-space bracket for
the exact telemetry UUID and enforces freshness, dimension, loaded-entity, and
mission marker-state gates. The current server telemetry is not a multi-contact
sensor list.

**FLIR safety/compatibility impact:** Client-only, default-off preference and rendering;
no packet, mission/persistence schema, acquisition policy, or native flight
change. No through-wall or thermal-sensor claim.

**FLIR validation:** Focused tests, full Gradle tests, and build pass;
`git diff --check` passes. Live rendered appearance, config reload, and GUI/key
input behavior remain unverified because no Forge client runtime was exercised.
