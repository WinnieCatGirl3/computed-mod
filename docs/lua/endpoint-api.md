# Java Endpoint API

Endpoints are the only bridge from Lua to Minecraft and addons. Handlers receive the host object; Lua receives only validated values.

## ComputedEndpoints.register(id, registration)

Registers one stable namespaced endpoint during mod setup and returns its immutable definition. The registration callback receives an `EndpointBuilder`. It runs on the registration thread, is unavailable to Lua previews, and has the side effect of adding a global API entry. It throws on invalid or duplicate endpoint IDs.

```java
ComputedEndpoints.register("addon:storage", endpoint -> endpoint
        .method("stored", signature, policy, handler, previewFixture, "Returns stored units."));
```

## EndpointBuilder.method(methodId, signature, policy, handler)

Adds a method and returns the builder. Parameters declare stable ID, argument/return schema, execution/yield/side-effect/preview policy, and handler. The short overload marks preview unavailable. It throws on duplicate IDs or incomplete preview policy.

## EndpointBuilder.method(methodId, signature, policy, handler, previewFixture, documentation)

Adds a fully described method and returns the builder. `previewFixture` must be deterministic when preview is enabled. The documentation string feeds completion/signature help. Handler side effects must match the policy.

## ComputedEndpoints.find(id)

Returns an optional immutable endpoint definition. It has no side effects and may be called on either side for metadata lookup.

## ComputedEndpoints.definitions()

Returns all definitions sorted by stable ID. It has no side effects and powers documentation/completion checks.

## EndpointSignature.of(arguments, returns)

Creates a fixed-arity signature from ordered argument and return type lists. Empty lists mean no arguments or no returns. It returns an immutable signature, runs during registration on either side, has no preview behavior or side effects, and rejects no values beyond null lists becoming empty. Use the three-argument `EndpointSignature` constructor with `variadic = true` when trailing values are allowed. Example: `EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.NUMBER))`.

## EndpointPolicy.computerThread(sideEffect, previewAvailable)

Creates a non-yielding computer-thread policy. `sideEffect` declares production mutation and `previewAvailable` requires a deterministic fixture. It returns an immutable policy, runs during registration, and has no side effect itself. Use the record constructor for server-thread or yielding methods. Example: `EndpointPolicy.computerThread(false, true)`.

## EndpointResult.immediate(values...)

Returns validated Lua values without suspending the node. Parameters are zero or more LuaJ values; the default is no values. It can be returned on either execution side, has no side effect itself, and is used unchanged by preview fixtures. Return-schema mismatches become runtime errors. Example: `return EndpointResult.immediate(LuaValue.valueOf(12));`.

## EndpointResult.yielded(continuation)

Suspends the current node until the completion stage supplies an immediate result. The continuation is required and the endpoint policy must declare yielding. It is unavailable in preview unless a separate immediate fixture exists. Cancellation, exceptional completion, unload, definition replacement, or detach fails the invocation while retaining prior committed outputs. Example: `return EndpointResult.yielded(future);`.

## EndpointResult.unavailable(reason)

Returns an explicit unavailable result with a nonblank reason. Calling Lua receives a runtime error; no values or side effects are produced. It is suitable for optional integrations and blocked preview behavior. Example: `return EndpointResult.unavailable("machine is not loaded");`.

## EndpointRuntimeLifecycle.register(listener)

Registers tick and unload callbacks for endpoint-owned external state. The listener is required for useful behavior and is called with the computer ID and host. Registration mutates the global lifecycle list, runs during mod setup, and has no preview callback. Listeners must cancel pending work and release addon objects on unload. Example: `EndpointRuntimeLifecycle.register(listener);`.

## computed:world/time

Signature: `() -> number`. Runs on the server thread, does not yield, has no side effects, and returns fixture `6000` in preview. Errors when the computer host cannot expose world time. Example: `ctx:endpoint("computed:world"):call("time")`.

## computed:world/position

Signature: `() -> number, number, number`. Runs on the server thread, has no side effects, and is available in previews with a fixed position fixture. Errors when host world access is unavailable. Example: `local x, y, z = ctx:endpoint("computed:world"):call("position")`.

## computed:world/rotation

Signature: `() -> number, number, number`. Runs on the server thread, has no side effects, and is available in previews with a fixed rotation fixture. Errors when host world access is unavailable. Example: `local yaw, pitch, roll = ctx:endpoint("computed:world"):call("rotation")`.

## computed:world/block_present

Signature: `(face: string) -> boolean`. Runs on the server thread, has no side effects, and returns `false` in previews. Unknown face names return `false`; invalid argument types are errors. Example: `ctx:endpoint("computed:world"):call("block_present", "front")`.

## computed:redstone/input

Signature: `(face: string) -> number`. Runs on the server thread, has no side effects, and returns zero in previews. Unknown face names return zero. Example: `ctx:endpoint("computed:redstone"):call("input", "left")`.

## computed:redstone/comparator

Signature: `(face: string) -> number`. Runs on the server thread, has no side effects, and returns zero in previews. Unknown face names return zero. Example: `ctx:endpoint("computed:redstone"):call("comparator", "front")`.

## computed:redstone/output

Signature: `(face: string, level: number) -> ()`. Runs on the server thread, clamps power to 0–15, performs a world side effect, and is unavailable in previews. Unknown face names are ignored. Example: `ctx:endpoint("computed:redstone"):call("output", "back", 15)`.

## computed:command/run

Signature: `(string) -> ()`. Runs on the server thread, performs a command side effect, and is unavailable in preview. The Creative Computer type no longer exists, so this endpoint currently errors on every computer; it stays registered for compatibility. Example: `ctx:endpoint("computed:command"):call("run", "say hello")`.

## computed:widget/text

Signature: `(text: string, color: number) -> table`. Runs on the computer thread, does not yield, has no world side effect, and uses the same deterministic fixture in preview. Returns a full text widget table. Example: `ctx:output("widget", ctx:endpoint("computed:widget"):call("text", "Ready", 0xffffffff))`.

## computed:widget/number

Signature: `(value: number, text: string, color: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Renders the preformatted text right-aligned; `value` is carried for semantics. Format the text node-side with `string.format`. Example: `ctx:output("widget", ctx:endpoint("computed:widget"):call("number", 3.14159, "3.1416", 0xffffffff))`.

## computed:widget/clock

Signature: `(color: number, showSeconds: boolean) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Errors on invalid argument types. Example: `ctx:endpoint("computed:widget"):call("clock", 0xffffffff, true)`.

## computed:widget/button

Signature: `(label: string, color: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. The returned table carries the node instance ID for targeted input events. Example: `ctx:endpoint("computed:widget"):call("button", "Run", 0xffffffff)`.

## computed:widget/checkbox

Signature: `(label: string, color: number, checked: boolean) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Clicks on the rendered widget deliver a toggle `input` event to the originating node; the node owns the checked state. Example: `ctx:endpoint("computed:widget"):call("checkbox", "Enabled", 0xffffffff, false)`.

## computed:widget/text_input

Signature: `(placeholder: string, color: number, value: string) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Clicking the widget opens a client-side text dialog; the submitted string is routed to the node's `input` event. Example: `ctx:endpoint("computed:widget"):call("text_input", "Name…", 0xffffffff, "")`.

## computed:widget/slider

Signature: `(value: number, minimum: number, maximum: number, color: number, step: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Errors on invalid argument types. Example: `ctx:endpoint("computed:widget"):call("slider", 0.5, 0, 1, 0xffffffff, 0.01)`.

## computed:widget/progress

Signature: `(value: number, maximum: number, color: number, segments: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Errors on invalid argument types. Example: `ctx:endpoint("computed:widget"):call("progress", 5, 10, 0xffffffff, 10)`.

## computed:widget/terminal

Signature: `(rows: table, fg: number, bg: number, scale: number, cursorX: number, cursorY: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. `rows` is an array of strings or styled row tables from `computed:widget/line`; at most 64 rows are kept and each row is clipped to 256 characters. `fg`/`bg` are default ARGB colors, `scale` is clamped to 0.5–2, and `cursorX`/`cursorY` hide the cursor when negative. Example: `ctx:endpoint("computed:widget"):call("terminal", {"Ready"}, 0xffffffff, 0xff000000, 1, -1, -1)`.

## computed:widget/line

Signature: `(text: string, fg: number, bg: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Returns a styled terminal row table usable in a `computed:widget/terminal` rows array. Example: `ctx:endpoint("computed:widget"):call("line", "OK", 0xff00ff00, 0xff000000)`.

## computed:widget/panel

Signature: `(color: number, border: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Returns a panel widget; `border` of `0` draws no border. Example: `ctx:endpoint("computed:widget"):call("panel", 0xff3c3c3c, 0)`.

## computed:widget/text_block

Signature: `(text: string, fg: number, bg: number, alignment: string) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. Returns a multiline text block widget; lines are separated by `\n` and `bg` of `0` is transparent. Example: `ctx:endpoint("computed:widget"):call("text_block", "line 1\nline 2", 0xffffffff, 0, "left")`.

## computed:widget/table

Signature: `(columns: string, rows: table, headerFg: number, headerBg: number, cellFg: number, cellBg: number) -> table`. Runs on the computer thread, has no side effects, and has a deterministic preview fixture. `columns` is a comma-separated header list; `rows` is an array of comma-separated strings or arrays of cell strings. At most 16 columns and 64 rows of 64 characters are kept. Example: `ctx:endpoint("computed:widget"):call("table", "Name,Value", {"a,1", "b,2"}, 0xffffffff, 0xff202020, 0xffffffff, 0xff000000)`.

## computed:monitor/show

Signature: `(widgets: table) -> ()`. Runs on the server thread, refreshes an adjacent monitor, performs a world side effect, and is unavailable in previews. The endpoint target selects the computer-relative face. Invalid widget records are ignored. Example: `ctx:endpoint("computed:monitor", "front"):call("show", widgets)`.

## computed:fs/list

Signature: `(path: string) -> table`. Lists entry names inside a directory on the per-computer isolated filesystem (rooted under the world save). Runs on the computer thread with a filesystem side effect and is unavailable in preview. Errors when the filesystem is not attached or the path is not a directory. Example: `local names = ctx:endpoint("computed:fs"):call("list", "/")`.

## computed:fs/exists

Signature: `(path: string) -> boolean`. Reports whether a path exists on the computer filesystem. Runs on the computer thread, does not yield, and is unavailable in preview. Errors when the filesystem is not attached. Example: `local present = ctx:endpoint("computed:fs"):call("exists", "/config.txt")`.

## computed:fs/is_dir

Signature: `(path: string) -> boolean`. Reports whether a path is a directory. Runs on the computer thread, does not yield, and is unavailable in preview. Errors when the filesystem is not attached. Example: `local isFolder = ctx:endpoint("computed:fs"):call("is_dir", "/logs")`.

## computed:fs/is_read_only

Signature: `(path: string) -> boolean`. Always returns `false`; the computer filesystem is writable within its quota. Runs on the computer thread and is unavailable in preview. Example: `local locked = ctx:endpoint("computed:fs"):call("is_read_only", "/data.txt")`.

## computed:fs/get_size

Signature: `(path: string) -> number`. Returns the size of a file in bytes. Runs on the computer thread and is unavailable in preview. Errors when the filesystem is not attached, the path is missing, or the path is a directory. Example: `local bytes = ctx:endpoint("computed:fs"):call("get_size", "/data.txt")`.

## computed:fs/get_free_space

Signature: `(path: string) -> number`. Returns remaining quota bytes for the computer filesystem (path is accepted for CraftOS compatibility and ignored). Runs on the computer thread and is unavailable in preview. Example: `local free = ctx:endpoint("computed:fs"):call("get_free_space", "/")`.

## computed:fs/make_dir

Signature: `(path: string) -> boolean`. Creates a directory and parents; succeeds when the directory already exists. Runs on the computer thread with a filesystem side effect and is unavailable in preview. Errors when the filesystem is not attached or the path is invalid. Example: `ctx:endpoint("computed:fs"):call("make_dir", "/logs")`.

## computed:fs/move

Signature: `(from: string, to: string) -> boolean`. Moves or renames a file within the computer filesystem. Runs on the computer thread with a filesystem side effect and is unavailable in preview. Errors when the source is missing, a directory, or the quota would be exceeded. Example: `ctx:endpoint("computed:fs"):call("move", "/old.txt", "/new.txt")`.

## computed:fs/copy

Signature: `(from: string, to: string) -> boolean`. Copies a file within the computer filesystem. Runs on the computer thread with a filesystem side effect and is unavailable in preview. Errors when the source is missing, a directory, or the quota would be exceeded. Example: `ctx:endpoint("computed:fs"):call("copy", "/data.txt", "/backup.txt")`.

## computed:fs/delete

Signature: `(path: string) -> boolean`. Deletes a file or directory tree. Missing paths succeed. Runs on the computer thread with a filesystem side effect and is unavailable in preview. Errors when the filesystem is not attached or the path is invalid. Example: `ctx:endpoint("computed:fs"):call("delete", "/tmp.txt")`.

## computed:fs/combine

Signature: `(...) -> string`. Joins path segments into a normalized computer path (variadic). Runs on the computer thread, has no side effects, and is unavailable in preview. Example: `local path = ctx:endpoint("computed:fs"):call("combine", "/", "logs", "run.txt")`.

## computed:fs/get_dir

Signature: `(path: string) -> string`. Returns the parent directory of a path. Runs on the computer thread, has no side effects, and is unavailable in preview. Example: `local parent = ctx:endpoint("computed:fs"):call("get_dir", "/logs/run.txt")`.

## computed:fs/get_name

Signature: `(path: string) -> string`. Returns the final segment of a path. Runs on the computer thread, has no side effects, and is unavailable in preview. Example: `local name = ctx:endpoint("computed:fs"):call("get_name", "/logs/run.txt")`.

## computed:fs/get_id

Signature: `() -> number`. Returns this computer's numeric id — the folder name under `<world>/computed/computer/`. Runs on the computer thread, has no side effects, and is unavailable in preview. Example: `local id = ctx:endpoint("computed:fs"):call("get_id")`.

## computed:fs/open

Signature: `(path: string, mode: string) -> table`. Opens a file with mode `r`, `w`, `a`, `rb`, `wb`, or `ab` and returns a handle table. Handle methods: `read_line()`, `read_all()`, `read(count)`, `read_bytes(count)`, `write(text)`, `write_line(text)`, `seek(whence, offset)`, `flush()`, `close()`. Reads return `nil` at EOF. Runs on the computer thread with a filesystem side effect and is unavailable in preview. Errors on unsupported modes, missing read targets, closed handles, mode mismatches, or quota exhaustion. Example: `local handle = ctx:endpoint("computed:fs"):call("open", "/note.txt", "w"); handle:write_line("hi"); handle:close()`.

## computed:net/send

Signature: `(target: number, message: string) -> boolean`. Queues a message for the computer with that numeric ID (computers sharing an ID share the inbox). Messages are at most 1024 characters; 64 messages are kept per computer and the oldest are dropped when the queue is full. Returns `true` when the target computer was online, `false` when the message was only queued. Runs on the server thread with a side effect and is unavailable in preview. Errors on a negative target or an oversized message. Example: `ctx:endpoint("computed:net"):call("send", 3, "ready")`.

## computed:net/broadcast

Signature: `(message: string) -> number`. Queues the message for every online computer except the sender and returns how many were reached. The same size and queue limits as `send` apply. Runs on the server thread with a side effect and is unavailable in preview. Errors on an oversized message. Example: `local reached = ctx:endpoint("computed:net"):call("broadcast", "ping")`.

## computed:net/receive

Signature: `(timeout: number) -> sender: number?, message: string?`. Waits for the next queued message and returns the sender ID and the message, or `nil` values when the timeout in seconds elapses first (`0` waits indefinitely). The node yields while waiting, so other nodes keep ticking. Messages are held for up to two minutes for computers that are offline or not listening. Runs on the server thread with a side effect and is unavailable in preview. Errors when the timeout is negative or above 600 seconds. Example: `local sender, message = ctx:endpoint("computed:net"):call("receive", 5)`.

## create:kinetic/speed

Signature: `() -> number`. The target is a computer-relative face. It runs on the computer/server tick thread, does not yield or mutate the world, and returns zero in preview. Production errors when Create is absent or the target is invalid; a non-kinetic block returns zero. Example: `ctx:endpoint("create:kinetic", "front"):call("speed")`.

## create:kinetic/stress

Signature: `() -> number`. It returns the adjacent Create block's applied stress units, runs on the computer/server tick thread, does not yield or mutate, and returns zero in preview. Missing Create and invalid targets are errors; non-kinetic blocks return zero. Example: `ctx:endpoint("create:kinetic", "left"):call("stress")`.

## create:kinetic/capacity

Signature: `() -> number`. It returns the adjacent Create block's generated stress capacity, runs on the computer/server tick thread, does not yield or mutate, and returns zero in preview. Missing Create and invalid targets are errors; non-kinetic blocks return zero. Example: `ctx:endpoint("create:kinetic", "right"):call("capacity")`.

## create:redstone_link/receive

Signature: `(firstItemId: string, secondItemId: string) -> number`. It registers a virtual Create redstone-link listener owned by the node and returns power from 0 through 15. It runs on the server tick thread, does not yield, retains an external network actor, and is unavailable in preview. Invalid item IDs, absent Create, or unavailable network APIs are errors or return zero. Actors are removed on definition replacement and unload. Example: `ctx:endpoint("create:redstone_link"):call("receive", "minecraft:iron_ingot", "minecraft:redstone")`.

## create:redstone_link/transmit

Signature: `(firstItemId: string, secondItemId: string, strength: number) -> ()`. It registers or updates a virtual Create transmitter, clamps strength to 0 through 15, performs a network side effect, runs on the server tick thread, does not yield, and is unavailable in preview. Invalid items or absent Create fail the invocation. Actors are removed on definition replacement and unload. Example: `ctx:endpoint("create:redstone_link"):call("transmit", "minecraft:iron_ingot", "minecraft:redstone", 15)`.

## computercraft:channel/read

Signature: `(channel: string) -> table`. It returns `{ value = ... }` for the named value written by an attached CC computer. Names contain 1 through 64 characters. It runs on the computer thread, does not yield or mutate the world, and returns an empty table fixture in preview. It errors when CC is absent or the host is not a server computer. Example: `ctx:endpoint("computercraft:channel"):call("read", "control")`.

## computercraft:channel/publish

Signature: `(channel: string, value: table) -> ()`. It publishes a named graph value and queues `computed_output_changed` on attached CC computers when the value changes. It runs on the computer thread, performs an integration side effect, does not yield, and is unavailable in preview. Unsupported or cyclic values and invalid channel names are errors. Example: `ctx:endpoint("computercraft:channel"):call("publish", "status", { ready = true })`.

## computercraft:peripheral/methods

Signature: `() -> table`. The endpoint target is a computer-relative side. It returns the adjacent peripheral's sorted public method names, runs on the server tick thread, does not yield or mutate, and is unavailable in preview. Missing CC, invalid sides, and detached peripherals are errors. Call it through the bound shorthand: `ctx:endpoint("computercraft:peripheral", "left"):methods()`.

## computercraft:peripheral/call

Signature: `(methodName: string, ...) -> table`. It invokes a public CC API method and returns all results as an integer-keyed table. It runs on the server tick thread, may perform peripheral side effects, and may yield for main-thread tasks, `MethodResult` continuations, or queued events. It is unavailable in preview. Missing methods, unsupported values, detach, unload, and failed continuations are errors; prior outputs remain committed while suspended. Example: `local result = ctx:endpoint("computercraft:peripheral", "left"):call("getEnergy")`.

## aeronautics:telemetry/measure

Signature: `() -> table`. It returns telemetry for the nearest Create contraption (including Create Aeronautics structures) around the computer. The table has `present` (boolean), `name` and `source` (strings), `x`/`y`/`z` (contraption position), `vx`/`vy`/`vz` (contraption velocity), `speed`, `distance`, `heading`, `pitch`, `roll`, the heading frame `hx`/`hy`/`hz`, the right frame `rx`/`ry`/`rz`, and the angular velocity `wx`/`wy`/`wz`. It runs on the server tick thread, does not yield or mutate the world, and returns `present = false` with zeroed fields in preview. Missing Create Aeronautics is an error; when no contraption is within range `present` is `false`. Example: `local telemetry = ctx:endpoint("aeronautics:telemetry"):call("measure")`.
