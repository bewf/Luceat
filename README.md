# Luceat

Client-side dynamic lighting for Minecraft Java on Fabric. Lights are shaded on the GPU, so moving a light never rebuilds chunks.

Requires Sodium. Status: early development.

## Using it

Press J in game, or type `/luceat`, to open the settings. `/luceat perf` shows which settings cost the most on your computer.

## Versions

Every Minecraft version has its own folder with its own build.

| Folder | Minecraft | Status |
| --- | --- | --- |
| [26.1.2](26.1.2) | 26.1.2 | In development |

To build one, open its folder and run `./gradlew build`. The jar ends up in `build/libs`.

The mod version is set once, in `version.properties` at the repository root. Every build reads it for the jar name and `fabric.mod.json`, and the in-game version text comes from there too.

## License

##### Code licensed under [ARR](https://github.com/bewf/luceat/blob/main/LICENSE.md). Unmodified redistribution **in free modpacks** is permitted with credit. See the [full license](https://github.com/bewf/luceat/blob/main/LICENSE.md) on GitHub for details.
