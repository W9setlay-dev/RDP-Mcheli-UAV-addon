# Audit report

## Mission flow

The current flow is centered on `MissionManager`, `MissionLifecycle`, and the persisted world data layer.

1. `MissionManager.create(...)` reserves aircraft assignment, registers the aircraft owner, persists the mission to `RDPMissionWorldData`, and enqueues it.
2. `MissionLifecycle.tick(...)` restores persisted mission state, dispatches queued missions, and updates active mission state.
3. `dispatch(...)` resolves the live aircraft, validates fuel, attack capability, and target availability, then hands off to `WingManFlightAdapter.dispatch(...)`.
4. The active mission path may recover target loss, request return, or fail when the aircraft is lost or the target becomes invalid.

### Current mission flow: strengths

- Explicit aircraft assignment reservations prevent duplicate mission claims.
- Mission persistence exists through `RDPMissionWorldData` and `MissionNbtCodec`.
- Target acquisition and selection logic are source-level and deterministic.
- The architecture keeps mission state transitions behind `RDPMission.transitionTo(...)`.

### Current mission flow: gaps

- Queue restoration is idempotent and assignment reconstruction excludes all
  terminal states; live restart and multi-client race behavior remain
  runtime-unverified.
- `RDPUavMod.ServerTickHandler` is still a placeholder abstraction rather than a full execution scheduler.
- Mission completion and failure are handled by state transitions but not by a robust mission outcome pipeline.

## Target flow

The target flow begins in `TargetSpec` and is resolved through `TargetSelector` and `TargetResolver`.

- `TargetSpec.manual(...)` accepts explicit registry IDs.
- `TargetSpec.automatic(...)` accepts a namespace.
- `TargetSpec.hostile(...)` maps to a hostile namespace policy.
- `TargetSelector.select(...)` finds the best valid candidate by priority then distance.
- `TargetResolver.find(...)` resolves live entities near a point using `EntityList` and a radius box.
- `TargetClassifier` recognizes hostile namespace families such as `srparasites`, `lycanitesmobs`, `scp`, `techguns`, and Minecraft hostile entities.

### Target flow: strengths

- Explicit manual selection is deterministic and safe.
- Automatic hostile matching can cover modded hostile mobs and generic hostile categories.
- The mission envelope records both search radius and observation distance.

### Target flow: gaps

Point designation remains an explicit server-raytraced block position and is
rejected when the active aircraft profile is unknown or the point is outside
that profile's detection range; live camera/render confirmation is still
runtime-unverified.

- Target classification is still a simple registry-string heuristic, not a real threat model.
- Structure targets are not model-aware; this is a world point / entity abstraction, not a structural “specific model” attack planner.
- There is no robust threat level model for bandits, military vehicles, parasitic nests, or dynamic anomalies.

The captured session also showed a route completing while the locked entity was
unavailable. The lifecycle now preserves UUID identity and enters bounded
`WAITING_FOR_TARGET` recovery; it does not treat route completion as payload
success.

## Movement flow

Movement is routed through the WingMan adapter layer rather than custom direct movement commands.

- `WingManFlightAdapter` owns the native WingMan mission-node submission boundary.
- `MissionLifecycle` coordinates the mission objective and target point.
- `KamikazeImpactDetector` supplies hazard checks for detonation conditions.
- `KamikazePilotOverride` allows operator-targeted self-destruct logic.

### Movement flow strengths

- The addition keeps motion under the real aircraft controller rather than faking movement.
- The mission system is explicitly not a fake physics abstraction.
- A captured CRM session observed native WingMan route acceptance and
  `FLY_TO reached`; this proves route execution evidence for that session, not
  universal aircraft convergence.

### Movement flow gaps

- The current code does not provide a complete route manager, patrol planner, or waypoint recovery loop.
- It does not recognize hostile-vehicle categories or mission guidance based on terrain and cover.
- It does not maintain persistent route/coverage state across multiple UAVs.

## Network flow

The packet path is implemented in `RdpUavNetwork` and the GUI/HUD layer uses the server-synchronized mission summaries.

- The server validates ownership and mission state before aborting a mission.
- HUD telemetry is synced from the server world tick.
- Client requests are treated as commands that the server verifies before mutating state.

### Network flow strengths

Point, square, and entity requests are validated on the server before mission
creation or target mutation.

- Client/server separation is explicit.
- Mission aborts are server-authoritative.

### Network flow gaps

- The system has not yet implemented a generalized mission approval pipeline or a multi-UAV operator dashboard.
- There is no rich mission assignment feed for friendly groups or dynamic target-sharing.

## Persistence flow

Mission persistence is handled by `RDPMissionWorldData` and `MissionNbtCodec`.

- Mission records are serialized to world NBT.
- `RDPMission.restore(...)` rehydrates mission state.
- `RDPScanReport` has its own report persistence logic.

### Persistence flow strengths

- Mission records are world-scoped and can survive reloads.
- A single state machine is used for transitions.

### Persistence flow gaps

- Assignment and queue restoration are source-level authoritative and
  idempotent; live restart recovery remains runtime-unverified.
- Malformed NBT records now fail closed with contextual validation errors.
- There is no robust multi-UAV coordination persistence or route bookkeeping.

## RDP hostile-world value

The real RDP environment is hostile and dynamic: parasites, hostile mobs, military-style threats, bandit behavior, vehicles, changing terrain, and hostile structures produce a constant need for:

- persistent surveillance
- target prioritization by risk and mobility
- area coverage around critical assets
- route planning through unsafe terrain
- persistent tracking of the same hostile site or nest
- safe recovery when a mission is interrupted or fails

The current addon provides solid building blocks but not the full hostile-world utility layer needed for a realistic RDP mission loop.

## Known root causes and unresolved questions

- Hostile-world mission logic is not yet converted into a threat model with dynamic roles and constraints.
- The current target system does not distinguish between legitimate mission targets and nuisance entities.
- Structure-focused targeting is not a first-class mission type.
- Multi-UAV coordination and reassignment remain conceptual rather than implemented.
- The system lacks a mission recovery model for mission interruption, lost signal, lost target, or terrain anomaly.

## Conclusion

The repository already has the core mission architecture. The rework should expand it toward an RDP-specific, hostile-world mission system that prioritizes survival utility, persistent observation, threat reporting, route management, and resilient UAV automation rather than simply "move the aircraft somewhere" scripting.
