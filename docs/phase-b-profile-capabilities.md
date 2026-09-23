# Phase B: explicit aircraft profile capabilities

Phase B wires the existing profile model into the server dispatch boundary.
Profiles are now a compatibility policy, not only descriptive metadata.

## Implemented

- `VehicleCapability.validateMission` returns deterministic rejection reasons.
- Unknown UAV profiles fail closed with `UNSUPPORTED_AIRCRAFT_PROFILE`.
- Server dispatch validates UAV mission intent before fuel, target, route, or
  payload execution.
- Profile altitude configuration rejects non-finite values and preferred
  altitudes outside the declared minimum/maximum range.
- Piloted non-UAV aircraft retain their separate native weapon path; this
  phase does not claim autonomous WingMan control for them.

## Supported policy

- Configured one-way profiles may execute declared strike and square-strike
  intent, subject to payload and terminal validation later in the lifecycle.
- Configured reusable profiles may execute strike intent and remain eligible
  for recovery behavior.
- Unknown profiles are not silently treated as generic UAVs.

This is source- and test-verified policy. Native aircraft response and live
mission completion remain unverified until the isolated Forge/McHeli runtime
smoke test.
