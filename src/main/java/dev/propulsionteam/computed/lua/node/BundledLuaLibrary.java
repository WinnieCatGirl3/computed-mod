package dev.propulsionteam.computed.lua.node;

import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BundledLuaLibrary {
    private static final List<Entry> ENTRIES = List.of(
            new Entry("computed:add", "computed/lua/nodes/math/add.lua"),
            new Entry("computed:counter", "computed/lua/nodes/state/counter.lua"),
            new Entry("computed:world_time", "computed/lua/nodes/world/time.lua"),
            new Entry("computed:block_location", "computed/lua/nodes/world/location.lua"),
            new Entry("computed:block_rotation", "computed/lua/nodes/world/rotation.lua"),
            new Entry("computed:block_presence", "computed/lua/nodes/world/presence.lua"),
            new Entry("computed:redstone_input", "computed/lua/nodes/world/redstone_input.lua"),
            new Entry("computed:comparator_read", "computed/lua/nodes/world/comparator.lua"),
            new Entry("computed:redstone_emitter", "computed/lua/nodes/world/redstone_output.lua"),
            new Entry("computed:net_send", "computed/lua/nodes/net/send.lua"),
            new Entry("computed:net_broadcast", "computed/lua/nodes/net/broadcast.lua"),
            new Entry("computed:net_receive", "computed/lua/nodes/net/receive.lua"),
            new Entry("computed:command", "computed/lua/nodes/io/command.lua"),
            new Entry("computed:event_sender", "computed/lua/nodes/flow/event_sender.lua"),
            new Entry("computed:event_receiver", "computed/lua/nodes/flow/event_receiver.lua"),
            new Entry("computed:text_widget", "computed/lua/nodes/widgets/text.lua"),
            new Entry("computed:number_widget", "computed/lua/nodes/widgets/number.lua"),
            new Entry("computed:number_input_widget", "computed/lua/nodes/widgets/number_input.lua"),
            new Entry("computed:clock_widget", "computed/lua/nodes/widgets/clock.lua"),
            new Entry("computed:button_widget", "computed/lua/nodes/widgets/button.lua"),
            new Entry("computed:checkbox_widget", "computed/lua/nodes/widgets/checkbox.lua"),
            new Entry("computed:text_input_widget", "computed/lua/nodes/widgets/text_input.lua"),
            new Entry("computed:slider_widget", "computed/lua/nodes/widgets/slider.lua"),
            new Entry("computed:progress_bar_widget", "computed/lua/nodes/widgets/progress.lua"),
            new Entry("computed:peripheral", "computed/lua/nodes/widgets/monitor.lua"),
            new Entry("computed:glasses_hud", "computed/lua/nodes/widgets/glasses_hud.lua"),
            new Entry("computed:terminal_widget", "computed/lua/nodes/widgets/terminal.lua"),
            new Entry("computed:terminal_line", "computed/lua/nodes/widgets/terminal_line.lua"),
            new Entry("computed:panel_widget", "computed/lua/nodes/widgets/panel.lua"),
            new Entry("computed:text_block_widget", "computed/lua/nodes/widgets/text_block.lua"),
            new Entry("computed:table_widget", "computed/lua/nodes/widgets/table.lua"),
            new Entry("computed:constant", "computed/lua/nodes/sources/constant.lua"),
            new Entry("computed:tick", "computed/lua/nodes/sources/tick.lua"),
            new Entry("computed:pulse", "computed/lua/nodes/sources/pulse.lua"),
            new Entry("computed:oscillator", "computed/lua/nodes/sources/oscillator.lua"),
            new Entry("computed:pass_every_n", "computed/lua/nodes/state/pass_every_n.lua"),
            new Entry("computed:delay", "computed/lua/nodes/state/delay.lua"),
            new Entry("computed:sample_hold", "computed/lua/nodes/state/sample_hold.lua"),
            new Entry("computed:math_random", "computed/lua/nodes/math/random.lua"),
            new Entry("computed:math_clamp", "computed/lua/nodes/math/clamp.lua"),
            new Entry("computed:math_map", "computed/lua/nodes/math/map.lua"),
            new Entry("computed:math_lerp", "computed/lua/nodes/math/lerp.lua"),
            new Entry("computed:math_average", "computed/lua/nodes/math/average.lua"),
            new Entry("computed:logic_not", "computed/lua/nodes/logic/not.lua"),
            new Entry("computed:cmp_approx", "computed/lua/nodes/logic/approximately.lua"),
            new Entry("computed:edge_rise", "computed/lua/nodes/logic/edge_rise.lua"),
            new Entry("computed:edge_fall", "computed/lua/nodes/logic/edge_fall.lua"),
            new Entry("computed:schmitt", "computed/lua/nodes/logic/schmitt.lua"),
            new Entry("computed:mux", "computed/lua/nodes/logic/mux.lua"),
            new Entry("computed:sr_latch", "computed/lua/nodes/logic/sr_latch.lua"),
            new Entry("computed:d_flipflop", "computed/lua/nodes/logic/d_flipflop.lua"),
            new Entry("computed:bool_to_level", "computed/lua/nodes/io/bool_to_level.lua"),
            new Entry("computed:level_to_bool", "computed/lua/nodes/io/level_to_bool.lua"),
            new Entry("computed:display", "computed/lua/nodes/io/display.lua"),
            new Entry("computed:text_source", "computed/lua/nodes/text/source.lua"),
            new Entry("computed:color_source", "computed/lua/nodes/sources/color.lua"),
            new Entry("computed:speaker_play_note", "computed/lua/nodes/speaker/play_note.lua"),
            new Entry("computed:speaker_play_sound", "computed/lua/nodes/speaker/play_sound.lua"),
            new Entry("computed:speaker_play_wave", "computed/lua/nodes/speaker/play_wave.lua"),
            new Entry("computed:speaker_play_disc", "computed/lua/nodes/speaker/play_disc.lua"),
            new Entry("computed:speaker_stop", "computed/lua/nodes/speaker/stop.lua"),
            new Entry("computed:speaker_eject", "computed/lua/nodes/speaker/eject.lua"),
            new Entry("computed:speaker_status", "computed/lua/nodes/speaker/status.lua"),
            new Entry("computed:concatenate_strings", "computed/lua/nodes/text/concatenate.lua"),
            new Entry("computed:number_to_text", "computed/lua/nodes/text/number_to_text.lua"),
            new Entry("computed:if_branch", "computed/lua/nodes/flow/if.lua"),
            new Entry("computed:switch", "computed/lua/nodes/flow/switch.lua"),
            new Entry("computed:rgb_preview", "computed/lua/nodes/io/rgb_preview.lua"),
            new Entry("computed:aeronautics_telemetry", "computed/lua/nodes/integration/aeronautics/telemetry.lua", "create"));

    private BundledLuaLibrary() {}

    public static Map<String, LuaDefinitionSource> load() {
        Map<String, LuaDefinitionSource> definitions = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (entry.requiredMod() != null && !isModLoaded(entry.requiredMod())) {
                // CC-style: nodes for a mod that isn't installed stay hidden.
                continue;
            }
            add(definitions, entry.id(), read(entry.resource()));
        }
        unarySpecs().forEach(spec -> add(
                definitions,
                spec.id(),
                template(
                        "computed/lua/nodes/templates/unary.lua",
                        spec.id(),
                        spec.title(),
                        spec.category(),
                        spec.expression())));
        binarySpecs().forEach(spec -> add(
                definitions,
                spec.id(),
                template(
                        "computed/lua/nodes/templates/binary.lua",
                        spec.id(),
                        spec.title(),
                        spec.category(),
                        spec.expression())));
        comparisonSpecs().forEach(spec -> add(
                definitions,
                spec.id(),
                template(
                        "computed/lua/nodes/templates/comparison.lua",
                        spec.id(),
                        spec.title(),
                        spec.category(),
                        spec.expression())));
        return java.util.Collections.unmodifiableMap(definitions);
    }

    private static void add(
            Map<String, LuaDefinitionSource> definitions,
            String id,
            String source) {
        LuaDefinitionSource definition = new LuaDefinitionSource(
                1,
                id,
                source,
                "",
                LuaDefinitionSource.Origin.BUNDLED);
        definitions.put(definition.id(), definition);
    }

    private static String template(
            String resource,
            String id,
            String title,
            String category,
            String expression) {
        return read(resource)
                .replace("@ID@", id)
                .replace("@TITLE@", title)
                .replace("@CATEGORY@", category)
                .replace("@EXPRESSION@", expression);
    }

    private static List<Spec> unarySpecs() {
        return List.of(
                new Spec("computed:math_abs", "Absolute", "math", "math.abs(value)"),
                new Spec("computed:math_sqrt", "Square Root", "math", "math.sqrt(math.max(0, value))"),
                new Spec("computed:math_floor", "Floor", "math", "math.floor(value)"),
                new Spec("computed:math_ceil", "Ceiling", "math", "math.ceil(value)"),
                new Spec("computed:math_round", "Round", "math", "math.floor(value + 0.5)"),
                new Spec("computed:math_negate", "Negate", "math", "-value"),
                new Spec("computed:math_log", "Natural Log", "math", "value > 0 and math.log(value) or 0"),
                new Spec("computed:math_log10", "Log 10", "math", "value > 0 and math.log(value, 10) or 0"),
                new Spec("computed:math_exp", "Exponent", "math", "math.exp(value)"),
                new Spec("computed:math_sign", "Sign", "math", "value > 0 and 1 or (value < 0 and -1 or 0)"),
                new Spec("computed:math_sin", "Sine", "math", "math.sin(value)"),
                new Spec("computed:math_cos", "Cosine", "math", "math.cos(value)"),
                new Spec("computed:math_tan", "Tangent", "math", "math.tan(value)"),
                new Spec("computed:quantize_redstone", "Quantize Redstone", "io", "math.max(0, math.min(15, math.floor(value + 0.5)))"));
    }

    private static List<Spec> binarySpecs() {
        return List.of(
                new Spec("computed:math_add", "Add", "math", "a + b"),
                new Spec("computed:math_subtract", "Subtract", "math", "a - b"),
                new Spec("computed:math_multiply", "Multiply", "math", "a * b"),
                new Spec("computed:math_divide", "Divide", "math", "b == 0 and 0 or a / b"),
                new Spec("computed:math_mod", "Modulo", "math", "b == 0 and 0 or a % b"),
                new Spec("computed:math_min", "Minimum", "math", "math.min(a, b)"),
                new Spec("computed:math_max", "Maximum", "math", "math.max(a, b)"),
                new Spec("computed:math_pow", "Power", "math", "a ^ b"),
                new Spec(
                        "computed:math_atan2",
                        "Atan2",
                        "math",
                        "b > 0 and math.atan(a / b) or (b < 0 and (a >= 0 and math.atan(a / b) + math.pi or math.atan(a / b) - math.pi) or (a >= 0 and math.pi / 2 or -math.pi / 2))"),
                new Spec("computed:logic_and", "And", "logic", "(a ~= 0 and b ~= 0) and 1 or 0"),
                new Spec("computed:logic_or", "Or", "logic", "(a ~= 0 or b ~= 0) and 1 or 0"),
                new Spec("computed:logic_xor", "Xor", "logic", "((a ~= 0) ~= (b ~= 0)) and 1 or 0"),
                new Spec("computed:logic_nand", "Nand", "logic", "(not (a ~= 0 and b ~= 0)) and 1 or 0"),
                new Spec("computed:logic_nor", "Nor", "logic", "(not (a ~= 0 or b ~= 0)) and 1 or 0"),
                new Spec("computed:logic_xnor", "Xnor", "logic", "((a ~= 0) == (b ~= 0)) and 1 or 0"));
    }

    private static List<Spec> comparisonSpecs() {
        return List.of(
                new Spec("computed:cmp_eq", "Equal", "logic", "a == b"),
                new Spec("computed:cmp_gt", "Greater Than", "logic", "a > b"),
                new Spec("computed:cmp_lt", "Less Than", "logic", "a < b"),
                new Spec("computed:cmp_ge", "Greater or Equal", "logic", "a >= b"),
                new Spec("computed:cmp_le", "Less or Equal", "logic", "a <= b"));
    }

    private static String read(String path) {
        ClassLoader loader = BundledLuaLibrary.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled Lua definition: " + path);
            }
            return normalizeLineEndings(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled Lua definition: " + path, exception);
        }
    }

    private static String normalizeLineEndings(String source) {
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * True when the mod is loaded. Outside a running game (plain unit tests, bare dev classpath)
     * FML is unavailable — treat gated nodes as present so bundled definitions stay testable.
     */
    private static boolean isModLoaded(String modId) {
        try {
            return net.neoforged.fml.ModList.get().isLoaded(modId);
        } catch (Throwable unavailableInThisEnvironment) {
            return true;
        }
    }

    private record Entry(String id, String resource, String requiredMod) {
        Entry(String id, String resource) {
            this(id, resource, null);
        }
    }

    private record Spec(String id, String title, String category, String expression) {}
}
