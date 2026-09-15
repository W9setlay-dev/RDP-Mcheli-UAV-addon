# Proposed architecture for a hostile-world UAV mission system

## Design objective

The addon should behave like a persistent autonomous support system for a dangerous, dynamic RDP world rather than a loose collection of movement commands. In hostile terrain, the most valuable features are those that answer operational questions before a player commits to a risky action.

## Priority features

### 1. Situational awareness and reconnaissance

- Persistent local scan loops around player bases, roads, depots, and anomaly zones
- Periodic observation of known hostile hotspots and previously discovered structures
- Risk-weighted alerting when hostile entities appear near a protected target

### 2. Target filtering and entity prioritization

- Distinguish hostile mobs, military vehicles, bandits, parasitic nests, and non-combat world hazards
- Use mission-specific filtering so a recon UAV does not waste time on irrelevant targets
- Track the target’s last known location and confidence even when it breaks line of sight

### 3. Area coverage and patrol planning

- Patrol routes around supply lines, settlement edges, and event zones
- Search patterns around new anomalies or recently discovered parasite nests
- Multi-UAV route division so the same region is not repeatedly scanned without value

### 4. Mission resilience and recovery

- Reacquire a target if it dropped out of loaded entities or moved behind terrain
- Return to a safe holding point when fuel, hostile fire, or target loss occurs
- Resume a mission from its last valid observation rather than abandon the entire task

### 5. Multi-UAV coordination and reassignment

- Track a mission by aircraft, owner, target, and zone
- Reassign a UAV if its original target is lost or the aircraft becomes unavailable
- Prevent duplicate coverage of the same search sector unless explicitly intended

## Layering model

```text
Mission Manager
    |
    +-- Mission Planner
    +-- Mission Scheduler
    +-- Decision Engine
    +-- Target Service
    +-- Lock Manager
    +-- Navigation Controller
    +-- Role Controller
    +-- Operator Override Controller
    +-- Vehicle Capability Resolver
    +-- McHeliCE/Wingman Adapter
    +-- Persistence Service
    +-- Diagnostics and Logging
    +-- Network Synchronization
```

## High-value hostile-world roles

The role model should remain simple and realistic:

- RECONNAISSANCE
- PATROL
- SEARCH
- INTERCEPT
- ESCORT
- TARGET_TRACK
- ASSET_PROTECTION
- DISPOSABLE
- GENERAL_PURPOSE

## Mission state model

The existing mission state machine can be kept as a compatibility layer while adding a clearer hostile-world model:

```text
CREATED -> PLANNING -> ASSIGNED -> SEARCHING -> TARGET_FOUND -> TRACKING -> EXECUTING -> ASSESSING -> COMPLETED
                                \-> ABORTED
                                \-> NO_TARGET
                                \-> LOST_TARGET
                                \-> TIMEOUT
                                \-> VEHICLE_UNAVAILABLE
                                \-> EXECUTION_ERROR
```

## Real RDP-specific mission patterns

### Patrol/route monitoring

Use for supply roads, village perimeters, and high-risk transit points.

### Search and sweep

Useful for parasite nests, hostile spawns, unknown anomalies, or hidden military vehicles.

### Site protection

Protect a base, tower, warehouse, mobile depot, or important anomaly site from later hostile pressure.

### Persistent observation

Keep a UAV above a site and report changes in entity density or target movement.

### Target tracking

Track a hostile entity or vehicle even when the target leaves a server-loaded chunk or is intermittently obscured.

## Compatibility and feasibility

This proposal is compatible with the project’s current architecture because:

- mission state, queueing, and target selection already exist
- the server remains the authoritative state owner
- WingMan remains the controller boundary
- the addon can add threat filters and route state without replacing native flight logic

The rework should remain deliberately bounded to what can be observed in-game and measured by source-level tests.

## Design rules

- Do not add a feature merely because it sounds advanced.
- Require every feature to solve a real RDP survival problem.
- Keep mission behavior observable in Minecraft and traceable via logs and mission state.
- Prefer a simple persistent utility loop over speculative AI abstractions.
- Require operator visibility before dangerous actions are launched.
