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

- **`play_sequence`** — play a list of notes with timing (little melodies without external
  clock data).
- **Disc playlist** — queue several discs; `play` advances through them.
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
- **Redstone endpoint** — read and write the redstone signal at the computer's face, so graphs
  can switch the world.
- **`has_display()` endpoint** — for programs that need to know whether a monitor block is
  present; both portable and wall monitors answer.
- **Energy endpoint** — read and extract FE/EU (Create / Forge Energy integration).
- **Autostart disk** — a program that runs as soon as the computer turns on.

### Integrations

- **Create screens as monitors** — bind Create's display blocks as a monitor surface
  (kinetic speed and stress already work through the Create Kinetic node).

## Recently shipped

A short look at what the thread already produced:

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
