# Terrain-Cleared Mission FSM, AI Authority, and Operator Trim

## What changed

This pass connects mission route intent to the addon flight controller, makes the active mission own aircraft movement while an operator remains in the station, adds bounded camera-look trim, and fails closed when terrain or corridor information is missing.

### Control ownership and tick order

- `RDPUavMod.ServerTickHandler` applies the addon command at server world-tick `START` with Forge priority `HIGHEST`.
- The checked-in McHeliCE 1.5.1 `WingmanTickHandler` also runs at `START`, at the normal event priority. The addon first renews its mission control lease. Transformer guards then skip WingMan's movement-writing helpers (`setRotYaw/Pitch/Roll`, throttle/engine, boost, hold-stop, and taxi position) for that leased aircraft.
- McHeliCE's aircraft entity update follows the world `START` callbacks. It receives the addon attitude and throttle setpoints and runs its normal aircraft physics/collision update. The lifecycle updates route intent at world `END`, which the controller consumes on the next `START`.
- `PacketPlayerControlBase.handlePilotControls` only copies pilot axis booleans into the aircraft. The injected post-handler hook clears those four axis flags for the authorized pilot when an active mission owns movement. Packet processing continues into the unrelated weapon, camera-mode, free-look, and station handlers.
- Fixed-wing and helicopter controllers are selected using both the configured vehicle profile and the actual `MCH_EntityHeli` runtime type. This avoids sending a native helicopter through the fixed-wing controller merely because its profile metadata is generic.

This establishes a single addon-owned movement writer during active route control. It does not establish that the chosen gains or resulting trajectories are flight-tested; only an in-game run can verify those.

### Operator trim

`OperatorTrimClientHandler` samples camera orientation relative to the aircraft's authoritative orientation and sends only yaw/pitch deltas. The server packet binds the sample to aircraft UUID, mission UUID, sequence, dimension, operator, and active control authority. It rejects replayed, stale, non-finite, excessive, or unauthorized input. There are no client-supplied world positions or absolute aircraft rotations.

`OperatorTrimState` accumulates profile-limited corrections and decays them when camera motion stops. A single guidance-mixing point rotates the route's horizontal direction for yaw trim and offsets the vertical setpoint for pitch trim. Fixed-wing control turns that into heading/bank response; helicopter control turns it into yaw/cyclic/collective response. The AI lease and `ControlOwner` remain active throughout.

The trim capture depends on the active UAV station's camera entity and synchronization behaving as expected. Verify its direction and lack of drift in-game.

### Terrain and corridor handling

`MinecraftWorldPerceptionAdapter` first checks whether a block is loaded and never requests chunk loading. It reports loaded/unloaded terrain, solid/fluid surface information, and local collision/fluid occupancy. Unknown or unloaded samples result in a bounded hold/replan reason.

`TerrainRoutePlanner` samples at most 49 points, no farther than 384 blocks, with lookahead based on speed. Transit setpoints use cruise AGL above the highest known surface in lookahead and clamp against profile and world-height bounds. Before entering rising terrain, a climb-envelope check compares the configured maximum climb rate with the time available at current ground speed. An infeasible route requests a bounded climb hold and is reconsidered on later ticks. Terminal guidance has its own ground-clearance check and limits the final contact exception to 1.5 blocks; it does not route a transit waypoint directly to target Y.

Terrain sampling is deliberately local and conservative. It is not a complete voxel pathfinder or threat planner; unknown cells remain blocked, and the controller may wait/replan instead of finding a detour.

### Profiles and durable execution stages

The existing `rdp-uav-vehicles.cfg` altitude entries are interpreted as terrain-relative values by the flight planner: `minimumExecutionAltitude` is minimum AGL, `executionAltitude` is cruise AGL, `maximumExecutionAltitude` is maximum AGL, and `stationRadius` configures fixed-wing orbit radius.

| Profile | Minimum / cruise / maximum AGL | Station radius |
|---|---:|---:|
| One-way fixed wing | 8 / 32 / 128 blocks | 96 blocks |
| Reusable fixed wing | 64 / 128 / 192 blocks | 256 blocks |
| Helicopter | 4 / 8 / 32 blocks | 0 (native hold) |

These are editable game-tuning defaults, not measured aircraft performance. Existing exact legacy defaults are migrated; customized values are preserved. Operator trim limits are configurable with `maximumYawCorrectionDegrees` and `maximumPitchCorrectionDegrees`.

`MissionExecutionStage` is persisted with parent/child mission IDs. Older saves derive the stage from their existing mission state/type. Reconnaissance reporting enters a profile-specific station hold: fixed-wing aircraft follow eight connected forward-orbit points, while a helicopter receives a hover setpoint at the planned center and AGL through the helicopter controller. Retasking requires a held reconnaissance mission with a persisted report, exact loaded target, supported aircraft, and executable payload/weapon. Assignment and aircraft-record reservations transfer together; expected failure leaves the parent assignment intact.

## Automated validation

Coverage added or extended for bounded and expiring trim, fixed-wing coordinated course response, helicopter control selection/response, terrain AGL/world-height limits, climb-envelope feasibility, mission-stage persistence/migration, linked-retask identity, mission movement authority, and transformer preservation of station camera/free-look handlers.

Run `gradlew.bat test` from the repository root. A pass validates source contracts and deterministic controller math. It does not prove aircraft trajectory, station camera alignment, weapon behavior, or impact timing in Minecraft.

## Developer live-test checklist

Use a copied world and the target RDP instance with the current addon build. With an operator present in the station, test each supported airframe through: straight level route; turn; climb/descent; small camera trim then release; rising terrain and water corridor hold; one-way reconnaissance station orbit; helicopter native hold; abort during transit and before terminal commitment; reconnaissance report followed by linked strike retask. Compare the `FlightControl` base/final/applied command and observed yaw/pitch/roll, position, velocity, AGL, trim age, and hold reason. Record cases where requested and observed motion diverge. Do not mark live flight behavior complete based on unit tests or log setpoints alone.
