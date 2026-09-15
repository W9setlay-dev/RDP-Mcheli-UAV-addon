# Bug reproduction matrix

| Bug / risk | Reproduction path | Impact | Likely cause | Source evidence |
| --- | --- | --- | --- | --- |
| stale WingMan entry treated as a live controller | mission remains queued while controller is inert | false mission execution or silent failure | stale mission node or entry state not filtered correctly | `MissionLifecycle` and `WingManFlightAdapter` |
| target not found despite valid entity | entity is loaded but radius or dimension does not match mission envelope | mission fails or is deferred | target search radius and dimension guards are too strict or not validated | `TargetResolver.find(...)`, `MissionLifecycle` |
| assignment conflict | same aircraft assigned to multiple missions | mission duplication | assignment registry not revalidated across reload/restart | `AircraftAssignmentRegistry`, `MissionManager.create(...)` |
| invalid target fields | mission created with malformed target config | mission rejected unexpectedly | missing validation in mission creation path | `RDPMission` constructor, `TargetSpec` |
| queue restoration mismatch | world is reloaded while mission remains queued | lost or duplicated queued work | restore path not sufficiently authoritative | `MissionManager.restoreQueued(...)` |
| point mission advances before flight | point is visible but the aircraft has not reached the native route node | false target acquisition or premature scan | lifecycle used distance alone instead of WingMan route progress | `MissionLifecycle`, `WingManFlightAdapter` |
| exact lock rejected on ordinary UAV | aim at a loaded mob or vehicle and press `L` | operator cannot request reconnaissance | lock path incorrectly required a destructive payload | `RdpUavNetwork.ServerAiTargetLockHandler` |
| moving target route becomes stale | locked entity moves more than four blocks | static approach/orbit and lost observation | native route was not rebuilt after target movement | `MissionLifecycle.refreshRoute(...)` |

## Recommended validation steps

- Validate each mission path with a deterministic unit test before live server use.
- Check queue state and aircraft assignment together whenever a mission is created or restored.
- Keep target-validation logic explicit and traceable by mission reason strings.
- Log mission failure reasons with aircraft UUID, mission UUID, dimension, tick age, and target state.
- Verify `L` success and rejection are visible to the operator; do not rely on silent packet failure.
