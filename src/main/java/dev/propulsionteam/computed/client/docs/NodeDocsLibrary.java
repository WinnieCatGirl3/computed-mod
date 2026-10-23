package dev.propulsionteam.computed.client.docs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * In-game node reference shown by the editor's Docs button. Content lives in code (not markdown
 * resources) so it is always on the classpath and stays in lockstep with the bundled library ids.
 */
public final class NodeDocsLibrary {
    private NodeDocsLibrary() {}

    private static final List<NodeDocsEntry> ENTRIES = build();

    public static List<NodeDocsEntry> all() {
        return ENTRIES;
    }

    public static List<NodeDocsEntry> search(String query) {
        if (query == null || query.isBlank()) {
            return ENTRIES;
        }
        String needle = query.strip().toLowerCase(java.util.Locale.ROOT);
        List<NodeDocsEntry> matches = new ArrayList<>();
        for (NodeDocsEntry entry : ENTRIES) {
            if (entry.title().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || entry.id().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || entry.summary().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || entry.folder().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    public static NodeDocsEntry byId(String id) {
        for (NodeDocsEntry entry : ENTRIES) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private static List<NodeDocsEntry> build() {
        List<NodeDocsEntry> list = new ArrayList<>();

        // ── Overview / welcome ──────────────────────────────────────────────
        list.add(new NodeDocsEntry(
                "docs:intro",
                "How to use these docs",
                "help",
                """
                Every entry explains one built-in node: what it does, its ports, and a short Lua \
                example of how it is typically wired. Use the search box to jump to a node by name \
                or id (for example "slider" or "computed:clock_widget").

                Nodes are only part of a graph after you drag them from the explorer. Examples \
                here show the Lua a custom node would use — bundled nodes already do this for you.
                """,
                """
                -- Custom node that re-exports a bundled widget:
                local node = computed.node(1, "example:my_clock", "My Clock")
                node:category("widgets")
                node:output("widget", "widget")
                node:on_run(function(ctx)
                  local w = ctx:endpoint("computed:widget"):call("clock", 0xffffffff, false)
                  w.line = 1
                  ctx:output("widget", w)
                end)
                return node
                """,
                new String[] {},
                new String[] {}));

        // ── Keyboard / mouse reference ─────────────────────────────────────
        list.add(new NodeDocsEntry(
                "docs:keys",
                "Keyboard Shortcuts",
                "help",
                """
                Mouse

                - Left-drag empty canvas: rubber-band selection (hold Shift to add to it)
                - Left-drag a node: move it; dragging a frame moves every node inside it
                - Middle- or right-drag: pan the canvas
                - Mouse wheel: zoom around the cursor
                - Double-click a node: zoom to it; double-click a comment: edit its text
                - Right-click empty canvas: add-node menu; right-click a wire: delete it
                - Drag from an output pin: create a wire; dropping it on empty canvas opens the quick-add picker
                - Click or drag the minimap (bottom right): jump around the graph

                Keys

                - Tab: quick-add picker (type to filter, Enter inserts, arrows choose)
                - Shift+A: node explorer at the cursor
                - F: zoom to fit the selection, or the whole graph
                - Ctrl+Z / Ctrl+Y: undo / redo (matched to your keyboard layout)
                - Ctrl+A: select everything
                - Ctrl+C / Ctrl+X / Ctrl+V: copy / cut / paste at the cursor
                - Ctrl+D: duplicate the selection
                - Ctrl+U: unlink the selected nodes
                - Delete: delete the selection
                - Escape: close a picker or stop editing a comment or frame title
                """,
                "",
                new String[] {},
                new String[] {}));

        // ── Flow ───────────────────────────────────────────────────────────
        list.add(entry(
                "computed:if_branch", "If Branch", "flow",
                "Routes two values through a single path based on a boolean condition. Connect the condition to 'cond'; true uses the 'true' input, false uses 'false'.",
                """
                -- Equivalent pure Lua:
                local result
                if condition ~= 0 then
                  result = trueValue
                else
                  result = falseValue
                end
                ctx:output("result", result)
                """,
                new String[] {"cond:boolean", "true:number", "false:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:switch", "Switch", "flow",
                "Routes a value by matching a key against several case labels. Unmatched keys fall through to the default output.",
                """
                local key = ctx:input("key")
                if key == "left" then
                  ctx:output("left", ctx:input("value"))
                elseif key == "right" then
                  ctx:output("right", ctx:input("value"))
                else
                  ctx:output("default", ctx:input("value"))
                end
                """,
                new String[] {"key:string", "value:number"},
                new String[] {"left:number", "right:number", "default:number"}));

        list.add(entry(
                "computed:event_sender", "Event Sender", "flow",
                "Broadcasts a named event to every Event Receiver in the graph. Use this to decouple producers from consumers without drawing wires between them.",
                """
                node:on_run(function(ctx)
                  ctx:emit("alarm", ctx:input("level"))
                end)
                """,
                new String[] {"level:number"},
                new String[] {}));

        list.add(entry(
                "computed:event_receiver", "Event Receiver", "flow",
                "Fires when a matching Event Sender emits. The event payload arrives as the first argument of the handler.",
                """
                node:on_event("alarm", function(ctx, level)
                  ctx:set_state("last", level)
                end)
                """,
                new String[] {},
                new String[] {"level:number"}));

        // ── Math ───────────────────────────────────────────────────────────
        list.add(entry(
                "computed:add", "Add", "math",
                "Adds two numbers. Works as a binary math template; other arithmetic nodes follow the same pattern with different operators.",
                "a + b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_add", "Add (template)", "math",
                "Binary math node: result = a + b. Identical to 'Add'; kept as a template id for custom math nodes.",
                "a + b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_subtract", "Subtract", "math",
                "result = a − b.",
                "a - b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_multiply", "Multiply", "math",
                "result = a × b.",
                "a * b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_divide", "Divide", "math",
                "result = a ÷ b. Division by zero yields 0 instead of an error.",
                "b == 0 and 0 or a / b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_mod", "Modulo", "math",
                "result = a mod b (Lua '%').",
                "a % b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_min", "Minimum", "math",
                "Returns the smaller of two numbers.",
                "math.min(a, b)",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_max", "Maximum", "math",
                "Returns the larger of two numbers.",
                "math.max(a, b)",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_pow", "Power", "math",
                "result = a raised to the power b.",
                "a ^ b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_atan2", "Atan2", "math",
                "Four-quadrant arctangent of (a, b) in radians.",
                "math.atan(a, b)",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:logic_and", "And", "math",
                "Both inputs non-zero → 1, otherwise 0.",
                "(a ~= 0 and b ~= 0) and 1 or 0",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:logic_or", "Or", "math",
                "At least one input non-zero → 1, otherwise 0.",
                "(a ~= 0 or b ~= 0) and 1 or 0",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:logic_xor", "Xor", "math",
                "Exactly one input non-zero → 1, otherwise 0.",
                "((a ~= 0) ~= (b ~= 0)) and 1 or 0",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:logic_nand", "Nand", "math",
                "Negated And: 0 only when both inputs are non-zero.",
                "(not (a ~= 0 and b ~= 0)) and 1 or 0",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:logic_nor", "Nor", "math",
                "Negated Or: 1 only when both inputs are zero.",
                "(not (a ~= 0 or b ~= 0)) and 1 or 0",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:logic_xnor", "Xnor", "math",
                "Equality of two booleans: 1 when both inputs match.",
                "((a ~= 0) == (b ~= 0)) and 1 or 0",
                new String[] {"a:number", "b:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:counter", "Counter", "math",
                "Increments an internal count each time it receives a trigger event. Reset clears the count back to zero.",
                """
                node:state("count", 0)
                node:on_event("input", function(ctx)
                  ctx:set_state("count", ctx:state("count") + 1)
                end)
                node:on_event("reset", function(ctx)
                  ctx:set_state("count", 0)
                end)
                """,
                new String[] {"reset:boolean"},
                new String[] {"count:number"}));

        list.add(entry(
                "computed:math_random", "Random", "math",
                "Pseudo-random number in [min, max] regenerated on each tick.",
                "math.random() * (max - min) + min",
                new String[] {"min:number", "max:number"},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:math_clamp", "Clamp", "math",
                "Constrains a number to the [min, max] range.",
                "math.max(min, math.min(max, value))",
                new String[] {"value:number", "min:number", "max:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_map", "Map Range", "math",
                "Remaps a value from one range into another, optionally clamped.",
                """
                local t = (value - inMin) / (inMax - inMin)
                ctx:output("result", outMin + t * (outMax - outMin))
                """,
                new String[] {"value:number", "inMin:number", "inMax:number", "outMin:number", "outMax:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_lerp", "Lerp", "math",
                "Linear interpolation between a and b by factor t (0..1).",
                "a + (b - a) * t",
                new String[] {"a:number", "b:number", "t:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_average", "Average", "math",
                "Rolling mean of recent samples of the input signal.",
                "sum / count",
                new String[] {"value:number"},
                new String[] {"average:number"}));

        list.add(entry(
                "computed:math_abs", "Absolute", "math",
                "Absolute value of the input.",
                "math.abs(value)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_sqrt", "Square Root", "math",
                "Square root, clamped at 0 for negative inputs.",
                "math.sqrt(math.max(0, value))",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_floor", "Floor", "math",
                "Rounds down to the nearest integer.",
                "math.floor(value)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_ceil", "Ceiling", "math",
                "Rounds up to the nearest integer.",
                "math.ceil(value)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_round", "Round", "math",
                "Rounds to the nearest integer (half up).",
                "math.floor(value + 0.5)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_negate", "Negate", "math",
                "Multiplies the input by −1.",
                "-value",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_log", "Natural Log", "math",
                "Natural logarithm; non-positive inputs yield 0.",
                "value > 0 and math.log(value) or 0",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_log10", "Log 10", "math",
                "Base-10 logarithm; non-positive inputs yield 0.",
                "value > 0 and math.log(value, 10) or 0",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_exp", "Exponent", "math",
                "e raised to the power of the input.",
                "math.exp(value)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_sign", "Sign", "math",
                "−1, 0, or +1 depending on the sign of the input.",
                "value > 0 and 1 or (value < 0 and -1 or 0)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_sin", "Sine", "math",
                "Sine of the angle in radians.",
                "math.sin(value)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_cos", "Cosine", "math",
                "Cosine of the angle in radians.",
                "math.cos(value)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:math_tan", "Tangent", "math",
                "Tangent of the angle in radians.",
                "math.tan(value)",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:quantize_redstone", "Quantize Redstone", "math",
                "Rounds a continuous value to a whole redstone level 0–15.",
                "math.max(0, math.min(15, math.floor(value + 0.5)))",
                new String[] {"value:number"},
                new String[] {"level:number"}));

        // ── Logic ──────────────────────────────────────────────────────────
        list.add(entry(
                "computed:logic_not", "Not", "logic",
                "Inverts a boolean: non-zero becomes 0, zero becomes 1.",
                "(value == 0) and 1 or 0",
                new String[] {"value:number"},
                new String[] {"result:number"}));

        list.add(entry(
                "computed:cmp_approx", "Approximately Equal", "logic",
                "True when |a − b| is within the given tolerance.",
                "math.abs(a - b) <= tolerance",
                new String[] {"a:number", "b:number", "tolerance:number"},
                new String[] {"equal:boolean"}));

        list.add(entry(
                "computed:cmp_eq", "Equal", "logic",
                "True when a == b.",
                "a == b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:boolean"}));

        list.add(entry(
                "computed:cmp_gt", "Greater Than", "logic",
                "True when a > b.",
                "a > b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:boolean"}));

        list.add(entry(
                "computed:cmp_lt", "Less Than", "logic",
                "True when a < b.",
                "a < b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:boolean"}));

        list.add(entry(
                "computed:cmp_ge", "Greater or Equal", "logic",
                "True when a ≥ b.",
                "a >= b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:boolean"}));

        list.add(entry(
                "computed:cmp_le", "Less or Equal", "logic",
                "True when a ≤ b.",
                "a <= b",
                new String[] {"a:number", "b:number"},
                new String[] {"result:boolean"}));

        list.add(entry(
                "computed:edge_rise", "Edge Rise", "logic",
                "Fires for one tick when the input transitions from zero to non-zero (rising edge).",
                """
                local current = ctx:input("value") ~= 0
                local previous = ctx:state("prev")
                ctx:set_state("prev", current)
                ctx:output("rising", current and not previous)
                """,
                new String[] {"value:number"},
                new String[] {"rising:boolean"}));

        list.add(entry(
                "computed:edge_fall", "Edge Fall", "logic",
                "Fires for one tick when the input transitions from non-zero to zero (falling edge).",
                """
                local current = ctx:input("value") ~= 0
                local previous = ctx:state("prev")
                ctx:set_state("prev", current)
                ctx:output("falling", previous and not current)
                """,
                new String[] {"value:number"},
                new String[] {"falling:boolean"}));

        list.add(entry(
                "computed:schmitt", "Schmitt Trigger", "logic",
                "Hysteresis comparator: switches high above the upper threshold and low below the lower threshold, filtering noise.",
                """
                local level = ctx:state("level")
                local value = ctx:input("value")
                if value >= ctx:field("high") then level = true end
                if value <= ctx:field("low") then level = false end
                ctx:set_state("level", level)
                ctx:output("level", level and 1 or 0)
                """,
                new String[] {"value:number"},
                new String[] {"level:number"}));

        list.add(entry(
                "computed:mux", "Multiplexer", "logic",
                "Selects one of several inputs based on a selector index (0-based).",
                """
                local options = { a, b, c, d }
                local index = math.floor(ctx:input("select")) + 1
                ctx:output("value", options[index] or 0)
                """,
                new String[] {"select:number", "a:number", "b:number", "c:number", "d:number"},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:sr_latch", "SR Latch", "logic",
                "Set-Reset flip-flop: set=1 latches output high, reset=1 latches low. Both 0 holds the previous state.",
                """
                local s = ctx:input("set") ~= 0
                local r = ctx:input("reset") ~= 0
                local q = ctx:state("q")
                if s then q = true end
                if r then q = false end
                ctx:set_state("q", q)
                ctx:output("q", q and 1 or 0)
                """,
                new String[] {"set:number", "reset:number"},
                new String[] {"q:boolean"}));

        list.add(entry(
                "computed:d_flipflop", "D Flip-Flop", "logic",
                "Samples the D input on a rising clock edge and holds it until the next edge.",
                """
                local d = ctx:input("d") ~= 0
                local clock = ctx:input("clock") ~= 0
                local previousClock = ctx:state("prev_clock")
                if clock and not previousClock then
                  ctx:set_state("q", d)
                end
                ctx:set_state("prev_clock", clock)
                ctx:output("q", ctx:state("q") and 1 or 0)
                """,
                new String[] {"d:boolean", "clock:boolean"},
                new String[] {"q:boolean"}));

        // ── State ──────────────────────────────────────────────────────────
        list.add(entry(
                "computed:pass_every_n", "Pass Every N", "state",
                "Forwards every Nth input value and drops the rest. Useful for rate limiting.",
                """
                node:state("tick", 0)
                node:on_event("input", function(ctx, value)
                  local n = math.max(1, math.floor(ctx:field("every")))
                  local tick = ctx:state("tick") + 1
                  ctx:set_state("tick", tick)
                  if tick % n == 0 then
                    ctx:output("value", value)
                  end
                end)
                """,
                new String[] {"value:number"},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:delay", "Delay", "state",
                "Holds each input value for a fixed number of ticks before emitting it.",
                """
                node:on_event("input", function(ctx, value)
                  ctx:set_state("pending", value)
                  ctx:set_state("wait", math.floor(ctx:field("ticks")))
                end)
                node:on_run(function(ctx)
                  local wait = ctx:state("wait")
                  if wait > 0 then
                    ctx:set_state("wait", wait - 1)
                    if wait - 1 == 0 then
                      ctx:output("value", ctx:state("pending"))
                    end
                  end
                end)
                """,
                new String[] {"value:number"},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:sample_hold", "Sample & Hold", "state",
                "Captures the input when triggered and holds that sample until the next trigger.",
                """
                node:on_event("input", function(ctx, value)
                  if ctx:input("trigger") ~= 0 then
                    ctx:set_state("held", value)
                  end
                  ctx:output("value", ctx:state("held"))
                end)
                """,
                new String[] {"value:number", "trigger:boolean"},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:world_time", "World Time", "state",
                "Emits the current day time (0–24000) each tick.",
                "ctx:output(\"time\", ctx:endpoint(\"computed:world\"):call(\"time\"))",
                new String[] {},
                new String[] {"time:number"}));

        // ── World ──────────────────────────────────────────────────────────
        list.add(entry(
                "computed:block_location", "Block Location", "world",
                "Reads the computer's world position as x, y, z outputs.",
                """
                local x, y, z = ctx:endpoint("computed:block"):call("location")
                ctx:output("x", x)
                """,
                new String[] {},
                new String[] {"x:number", "y:number", "z:number"}));

        list.add(entry(
                "computed:block_rotation", "Block Rotation", "world",
                "Reads the computer's facing direction as a yaw angle or cardinal name.",
                "local yaw = ctx:endpoint(\"computed:block\"):call(\"rotation\")",
                new String[] {},
                new String[] {"yaw:number", "direction:string"}));

        list.add(entry(
                "computed:block_presence", "Block Presence", "world",
                "Reports whether a block is present at a face of the computer as text "
                        + "('present' or 'absent') plus the block's namespaced id.",
                """
                local present = ctx:endpoint("computed:world"):call("block_present", ctx:field("face"))
                local block = ctx:endpoint("computed:world"):call("block_id", ctx:field("face"))
                ctx:output("present", present and "present" or "absent")
                ctx:output("block", block)
                """,
                new String[] {},
                new String[] {"present:string", "block:string"}));

        list.add(entry(
                "computed:display_present", "Display Present", "world",
                "Reports whether a display answers the computer — a monitor multiblock at the face "
                        + "or a paired portable monitor someone carries: attached plus "
                        + "'present' / 'absent' as text.",
                """
                local attached = ctx:endpoint("computed:monitor", ctx:field("face")):call("has_display")
                ctx:output("attached", attached)
                ctx:output("present", attached and "present" or "absent")
                """,
                new String[] {},
                new String[] {"attached:boolean", "present:string"}));

        list.add(entry(
                "computed:redstone_input", "Redstone Input", "world",
                "Reads the redstone signal strength arriving at the computer from a side.",
                "local strength = ctx:endpoint(\"computed:redstone\"):call(\"input\", \"front\")",
                new String[] {"side:string"},
                new String[] {"strength:number"}));

        list.add(entry(
                "computed:comparator_read", "Comparator Read", "world",
                "Reads the analog signal strength a comparator is providing to the computer.",
                "local strength = ctx:endpoint(\"computed:redstone\"):call(\"comparator\")",
                new String[] {},
                new String[] {"strength:number"}));

        list.add(entry(
                "computed:redstone_emitter", "Redstone Emitter", "world",
                "Writes a redstone signal strength (0–15) out of a chosen side of the computer.",
                """
                ctx:endpoint("computed:redstone"):call("emit", "front", level)
                ctx:output("level", level)
                """,
                new String[] {"level:number", "side:string"},
                new String[] {"level:number"}));

        // ── Sources ────────────────────────────────────────────────────────
        list.add(entry(
                "computed:constant", "Constant", "sources",
                "Emits a fixed value set in the node's fields. Handy as a named parameter for a graph section.",
                "ctx:output(\"value\", ctx:field(\"value\"))",
                new String[] {},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:tick", "Tick", "sources",
                "Fires once per game tick. Use this as a heartbeat to drive animations or polling loops.",
                "ctx:output(\"tick\", ctx:tick())",
                new String[] {},
                new String[] {"tick:number"}));

        list.add(entry(
                "computed:pulse", "Pulse", "sources",
                "Emits a short pulse on a configurable interval. Good for periodic triggers without a full tick stream.",
                """
                local period = math.max(1, math.floor(ctx:field("period")))
                local tick = ctx:state("tick") + 1
                ctx:set_state("tick", tick)
                ctx:output("pulse", tick % period == 0 and 1 or 0)
                """,
                new String[] {},
                new String[] {"pulse:boolean"}));

        list.add(entry(
                "computed:oscillator", "Oscillator", "sources",
                "Produces a continuous sine wave between min and max at the given frequency.",
                """
                local t = ctx:tick()
                local wave = math.sin(t * ctx:field("frequency") * math.pi * 2)
                ctx:output("value", ctx:field("min") + (wave * 0.5 + 0.5) * (ctx:field("max") - ctx:field("min")))
                """,
                new String[] {},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:color_source", "Color Source", "sources",
                "Emits a fixed ARGB color value for use with widgets and RGB peripherals.",
                "ctx:output(\"color\", ctx:field(\"color\"))",
                new String[] {},
                new String[] {"color:number"}));

        // ── Text ───────────────────────────────────────────────────────────
        list.add(entry(
                "computed:text_source", "Text Source", "text",
                "Emits a fixed string set in the node's fields. Connect it to any string input (labels, terminal rows, text blocks).",
                "ctx:output(\"text\", ctx:field(\"text\"))",
                new String[] {},
                new String[] {"text:string"}));

        list.add(entry(
                "computed:concatenate_strings", "Concatenate", "text",
                "Joins two strings into one.",
                "a .. b",
                new String[] {"a:string", "b:string"},
                new String[] {"result:string"}));

        list.add(entry(
                "computed:number_to_text", "Number to Text", "text",
                "Converts a number to its string form, optionally with a fixed number of decimal places.",
                "string.format(format, value)",
                new String[] {"value:number", "format:string"},
                new String[] {"text:string"}));

        // ── IO ─────────────────────────────────────────────────────────────
        list.add(entry(
                "computed:command", "Run Command", "io",
                "Runs a server command through the computer host. Restricted by the sandbox — creative or op may be required depending on config.",
                "ctx:endpoint(\"computed:host\"):call(\"run\", command)",
                new String[] {"command:string"},
                new String[] {}));

        list.add(entry(
                "computed:bool_to_level", "Boolean to Level", "io",
                "Converts a boolean (true/false) into a redstone level (15/0).",
                "ctx:output(\"level\", value ~= 0 and 15 or 0)",
                new String[] {"value:boolean"},
                new String[] {"level:number"}));

        list.add(entry(
                "computed:level_to_bool", "Level to Boolean", "io",
                "Converts a redstone level into a boolean: non-zero → true.",
                "ctx:output(\"value\", level > 0)",
                new String[] {"level:number"},
                new String[] {"value:boolean"}));

        list.add(entry(
                "computed:display", "Display", "io",
                "Sends a text line to the computer's built-in terminal output for debugging.",
                "ctx:endpoint(\"computed:host\"):call(\"display\", text)",
                new String[] {"text:string"},
                new String[] {}));

        list.add(entry(
                "computed:rgb_preview", "RGB Preview", "io",
                "Previews an ARGB color on the node itself in the editor canvas (visual only).",
                "ctx:output(\"color\", color)",
                new String[] {"color:number"},
                new String[] {"color:number"}));

        // ── Net ────────────────────────────────────────────────────────────
        list.add(entry(
                "computed:net_send", "Net Send", "net",
                "Sends a message to a named channel on the local network.",
                "ctx:endpoint(\"computed:net\"):call(\"send\", channel, message)",
                new String[] {"channel:string", "message:string"},
                new String[] {}));

        list.add(entry(
                "computed:net_broadcast", "Net Broadcast", "net",
                "Broadcasts a message to every computer on the network.",
                "ctx:endpoint(\"computed:net\"):call(\"broadcast\", message)",
                new String[] {"message:string"},
                new String[] {}));

        list.add(entry(
                "computed:net_receive", "Net Receive", "net",
                "Receives the next message from a named channel (blocking until one arrives).",
                """
                node:on_event("input", function(ctx)
                  local channel, message = ctx:endpoint("computed:net"):call("receive", ctx:field("channel"))
                  ctx:output("message", message)
                end)
                """,
                new String[] {},
                new String[] {"channel:string", "message:string"}));

        // ── Speaker ────────────────────────────────────────────────────────
        list.add(entry(
                "computed:speaker_play_note", "Play Note", "speaker",
                "Plays a musical note through the connected speaker for a given duration.",
                "ctx:endpoint(\"computed:speaker\"):call(\"play_note\", note, volume, duration)",
                new String[] {"note:string", "volume:number", "duration:number"},
                new String[] {}));

        list.add(entry(
                "computed:speaker_play_sound", "Play Sound", "speaker",
                "Plays a Minecraft sound event through the speaker.",
                "ctx:endpoint(\"computed:speaker\"):call(\"play_sound\", soundId, volume, pitch)",
                new String[] {"sound:string", "volume:number", "pitch:number"},
                new String[] {}));

        list.add(entry(
                "computed:speaker_play_wave", "Play Wave", "speaker",
                "Plays a raw waveform tone with optional attack/decay envelope.",
                "ctx:endpoint(\"computed:speaker\"):call(\"play_wave\", waveId, frequency, volume, attack, decay)",
                new String[] {"wave:string", "volume:number", "attack:number", "decay:number"},
                new String[] {}));

        list.add(entry(
                "computed:speaker_play_sweep", "Play Sweep", "speaker",
                "Glides between two frequencies over a duration (risers, alarms, motors).",
                "ctx:endpoint(\"computed:speaker\"):call(\"play_sweep\", waveId, fromHz, toHz, duration, volume)",
                new String[] {"trigger:boolean", "event:event"},
                new String[] {}));

        list.add(entry(
                "computed:speaker_play_disc", "Play Disc", "speaker",
                "Plays the inserted music disc, optionally looping it.",
                "ctx:endpoint(\"computed:speaker\"):call(\"play\")",
                new String[] {"trigger:boolean", "event:event"},
                new String[] {}));

        list.add(entry(
                "computed:speaker_play_sequence", "Play Sequence", "speaker",
                "Plays timed notes through the speaker without a disc: fill the note inputs "
                        + "with instrument:note:seconds steps (top to bottom, empty ones are "
                        + "skipped); each seconds value is the gap until the next note.",
                """
                local steps = { "harp:12:0.5", "bell:18:0.5" }
                ctx:endpoint("computed:speaker"):call("play_sequence", table.concat(steps, " "))
                """,
                new String[] {
                    "trigger:boolean", "event:event",
                    "note_1:string", "note_2:string", "note_3:string", "note_4:string",
                    "note_5:string", "note_6:string", "note_7:string", "note_8:string",
                    "note_9:string", "note_10:string", "note_11:string", "note_12:string",
                    "note_13:string", "note_14:string", "note_15:string", "note_16:string"
                },
                new String[] {}));

        list.add(entry(
                "computed:speaker_stop", "Stop Speaker", "speaker",
                "Stops all current speaker playback.",
                "ctx:endpoint(\"computed:speaker\"):call(\"stop\")",
                new String[] {},
                new String[] {}));

        list.add(entry(
                "computed:speaker_eject", "Eject Disc", "speaker",
                "Ejects the currently loaded music disc from the speaker.",
                "ctx:endpoint(\"computed:speaker\"):call(\"eject\")",
                new String[] {},
                new String[] {}));

        list.add(entry(
                "computed:speaker_status", "Speaker Status", "speaker",
                "Reports whether the speaker is currently playing something.",
                "local playing = ctx:endpoint(\"computed:speaker\"):call(\"status\")",
                new String[] {},
                new String[] {"playing:boolean"}));

        // ── Widgets ────────────────────────────────────────────────────────
        list.add(entry(
                "computed:peripheral", "Monitor Peripheral", "widgets",
                "The monitor hub. Up to 6 (or more, depending on screen size) widget inputs; whatever you connect is shown on the monitor.",
                """
                local w = ctx:endpoint("computed:widget"):call("clock", 0xffffffff, false)
                w.line = 1
                ctx:endpoint("computed:monitor"):call("show", { w })
                """,
                new String[] {"widget_1:widget", "widget_2:widget", "widget_3:widget", "widget_4:widget", "widget_5:widget", "widget_6:widget"},
                new String[] {}));

        list.add(entry(
                "computed:glasses_hud", "Glasses HUD", "widgets",
                "The glasses hub. Widget inputs are painted on the HUD of every player whose link card points at this computer, with their own x/y and size like on a monitor - text, clocks, progress bars and friends without a screen.",
                """
                local w = ctx:endpoint("computed:widget"):call("clock", 0xffffffff, false)
                w.line = 1
                ctx:endpoint("computed:glasses"):call("show", { w })
                """,
                new String[] {"widget_1:widget", "widget_2:widget", "widget_3:widget", "widget_4:widget", "widget_5:widget", "widget_6:widget"},
                new String[] {}));

        list.add(entry(
                "computed:text_widget", "Text Widget", "widgets",
                "A single line of left-aligned text on the monitor.",
                "local w = ctx:endpoint(\"computed:widget\"):call(\"text\", \"Hello\", 0xffffffff)",
                new String[] {"text:string", "color:number"},
                new String[] {"widget:widget"}));

        list.add(entry(
                "computed:number_widget", "Number Widget", "widgets",
                "A single right-aligned number on the monitor. Integral values (colours, ticks, time) render exactly; fractional values use the format field (Lua string.format patterns like %.4g). An explicitly chosen format always wins, even for integral values.",
                "local w = ctx:endpoint(\"computed:widget\"):call(\"number\", 3.14159, \"3.1416\", 0xffffffff)",
                new String[] {"value:number", "color:number"},
                new String[] {"widget:widget"}));

        list.add(entry(
                "computed:clock_widget", "Clock Widget", "widgets",
                "Shows the current world time. Optionally includes seconds.",
                "local w = ctx:endpoint(\"computed:widget\"):call(\"clock\", 0xffffffff, true)",
                new String[] {"color:number", "show_seconds:boolean"},
                new String[] {"widget:widget"}));

        list.add(entry(
                "computed:button_widget", "Button Widget", "widgets",
                "A clickable button. When pressed, the node emits a one-tick 'clicked' pulse.",
                """
                local w = ctx:endpoint("computed:widget"):call("button", "Run", 0xff3366ff)
                w.line = 1
                ctx:output("widget", w)
                ctx:output("clicked", ctx:state("pending_click"))
                ctx:set_state("pending_click", false)
                """,
                new String[] {"label:string", "color:number"},
                new String[] {"widget:widget", "clicked:boolean"}));

        list.add(entry(
                "computed:checkbox_widget", "Checkbox Widget", "widgets",
                "A toggleable checkbox. Clicking flips the internal checked state; the node re-emits the widget with the new value.",
                """
                local w = ctx:endpoint("computed:widget"):call("checkbox", "Enabled", 0xffffffff, ctx:state("checked"))
                ctx:output("widget", w)
                ctx:output("checked", ctx:state("checked"))
                """,
                new String[] {"label:string", "color:number"},
                new String[] {"widget:widget", "checked:boolean"}));

        list.add(entry(
                "computed:text_input_widget", "Text Input Widget", "widgets",
                "An editable text field. Clicking opens a client dialog; the submitted string arrives as an 'input' event and is also exposed on the value output.",
                """
                local w = ctx:endpoint("computed:widget"):call("text_input", "Name…", 0xffffffff, ctx:state("value"))
                ctx:output("widget", w)
                ctx:output("value", ctx:state("value"))
                """,
                new String[] {"placeholder:string", "color:number"},
                new String[] {"widget:widget", "value:string", "submitted:boolean"}));

        list.add(entry(
                "computed:number_input_widget", "Number Input Widget", "widgets",
                "An editable number field (same dialog as the text input, on both monitor kinds). Non-numeric submissions are ignored and keep the last value; only valid numbers raise 'submitted'. Integral values render exactly.",
                """
                node:on_event("input", function(ctx, text)
                  local number = tonumber(text)
                  if number ~= nil then
                    ctx:set_state("value", number)
                    ctx:set_state("pending_submit", true)
                  end
                end)
                """,
                new String[] {"placeholder:string", "color:number"},
                new String[] {"widget:widget", "value:number", "submitted:boolean"}));

        list.add(entry(
                "computed:slider_widget", "Slider Widget", "widgets",
                "A horizontal slider between min and max. Dragging updates the value; the node re-renders immediately.",
                """
                local w = ctx:endpoint("computed:widget"):call("slider", ctx:state("value"), 0, 100, 0xffffffff, 1)
                ctx:output("widget", w)
                ctx:output("value", ctx:state("value"))
                """,
                new String[] {"minimum:number", "maximum:number", "color:number"},
                new String[] {"widget:widget", "value:number"}));

        list.add(entry(
                "computed:progress_bar_widget", "Progress Bar Widget", "widgets",
                "A filled bar representing value / maximum. Optionally segmented for a chunky look.",
                "local w = ctx:endpoint(\"computed:widget\"):call(\"progress\", 42, 100, 0xff00ff88, 0)",
                new String[] {"value:number", "maximum:number", "color:number"},
                new String[] {"widget:widget"}));

        list.add(entry(
                "computed:terminal_widget", "Terminal Widget", "widgets",
                "A fixed-pitch character grid. Accepts plain strings or styled row tables from Terminal Line.",
                """
                local line = ctx:endpoint("computed:widget"):call("line", "OK", 0xff00ff00, 0xff000000)
                local w = ctx:endpoint("computed:widget"):call("terminal", { line }, 0xffffffff, 0xff000000, 1, -1, -1)
                """,
                new String[] {"text:string", "fg:number", "bg:number"},
                new String[] {"widget:widget"}));

        list.add(entry(
                "computed:terminal_line", "Terminal Line", "widgets",
                "Builds a styled terminal row (text + fg + bg colors) for use inside a Terminal Widget.",
                "local row = ctx:endpoint(\"computed:widget\"):call(\"line\", \"Hello\", 0xffffffff, 0xff000000)",
                new String[] {"text:string", "fg:number", "bg:number"},
                new String[] {"row:table"}));

        list.add(entry(
                "computed:panel_widget", "Panel Widget", "widgets",
                "A solid colored rectangle used as a background or divider. Optional border color.",
                "local w = ctx:endpoint(\"computed:widget\"):call(\"panel\", 0xff202040, 0xff404080)",
                new String[] {"color:number", "border:number"},
                new String[] {"widget:widget"}));

        list.add(entry(
                "computed:text_block_widget", "Text Block Widget", "widgets",
                "A multi-line text block with optional background. Supports left/center/right alignment.",
                "local w = ctx:endpoint(\"computed:widget\"):call(\"text_block\", \"Line 1\\nLine 2\", 0xffffffff, 0xff000000, \"left\")",
                new String[] {"text:string", "fg:number", "bg:number"},
                new String[] {"widget:widget"}));

        list.add(entry(
                "computed:table_widget", "Table Widget", "widgets",
                "A data table with a header row and cell rows. Columns are comma-separated; cells are strings.",
                """
                local w = ctx:endpoint("computed:widget"):call("table", "Name,Value", { {"a", "1"}, {"b", "2"} }, 0xffffffff, 0xff202020, 0xffffffff, 0xff101010)
                """,
                new String[] {"columns:string", "rows:table"},
                new String[] {"widget:widget"}));

        // ── Integrations ───────────────────────────────────────────────────
        list.add(entry(
                "computed:cc_input", "CC:Tweaked Input", "integration",
                "Reads a value from a ComputerCraft peripheral via the CC integration bridge. Requires CC:Tweaked.",
                "local value = ctx:endpoint(\"computercraft:peripheral\"):call(\"call\", name, method)",
                new String[] {"name:string", "method:string"},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:cc_output", "CC:Tweaked Output", "integration",
                "Writes a value to a ComputerCraft peripheral. Requires CC:Tweaked.",
                "ctx:endpoint(\"computercraft:peripheral\"):call(\"call\", name, method, value)",
                new String[] {"name:string", "method:string", "value:number"},
                new String[] {}));

        list.add(entry(
                "computed:create_kinetic", "Create Kinetic", "integration",
                "Reads speed/stress/capacity from a Create kinetic network. Requires Create.",
                """
                local speed = ctx:endpoint("create:kinetic"):call("speed")
                local stress = ctx:endpoint("create:kinetic"):call("stress")
                """,
                new String[] {},
                new String[] {"speed:number", "stress:number", "capacity:number"}));

        list.add(entry(
                "computed:create_link_receiver", "Create Link Receiver", "integration",
                "Receives a value from a Create redstone link pair. Requires Create.",
                "local value = ctx:endpoint(\"create:redstone_link\"):call(\"receive\", frequency)",
                new String[] {"frequency:string"},
                new String[] {"value:number"}));

        list.add(entry(
                "computed:create_link_sender", "Create Link Sender", "integration",
                "Transmits a value over a Create redstone link pair. Requires Create.",
                "ctx:endpoint(\"create:redstone_link\"):call(\"transmit\", frequency, value)",
                new String[] {"frequency:string", "value:number"},
                new String[] {}));

        list.add(entry(
                "computed:aeronautics_telemetry", "Aeronautics Telemetry", "integration",
                "Aggregated flight telemetry from a Create Aeronautics assembly. Requires the Aeronautics mod.",
                """
                local present, pitch, roll, heading = ctx:endpoint("aeronautics:telemetry"):call("read")
                ctx:output("present", present)
                """,
                new String[] {},
                new String[] {"present:boolean", "pitch:number", "roll:number", "heading:number"}));

        list.add(entry(
                "computed:aeronautics_position", "Aeronautics Position", "integration",
                "World position of the linked Aeronautics assembly. Requires the Aeronautics mod.",
                "local x, y, z = ctx:endpoint(\"aeronautics:position\"):call(\"read\")",
                new String[] {},
                new String[] {"x:number", "y:number", "z:number"}));

        list.add(entry(
                "computed:aeronautics_velocity", "Aeronautics Velocity", "integration",
                "Linear velocity vector of the linked assembly. Requires the Aeronautics mod.",
                "local vx, vy, vz = ctx:endpoint(\"aeronautics:velocity\"):call(\"read\")",
                new String[] {},
                new String[] {"vx:number", "vy:number", "vz:number"}));

        list.add(entry(
                "computed:aeronautics_angular_velocity", "Aeronautics Angular Velocity", "integration",
                "Angular velocity of the linked assembly in radians/tick. Requires the Aeronautics mod.",
                "local wx, wy, wz = ctx:endpoint(\"aeronautics:angular_velocity\"):call(\"read\")",
                new String[] {},
                new String[] {"wx:number", "wy:number", "wz:number"}));

        list.add(entry(
                "computed:aeronautics_forward", "Aeronautics Forward", "integration",
                "Forward-facing unit vector of the assembly. Requires the Aeronautics mod.",
                "local fx, fy, fz = ctx:endpoint(\"aeronautics:forward\"):call(\"read\")",
                new String[] {},
                new String[] {"fx:number", "fy:number", "fz:number"}));

        list.add(entry(
                "computed:aeronautics_right", "Aeronautics Right", "integration",
                "Right-facing unit vector of the assembly. Requires the Aeronautics mod.",
                "local rx, ry, rz = ctx:endpoint(\"aeronautics:right\"):call(\"read\")",
                new String[] {},
                new String[] {"rx:number", "ry:number", "rz:number"}));

        list.add(entry(
                "computed:aeronautics_pitch", "Aeronautics Pitch", "integration",
                "Pitch angle of the assembly in degrees. Requires the Aeronautics mod.",
                "local pitch = ctx:endpoint(\"aeronautics:pitch\"):call(\"read\")",
                new String[] {},
                new String[] {"pitch:number"}));

        list.add(entry(
                "computed:aeronautics_roll", "Aeronautics Roll", "integration",
                "Roll angle of the assembly in degrees. Requires the Aeronautics mod.",
                "local roll = ctx:endpoint(\"aeronautics:roll\"):call(\"read\")",
                new String[] {},
                new String[] {"roll:number"}));

        list.add(entry(
                "computed:aeronautics_heading", "Aeronautics Heading", "integration",
                "Compass heading of the assembly in degrees. Requires the Aeronautics mod.",
                "local heading = ctx:endpoint(\"aeronautics:heading\"):call(\"read\")",
                new String[] {},
                new String[] {"heading:number"}));

        list.add(entry(
                "computed:aeronautics_distance", "Aeronautics Distance", "integration",
                "Distance from the assembly to a target point. Requires the Aeronautics mod.",
                "local distance = ctx:endpoint(\"aeronautics:distance\"):call(\"read\", x, y, z)",
                new String[] {"x:number", "y:number", "z:number"},
                new String[] {"distance:number"}));

        list.add(entry(
                "computed:aeronautics_speed", "Aeronautics Speed", "integration",
                "Scalar speed of the assembly (blocks/tick). Requires the Aeronautics mod.",
                "local speed = ctx:endpoint(\"aeronautics:speed\"):call(\"read\")",
                new String[] {},
                new String[] {"speed:number"}));

        list.add(entry(
                "computed:aeronautics_velocity_sensor", "Aeronautics Velocity Sensor", "integration",
                "World-space velocity of a specific point on the assembly. Requires the Aeronautics mod.",
                "local vx, vy, vz = ctx:endpoint(\"aeronautics:velocity_sensor\"):call(\"read\", lx, ly, lz)",
                new String[] {"lx:number", "ly:number", "lz:number"},
                new String[] {"vx:number", "vy:number", "vz:number"}));

        list.add(entry(
                "computed:aeronautics_present", "Aeronautics Present", "integration",
                "True when an Aeronautics assembly is linked to this computer. Requires the Aeronautics mod.",
                "local present = ctx:endpoint(\"aeronautics:present\"):call(\"read\")",
                new String[] {},
                new String[] {"present:boolean"}));

        list.add(entry(
                "computed:aeronautics_name", "Aeronautics Name", "integration",
                "Display name of the linked assembly. Requires the Aeronautics mod.",
                "local name = ctx:endpoint(\"aeronautics:name\"):call(\"read\")",
                new String[] {},
                new String[] {"name:string"}));

        list.add(entry(
                "computed:aeronautics_source", "Aeronautics Source", "integration",
                "Identifier of the linked assembly's source entity. Requires the Aeronautics mod.",
                "local source = ctx:endpoint(\"aeronautics:source\"):call(\"read\")",
                new String[] {},
                new String[] {"source:string"}));

        list.add(entry(
                "computed:aeronautics_vertical_speed", "Aeronautics Vertical Speed", "integration",
                "Climb rate of the assembly in blocks/second (positive = up). Requires the Aeronautics mod.",
                "local vertical_speed = ctx:endpoint(\"aeronautics:telemetry\"):call(\"measure\").vy",
                new String[] {},
                new String[] {"vertical_speed:number"}));

        list.add(entry(
                "computed:aeronautics_altitude", "Aeronautics Altitude", "integration",
                "Altitude (Y coordinate) of the assembly in blocks. Requires the Aeronautics mod.",
                "local altitude = ctx:endpoint(\"aeronautics:telemetry\"):call(\"measure\").y",
                new String[] {},
                new String[] {"altitude:number"}));

        list.add(entry(
                "computed:threshold_trigger", "Threshold Trigger", "logic",
                "Latches true when a value crosses a threshold; hysteresis prevents chatter. "
                        + "Typical use: altitude below X triggers an alarm.",
                "local triggered = value >= ctx:field(\"threshold\")",
                new String[] {"value:number"},
                new String[] {"triggered:boolean"}));

        list.sort(Comparator
                .comparing(NodeDocsEntry::folder, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NodeDocsEntry::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(list);
    }

    private static NodeDocsEntry entry(
            String id, String title, String category, String summary, String example,
            String[] inputs, String[] outputs) {
        return new NodeDocsEntry(id, title, category, summary, example, inputs, outputs);
    }
}
