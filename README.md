# RDP UAV Missions

This is the Forge 1.12.2 addon project for player-configured reconnaissance
and target missions. It targets the exact installed runtime:

* Minecraft 1.12.2
* Forge 14.23.5.2864
* McHeli CE 1.5.1 with merged WingMan classes
* RDP Core 1.0.0
* Java source/target compatibility 8

Place the verified runtime jars at `libs/mcheli-ce-1.5.1.jar` and
`libs/rdpcore-1.0.0.jar` before running Gradle, or set `RDP_MCHELI_JAR` and
`RDP_CORE_JAR` to the installed instance jar paths. The jars are intentionally
not copied into source control.

The addon now includes a small mission-control GUI and AI operator help page.
On a server, use `/rdpuav mission gui`. The screen receives only the current
player's mission summaries from the server; select a mission and use
`Abort selected` to safely stop a non-terminal mission, Refresh requests a new
snapshot, Operator help explains the AI pilot workflow, and Close exits the
screen. Abort requests are checked server-side against the player's UUID,
mission ownership, and terminal state. The GUI does not create missions or
authorize autonomous attacks.

Automatic selection ranks valid candidates by target priority and then distance;
manual selection only returns the explicitly requested registry ID. The
dependency-free core test can be run with:

```text
javac -source 8 -target 8 ...
java net.vas.rdp.uav.CoreMissionTest
```

ForgeGradle configuration was verified with the local ForgeGradle 2.3
installation. A full `test`/`build` additionally requires the exact
`net.minecraftforge:forge:1.12.2-14.23.5.2864` userdev artifact; only
`14.23.5.2859` is cached in the current environment, so no version-mismatched
Forge build is substituted.

## Exact Forge 14.23.5.2859 setup requested for this GUI

The checked-in `build.gradle` still targets the previously validated
`14.23.5.2864` environment. To build this small GUI addon against the exact
environment requested for deployment, make the following one-line change
before running Gradle:

```groovy
minecraft {
    version = '1.12.2-14.23.5.2859'
}
```

Then:

1. Install Java `1.8.0_492` and make it the `JAVA_HOME` used by Gradle.
2. Put the exact McHeli CE `1.5.1` jar at
   `rdp-uav-addon/libs/mcheli-ce-1.5.1.jar`.
3. Put the matching RDP Core jar at
   `rdp-uav-addon/libs/rdpcore-1.0.0.jar`.
4. Use ForgeGradle 2.3 with Maven access to
   `https://maven.minecraftforge.net`.
5. Run `gradlew setupDecompWorkspace` and `gradlew build` from
   `rdp-uav-addon`.
6. Copy `build/libs/rdp-uav-addon-0.1.0.jar` into the Minecraft 1.12.2
   `mods` directory alongside Forge `14.23.5.2859` and McHeli CE `1.5.1`.
7. Start a dedicated server and client with the same mod set, then run
   `/rdpuav mission gui`.

Do not copy the source project into `mods`, and do not mix McHeli versions.
The GUI was source-compiled against cached 1.12.2 APIs, but no Minecraft
client/server runtime test was run in this session.
