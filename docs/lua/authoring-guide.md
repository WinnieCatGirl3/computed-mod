# Lua Node Authoring Guide

New to Computed? The [Getting Started](../getting-started.md) tutorial shows the editor basics
first. This page is about writing your own nodes in Lua.

Every Computed node is a Lua definition. A file creates one node, declares an immutable schema, installs callbacks, and returns that node.

```lua
local node = computed.node(1, "example:counter", "Counter")

node:category("state")
node:style("standard")
node:input("increment", "number")
node:output("count", "number")
node:field("step", "number", {
    default = 1,
    min = 0,
    max = 10,
    control = "slider",
    step = 0.5,
    label = "Step Size"
})
node:state("count", 0)

node:on_run(function(ctx)
    local next = ctx:state("count") + ctx:input("increment") * ctx:field("step")
    ctx:set_state("count", next)
    ctx:output("count", next)
end)

return node
```

Definition IDs are stable, lowercase, namespaced identifiers. Port, field, state, and event IDs start with a lowercase letter and contain only lowercase letters, digits, `_`, `.`, or `-`. Schemas cannot change while an instance is running.

Choose `input` execution for dataflow nodes, `tick` for world sensors, `step` for explicitly stepped nodes, and `event` when only named handlers should run. A failed invocation discards all staged output and state changes. The next eligible execution retries the callback.

For user-authored wireless graph flow, see [Graph events](graph-events.md). Lua callbacks can inspect configurable per-instance inputs with `ctx:inputs()`.

Use endpoints for Minecraft or addon access. Lua values never contain Java or Minecraft objects.

## Create a node in a computer

Open the Node Explorer, expand **User Nodes**, and choose **New Lua Node…**. The editor starts with a unique reusable `user:node_<id>` definition. Edit and preview the source, then choose **Save**. Computed adds the validated definition to that computer's embedded library and places its first instance at the canvas anchor. Additional instances appear under **User Nodes** and can be placed like bundled nodes.

Right-click an existing definition in the explorer to edit it. Replacing a definition with the same ID requires confirmation when its source hash changes.

## Field controls

Field values belong to each node instance and participate in autosave, undo, duplication, and server validation. Use value controls for unrestricted numbers and explicit sliders for bounded values:

```lua
node:field("name", "text", { default = "Display" })
node:field("enabled", "boolean", { default = true })
node:field("side", "direction", { default = "front" })
node:field("mode", "choice", {
    default = "normal",
    choices = { "normal", "inverted" }
})
node:field("speed", "number", {
    default = 10,
    min = 0,
    max = 20,
    control = "slider",
    step = 1
})
```

See [Lua API Reference](lua-api-reference.md), [Types and State](types-and-state.md), [Sandbox and Budgets](sandbox-and-budgets.md), and the [examples](https://github.com/WinnieCatGirl3/computed-mod/tree/main/docs/lua/examples) in the repository.
