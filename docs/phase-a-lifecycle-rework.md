# Phase A: authoritative mission lifecycle rework (historical snapshot)

This phase document records the initial architecture. Its statement that
WingMan owns aircraft movement was superseded by Phase 15 and the current
leased RDP airframe-controller path; WingMan now sequences route nodes.

## Scope

Phase A establishes the smallest reliable mission-core contract before
profile, weapon, networking, UI, and runtime work is expanded:

```text
mission definition -> server validation/assignment -> dispatch -> execution
-> payload/outcome observation -> recovery or terminal cleanup
```

`RDPMission` remains the durable domain object. `MissionLifecycle` remains the
server tick coordinator. WingMan remains the aircraft movement authority.
Client requests and native observations must enter through the existing server
boundary; they must not mutate mission state directly.

## Implemented in this phase

- Repeated transitions to the current state are idempotent and no longer move
  `lastUpdateTick`, so a retry cannot extend a mission indefinitely.
- Abort, completion, failure, and kamikaze detonation operations are monotonic
  and safe for duplicate events.
- Every state transition records a reason for operator diagnostics and
  persistence.
- A hard mission deadline is evaluated from `createdTick`, independent of
  recoverable-state refreshes. Expired queued and active missions become
  `FAILED` with `MISSION_TIMEOUT`.
- Persistence schema 4 stores the last transition reason, lifecycle phase, and
  control owner while retaining compatibility with schema 1 through 3 records.

## Decision record

**Decision:** Keep the existing granular `MissionState` values as the
compatibility model and harden their mutation boundary instead of replacing
the enum in one migration.

**Evidence:** Existing network, persistence, WingMan, and tests directly use
these values.

**Alternative rejected:** A new parallel lifecycle enum would create two
sources of truth and increase migration risk.

**Safety/compatibility impact:** Existing callers remain source-compatible;
terminal operations cannot resurrect or overwrite a completed mission, and
timeouts cannot be postponed by same-state retries.

Control ownership is projected from the authoritative mission state rather than
only from the compatibility phase. Terminal states, including confirmed
kamikaze detonation and aircraft loss, map to `SAFETY`; recovery and return
states map to `RECOVERY`; and `KAMIKADZE_TARGET` alone maps to
`TERMINAL_GUIDANCE`. The same projection is reconstructed on persistence
restore, preventing a stale `MISSION_AI` owner after reload.

**Validation:** Focused lifecycle tests, full Gradle tests, build, and
`git diff --check`. Live Forge/Minecraft mission execution remains unverified.

## Next phases

1. Profile capability guards and explicit unsupported combinations. (Phase B
   begins with the server dispatch guard and profile validation contract.)
2. Weapon capability/payload outcome contracts.
3. Server networking and persistence reconciliation.
4. Operator UI and world-space status rendering.
5. Isolated runtime smoke validation.
