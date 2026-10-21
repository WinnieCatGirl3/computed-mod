# CC:Tweaked Integration

CC:Tweaked support is optional and targets its public `dan200.computercraft.api` surface. It is peripheral interoperability, not CraftOS compatibility.

Adjacent peripherals are exposed through `ctx:endpoint("computercraft:peripheral", side)`. A proxy provides `methods()` and `call(methodName, ...)`. Calls return an integer-keyed result table. Main-thread tasks and yielded `MethodResult` continuations suspend the node while committed outputs remain visible. Queued peripheral events resume pull-event callbacks. Unload, definition replacement, peripheral detach, or cancellation terminates the continuation safely.

Dedicated CC Input and CC Output Lua nodes exchange named channel values. The Computed peripheral lists channels, reads outputs, writes inputs, and emits output-changed events.

When Create Aeronautics is installed, the peripheral also exposes a fixed `measure()` method. It returns the same telemetry table as the `aeronautics:telemetry` endpoint (position, velocity, speed, heading, and the rest).

Filesystem, terminal, rednet, HTTP, and other CraftOS globals are not exposed.

```lua
local node = computed.node(1, "example:cc_query", "CC Query")
node:field("side", "direction", { default = "left" })
node:output("result", "table")
node:on_run(function(ctx)
    local peripheral = ctx:endpoint("computercraft:peripheral", ctx:field("side"))
    ctx:output("result", { methods = peripheral:methods() })
end)
return node
```

## Calling an adjacent peripheral

```lua
local node = computed.node(1, "example:cc_call", "CC Call")
node:category("integration/computercraft")
node:execution("tick")
node:field("side", "direction", { default = "left" })
node:output("result", "table")
node:on_run(function(ctx)
    local peripheral = ctx:endpoint("computercraft:peripheral", ctx:field("side"))
    ctx:output("result", peripheral:call("getEnergy"))
end)
return node
```

If `getEnergy` completes immediately, the result table is committed in the same graph tick. If it returns a yielding `MethodResult`, the invocation remains private until its callback completes. The prior committed output remains visible while waiting.

## Using Computed channels from CraftOS

```lua
local computed = peripheral.find("computed")
computed.write("control", { enabled = true, level = 12 })

for _, name in ipairs(computed.listChannels()) do
    print(name)
end

local status = computed.read("status")
local event, channel, value = os.pullEvent("computed_output_changed")
```

`write(channel, value)` feeds a CC Input node. `read(channel)` reads the latest CC Output value. `listChannels()` returns input and output channel names. `computed_output_changed` is queued only when a published value changes.

## Yielded call behavior

Only values supported by both Computed state serialization and the CC API cross the bridge: nil, booleans, finite numbers, strings, and acyclic tables with string or integer keys. Filesystem mounts are refused. A detached peripheral fails its pending invocation, releases its attachment, and leaves committed state and outputs unchanged.
