package dev.propulsionteam.computed.lua.endpoint;

import java.util.List;

public interface BuiltinEndpointHost {
    double worldTime();

    /**
     * Numeric computer ID used to address messages ({@code computed:net}); allocates one on
     * first use. Returns {@code -1} when the host cannot provide one (such as in tests).
     */
    default int numericComputerId() {
        return -1;
    }

    default double[] position() {
        return new double[] {0, 0, 0};
    }

    default double[] rotation() {
        return new double[] {0, 0, 0};
    }

    default int redstoneInput(String face) {
        return 0;
    }

    default int comparatorInput(String face) {
        return 0;
    }

    default boolean blockPresent(String face) {
        return false;
    }

    /**
     * Namespaced id of the block at a relative face ({@code minecraft:stone}), or
     * {@code minecraft:air} when the face is empty or unknown.
     */
    default String blockId(String face) {
        return "minecraft:air";
    }

    default void redstoneOutput(String face, int level) {}

    default void showWidgets(String target, List<BuiltinWidget> widgets) {}

    /**
     * Latest glasses HUD widgets from the graph, painted on every linked player's HUD like on a
     * monitor (own x/y/size on the fixed glasses canvas). Delivered to every player whose link
     * card points at this computer.
     */
    default void showGlassesHud(List<BuiltinWidget> widgets) {}

    void runCommand(String command);
}
