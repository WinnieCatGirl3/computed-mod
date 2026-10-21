# Winnie's Computed

Fork of [The Propulsion Team](https://github.com/Propulsion-Team/computed-mod)'s **Computed**,
re-themed and reworked with original CC0 assets and my own spin on it.

Winnie's Computed is a NeoForge 1.21.1 programmable Lua node-graph mod. LuaJ powers one sandboxed
VM per computer while the Computed engine owns graph scheduling, safe Minecraft endpoints,
persistence, networking, the editor, monitor rendering, and integrations.

Program format 3 stores one root graph and an embedded Lua definition library. Legacy graphs,
Functions, Sections, JSON custom nodes, CMP1, and CMP2 are intentionally discarded or rejected
without migration.

## Guides

New to Computed? Start with the [Getting Started](getting-started.md) tutorial — your first
program in five minutes, no Lua required.

| Guide | Purpose |
|---|---|
| [Getting Started](getting-started.md) | Build your first program step by step |
| [Using the Editor](editor.md) | Shortcuts, frames, comments, all toolbar tools |
| [In-Game Documentation](in-game-docs.md) | 120+ importable example programs |
| [Monitors](monitors.md) | Screens, widget layout, all widget types |
| [Portable Monitor](portable-monitor.md) | Handheld display and Link Cards |
| [Glasses](glasses.md) | Wearable HUD with live graph widgets |
| [Speaker](speaker.md) | Notes, sounds, waves, and music discs |
| [Node Catalog](nodes.md) | Every bundled node at a glance |
| [Integrations](integrations.md) | Create, Aeronautics, CC:Tweaked, Jade, GeckoLib |
| [Authoring](lua/authoring-guide.md) | Create your own Lua nodes |
| [Lua API](lua/lua-api-reference.md) | Complete definition and context method reference |
| [Java endpoints](lua/endpoint-api.md) | Safely expose addon and Minecraft behavior |
| [Types and state](lua/types-and-state.md) | Connection types and persistent values |
| [Sandbox](lua/sandbox-and-budgets.md) | Available libraries and instruction limits |
| [Filesystem](lua/fs.md) | Per-computer isolated CraftOS-style files |
| [Live preview](lua/live-preview.md) | Editor validation and fixtures |
| [Import/export](lua/import-export.md) | Graph packages and program disks |
| [CC:Tweaked](lua/computercraft.md) | Optional peripheral bridge |
| [Graph events](lua/graph-events.md) | Named wireless graph events |
| [Architecture](lua/architecture.md) | Runtime and package boundaries |

Runnable code examples live under
[`docs/lua/examples`](https://github.com/WinnieCatGirl3/computed-mod/tree/main/docs/lua/examples)
in the repository.

## Building

Build and verify with:

```text
./gradlew clean check build
```

## License

Code under GPL-3.0, all visual assets (textures, models, artwork) under CC0 1.0.
See the repository [license](https://github.com/WinnieCatGirl3/computed-mod/blob/main/LICENSE.txt)
and the [Web's Node Lib notice](https://github.com/WinnieCatGirl3/computed-mod/blob/main/LICENSE-webs_node_lib.txt).
