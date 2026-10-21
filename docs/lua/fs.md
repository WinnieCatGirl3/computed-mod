# Computer Filesystem (computed:fs)

Every computer owns an isolated CraftOS-style filesystem. Files live under the world save at `<world>/computed/computer/<id>/`, where `<id>` is the computer's numeric id (ComputerCraft-compatible: ids are allocated from `<world>/computed/ids.json`, which stores the last assigned id; the first computer gets `0`). Computers with different ids never read each other's files and the tree travels with the world. There is no `io`/`os` global — everything goes through the `computed:fs` endpoint.

## Which folder belongs to which computer?

- **On the item**: the computer item shows `Computer ID: <id>` under its name, so dropped or stored computers keep their folder.
- **In the editor**: the bottom-left corner shows `Computer ID: <id>` for the open computer.
- **From Lua**: `ctx:endpoint("computed:fs"):call("get_id")` returns the numeric id (a number).
- **On disk**: each root contains a `computer.txt` with the id, UUID, dimension, and block position.
- **Changing it**: **Import / Export → Set Computer ID** in the editor assigns a chosen id (usable in survival). The filesystem switches to the new folder on the next tick (open handles are closed), and the id counter in `ids.json` moves forward so future computers never receive that id again. Computers that share an id also share their package folders (`computed/packages/<id>`).

## Quota

Each computer filesystem is capped at 1 MiB. `get_free_space` reports the remaining bytes. Writes that would exceed the quota fail with an error; delete or overwrite to free space.

## Paths

Paths are absolute-ish strings under the computer root. `/` and empty paths mean the root. `.` segments are skipped and `..` clamps at the root (it cannot escape the computer). Segments are sanitized; control characters and Windows-illegal characters become `_`.

## Top-level methods

```lua
local fs = ctx:endpoint("computed:fs")

local id    = fs:call("get_id")             -- this computer's numeric id (number)
local names = fs:call("list", "/")          -- array of entry names
local ok    = fs:call("exists", "/data.txt")
local isDir = fs:call("is_dir", "/logs")
local size  = fs:call("get_size", "/data.txt")
local free  = fs:call("get_free_space", "/")
fs:call("make_dir", "/logs")
fs:call("move", "/old.txt", "/new.txt")
fs:call("copy", "/data.txt", "/backup.txt")
fs:call("delete", "/tmp.txt")
local path  = fs:call("combine", "/", "logs", "run.txt")
local parent = fs:call("get_dir", "/logs/run.txt")
local name  = fs:call("get_name", "/logs/run.txt")
```

## File handles

`open(path, mode)` returns a handle table. Modes: `r`, `w`, `a` (text) and `rb`, `wb`, `ab` (binary). Handles work with `.` or `:` calls.

```lua
local handle = fs:call("open", "/note.txt", "w")
handle:write_line("hello")
handle:flush()
handle:close()

local reader = fs:call("open", "/note.txt", "r")
local line = reader:read_line()   -- nil at EOF
local rest = reader:read_all()    -- nil at EOF
reader:close()

local binary = fs:call("open", "/blob.bin", "rb")
local chunk = binary:read_bytes(16)  -- raw bytes as a string; nil at EOF
binary:seek("set", 0)
binary:close()
```

Handle methods: `read_line()`, `read_all()`, `read(count)`, `read_bytes(count)`, `write(text)`, `write_line(text)`, `seek(whence, offset)` with `set`/`cur`/`end`, `flush()`, `close()`. Reads return `nil` at EOF. Writing on a read handle or reading on a write handle is an error. Closing twice is safe.

## Lifecycle

Open handles are flushed and closed when the computer unloads (block removed, program replaced, world unload). The filesystem itself is never deleted automatically.
