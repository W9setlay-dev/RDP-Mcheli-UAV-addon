# Runtime test matrix

| Area | Command | Result | Notes |
| --- | --- | --- | --- |
| repository inspection | `git --no-pager status --short` | PASS | Confirmed working tree state |
| repository revision | `git --no-pager log -1 --oneline` | PASS | Resolved revision `1429d1f` |
| dependency inspection | `Get-ChildItem -Depth 2` | PASS | Confirmed project structure and runtime jar locations |
| code search | `grep -RInE 'TODO|FIXME|HACK|dummy|stub|placeholder|unsupported|not implemented|return null|return false|return true|FAILED|OUT_OF_RANGE|SelfDestruct|lock|unlock|target|mission|override|wingman|movement|rotation|velocity' .` | PASS | Source-level scan performed for stub and dead-code hotspots |
| baseline unit test | `.\gradlew.bat test --no-daemon --quiet` | PASS | Source-level tests passed |
| Forge runtime compilation | `./gradlew clean build` | BLOCKED | Exact Forge runtime setup depends on a missing userdev artifact |
| static analysis / packaging | `./gradlew check` | BLOCKED | Same Forge runtime blocker; substitute verification is the successful source test run |
| live Minecraft client/server test | unavailable in this environment | BLOCKED | Requires real Forge client/server runtime |
| JAR inspection | project JAR in `build/libs` | PASS | Build artifact exists |
| source validation | `javac` / project tests | PASS | Source-level validation only |
| exact entity/vehicle lock semantics | `.\gradlew.bat test --no-daemon` | PASS | Lock is non-destructive and server-revalidated; live McHeli acceptance remains unverified |
| native route refresh semantics | `.\gradlew.bat test --no-daemon` | PASS | Route rebuild code compiles and is covered by lifecycle source validation; live turning/orbit remains unverified |
| captured native WingMan route execution | CRM `latest.log` 2026-09-23 | OBSERVED | Server log records accepted `FLY_TO` routes and `FLY_TO reached`; no current process was running during this validation, and trajectory convergence remains unproven |
| captured kamikaze effect confirmation | CRM `latest.log` 2026-09-23 | NOT OBSERVED | Log contained a detonation-labeled path but zero `IMPACT_OBSERVED`/`EFFECT_CONFIRMED` events; it was treated as false success and the source path was corrected |
| single-press exact lock / separate clear action | `.\gradlew.bat test --no-daemon` | PASS | Client now sends exact crosshair UUID on `L`; `J` and dashboard clear request recovery; live packet behavior remains unverified |
| production artifact build | `.\gradlew.bat build --no-daemon` | PASS | Reobfuscated addon JAR built successfully |
| RDP instance deployment | copy rebuilt `rdp-uav-addon-0.1.0.jar` to an RDP instance | NOT ATTEMPTED | No JAR was copied during the current rework; deployment is intentionally outside source validation |
| final sequential validation | `.\gradlew.bat test --no-daemon` then `.\gradlew.bat build --no-daemon` | PASS | Tests and reobfuscated build passed sequentially; no deployment claim is made |
| guided weapon capability and native seeker contract | `.\gradlew.bat test --no-daemon` | PASS | Verified objective compatibility, UUID continuity, native API decision handling, and explicit unsupported outcomes |
| guided weapon live launch/seeker/impact | controlled RDP gameplay session | BLOCKED | Source and native API wiring are complete, but this environment cannot reliably drive a controlled in-game launch and observe seeker acquisition, trajectory, impact, or damage |

## Remaining risk

The runtime matrix is deliberately limited to what is available in this environment. The mission and guided-weapon systems are proven as source-level integrations against the local McHeliCE 1.5.1 API, but not as a live in-game autonomous flight or projectile-effects pipeline. The next runtime pass must verify visible fixed-wing turning, moving-target tracking, orbit behavior, lock/clear feedback, native seeker acquisition, terminal approach, impact, and damage in the installed RDP instance.
