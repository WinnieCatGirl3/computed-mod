# Ideas & Roadmap

This page collects what is planned for Winnie's Computed — and it is a **thread you can write
in**. Everything here starts as a community idea.

!!! question "Have an idea?"
    Open a post in the **[Ideas discussion thread](https://github.com/WinnieCatGirl3/computed-mod/discussions/categories/ideas)**
    on GitHub. Reply to existing posts to vote with a 👍, or start your own — a short title and
    one or two sentences are enough. The best ideas get picked up here.

## Open ideas

Ideas the community and the maintainer still want to build. Click through to the thread if you
want to comment on one.

### Speaker and audio

- **Custom instrument samples** — drop your own `.ogg` files into the speaker folder to use
  them as instruments and waveforms.

### Widgets and display

- **Chart widget** — plot values over time as a curve (progress bar, checkbox, and text input
  already exist).
- **Image widget** — show a texture or item icon on the monitor.
- **Right-click widget actions** — a second action column for widgets (for example a context
  menu).

### Computer and world

- **Radar / sensor endpoint** — query entities, light level, weather, or blocks in a radius
  (`scan(radius)` returns a table).
- **Energy endpoint** — read and extract FE/EU (Create / Forge Energy integration).

### Integrations

- **Create screens as monitors** — bind Create's display blocks as a monitor surface
  (kinetic speed and stress already work through the Create Kinetic node).

## Recently shipped

A short look at what the thread already produced:

- Speaker: **Play Sequence** (timed `instrument:note:seconds` melodies without clock nodes) and a
  **disc playlist** — queue up to 8 discs with right-click, `play` advances through them, loop
  cycles the whole queue.
- **Autostart disk** — right-click a written Program Disk to flag it; the computer loads and runs
  the program at startup (through the regular validation, an existing program always wins).
- **`has_display()`** — `computed:monitor/has_display` and the **Display Present** node answer
  whether a wall monitor is attached at a face or a player carries a paired portable monitor.
- **Redstone endpoints** — `computed:redstone` reads input/comparator levels and writes output at
  the computer's faces (Redstone Input, Comparator Read, Redstone Emitter nodes).
- Speaker: **Play Sweep** (frequency glide), **attack/decay envelopes** on Play Wave, and
  **disc loop** for endless playback.
- Aeronautics: **vertical speed** and **altitude** nodes, plus the **Threshold Trigger** node
  for alarms (altitude below X, overspeed, climb warnings).
- Glasses HUD: graph widgets on your glasses, a movable widget region in the K designer, and
  a reworked glasses model with its own recipe.
- Undo/redo and auto-save in the editor, the Lua live preview, and a demo disk in the
  creative tab.
- Portable monitor: right-click a channel tab to remove it, and tabs show computer names.
- Computer-to-computer messaging (`computed:net`), the Aeronautics telemetry nodes, and the
  Create Kinetic sensor.

!!! note "Raw backlog"
    The maintainer's working list lives in
    [`IDEAS.md`](https://github.com/WinnieCatGirl3/computed-mod/blob/main/IDEAS.md) in the
    repository — the thread is the place to add to it.
