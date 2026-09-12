# Workstations - 1.16.5 Fabric and Forge

Architectury port of Workstations to Minecraft 1.16.5. Two jars, one per loader. Architectury API is required.

- Minecraft: 1.16.5
- Fabric Loader: 0.14.23 + Fabric API 0.42.0+1.16 + Architectury API 1.32.68 (Fabric)
- Forge: 36.2.39 + Architectury API 1.32.68 (Forge)
- Java: 16+
- Mod ID / namespace: `villager_workstations`
- Minecraft Comes Alive (MCA) integration is not included on this line

Install **one** of:

- Fabric: Fabric Loader + Fabric API + Architectury API (Fabric) + `villager-workstations-1.16.5-fabric-2.0.0.jar`
- Forge: Forge + Architectury API (Forge) + `villager-workstations-1.16.5-forge-2.0.0.jar`

Do not install `-dev-shadow` or `-raw` jars. Worlds that used `keyboard_workstations:` IDs will not carry over.

Build with JDK 17 (`sdk use java 17.0.16-tem`) and `./gradlew build`. Player jars land in `fabric/build/libs/` and `forge/build/libs/`. The compiled mod still needs Java 16+ at runtime.
