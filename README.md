Moonrise
==
[![Modrinth](https://img.shields.io/badge/Modrinth-gray?logo=modrinth)](https://modrinth.com/mod/moonrise-opt)
[![CurseForge](https://img.shields.io/badge/CurseForge-gray?logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/moonrise)
[![Release](https://img.shields.io/github/v/release/Tuinity/Moonrise?include_prereleases)](https://github.com/Tuinity/Moonrise/releases)
[![License](https://img.shields.io/github/license/Tuinity/Moonrise)](LICENSE.md)

Fabric/NeoForge mod for optimising performance of the integrated (singleplayer/LAN) and dedicated server.


## Purpose
Moonrise aims to optimise the game *without changing Vanilla behavior*. If you find that there are changes to Vanilla behavior,
please open an issue.

Moonrise ports several important [Paper](https://github.com/PaperMC/Paper/)
patches. Listed below are notable patches:
 - Chunk system rewrite
 - Collision optimisations
 - Entity tracker optimisations
 - Random ticking optimisations
 - [Starlight](https://github.com/PaperMC/Starlight/)

## Known Compatibility Issues
| Mod         | Status                                                                                                                                                                                                                                                                                                   |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Lithium     | <details><summary>✅ compatible</summary>Lithium optimises many of the same parts of the game as Moonrise, for example the chunk system. Moonrise will automatically disable conflicting parts of Lithium. This mechanism needs to be manually validated for each Moonrise and Lithium release.</details> |
| Radium      | <details><summary>✅ compatible</summary>Radium is an unofficial port of Lithium to NeoForge. Radium will automatically disable conflicting parts of itself when Moonrise is present. Any compatibility issues should be reported to Radium first.</details>                                              |
| FerriteCore | <details><summary>📝 requires config changes</summary>In `config/ferritecore-mixin.toml`:<br/>Set `replaceNeighborLookup` and `replacePropertyMap` to `false`</details>                                                                                                                                  |
| C2ME        | <details><summary>❌ incompatible</summary>C2ME is based around modifications to the chunk system, which Moonrise replaces wholesale. This makes them fundamentally incompatible.</details>                                                                                                               |

## Configuration
Moonrise provides documented configuration options for tuning the chunk system and enabling bugfixes in the config file `$mcdir$/config/moonrise.yml`.
Important configuration options may be configured from the mods menu as well.

## Contact
[Discord](https://discord.gg/tuinity)
