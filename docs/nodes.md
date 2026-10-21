# Node Catalog

Every bundled node in one place. The same list lives in-game under the **Docs** button — there you
can also import an example graph for each node. IDs look like `computed:add`; you rarely need them
because you place nodes by title.

## Math

| Node | Does |
|---|---|
| `computed:add` | Adds two numbers |
| `computed:math_add` / `math_subtract` / `math_multiply` / `math_divide` / `math_mod` | Arithmetic (divide/mod guard against 0) |
| `computed:math_min` / `math_max` / `math_pow` / `math_atan2` | Min, max, power, atan2 |
| `computed:math_abs` / `math_sqrt` / `math_floor` / `math_ceil` / `math_round` / `math_negate` | Rounding and sign |
| `computed:math_log` / `math_log10` / `math_exp` / `math_sign` | Log, exp, sign |
| `computed:math_sin` / `math_cos` / `math_tan` | Trigonometry |
| `computed:math_random` | Random number |
| `computed:math_clamp` | Clamp between min and max |
| `computed:math_map` | Rescale from one range to another |
| `computed:math_lerp` | Linear interpolation |
| `computed:oscillator` | Sine oscillator (period, amplitude) |

## Logic

| Node | Does |
|---|---|
| `computed:logic_and` / `or` / `xor` / `nand` / `nor` / `xnor` | Two-input gates |
| `computed:logic_not` | Negation |
| `computed:cmp_eq` / `cmp_gt` / `cmp_lt` / `cmp_ge` / `cmp_le` | Comparisons |
| `computed:cmp_approx` | Approximately equal |
| `computed:edge_rise` / `edge_fall` | Rising / falling edge |
| `computed:schmitt` | Schmitt trigger (hysteresis) |
| `computed:mux` | Multiplexer (picks a or b) |

## State

| Node | Does |
|---|---|
| `computed:counter` | Counts up by a step |
| `computed:delay` | Delays a value by N ticks |
| `computed:pass_every_n` | Lets every n-th pulse through |
| `computed:sample_hold` | Samples a value on a clock |
| `computed:math_average` | Running average |
| `computed:sr_latch` | Set/reset latch |
| `computed:d_flipflop` | D flip-flop |

## Flow

| Node | Does |
|---|---|
| `computed:if_branch` | Conditional routing |
| `computed:switch` | Selects a or b |
| `computed:tick` | Fires every tick |
| `computed:pulse` | Single pulse |
| `computed:event_sender` / `event_receiver` | Named wireless events inside one graph |

## World and redstone

| Node | Does |
|---|---|
| `computed:world_time` | Time of day |
| `computed:block_location` | The computer's x/y/z |
| `computed:block_rotation` | Yaw, pitch, roll |
| `computed:block_presence` | Is a block on face X? |
| `computed:redstone_input` | Read redstone level |
| `computed:comparator_read` | Read comparator |
| `computed:redstone_emitter` | Output redstone |
| `computed:bool_to_level` / `level_to_bool` | Convert redstone levels |
| `computed:quantize_redstone` | Round to 0–15 |
| `computed:display` | Display sink |
| `computed:rgb_preview` | RGB LED preview |
| `computed:command` | Run a command (disabled — see [endpoint docs](lua/endpoint-api.md#computedcommandrun)) |

## Text and sources

| Node | Does |
|---|---|
| `computed:text_source` | Constant text |
| `computed:concatenate_strings` | Join strings |
| `computed:number_to_text` | Format a number as text |
| `computed:constant` | Constant number |
| `computed:color_source` | Constant color |

## Networking

| Node | Does |
|---|---|
| `computed:net_send` | Send a message to a computer ID |
| `computed:net_broadcast` | Send to every computer |
| `computed:net_receive` | Receive messages (sender, message) |

## Widgets and monitors

See [Monitors](monitors.md) for layout rules.

| Node | Does |
|---|---|
| `computed:text_widget` | One line of text |
| `computed:number_widget` | Right-aligned number |
| `computed:number_input_widget` | Editable number |
| `computed:text_input_widget` | Editable text |
| `computed:text_block_widget` | Multi-line text |
| `computed:button_widget` | Clickable button |
| `computed:checkbox_widget` | Toggle |
| `computed:slider_widget` | Slider |
| `computed:progress_bar_widget` | Progress bar |
| `computed:clock_widget` | Clock |
| `computed:table_widget` | Table |
| `computed:panel_widget` | Background panel |
| `computed:terminal_widget` | Terminal area |
| `computed:terminal_line` | One styled line |
| `computed:peripheral` | **Monitor** — up to 16 widgets to a screen |
| `computed:glasses_hud` | **Glasses HUD** — up to 16 widgets on every linked player's glasses |

## Speaker

See [Speaker](speaker.md).

`computed:speaker_play_note`, `speaker_play_sound`, `speaker_play_wave`, `speaker_play_disc`,
`speaker_stop`, `speaker_eject`, `speaker_status`.

## Integrations

See [Integrations](integrations.md) for setup notes.

| Node | Does |
|---|---|
| `computed:cc_input` / `cc_output` | CC:Tweaked channel values |
| `computed:create_kinetic` | Read a Create block's speed/stress/capacity |
| `computed:create_link_sender` / `create_link_receiver` | Create redstone links |
| `computed:aeronautics_telemetry` | All 22 telemetry fields at once |
| `computed:aeronautics_present` / `_name` / `_source` | Contraption presence, name, source |
| `computed:aeronautics_position` / `_velocity` / `_angular_velocity` | Position and motion vectors |
| `computed:aeronautics_speed` / `_distance` / `_heading` / `_pitch` / `_roll` | Scalars and orientation |
| `computed:aeronautics_forward` / `_right` | Heading and right frames |
| `computed:aeronautics_velocity_sensor` | Speed sensor with announcements |
