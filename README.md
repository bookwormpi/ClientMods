# ClientSideTesting

[![Build Status](https://github.com/bookwormpi/ClientSideTesting/actions/workflows/build.yml/badge.svg)](https://github.com/bookwormpi/ClientSideTesting/actions/workflows/build.yml)

A Fabric client-side utility mod for Minecraft, focused on block search, HUD overlays, and chunk player display.

---

## Features

- **Block Search & Highlight**
  - Instantly scan loaded chunks for a specific block type.
  - See the closest found block’s icon, name, and colored coordinates in a HUD overlay.
  - All found blocks are highlighted in the world with a dynamic colored box.
  - HUD overlay is smartly positioned to avoid status effect icons.
  - Results update immediately when you change the block type or move between chunks.

- **Chunk Player HUD**
  - (Optional) Overlay showing which players are in your current chunk.

- **Performance & Usability**
  - Block search is asynchronous and throttled to avoid lag.
  - No stale or lingering results when switching block types or disabling the feature.
  - Configurable search distance and maximum rendered blocks.

- **Modern Fabric Mod**
  - Built for recent Minecraft versions using Fabric API.
  - Designed for client-side use only (no server required).

---

## Getting Started

1. **Download**
   - Grab the latest release from the [GitHub Releases page](../../releases).

2. **Install**
   - Place the `.jar` file in your Minecraft `mods` folder.
   - Requires [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).

3. **Usage**
   - Enable the block search feature via your mod keybind or config (see below).
   - Set the block type to search for (default: Diamond Block).
   - Move around and watch the HUD and world highlights update in real time!

---

## Configuration

- **Block to Search:** Changeable via in-game config or keybind (default: Diamond Block).
- **Search Distance:** How far (in chunks) to search for blocks. Defaults to your render distance (max 16).
- **Max Rendered Blocks:** Limits the number of highlighted blocks for performance reasons.
- **Search Interval:** How often to rescan (in ticks).

---

## Building & Contributing

- **Builds:**
  - Automated with GitHub Actions. Each push/PR builds a `.jar` and uploads it as an artifact.
  - Releases are created automatically for new versions.

---

## Credits & Inspiration

- **Mod Author:** [bookwormpi](https://github.com/bookwormpi)
- **README & Documentation Style:**
  - Inspired by [Refined Storage 2](https://github.com/refinedmods/refinedstorage2)
- **AI Assistance:**
  - Most code and documentation generated with help from [GitHub Copilot](https://github.com/features/copilot)

---

## License

This project is licensed under the MIT License. See [LICENSE.txt](LICENSE.txt) for details.
