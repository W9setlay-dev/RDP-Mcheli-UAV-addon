# Guided-weapon GUI status

## Server-owned capability data

`GuidedWeaponRegistry` maps exact McHeli definition/type pairs to a configured
native behavior and allowed objective set. When the server sends mission data,
it includes every mounted weapon with its behavior, objectives, ammo count,
availability, and reason:

- `AVAILABLE:OK` only when a registered behavior is supported and ammunition
  is present;
- `BLOCKED:UNSUPPORTED_GUIDANCE` for registered unsupported behavior;
- `BLOCKED:NO_AMMUNITION` for a supported capability without ammo;
- `BLOCKED:UNREGISTERED_CAPABILITY` when a mounted weapon has no exact registry
  entry.

The client colors those server-produced rows, displays the reason code, and
limits selection to available weapon names. If the list exceeds six rows, it
reports how many additional rows are omitted.

## Objective and launch decisions

The row shows allowed objective types. An objective-specific mismatch remains
a server validation result (`UNSUPPORTED_OBJECTIVE_COMBINATION`) and is not
overridden by the client selector. `GuidedWeaponAdapter` returns explicit
decision reasons for unavailable targets, safety holds, capability mismatches,
and native handoff rejection. Mission telemetry/reporting carries the resulting
mission failure/outcome; the inventory rows do not predict that a later
launch will succeed.

Weapon selection alone is not proof of arming or launch. Launch acceptance,
projectile tracking, impact observation, effect confirmation, and unknown
outcome remain separate lifecycle states.

## Native API evidence and limits

The installed McHeli CE 1.5.1 jar is available and its public signatures were
inspected. The addon uses the existing `GuidedWeaponAdapter` boundary and
verified native behavior mappings; a similarly named weapon is never silently
substituted for an unsupported definition/objective. Camera thermal modes do
not establish a weapon seeker or guidance capability.

Source/API inspection and unit tests do not prove that each configured native
handoff works in a live client/server runtime. Such behavior remains subject to
the matching Forge/McHeli runtime matrix.

## Validation status

- Server rows include availability reason, behavior, objectives, and ammo:
  source-implemented.
- Unknown or unsupported inventory entries are visible but cannot be selected.
- Capability availability reason codes: covered by unit tests.
- Native launch, projectile tracking, and impact/effect outcomes: not runtime
  verified in this session.
