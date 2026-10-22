# Integrations

Computed talks to other mods through optional integrations. Only NeoForge and Minecraft are
required — every integration activates on its own when the matching mod is installed.

## CC:Tweaked

Full two-way support: call adjacent peripherals from Lua, exchange named channel values, and use
Computed as a CC peripheral. See [CC:Tweaked Integration](lua/computercraft.md) for the details and
the two bundled nodes (`cc_input`, `cc_output`).

## Create

| Node | Does |
|---|---|
| **Create Kinetic** | Reads speed, stress, and stress capacity of the Create block on a chosen face |
| **Create Link Sender** | Transmits on a Create redstone link (two item filters, power 0–15) |
| **Create Link Receiver** | Receives power from a Create redstone link |

All three need an adjacent Create block or an available redstone-link network.

## Create Aeronautics

When Create Aeronautics is installed, Computed measures the nearest contraption (including flying
structures) around the computer — 17 ready-made nodes from **Aeronautics Telemetry** (everything at
once) down to single values like speed, vertical speed, altitude, heading, or pitch. Each node has
a **Face** field; the measurement runs once per computer per tick no matter how many nodes you
use.

Combine a scalar node with the [Threshold Trigger](nodes.md) node for alarms: altitude below 50
switches a speaker on, vertical speed above zero tells you you are climbing. The same data is
available on the CC:Tweaked side as the peripheral method `measure()`.

**Sable Companion** (optional) is supported: Create Aeronautics uses it for hidden physics
sublevels, and Computed reads contraption positions through it automatically.

## Jade and WTHIT

Hover a Computed computer with Jade or WTHIT installed and the tooltip shows the computer's ID —
handy when you wire up [networking](lua/endpoint-api.md#computednetsend) between machines.
