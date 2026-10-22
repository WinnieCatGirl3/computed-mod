# Architecture and Package Boundaries

- `graph` owns immutable graphs, stable ports, deterministic analysis, and scheduling.
- `lua/compiler` owns validation, SHA-256 hashing, and prototype caching.
- `lua/runtime` owns per-computer VMs, transactional node instances, coroutines, and state serialization.
- `lua/sandbox` owns globals and instruction budgets.
- `lua/node` owns schemas, the fluent contract, bundled definitions, and embedded libraries.
- `lua/endpoint` owns safe Java registrations and preview fixtures.
- `client/editor` owns explorer and focused editor state.
- `client/renderer/node` owns the semantic palette and shared layout.
- `persistence` owns format 3 and the clean legacy reset.
- `network` remains authoritative for distance, permissions, revisions, and payload size.
- `integration/computercraft`, `integration/create`, `integration/aeronautics`, `integration/jade`, and `integration/wthit` own optional API boundaries and lifecycle cleanup.

Minecraft and addon objects stop at endpoint handlers. Lua nodes communicate through scheduler-owned edges, never by directly calling neighbors. `WireEditorController` remains the unchanged compatibility boundary for curves, colors, thickness, pulses, waypoints, hit testing, and socket behavior.

Format 3 stores one root graph, an embedded Lua library, definition ID/hash references, stable port snapshots, persistent state, revision, and metadata. It stores no Functions or Sections.
