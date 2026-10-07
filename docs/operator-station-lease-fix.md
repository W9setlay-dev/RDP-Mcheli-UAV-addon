# Operator-station lease fix

## Summary

Movement authority must follow the mission lifecycle: release the addon lease when the mission becomes terminal, but retain it while an active mission still owns flight, including station holding and the legacy direct-launch observation state. The pilot-input transformer already stops suppressing flight axes when the authoritative mission state no longer requires addon/WingMan control.

## Evidence from the repo

The native flight-control adapter enforces the active mission and movement authority gate:

```java
!MissionMovementAuthority.claimControl(mission, aircraft.getUniqueID(), tick)
|| !MissionMovementAuthority.canIssueNativeControl(mission, aircraft.getUniqueID(), tick)
```

This occurs inside `NativeAircraftControlAdapter.apply(...)` before writing aircraft setpoints.

The mission lifecycle is responsible for progression and terminal transitions. It must release the lease on every terminal state:
- `COMPLETE`
- `FAILED`
- `ABORTED`
- `KAMIKADZE_DETONATED`
- `AIRCRAFT_LOST`

`HOLDING` and `DIRECT_LAUNCH_COMPLETE` are not terminal or control-release states. `HOLDING` remains an active recovery route; `DIRECT_LAUNCH_COMPLETE` is a legacy persisted state that is still observed by the active mission lifecycle. Releasing the movement lease at either state transition would permit the active mission's writer to be interrupted or race to reclaim it.

## Implemented behavior

1. A terminal transition releases only the lease matching that mission and aircraft.
2. The lifecycle emits a `LEASE_RELEASED` debug event with mission UUID, aircraft UUID, tick, reason, state, and control owner.
3. Terminal states map to `ControlOwner.SAFETY`; `nativeWingManRequired` becomes false, so `suppressOperatorFlightInput` leaves subsequent native pilot-axis input untouched.
4. Active handoff/route states retain the mission lease and continue to suppress competing pilot flight axes.

The release occurs in the same authoritative mission transition. Manual axes are no longer filtered by the next native pilot-control packet processed after that transition; actual in-game packet/tick timing remains unverified.

## Logging

The lifecycle debug event includes:
- mission UUID
- aircraft UUID
- tick
- reason
- terminal state and control owner

## Validation status

- Source-level behavior: terminal states release their own lease; active holding and legacy launch-observation states retain it.
- Focused automated coverage: all five terminal states plus active `HOLDING` and `DIRECT_LAUNCH_COMPLETE`.
- Runtime validation: not attempted in this environment.
- Final claim: source and unit behavior are testable; live pilot-input restoration remains unverified until exercised in a matching client/server session.
