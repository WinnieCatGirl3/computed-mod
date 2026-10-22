package dev.propulsionteam.computed.lua.node;

import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntegrationLuaLibrary {
    private static final List<Entry> ENTRIES = List.of(
            new Entry("computed:cc_input", "computed/lua/nodes/integration/computercraft/input.lua"),
            new Entry("computed:cc_output", "computed/lua/nodes/integration/computercraft/output.lua"),
            new Entry("computed:create_kinetic", "computed/lua/nodes/integration/create/kinetic.lua"),
            new Entry("computed:create_link_receiver", "computed/lua/nodes/integration/create/link_receiver.lua"),
            new Entry("computed:create_link_sender", "computed/lua/nodes/integration/create/link_sender.lua"),
            new Entry("computed:aeronautics_telemetry", "computed/lua/nodes/integration/aeronautics/telemetry.lua"),
            new Entry("computed:aeronautics_position", "computed/lua/nodes/integration/aeronautics/position.lua"),
            new Entry("computed:aeronautics_velocity", "computed/lua/nodes/integration/aeronautics/velocity.lua"),
            new Entry("computed:aeronautics_angular_velocity", "computed/lua/nodes/integration/aeronautics/angular_velocity.lua"),
            new Entry("computed:aeronautics_forward", "computed/lua/nodes/integration/aeronautics/forward.lua"),
            new Entry("computed:aeronautics_right", "computed/lua/nodes/integration/aeronautics/right.lua"),
            new Entry("computed:aeronautics_pitch", "computed/lua/nodes/integration/aeronautics/pitch.lua"),
            new Entry("computed:aeronautics_roll", "computed/lua/nodes/integration/aeronautics/roll.lua"),
            new Entry("computed:aeronautics_heading", "computed/lua/nodes/integration/aeronautics/heading.lua"),
            new Entry("computed:aeronautics_distance", "computed/lua/nodes/integration/aeronautics/distance.lua"),
            new Entry("computed:aeronautics_speed", "computed/lua/nodes/integration/aeronautics/speed.lua"),
            new Entry("computed:aeronautics_vertical_speed", "computed/lua/nodes/integration/aeronautics/vertical_speed.lua"),
            new Entry("computed:aeronautics_altitude", "computed/lua/nodes/integration/aeronautics/altitude.lua"),
            new Entry("computed:aeronautics_velocity_sensor", "computed/lua/nodes/integration/aeronautics/velocity_sensor.lua"),
            new Entry("computed:aeronautics_present", "computed/lua/nodes/integration/aeronautics/present.lua"),
            new Entry("computed:aeronautics_name", "computed/lua/nodes/integration/aeronautics/name.lua"),
            new Entry("computed:aeronautics_source", "computed/lua/nodes/integration/aeronautics/source.lua"));

    private IntegrationLuaLibrary() {}

    public static Map<String, LuaDefinitionSource> load() {
        Map<String, LuaDefinitionSource> definitions = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            LuaDefinitionSource source = new LuaDefinitionSource(
                    1,
                    entry.id(),
                    read(entry.resource()),
                    "",
                    LuaDefinitionSource.Origin.INTEGRATION);
            definitions.put(source.id(), source);
        }
        return java.util.Collections.unmodifiableMap(definitions);
    }

    public static String unavailableReason(String definitionId) {
        if (definitionId.startsWith("computed:cc_")
                && !net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            return "CC:Tweaked is not installed";
        }
        if (definitionId.startsWith("computed:create_")
                && !net.neoforged.fml.ModList.get().isLoaded("create")) {
            return "Create is not installed";
        }
        if (definitionId.startsWith("computed:aeronautics_")
                && !net.neoforged.fml.ModList.get().isLoaded("aeronautics")) {
            return "Create Aeronautics is not installed";
        }
        return "";
    }

    private static String read(String path) {
        try (InputStream stream = IntegrationLuaLibrary.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing integration Lua definition: " + path);
            }
            return normalizeLineEndings(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read integration Lua definition: " + path, exception);
        }
    }

    private static String normalizeLineEndings(String source) {
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    private record Entry(String id, String resource) {}
}
