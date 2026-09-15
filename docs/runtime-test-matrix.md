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
| single-press exact lock / separate clear action | `.\gradlew.bat test --no-daemon` | PASS | Client now sends exact crosshair UUID on `L`; `J` and dashboard clear request recovery; live packet behavior remains unverified |
| production artifact build | `.\gradlew.bat build --no-daemon` | PASS | Reobfuscated addon JAR built successfully |
| RDP instance deployment | copy rebuilt `rdp-uav-addon-0.1.0.jar` to the existing addon slot | PASS | Replaced only the existing addon JAR; debug config remains `B:enabled=false` |
| final sequential validation and deployment | `.\gradlew.bat test --no-daemon` then `.\gradlew.bat build --no-daemon` | PASS | Tests and reobfuscated build passed sequentially; final JAR deployed and SHA-256 verified |

## Remaining risk

The runtime matrix is deliberately limited to what is available in this environment. The mission system is proven as a source-level architecture, but not as a live in-game autonomous flight pipeline. The next runtime pass must verify visible fixed-wing turning, moving-target tracking, orbit behavior, and lock/clear feedback in the installed RDP instance.
