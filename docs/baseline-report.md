# Baseline report

## Repository and environment

- Repository root: `D:\Forge Modding\rdp-uav-addon`
- Build tool: Gradle (`gradlew.bat` / `gradlew`)
- Java version: configured in `build.gradle` for `sourceCompatibility = 1.8` and `targetCompatibility = 1.8`
- Minecraft target: `1.12.2`
- Forge target: `1.12.2-14.23.5.2768` in the active build file
- McHeli CE target: `libs/mcheli-ce-1.5.1.jar`
- RDP Core target: `libs/rdpcore-1.0.0.jar`
- Test framework: JUnit 4.13.2
- Runtime launch configuration: Forge 1.12.2 runtime, with a `run` directory and FML core plugin configuration in `build.gradle`
- Output JAR: `build/libs/rdp-uav-addon-0.1.0.jar`

## Baseline commands executed

```text
cd "D:\Forge Modding\rdp-uav-addon"
git --no-pager status --short
git --no-pager log -1 --oneline
Get-ChildItem -Depth 2 | Select-Object FullName
grep -RInE 'TODO|FIXME|HACK|dummy|stub|placeholder|unsupported|not implemented|return null|return false|return true|FAILED|OUT_OF_RANGE|SelfDestruct|lock|unlock|target|mission|override|wingman|movement|rotation|velocity' .
.\gradlew.bat test --no-daemon --quiet
```

## Unavailable or substituted commands

```text
command: ./gradlew clean build
reason unavailable: ForgeGradle runtime depends on a matching Forge userdev artifact that is not available in this environment
substitute verification: source-level Gradle test execution succeeded; generated JAR exists in build/libs
remaining risk: client/server runtime verification remains blocked

command: ./gradlew check
reason unavailable: same Forge runtime blocker and no live client/server test target is installed
substitute verification: project test suite was executed and passed
remaining risk: exact runtime integration remains unverified

command: javac -source 8 -target 8 ...
reason unavailable: the exact McHeli and runtime classpath is local-only and not fully reproduced in a generic shell session
substitute verification: the project build produced classes and passed the source-level test suite
remaining risk: compile success does not prove live in-game behavior
```

## Baseline results

- Working tree status confirmed a repository with source and generated build artifacts present.
- `git log -1 --oneline` reported `1429d1f Save uncommitted changes`.
- `gradlew test --no-daemon --quiet` exited successfully with code `0`.
- The current project validates via dependency-free unit tests in the source tree; the Forge runtime itself remains unverified because the exact Forge userdev artifact is unavailable.

## Baseline failures and risks

- The project documentation already records the exact Forge runtime blocker: ForgeGradle 2.3 requests a legacy `userdev` jar while the published artifact for Forge 14.23.5.2864 is `userdev3`.
- The build file in this checkout currently targets `1.12.2-14.23.5.2768`, while the README and validation report describe the 14.23.5.2864 runtime. This is a documented version drift that must be resolved before any production claim is made.
- Runtime launch and in-game verification remain unavailable in this environment.

## Baseline warnings

- No Gradle compilation warnings were produced in the execution above.
- The project is source/API validated but not client/server runtime validated.
- Mission logic is present and tested, but real-world McHeli/WingMan runtime behavior still requires the actual game environment.

## Known environment limitations

- External Minecraft runtime jars are present in `libs/`, but they are not source-controlled and cannot be assumed to match an arbitrary server or client install.
- The project cannot complete exact Forge client/server integration tests without the matching Forge userdev artifact or a working Forge runtime installation.

## Summary

This project is a valid source-level mission add-on with deterministic mission and target tests passing, but it remains a controlled, source-level implementation instead of a verified live Minecraft deployment. The hostile-world mission system should therefore be designed around realistic, testable mission flows rather than assumptions about in-game weapon or autonomous flight behavior.
