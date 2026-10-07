# Entity lock

## Authority

The client sends an exact entity UUID and an explicit clear flag as an
operator request. It does not send confidence, target state, or ownership.
`RDPMission.targetId` is the committed mission identity; target observations
and HUD snapshots are projections, not alternate authorities.

## Client request path

Both the key binding and mission-control action use
`ClientAimRayTracer.trace(...)` from the active Minecraft render-view entity.
The ray checks blocks first and selects an entity only when its actual bounding
box intersects the unobstructed ray. It never falls back to a nearby target or
a stale captured entity. A block hit or miss produces a local explanation and
sends no lock request.

The server packet is `AiTargetLock` (message ID 9, client to server), carrying
only a UUID string and `clear` boolean. Clearing is an explicit request;
an empty UUID with `clear == false` is invalid and must not be treated as a
clear operation.
The server emits structured `ACCEPTED`, `REJECTED`, and `CLEARED` audit events.
Resolution distinguishes `INVALID_TARGET`, `TARGET_NOT_LOADED`,
`TARGET_DEAD`, and `TARGET_WRONG_DIMENSION`; range failures report
`TARGET_OUT_OF_RANGE`. A UUID not present in the operator's loaded world is
reported as not loaded; the server does not claim to know whether it exists in
an unloaded chunk or another server world.

The mission suggestion action uses `AiTargetLock.EntityTargetAssignment`
(message ID 20, client to server). It sends the selected suggestion's entity
UUID along with the mission UUID; a registry ID is presentation metadata only
and is never used to resolve an arbitrary same-type entity. The server resolves
the exact UUID and applies the same target policy, profile range, and
aircraft-to-target sightline checks before assignment.

## Server validation and mutation

The server schedules the request on its thread and verifies:

- the sender operates and is authorized for the controlled aircraft;
- the selected vehicle profile supports target lock;
- the UUID resolves to a loaded entity in the operator's current world;
- the target is not the aircraft or operator and is selectable by policy;
- the target is within profile lock range;
- the aircraft-to-target bounds sightline is not blocked by a block;
- a mission exists and a committed terminal target is not replaced.

The sightline check is deliberately conservative: the server has no verified
authoritative access to the current remote camera pose, so it traces between
the centers of the aircraft and target bounds. It does not claim to validate
the exact pixels visible in the client camera.

Only after validation does the server call
`RDPMission.applyOperatorEntityLock(...)` and mark mission world data dirty.
Rejections return an explicit reason in the server chat/log path. Clearing is
owner-authorized and aborts the active mission with
`TARGET_LOCK_CLEARED`; a terminal-guidance commitment cannot be cleared.
During mission execution, disappearance/out-of-range invalidation and expiry
after the target-loss grace period emit one `LOCK_INVALIDATED` and
`LOCK_EXPIRED` event respectively. `TargetLock.Validation` reports LOST or
EXPIRED while `RDPMission.targetId` retains the committed UUID for a later
same-identity reacquisition. Recovery emits `LOCK_REACQUIRED` and persists
reason clearing. The status reason is persisted, but reloading does not restore
a live lock or contact observation.

## Persistence and reacquisition

The mission NBT codec persists the committed `targetUuid`. The live
`TargetLock` observation and `ContactTracker` history are not persisted as
fresh observations. After reload, the lifecycle resolves the committed UUID
again; it does not substitute another entity if that identity is unavailable.

`ContactLifecycle` tracks runtime observation states. It is distinct from the
mission's committed target identity and must not be treated as the same state
machine.

## HUD contract

Client telemetry and the target HUD are presentation-only. An entity bracket
is drawn around the loaded entity's actual world-space AABB only when the
mission marker is visible and the client world matches the synchronized
dimension. Point markers are world-space 3D crosses with a client-side block
occlusion check. Neither rendering path changes mission state.

The server emits rejection reasons as operator feedback; a dedicated
lock-rejection GUI snapshot is not currently defined. Runtime packet ordering
and end-to-end GUI synchronization have not been validated in a live session.

## Validation status

- Client intent, both server assignment paths, mission mutation, and target
  UUID persistence: source-verified.
- Empty non-clear UUID, terminal lock protection, identity reacquisition, and
  packet UUID round-trip: covered by the contract/lifecycle test set where
  testable without a live `World`.
- Live camera ray, server line-of-sight, packet timing, and reload behavior:
  not runtime-verified.
