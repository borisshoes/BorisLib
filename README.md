# Welcome to BorisLib!
### This is a Library for most of my heavier Server-Sided Fabric Mods

This library has a few key features:
- Polymer-based Graphical Items for server-sided GUIs
- A robust framework for generating paged and configurable server-sided GUIs
- A built-in powerful mod config / properties file manager
- A system for storing mod data on Players, Worlds or Globally, with easy offline access
- A 'Conditions' system that acts like potion effects with the granularity and stackability of entity attributes
- A game-event system for keeping track of specific player actions
- A timer and login callback system for events that take place after a certain amount of time, or when a player returns to the server.
- A server-based velocity tracker for estimating the player's movement vectors without specialized packets
- A library of modular, pre-made, geometric and animated particle effects
- A whole bunch of useful utility methods for various mathematical operations, data structures, algorithms, text and sounds.
- An experimental particle packet bundler that drastically reduces network lag caused by particles (inspired by the PulseMC project)

### Try My Other Mods!
All server-side Fabric mods — no client installation required.

|                                                                                                                | Mod                      | Description                                                                                               | Links                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|:--------------------------------------------------------------------------------------------------------------:|--------------------------|-----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <img src="https://cdn.modrinth.com/data/9J7sCd3t/e6ce366187de25be0efc7ecc736fc27f05452888_96.webp" width="32"> | **Arcana Novum**         | Minecraft's biggest server-only full-feature Magic Mod! Adds powerful items, multiblocks and bosses!      | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/ArcanaNovum/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/arcana-novum) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/arcana-novum)                        |
| <img src="https://cdn.modrinth.com/data/xHHbHfVj/c6c224a3d8068cfb9b054e2a03eb9704906dd8cb_96.webp" width="32"> | **Ancestral Archetypes** | A highly configurable, Origins-style mod that lets players pick a mob to gain unique abilities!           | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/AncestralArchetypes) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/ancestral-archetypes) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/ancestral-archetypes) |
| <img src="https://cdn.modrinth.com/data/QfXOzeIK/b35cbf33da842f170d0aa562033aaddc2a9ab653_96.webp" width="32"> | **Ender Nexus**          | Highly configurable /home, /spawn, /warp, /tpa and /rtp commands all in one, and individually disablable. | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/EnderNexus/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/ender-nexus) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/ender-nexus-fabric-teleports)          |
| <img src="https://cdn.modrinth.com/data/Z63eULDV/dae01789d609498b8f1637ab31d8fe20b6108020_96.webp" width="32"> | **Fabric Mail**          | An in-game virtual mailbox system for sending packages and messages between online and offline players.   | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/fabric-mail/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/fabric-mail) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/fabric-mail)                          |
| <img src="https://cdn.modrinth.com/data/u40ARaBc/028062616fc2fb729afdbdc697d60f93ff61a918_96.webp" width="32"> | **Fabric Trade**         | Adds /trade, a secure player-to-player trading interface.                                                 | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/fabric-trade/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/fabric-trade) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/fabric-trade)                       |
| <img src="https://cdn.modrinth.com/data/WdlqG9Gd/a401b9bf08c33d85c907025d6689c657b5168508_96.webp" width="32"> | **Limited AFK**          | AFK detection and management with configurable kick thresholds for servers.                               | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/LimitedAFK/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/limited-afk) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/limited-afk)                           |
| <img src="https://cdn.modrinth.com/data/klpvLefw/97afbda2e56c3f14e04d0f9e0e1fe99db6bd2f27_96.webp" width="32"> | **Links in Chat**        | Makes URLs posted in chat clickable.                                                                      | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/fabric-linksinchat/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/links-in-chat) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/links-in-chat)               |


### Permission Nodes
BorisLib uses the [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api) for command permissions. Each node has a fallback vanilla permission level for servers without a permissions mod.

#### General
| Node | Default | Description |
|------|---------|-------------|
| `borislib.borislib` | `ALL` | Access the `/borislib` base command (shows version) |
| `borislib.reload` | `GAMEMASTERS` | Reload the BorisLib config file |

#### Config
Config commands are generated dynamically per config value. The `<modid>` and `<name>` below depend on the mod using the library.

| Node | Default | Description |
|------|---------|-------------|
| `<modid>.config` | `GAMEMASTERS` | List all config values |
| `<modid>.config.<name>.get` | `GAMEMASTERS` | Read a specific config value |
| `<modid>.config.<name>.set` | `GAMEMASTERS` | Change a specific config value |

#### Player Data
| Node | Default | Description |
|------|---------|-------------|
| `borislib.player` | `GAMEMASTERS` | View stored player data via `/borislib player <player>` |

#### Conditions
| Node | Default | Description |
|------|---------|-------------|
| `borislib.condition` | `GAMEMASTERS` | Access the `/borislib condition` subcommand tree |
| `borislib.condition.add` | `GAMEMASTERS` | Add a condition instance to an entity |
| `borislib.condition.remove` | `GAMEMASTERS` | Remove a specific condition instance from an entity |
| `borislib.condition.clear` | `GAMEMASTERS` | Clear all instances of a condition type from an entity |
| `borislib.condition.get` | `GAMEMASTERS` | Get the prevailing value of a condition on an entity |
| `borislib.condition.getinstance` | `GAMEMASTERS` | Get details of a specific condition instance by ID |
| `borislib.condition.getbase` | `GAMEMASTERS` | Get the base (default) value of a condition type |
| `borislib.condition.getinstances` | `GAMEMASTERS` | List all instances of a condition type on an entity |

#### Testmod (requires `testmodFeaturesEnabled` config option)
| Node | Default | Description |
|------|---------|-------------|
| `borislib.testmod` | `GAMEMASTERS` | Access the `/borislib testmod` subcommand tree |
| `borislib.testmod.worldcallback` | `GAMEMASTERS` | Schedule a repeating world-tick timer callback |
| `borislib.testmod.servercallback` | `GAMEMASTERS` | Schedule a repeating server-tick timer callback |
| `borislib.testmod.pagedgui` | `GAMEMASTERS` | Open a test paged GUI |
| `borislib.testmod.particlestress` | `GAMEMASTERS` | Run a particle stress test (~10k particles/tick) |
| `borislib.testmod.returnitem` | `GAMEMASTERS` | Test the item return timer/login callback system |
| `borislib.testmod.energybar` | `GAMEMASTERS` | Display a test energy bar in the action bar |
| `borislib.testmod.marker` | `GAMEMASTERS` | Access world marker subcommands |
| `borislib.testmod.marker.place` | `GAMEMASTERS` | Place a named world marker at your position |
| `borislib.testmod.marker.remove` | `GAMEMASTERS` | Remove a named world marker |
| `borislib.testmod.marker.list` | `GAMEMASTERS` | List all world markers across dimensions |
| `borislib.testmod.timestamp` | `GAMEMASTERS` | Access global timestamp subcommands |
| `borislib.testmod.timestamp.mark` | `GAMEMASTERS` | Set the global timestamp to the current time |
| `borislib.testmod.timestamp.read` | `GAMEMASTERS` | Read the stored global timestamp |

### Translation Credits:
- Russian - Reset1712