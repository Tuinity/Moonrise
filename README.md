Moonrise
==
Fabric/NeoForge mod for optimising performance of the integrated and dedicated server.


## Purpose
Moonrise is a port of several important [Paper](https://github.com/PaperMC/Paper/)
patches. Listed below are notable patches:
 - Chunk system rewrite
 - Collision optimisations
 - Entity tracker optimisations
 - Random ticking optimisations
 - [Starlight](https://github.com/PaperMC/Starlight/)

## Mod Compatibility
| Mod         | Compatibility                                                                                                                                                           |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FerriteCore | <details><summary>📝 requires config changes</summary>In `config/ferritecore-mixin.toml`:<br/>Set `replaceNeighborLookup` and `replacePropertyMap` to `false`</details> |
| ModernFix   | <details><summary>📝 requires config changes</summary>In `config/modernfix-mixins.properties`:<br/>Add `mixin.bugfix.paper_chunk_patches=false`</details>               |
| Lithium     | ❌ incompatible                                                                                                                                                          |
| C2ME        | ❌ incompatible                                                                                                                                                          |

## Contact
[Discord](https://discord.gg/tuinity)
