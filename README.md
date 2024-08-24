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
| Mod         | Status                                                                                                                                                                  |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FerriteCore | <details><summary>📝 requires config changes</summary>In `config/ferritecore-mixin.toml`:<br/>Set `replaceNeighborLookup` and `replacePropertyMap` to `false`</details> |
| Lithium     | ❌ incompatible                                                                                                                                                          |
| C2ME        | ❌ incompatible                                                                                                                                                          |

## Contact
[Discord](https://discord.gg/tuinity)
