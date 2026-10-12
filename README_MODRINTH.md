<div align="center">

<img src="https://raw.githubusercontent.com/borisshoes/BorisLib/main/src/main/resources/assets/borislib/icon.png" alt="BorisLib" width="140">

# BorisLib

**The server-side Fabric toolkit behind Arcana Novum and Ancestral Archetypes.<br/>Build mods with real GUIs, configs, saved data and custom effects. Players don't need any mods.**

[![Minecraft Version](https://img.shields.io/modrinth/game-versions/4COXIRzj?style=for-the-badge&label=Minecraft&color=2D7FF9)](https://modrinth.com/mod/boris-lib/versions)
[![Server-side](https://img.shields.io/badge/Environment-Server--side-C65135?style=for-the-badge)](https://modrinth.com/mod/boris-lib/versions)
[![Requires Fabric API](https://img.shields.io/badge/Requires-Fabric_API-DBD0B4?style=for-the-badge)](https://modrinth.com/mod/fabric-api)

[![Docs](https://img.shields.io/badge/Docs-API_Guide-2D7FF9?style=for-the-badge&logo=bookstack&logoColor=white)](https://github.com/borisshoes/BorisLib#api-guide)
[![Discord](https://img.shields.io/discord/1403978074550439966?style=for-the-badge&logo=discord&logoColor=white&label=Discord&color=5865F2)](https://discord.gg/dTKPeNEg6r)
[![Source](https://img.shields.io/badge/Source-GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/borisshoes/BorisLib)

</div>

**Here because a mod told you to install this?** Put the jar in your server's `mods` folder next to [Fabric API](https://modrinth.com/mod/fabric-api) and jump to [Server Setup](#server-setup). That is all you need.

**Making a mod?** Read on.

## Why BorisLib
Server-side mods work on vanilla clients, which also means that menus, configs, saved data and status effects all have to be built by hand. BorisLib is the set of systems I built for that while making [Arcana Novum](https://modrinth.com/mod/arcana-novum), a magic mod with 80+ items, boss fights and an in-game menu for everything. Each system is used on live servers, and you can use them in your mod with one Gradle line.

## What You Get
- **Chest GUIs that page, sort and filter.** Hand `PagedGui` a list and it handles the pages and the buttons. Over 60 textured, dyeable icons are included, and players without the resource pack see vanilla items in their place.
- **A config in a few lines.** Declare typed settings and get a commented `.properties` file, a `/yourmod config` command with tab completion and validation, and a permission node for every setting.
- **Saved data for players, worlds and the server.** Register one key and read or write your object anywhere. It works for offline players, and every scope has crash-safe backups.
- **Conditions: status effects that stack like attributes.** Any number of sources can apply the same effect, opposing effects cancel out, and magnitudes can reach infinity for full immunity or a full root. 31 conditions are built in, from damage and speed to gravity, healing and mob detection range.
- **Cutscenes.** Move a player's camera along a keyframed path with easing and splines, in their own dimension or another one, with a stand-in left where they were standing. Their state is restored even after a disconnect or a crash.
- **Timers and login callbacks.** Run code after a delay, on an interval, or the next time a player logs in.
- **Particle shapes.** Lines, circles, spheres and animated lightning bolts, visible at long range.
- **A large utility set.** Multi-entity raycasts, weighted random, safe teleport checks, inventory and item data helpers, text markup, number formatting and sounds.

## A Quick Look
A paged, sortable menu of any list:
```java
PagedGui<Item> gui = new PagedGui<>(MenuType.GENERIC_9x6, player, items);
gui.itemElemBuilder((item, index) -> GuiElementBuilder.from(item.getDefaultInstance()));
gui.elemClickFunction((item, index, click) -> player.sendSystemMessage(Component.translatable(item.getDescriptionId())));
gui.curSort(ItemSort.ALPHABETICAL);
GuiHelper.outlineGUI(gui, 0x1155dd, Component.literal("Items"));
gui.buildPage();
gui.open();
```

A damage buff that stacks with every other source:
```java
// +50% damage dealt for 10 seconds
Conditions.addCondition(server, player, new ConditionInstance(Conditions.MIGHT, id, 200, 0.5f));
```

Data that follows a player, online or not:
```java
Wallet wallet = DataAccess.getPlayer(uuid, Wallet.KEY);
wallet.coins += 5;
DataAccess.setPlayer(uuid, Wallet.KEY, wallet);
```

A camera cutscene:
```java
CameraPath path = CameraPath.builder()
      .add(CameraKeyframe.at(0.0).pos(0, 80, 0).rot(0, 20).build())
      .add(CameraKeyframe.at(1.0).pos(60, 80, 0).rot(90, 0).interp(InterpolationType.EASE_IN_OUT).build())
      .build();
SequenceManager.start(player, new CutsceneSequence(player.getUUID(), path, 200, true));
```

## Add It to Your Mod
```groovy
repositories {
    maven { url = "https://maven.borisshoes.net/releases" }
    maven { url = "https://maven.nucleoid.xyz" }
}

dependencies {
    implementation "net.borisshoes:BorisLib:4.0.0+26.3"
}
```
Use the version from the [versions page](https://modrinth.com/mod/boris-lib/versions) that matches your Minecraft version. To ship BorisLib inside your own jar, so that server owners have nothing extra to install, wrap the line in `include(...)`.

The **[API Guide on GitHub](https://github.com/borisshoes/BorisLib#api-guide)** documents every system with examples, and the classes carry Javadoc throughout. If you get stuck, ask on [Discord](https://discord.gg/dTKPeNEg6r).

## Mods Built with BorisLib
Install one of these to see what the library can do before you write any code.

<div align="center">

<a href="https://modrinth.com/mod/arcana-novum"><img src="https://raw.githubusercontent.com/borisshoes/ArcanaNovum/main/non-mod-resources/ArcanaNovum_Banner.png" alt="Arcana Novum" width="600"></a>

</div>

### [Arcana Novum](https://modrinth.com/mod/arcana-novum)
Minecraft's biggest server-side magic mod: 80+ Arcana Items, multiblock altars, 190+ augmentations, research, achievements and boss fights. Its Tome and Compendium menus are BorisLib GUIs, its 400+ settings are a BorisLib config, and its item effects use Conditions.

| Forging at the Starlight Forge | The Starpath Altar | Late-game gear |
|:---:|:---:|:---:|
| <img src="https://cdn.modrinth.com/data/9J7sCd3t/images/06303032d581ee3983e1d116136640a56e86d5f0_350.webp" alt="A Starlight Forge making a set of Runic Blink Arrows" width="230"> | <img src="https://cdn.modrinth.com/data/9J7sCd3t/images/ca16fd2499c7ecbb0501fa96c1eb699cf42189ca_350.webp" alt="The Starpath Altar about to teleport a player" width="230"> | <img src="https://cdn.modrinth.com/data/9J7sCd3t/images/71752bcef59af242f0c009316f1fe70f87dfe67f_350.webp" alt="A player geared with late-game Arcana items" width="230"> |

<div align="center">

<a href="https://modrinth.com/mod/ancestral-archetypes"><img src="https://github.com/borisshoes/AncestralArchetypes/blob/main/non-mod-resources/Logo_Trans_Medium.png?raw=true" alt="Ancestral Archetypes" width="420"></a>

</div>

### [Ancestral Archetypes](https://modrinth.com/mod/ancestral-archetypes)
An Origins-style mod where each player picks a mob-based Archetype and gains its abilities. It is highly configurable and balanced for competitive servers, and it runs on BorisLib's config, data storage and Conditions.

### More Mods Built with BorisLib
All server-side Fabric mods — no client installation required.

| | Mod | Description | Links |
|:---:|---|---|---|
| <img src="https://cdn.modrinth.com/data/QfXOzeIK/b35cbf33da842f170d0aa562033aaddc2a9ab653_96.webp" alt="Ender Nexus" width="64"> | **Ender Nexus** | Highly configurable /home, /spawn, /warp, /tpa and /rtp commands all in one, and individually disablable. | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/EnderNexus/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/ender-nexus) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/ender-nexus-fabric-teleports) |
| <img src="https://cdn.modrinth.com/data/Z63eULDV/dae01789d609498b8f1637ab31d8fe20b6108020_96.webp" alt="Fabric Mail" width="64"> | **Fabric Mail** | An in-game virtual mailbox system for sending packages and messages between online and offline players. | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/fabric-mail/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/fabric-mail) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/fabric-mail) |
| <img src="https://cdn.modrinth.com/data/u40ARaBc/028062616fc2fb729afdbdc697d60f93ff61a918_96.webp" alt="Fabric Trade" width="64"> | **Fabric Trade** | Adds /trade, a secure player-to-player trading interface. | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/fabric-trade/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/fabric-trade) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/fabric-trade) |
| <img src="https://cdn.modrinth.com/data/WdlqG9Gd/a401b9bf08c33d85c907025d6689c657b5168508_96.webp" alt="Limited AFK" width="64"> | **Limited AFK** | AFK detection and management with configurable kick thresholds for servers. | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/LimitedAFK/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/limited-afk) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/limited-afk) |

Made something with BorisLib? Tell me on [Discord](https://discord.gg/dTKPeNEg6r) and I will add it here.

## Server Setup
1. Install Fabric Loader and [Fabric API](https://modrinth.com/mod/fabric-api) on your server, then put the BorisLib jar in the `mods` folder. Every other library is bundled, and players do not need to install anything.
2. Launch the server once to generate the configs.
3. Open `config/polymer/auto-host.json` and change `"enabled": false` to `true`. The server now generates the resource pack for BorisLib and the mods built on it, and sends it to players when they connect.
4. (Optional) In the same file you can make the resource pack required, or set the `"message"` that players see when they are asked to download it.

Some mods, including Arcana Novum and Ancestral Archetypes, already include BorisLib in their own jar. You only need this download when a mod lists BorisLib as a separate requirement.

Admins can inspect and apply conditions with `/borislib condition`, and every command has a [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api) node with a vanilla permission level fallback. The full reference is on GitHub: [commands](https://github.com/borisshoes/BorisLib#commands) and [permission nodes](https://github.com/borisshoes/BorisLib#permission-nodes).

## Support
Questions go to the [Discord](https://discord.gg/dTKPeNEg6r), and bug reports and suggestions go to [GitHub Issues](https://github.com/borisshoes/BorisLib/issues).

Translations: Russian by Reset1712 and Spanish by Llama303.

### License Notice
By using this project in any form, you hereby give your "express assent" for the terms of the license of this project, and acknowledge that I, BorisShoes, have fulfilled my obligation under the license to "make a reasonable effort under the circumstances to obtain the express assent of recipients to the terms of this License."
