# AIO PvP

All-in-one client mod for PvP and quality of life on Fabric. It adds a movable HUD, combat tools and visual tweaks, and keeps them in one menu instead of a dozen separate mods.

Client side only. It works on any server and in singleplayer.

## Requirements

- Minecraft 1.21.11, 26.1.2, 26.2 or 26.3. Each version has its own jar, so download the one that matches your game.
- Fabric Loader 0.15 or newer
- Fabric API
- Java 21 for Minecraft 1.21.11, Java 25 for 26.x
- Mod Menu (optional, adds a config button to the mod list)

Works with Sodium, Iris, ImmediatelyFast and many other popular mods. See [Compatibility](#compatibility) for the tested list and the known notes.

## Compatibility

Tested on Minecraft 1.21.11 with Fabric Loader 0.20.1 and Fabric API 0.141.6. In each test the game started with the listed mods and with every AIO PvP module turned on. There were no crashes and no mixin errors.

| Group | Mods |
|---|---|
| Performance | Sodium 0.8.14, Sodium Extra, Reese's Sodium Options, Iris 1.10.8, Lithium, FerriteCore, ImmediatelyFast 1.14.3, Entity Culling, More Culling, Cull Leaves, Exordium, Dynamic FPS, C2ME |
| HUD and interface | Zoomify, AppleSkin, BetterF3, Mouse Tweaks, Xaero's Minimap, Xaero's World Map, Jade, Inventory Profiles Next, Shulker Box Tooltip, Controlling, Continuity, Mod Menu |
| PvP and visuals | Freelook, Perspektive, Gamma Utils, Full Brightness Toggle, Hit Color, Hit Color Plus, Combat Hitbox, Nametag Tweaks, Health Indicators, Dynamic Crosshair, Crosshair Indicator, Toggle Toggle Sprint, Auto Sprint Fix, Old Combat, Not Enough Animations, Particle Rain, Subtle Effects, Debugify |

### Known incompatibilities

- **Ok Zoomer** (16.0.0-beta.3 for 1.21.11) does not start on Minecraft 1.21.11 at all: it needs Java 25, while 1.21.11 runs on Java 21. This happens with or without AIO PvP.

No other mod in the list above is incompatible with AIO PvP.

### Notes

- **Same feature twice.** Mods that do what an AIO PvP module does (hit color, fullbright, freelook, zoom, nametags, crosshair, toggle sprint) load fine together with AIO PvP, but if both are turned on at the same time they may overwrite each other. Turn the feature on in one of them only.
- **Options locked by other mods.** When another installed mod already does the job, the matching AIO PvP option turns itself off and shows why in the menu:
  - Sodium: Fog Occlusion, No Error Context and Animate Only Visible Textures.
  - Exordium: HUD Caching.
  - Entity Culling (the mod): the Entity Culling option.
- **Sodium and chunk options.** Sodium replaces the vanilla chunk renderer, so the AIO PvP chunk options (Fog Occlusion, Animate Only Visible Textures) have no effect with it. Sodium has its own versions of these.
- **Replay Mod** was not tested.
- The test checks that the game starts and runs with the mods. It does not check every feature of every mod. If you find a problem with a specific mod, please open an issue with the game log.

## Installation

1. Install Fabric Loader for your Minecraft version.
2. Put Fabric API and `aio-pvp-<version>+<minecraft version>.jar` (for example `aio-pvp-1.0.52+1.21.11.jar`) into your `mods` folder.
3. Start the game.

## How to use

Press Right Shift to open the menu. You can change this key in Options > Controls or with the "Menu" button in the mod menu itself.

The left bar lists the categories (HUD, Combat, Visuals, Items, Misc). Each module is a card with a switch. Click a card to open its settings, with a live preview at the top. The search field finds modules by name, description or setting.

Click the star on a card to add the module to your favorites. The buttons next to the search field sort the cards and show only your favorites; favorites always come first.

Click "HUD Editor" to move HUD elements. Drag an element to place it, drag a corner to resize it, use the mouse wheel to scale it and right-click to reset it. Arrow keys move it by one pixel, G shows a grid and S toggles snapping.

The Settings button at the bottom of the left bar holds the menu theme (seven presets or your own accent color), the menu layout, the language (English or Polish) and a reset of all settings.

Any module can get its own toggle key in its settings.

### Commands

- `/fightreport` opens the card of your last fight.
- `/calc <expression>` is a chat calculator, for example `/calc 2+2*3`. It supports `+ - * / % ^`, brackets, `sqrt()` and `pi`. Nothing is sent to the server.

## Features

### HUD

| Module | What it does |
|---|---|
| Armor HUD | Your armor and its durability next to the hotbar, with a sound when a piece gets low. |
| Item Preview | Counters of chosen inventory items (totems, gapples, pearls...) next to the hotbar. |
| Potion Status | Active effects with time left, in place of the vanilla corner icons. Vertical or horizontal. |
| Key Overlay | Keys bound to each hotbar slot. |
| Coordinates | X / Y / Z with colored axes, in one line or stacked. |
| Locator Bar | Player heads on the locator bar with an outline in their color, names and the direction you face. The "On XP bar" option keeps the experience bar and draws the icons on it. |
| FPS, Ping, CPS, Day, Clock | Small info labels. |
| Sprint/Sneak Status | Shows `[Sprint (Toggled)]`, `[Sneak]` and similar, useful with Toggle Sprint. |
| Lag Detector | Warns when the server stops sending packets, so you know a miss was lag. |
| Direction | Compass strip with cardinal directions. |
| Elytra Pitch, Bow Pitch | Your pitch angle while gliding or drawing a bow. |
| Saturation | Gold outline on the hunger bar for saturation. Holding food blinks what it would add. Follows the shape of your resource pack's icons. |
| Media Player | What is playing in Spotify or the browser, with play/pause and skip buttons. Windows only. |
| HUD Tweaker | Position and scale of the title, actionbar, scoreboard and bossbar, and an option to lift the hotbar off the bottom edge. |

Armor HUD, Item Preview and Potion Status have three styles: plain, Vanilla GUI (always the original Minecraft textures) and Resourcepack GUI (textures from your resource pack).

### Combat

| Module | What it does |
|---|---|
| Crosshair Indicator | Crosshair that changes color on a target, or your own pixel crosshair drawn in a built-in editor. |
| Combat Hitboxes | Custom F3+B hitboxes with separate colors for your target, players and mobs. |
| Hit Color | Color, opacity and fade of the red hit flash. |
| Player Health Indicators | Hearts or a heart count by player names, only for players within a set distance (32 blocks by default). |
| Fight Report | After a fight with a player, a chat message and a card with hits landed, damage dealt in hearts, longest combo, average distance, opponent totems and your armor wear. Keeps a history of recent fights. |
| Damage Calculator | Damage of one hit and hits to kill: weapon, sharpness up to 500, strength, crits, opponent armor, protection per piece, resistance, absorption and saturation healing. |
| Cooldown Fix | Keeps the attack indicator in sync with the server (MC-218570). |
| Instant Hit | Shows hits before the server confirms them. See the note below. |

### Visuals

| Module | What it does |
|---|---|
| Clear View | Removes the pumpkin and spyglass overlays, the dark screen inside blocks, water and lava fog, and bobbers hooked on you. |
| Fullbright | Brightness above the vanilla limit. |
| Block Outline | Color and style of the block highlight (lines, whole block or visible side). |
| Nametags | White name text and a custom background. |
| Low Fire | Lower, fainter fire overlay. |
| No Potion Particles, No Explosion Particles | Hides those particles. |
| Name in 3rd Person | Your own name above your head in third person. |
| No Camera Shake | Turns off view bobbing for the camera but keeps the hand swing. |
| Freelook | Look around your player while holding a key, like Perspective Mod. See the note below. |

### Items

| Module | What it does |
|---|---|
| View Model | Position and scale of items in the main hand and offhand. |
| Shield | Shield animation fixes (MC-238293, block delay) and the shield's position and scale. |
| 1.7 Animations | 1.7 style sword block while blocking with a shield, and no item dip after a hit. |
| Totem Tweaks | Size of the totem pop animation. |
| Boat Item View | Items in hand, such as maps, stay visible while rowing. |
| Fast XP | Throws XP bottles faster while holding use. |
| Invisible Hand | See-through first person hand while you are invisible. |

### Misc

| Module | What it does |
|---|---|
| Command Keys | Bind a key or mouse button to a command (`/spawn`) or a chat message. |
| More Chat History | Keeps more chat messages than vanilla's 100. |
| No Sign GUI | Placed signs don't open the edit screen. |
| Waypoints | Save places with a name, color and icon, with a key or /waypoint; a beam, name and distance show in the world. The label size setting scales the name and the icon together. |
| Markers | Middle mouse button places a marker where you look (block icon, distance, coordinates); people in your client-side party see it too (`/mparty invite`). |
| Party (beta) | Client-side party with leader, inviter and member roles, a party menu (heads, ranks, kick), shared markers and member coordinates enabled by vote. Members' heads show on screen where they are, with distance and name; heads of far members move smoothly between position updates. |
| Party Vote | The vote window of the party (place it in the HUD Editor). It drops in from the top with one bounce, can play a sound and can use the vanilla look. Its settings are in the Party module. |
| Sounds | Change the volume of any sound by name (0-200%), with search and a hint to turn on subtitles. |
| Optimization | FPS options, see [Performance](#performance). An option another mod already handles is locked with the reason shown. |

The mod also fixes MC-263293 without a setting: toggle sprint and toggle sneak stay on after you respawn.

## Performance

The Optimization module (Misc) has these options. All are off by default.

| Option | What it does |
|---|---|
| Entity Culling | Players, mobs and items hidden behind full blocks are not drawn. |
| Fog Occlusion | Chunks past the end of the fog (under water, in lava, in the Nether) are not built or drawn. |
| No Error Context (restart) | OpenGL context without error checking. A little faster, but makes crashes harder to trace. |
| Animate Only Visible Textures (beta) | Animated textures (water, lava, magma, fire...) are sent to the GPU only if a visible chunk uses them. Helps most with 128x and bigger resource packs. |
| HUD Caching | The HUD is drawn into a texture at most 20 times per second; the other frames only show that texture. Markers and party heads still update every frame. |

### Measured FPS

How the numbers were made:

- **Hardware:** AMD Ryzen 5 PRO 4650G, NVIDIA GeForce RTX 3050 (driver 621.01), 16 GB RAM, Windows 11 Pro.
- **Game:** Minecraft 1.21.11, Java 21, Fabric Loader 0.20.1, Fabric API 0.141.6, AIO PvP 1.0.52. Window 1920x1080, render distance 12, VSync off, no FPS limit.
- **Scene:** a normal world with the fixed seed `aio-pvp-bench`, at the world spawn, flying in creative and looking the same way every time. Under the player there is a hidden cave with a lava floor, magma blocks and 120 zombies (all out of sight), and 12 pigs stand in front of the player. The AIO PvP HUD elements are on. The second scene is the same spot inside a 25x8x25 block of water.
- **Method:** the test runs inside the game (Fabric client gametest) and reads the time of every frame from the game's own frame log (the F3 frame graph). Each setting is measured for 6 seconds, in 6 rounds after one warm-up round. The order changes every round, so every setting is measured first, second and so on once. The table shows the median of the 6 rounds; "1% low" is the FPS at the slowest 1% of frames. "Rounds" is the lowest and highest round, to show how much the numbers move by themselves. A rare round where the test framework slowed the game to 20 FPS does not change the median.
- **Two runs:** AIO PvP alone, and AIO PvP with Sodium 0.8.14, Lithium 0.21.4, FerriteCore 8.2.0 and ImmediatelyFast 1.14.3. Compare the rows within one table. In this test scene Sodium was not faster than vanilla, so don't compare the two tables with each other.

The numbers move by about 3-5% between rounds on their own, so a change smaller than that is noise.

**AIO PvP alone**

| Surface | Average FPS | vs. all off | 1% low FPS | Rounds |
|---|---|---|---|---|
| All options off | 509 | | 270 | 490-539 |
| Entity Culling | 521 | +2% | 264 | 496-542 |
| Fog Occlusion | 492 | -3% | 254 | 475-520 |
| Animate Only Visible Textures | 494 | -3% | 263 | 20-528 |
| HUD Caching | 510 | 0% | 268 | 495-542 |
| All options on | 537 | +5% | 287 | 498-567 |

| Underwater | Average FPS | vs. all off | 1% low FPS | Rounds |
|---|---|---|---|---|
| All options off | 586 | | 292 | 570-604 |
| Entity Culling | 574 | -2% | 286 | 555-595 |
| Fog Occlusion | 582 | -1% | 298 | 20-596 |
| Animate Only Visible Textures | 593 | +1% | 303 | 558-600 |
| HUD Caching | 623 | +6% | 315 | 605-644 |
| All options on | 682 | +16% | 328 | 622-689 |

**AIO PvP with Sodium, Lithium, FerriteCore and ImmediatelyFast**

Fog Occlusion and Animate Only Visible Textures are locked when Sodium is installed (Sodium has its own versions), so their rows only show the noise.

| Surface | Average FPS | vs. all off | 1% low FPS | Rounds |
|---|---|---|---|---|
| All options off | 434 | | 216 | 382-458 |
| Entity Culling | 439 | +1% | 211 | 20-456 |
| Fog Occlusion (locked) | 422 | -3% | 211 | 400-443 |
| Animate Only Visible Textures (locked) | 425 | -2% | 207 | 412-460 |
| HUD Caching | 473 | +9% | 235 | 435-513 |
| All options on | 499 | +15% | 235 | 480-510 |

| Underwater | Average FPS | vs. all off | 1% low FPS | Rounds |
|---|---|---|---|---|
| All options off | 426 | | 212 | 423-446 |
| Entity Culling | 445 | +4% | 215 | 423-458 |
| Fog Occlusion (locked) | 434 | +2% | 218 | 428-470 |
| Animate Only Visible Textures (locked) | 436 | +2% | 216 | 408-452 |
| HUD Caching | 485 | +14% | 234 | 144-529 |
| All options on | 486 | +14% | 227 | 475-508 |

What this shows:

- **HUD Caching** gives the clearest gain, especially together with Sodium (+9% to +14%). The more HUD elements you have on, the more it saves.
- **All options together** give +5% to +16% here.
- **Entity Culling, Fog Occlusion and Animate Only Visible Textures** alone stay within the noise in this scene. They help in other situations: Entity Culling with many players or mobs behind walls (more than 120 zombies, for example a crowded server spawn), Fog Occlusion in lava, in the Nether or deep under water with a short view range, and Animate Only Visible Textures with 128x and larger resource packs, where every animated texture costs much more.

The benchmark is part of the repository's test code. Run it with `./gradlew :1.21.11:runClientGameTest` and the JVM options `-Daio.bench=true -Dfabric.client.gametest.disableNetworkSynchronizer=true`; the table is written to `versions/1.21.11/build/run/clientGameTest/aio_bench.md`.


## Beta features

**Party** (with Party Vote) and **Animate Only Visible Textures** are marked "(beta)" in the menu. They work in our tests, but they are new and have had little use on real servers yet. If something goes wrong, please open an issue with the game log.

## Instant Hit and Freelook

Some servers forbid mods like these. Both open a warning before they turn on, and once enabled they stay on until you restart the game. They are always off when the game starts. Check a server's rules before joining with them.

## Media Player and PowerShell

Java cannot read the Windows media controls, so the Media Player module uses a small PowerShell script. The module is off by default, and the script only runs while the module is enabled on Windows.

What happens when you turn it on:

1. The mod copies the script from the jar to `config/aio_pvp/media/media.ps1`. The full source is in this repository at [src/main/resources/assets/aio_pvp/media/media.ps1](src/main/resources/assets/aio_pvp/media/media.ps1).
2. It starts `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File media.ps1`. `-ExecutionPolicy Bypass` lets this one script run on systems that block unsigned scripts by default, and it changes no system setting. `-WindowStyle Hidden` keeps a console window from popping up over the game.
3. Every 0.5 seconds the script reads the current media session from Windows (`GlobalSystemMediaTransportControlsSessionManager`): title, artist, app name, position, length and play state. It prints them as one line of JSON, which the mod reads from the script's output.
4. When the track changes, it saves the cover as a 64x64 PNG to `config/aio_pvp/media/art.png`.
5. The play/pause, next and previous buttons write a single word (`toggle`, `next` or `prev`) to `config/aio_pvp/media/command.txt`. The script reads it and sends that command to the media session.

The script stops when you turn the module off or when the game closes (it checks the game's process ID). If it crashes right after starting three times in a row, the mod stops starting it until the next game launch. It reads nothing else from your computer and sends nothing over the network.

## Building from source

```
./gradlew build
```

This builds all four Minecraft versions from one source tree, using [Stonecutter](https://codeberg.org/stonecutter/stonecutter). Each jar is written to `versions/<minecraft version>/build/libs/aio-pvp-<version>+<minecraft version>.jar`. Upload that file, not the `-sources` one. To build one version only, run `./gradlew :26.2:build` (or another version). Gradle needs Java 21 and Java 25 installed: 1.21.11 is compiled for Java 21, 26.x for Java 25.

The code in `src/` is written for 1.21.11 with Mojang names. Lines that differ between versions sit in `//? if` comment blocks, and the settings of each version are in `versions/<minecraft version>/gradle.properties`. To edit the code as another version sees it, run `./gradlew "stonecutterSwitchTo26.2"`, and switch back with `./gradlew "stonecutterSwitchTo1.21.11"` before committing. GitHub Actions builds all four on every push.

## License

MIT, see [LICENSE](LICENSE). The armor break sound comes from Durability Viewer by Guntram Blohm (MIT), see [LICENSE-DurabilityViewer.txt](src/main/resources/LICENSE-DurabilityViewer.txt).
