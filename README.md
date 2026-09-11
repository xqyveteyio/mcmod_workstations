# Workstations - 1.16.5 Fabric port

Fabric port of the 1.20.1 Workstations mod to Minecraft 1.16.5.

- Minecraft: 1.16.5
- Fabric Loader: 0.14.21
- Fabric API: 0.42.0+1.16
- Java: 16+ (the code uses modern Java features from the 1.20.1 source)
- Minecraft Comes Alive (MCA) integration has been removed for this port.

Stations:

- Ranch station: breeds, culls, shears and milks livestock in its work area
- Farm station: tills, sows and harvests registered plots
- Lumber station: fells trees, replants saplings, and force-grows with bone meal
- Seed box and feed box: extra storage the farm/lumber and ranch stations draw from first

Build with `./gradlew build`.

Headless server smoke test has been run successfully with:

```bash
./gradlew runServer --no-daemon
```

sdk use java 17.0.16-tem