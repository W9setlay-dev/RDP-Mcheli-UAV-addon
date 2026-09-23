# AI lock and movement authority validation

## Validated source behavior

- The GUI sends target intent or an explicit clear request; it does not write
  aircraft motion.
- The server resolves the controlled aircraft and target UUID, checks operator
  ownership, dimension/loading, collision eligibility, target policy, profile
  lock support, and lock range before changing the mission.
- A committed entity target remains the mission UUID. The server does not
  replace it from a client camera position or nearby candidate.
- `MissionLifecycle` submits `WingManFlightAdapter.dispatch`,
  `refreshObservationRoute`, `refreshBoundedDetour`, or native WingMan attack
  state. `WingManFlightAdapter` changes route entries and commands, not
  aircraft position, velocity, yaw, or pitch.
- `MissionMovementAuthority` classifies active mission, terminal guidance, and
  recovery states as native-WingMan-owned. Operator flight input is only
  allowed for an explicitly manual owner.
- A lock request without a target now performs the same explicit server-side
  release/abort path as the GUI clear-lock request, matching the user-facing
  "press again to restore player control" contract.

## Not proven

No addon source currently consumes raw mouse movement to drive aircraft
motion, so there is no mouse controller to integration-test in this
repository. Live proof still requires a Forge/McHeli client/server session:

1. spawn and operate a supported UAV;
2. create a mission and acquire a 3D target lock;
3. confirm WingMan route state advances while camera input changes;
4. confirm aircraft trajectory follows native route state rather than camera;
5. clear the lock and confirm player control is restored;
6. verify target UUID and mission state through reload and packet replay.
