Moonrise
==
Fabric/NeoForge mod for optimising performance of the integrated and dedicated server.


## Purpose
Moonrise aims to optimise the game *without changing Vanilla behavior*.

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
| FerriteCore | <details><summary>📝 requires config changes</summary>In `config/ferritecore-mixin.toml`:<br/>Set `replaceNeighborLookup` and `replacePropertyMap` to `false`</details>                                                                                                                                  |
| C2ME        | <details><summary>❌ incompatible</summary>C2ME is based around modifications to the chunk system, which Moonrise replaces wholesale. This makes them fundamentally incompatible.</details>                                                                                                               |

## Contact
[Discord](https://discord.gg/tuinity)
