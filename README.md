Moonrise
==
[![Modrinth](https://img.shields.io/badge/Modrinth-gray?logo=modrinth)](https://modrinth.com/mod/moonrise-opt)
[![CurseForge](https://img.shields.io/badge/CurseForge-gray?logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/moonrise)
[![Release](https://img.shields.io/github/v/release/Tuinity/Moonrise?include_prereleases)](https://github.com/Tuinity/Moonrise/releases)
[![Discord](https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/tuinity)
[![License](https://img.shields.io/github/license/Tuinity/Moonrise)](LICENSE.md)

Moonrise is a Fabric/NeoForge mod for optimising performance of the integrated (singleplayer/LAN) and dedicated server.
Effectively all of Moonrise's changes are included in [Paper](https://papermc.io/),
except those from Paper that intentionally change gameplay.

Moonrise can run on either the client or server, but primarily benefits the server.

## Purpose
Moonrise aims to optimise the game *without changing Vanilla behaviour*.
If you use Moonrise on the client, most of the affected areas only affect
the integrated server or small of the client tick. This means that 
improvements client side would behave similar to 
[Lithium](https://modrinth.com/mod/lithium).

## Optimised areas
- Entity movement/collisions/physics/tracking
- Chunk ticking/loading/generation/saving
- Block/Entity retrieval (which systems like pathfinding and entity AI use frequently)

These changes result in measurably lower tick times on servers, 
and they improve chunk loading/generation.

## Responsiveness improvements
- Improve/fix the server list server ping UI (client install only)
- Handle packets sent while the integrated/dedicated server is waiting for next tick (lowering perceived latency)
- Lower worker thread count by default for low core systems (improving stability on them at cost of chunk loading speed)
- Reduce TPS catchup by default (stops the server from speeding up when it momentarily lags)

## Known Compatibility Issues
| Mod         | Status                                                                                                                                                                                                                                                                                                   |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Lithium     | <details><summary>✅ compatible</summary>Lithium optimises many of the same parts of the game as Moonrise, for example the chunk system. Moonrise will automatically disable conflicting parts of Lithium. This mechanism needs to be manually validated for each Moonrise and Lithium release.</details> |
| FerriteCore | <details><summary>✅ compatible</summary>FerriteCore optimises some of the same parts of the game as Moonrise. Moonrise will automatically disable conflicting parts of FerriteCore. This mechanism needs to be manually validated for each Moonrise and FerriteCore release.</details>                   |
| C2ME        | <details><summary>❌ incompatible</summary>C2ME is based around modifications to the chunk system, which Moonrise replaces wholesale. This makes them fundamentally incompatible.</details>                                                                                                               |

## Configuration
Moonrise provides documented configuration options (the config itself contains the documentation) for tuning the chunk system and enabling bugfixes in the config file `$mcdir$/config/moonrise.yml`.
Important configuration options may be configured from the mods menu as well.

## Contact
[Discord](https://discord.gg/tuinity)

[Github](https://github.com/Tuinity/Moonrise)
