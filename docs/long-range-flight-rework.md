# Long-range mission altitude and terminal execution

## Behavior and authority

Mission launch definitions may carry an optional integer cruise world-Y.
Blank input preserves the existing aircraft-profile default. The GUI request
is validated by the server against the active dimension (`1..world height - 2`),
then stored in each authoritative mission. The runtime mission NBT schema is
10; missions without the field retain a legacy unresolved value until their
first server route dispatch, when the old profile/target-derived altitude is
resolved once and persisted.

The altitude is an absolute world coordinate, not AGL. Terrain planning may
raise a requested value when the corridor would violate the aircraft profile's
minimum AGL or dimension bounds; it must not lower the aircraft to satisfy the
requested Y. The fixed-wing and helicopter controllers retain the explicit
setpoint instead of applying their profile execution-altitude clamp. Existing
terrain and airspace checks remain active.

`APPROACHING`, `ATTACK_TARGET`, and `ON_TARGET` are target-tracking/preparation
stages. Only the server-authorized `KAMIKADZE_TARGET` state maps to
`TERMINAL`, which activates terminal terrain/pitch behavior. During terminal
execution the planner uses the resolved target endpoint and its calculated
approach XYZ; before then, mission routing retains the cruise world-Y.
Lease acquisition/arbitration is unchanged.

Mission-definition packet v5 appends the optional cruise-Y field; v3 and v4
layouts remain accepted without it. The square-strike request also carries an
optional backward-compatible altitude extension. Mission definitions persist
this input through the local NBT codec. Swarm launch copies the validated value
into each member's independent mission snapshot; it does not couple member
phases.

## Manual Minecraft validation

Status: `NOT RUN` until an operator supplies live client/server evidence.

Build identifier:

- Addon JAR: `build\libs\rdp-uav-addon-0.1.0.jar`
- SHA-256: `12FC885BDA04FD45D44809DF8B600A61052CC620AE0DEE91B15E8508C1489C4B`
- Runtime tuple: Minecraft 1.12.2, Forge 14.23.5.2859, McHeliCE 1.5.1,
  RDPCore 1.0.0, Java 8

Use a disposable test instance and world. Keep the aircraft, target, and route
chunks loaded. Do not replace files in an active instance or use a production
world for this test.

1. Open mission control, select **EXECUTION**, and enter an integer absolute
   cruise world-Y (for example `140`). Validate and launch a supported point mission whose
   endpoint is well beyond the normal detection range.
2. Capture the configured Y, mission UUID, aircraft UUID, target XYZ, dimension,
   aircraft Y over time, mission state/stage, and movement owner. Confirm the
   route advances toward the endpoint while `executionStage` is not `TERMINAL`
   and `cruiseAltitudeY` remains the configured value. Terrain may demand a
   higher `routeTargetY`; it must never silently replace the stored cruise Y.
3. Repeat with the altitude field blank. Confirm the server resolves a
   profile-derived cruise altitude once and retains the same snapshot after a
   save/reload.
4. In a disposable test world, run one authorized one-way terminal mission.
   Confirm preparation/attack states retain cruise routing, then only
   `KAMIKADZE_TARGET` changes the stage to `TERMINAL`. Confirm the terminal
   waypoint follows the planner's approach XYZ toward the resolved target Y.
5. Launch two eligible swarm members with the same configured cruise Y. Record
   each mission UUID/state independently; pause or hold one member and confirm
   the other continues its own phase transitions and altitude control.
6. Repeat with a value above `world height - 2`. The server must reject the
   request without creating a mission. Save/reload an accepted mission and
   confirm its cruise-Y snapshot remains unchanged.

Expected diagnostics include accepted cruise-Y on the mission, periodic
`RDP_FLIGHT_COMPUTER` records showing `executionStage`, `cruiseAltitudeY`, and
`routeTargetY`, and explicit terrain-hold reasons if terrain/airspace prevents
progress. Failure signatures include early `TERMINAL` stage before
`KAMIKADZE_TARGET`, a controller setpoint clamped to profile altitude despite
an explicit Y, target routing using cruise Y after terminal authorization,
swarm members changing state together, or an invalid dimension request being
accepted. Capture `latest.log`, screenshots of the GUI and aircraft altitude,
the mission snapshot before/after reload, and the exact tested JAR hash.
