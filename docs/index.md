---
title: Home
---

<div class="computed-hero" markdown>

![Computed](assets/images/logo.png){ .hero-logo width=96 }

# Winnie's Computed

Lua node-graph programming for NeoForge 1.21.1 — visual scripting with a sandboxed Lua runtime.

[Get started](getting-started.md){ .md-button .md-button--primary }
[View on GitHub](https://github.com/WinnieCatGirl3/computed-mod){ .md-button .md-button--gh }

</div>

<div class="grid cards" markdown>

-   **Getting Started**

    ---

    Build your first program in five minutes, no Lua required

-   **Using the Editor**

    ---

    Shortcuts, frames, comments, and all toolbar tools

-   **In-Game Documentation**

    ---

    120+ example programs you can import with one click

-   **Monitors**

    ---

    Screens, widget layout, and every widget type

-   **Portable Monitor**

    ---

    Handheld display and Link Cards

-   **Glasses**

    ---

    Wearable HUD with live graph widgets

-   **Speaker**

    ---

    Notes, sounds, waves, and music discs

-   **Node Catalog**

    ---

    Every bundled node at a glance

-   **Integrations**

    ---

    Create, Aeronautics, CC:Tweaked, Jade, GeckoLib

-   **Lua Authoring**

    ---

    Create your own nodes in Lua

-   **Lua API Reference**

    ---

    Complete definition and context method reference

-   **Java Endpoints**

    ---

    Safely expose addon and Minecraft behavior

-   **Types and State**

    ---

    Connection types and persistent values

-   **Sandbox**

    ---

    Available libraries and instruction limits

-   **Filesystem**

    ---

    Per-computer isolated CraftOS-style files

-   **Live Preview**

    ---

    Editor validation and fixtures

-   **Import and Export**

    ---

    Graph packages and program disks

-   **CC:Tweaked**

    ---

    Optional peripheral bridge

-   **Graph Events**

    ---

    Named wireless graph events

-   **Architecture**

    ---

    Runtime and package boundaries

</div>

## About

Fork of [The Propulsion Team](https://github.com/Propulsion-Team/computed-mod)'s **Computed**,
re-themed and reworked with original CC0 assets and my own spin on it. LuaJ powers one sandboxed VM
per computer while the Computed engine owns graph scheduling, safe Minecraft endpoints,
persistence, networking, the editor, monitor rendering, and integrations.

Program format 3 stores one root graph and an embedded Lua definition library. Legacy graphs,
Functions, Sections, JSON custom nodes, CMP1, and CMP2 are intentionally discarded or rejected
without migration.

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
