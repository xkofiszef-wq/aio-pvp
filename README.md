# AIO PvP

All-in-one client mod for PvP and quality of life on Fabric. It adds a movable HUD, combat tools and visual tweaks, and keeps them in one menu instead of a dozen separate mods.

Client side only. It works on any server and in singleplayer.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.15 or newer
- Fabric API
- Java 21
- Mod Menu (optional, adds a config button to the mod list)

Works with Sodium and ImmediatelyFast (tested with Sodium 0.8.14, ImmediatelyFast 1.14.3 and Fabric API 0.141.6). Both make the game noticeably smoother, especially on busy servers.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put Fabric API and `aio-pvp-<version>+<minecraft version>.jar` (for example `aio-pvp-1.0.29+1.21.11.jar`) into your `mods` folder.
3. Start the game.

## How to use

Press Right Shift to open the menu. You can change this key in Options > Controls or with the "Menu" button in the mod menu itself.

The left bar lists the categories (HUD, Combat, Visuals, Items, Misc). Each module is a card with a switch. Click a card to open its settings, with a live preview at the top. The search field finds modules by name, description or setting.

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

The mod also fixes MC-263293 without a setting: toggle sprint and toggle sneak stay on after you respawn.

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

The jar is written to `build/libs/aio-pvp-<version>+<minecraft version>.jar`. Upload that file, not the `-obf` or `-sources` ones.

## License

MIT, see [LICENSE](LICENSE). The armor break sound comes from Durability Viewer by Guntram Blohm (MIT), see [LICENSE-DurabilityViewer.txt](src/main/resources/LICENSE-DurabilityViewer.txt).
