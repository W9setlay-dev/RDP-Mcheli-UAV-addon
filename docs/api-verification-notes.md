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

- McHeliCE 1.5.1 exposes native `MCH_WeaponSet.use(...)`, predicted impact
  helpers, and WingMan target coordinates, but no projectile steering or
  per-projectile target UUID API.  RDP therefore uses native WingMan attack
  nodes and bounded moving-target coordinate updates only; it does not
  replace projectile physics.
- No stable native camera/seat projection API was present in the verified jar.
  The client HUD remains marker-only when projection is unavailable.
- `MissionNode` has no speed field and `WingmanEntry` has no speed setter in
  McHeliCE 1.5.1.  The configured one-way speed is consequently a bounded
  policy value retained for future native support; dispatch never writes
  aircraft motion or simulates speed outside WingMan.

- exact structure-model destruction (for example specific parasite nests) is not yet a first-class mission type
- dynamic military or bandit role semantics are not yet implemented as a validated runtime feature
- direct hostile-vehicle targeting is still conceptual and should be implemented only after a world-facing validation step

## Test method decision

The project uses dependency-free Java tests to validate mission-state transitions, target selection, persistence, and queue logic. Real root-tier McHeli behavior still requires a live 1.12.2 Forge client/server runtime.
## Rework safety boundaries

The addon now keeps target filtering server-authoritative: vehicle-classified
contacts are not hostile candidates, and deeply ground-embedded contacts are
rejected according to `rdp-uav-targets.cfg`. The policy only rejects a
contact; it never teleports or otherwise mutates entities. One-way attack
missions terminate with `ONE_WAY_TARGET_MISSED` when their locked contact
disappears rather than retaining a native loiter route indefinitely.
