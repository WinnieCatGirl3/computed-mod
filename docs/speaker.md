# Speaker

The Speaker block plays notes, sounds, generated waves, and music discs from your graph. Place up
to six speakers around one computer — one per side.

## Crafting

```text
Iron     Jukebox     Iron
Note Block  Redstone Note Block
Iron     Iron        Iron
```

The speaker faces you when you place it. Right-click it with a music disc to insert the disc;
right-click it empty-handed to take the disc back.

## Addressing a speaker

Every speaker node has a **Face** field (`front` by default). It selects the side of the computer
the speaker sits on: `front`, `back`, `left`, `right`, `up`, `down` (also `north`, `east`, `south`,
`west`, `top`, `bottom`). The node errors if no speaker is on that side.

## The seven speaker nodes

Six of them are triggers: they fire when the **Trigger** input turns on or an event arrives.

| Node | What it does | Extra fields |
|---|---|---|
| **Play Note** | Plays one note-block note | Instrument (16 choices, default `harp`), Note 0–24 (default 12) |
| **Play Sound** | Plays any named sound | Sound ID (e.g. `block.note_block.pling`), Volume 0–4, Pitch 0.5–2 |
| **Play Wave** | Plays a synthesized waveform | Wave (`sine`, `square`, `saw`, `triangle`, `noise`), Frequency 55–3520 Hz, Volume 0–3 |
| **Play Disc** | Starts the inserted music disc | — |
| **Stop** | Stops whatever is playing | — |
| **Eject** | Spits the disc out | — |
| **Speaker Status** | Reports the speaker state | Outputs: `has_disc`, `is_playing` (updates every tick) |

Example — play a doorbell note when a button is pressed:

1. Place a **Play Note** node, set **Instrument** to `bell` and **Note** to `18`.
2. Connect a button widget (or any boolean source) to **Trigger**.
3. Set **Face** to the side the speaker is on.

Example — an alarm that alternates two notes: drive **Trigger** from a fast
[counter](nodes.md) or [pulse](nodes.md) node and switch the note with an
[if branch](nodes.md).

## Useful limits

| Limit | Value |
|---|---|
| Note range | 0–24 (12 = A, the default) |
| Note instruments | `harp`, `basedrum`, `snare`, `hat`, `bass`, `flute`, `bell`, `guitar`, `chime`, `xylophone`, `iron_xylophone`, `cow_bell`, `didgeridoo`, `bit`, `banjo`, `pling` |
| Wave frequencies | 55–3520 Hz |
| Wave aliases | `sin`→sine, `sq`→square, `sawtooth`→saw, `tri`→triangle, `white`→noise |
| Simultaneous discs | One per speaker |
| Speakers per computer | Six (one per side) |

Timing is up to your graph: speaker nodes have no built-in wait — use a **Delay** node or drive
them from a tick-based counter.
