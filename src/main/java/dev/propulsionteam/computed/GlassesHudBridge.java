package dev.propulsionteam.computed;

import net.minecraft.core.BlockPos;

/**
 * Client-side hooks for the glasses HUD (installed by {@code ComputedClient}): widget draw list
 * delivery from the server. No-ops on dedicated servers.
 */
public final class GlassesHudBridge {
    private static WidgetsConsumer widgetsConsumer = (dimension, pos, hash, drawListBytes) -> {};

    /** Latest HUD widgets of one computer: dimension, position, state hash, wire bytes. */
    @FunctionalInterface
    public interface WidgetsConsumer {
        void accept(String dimension, BlockPos pos, long hash, byte[] drawListBytes);
    }

    private GlassesHudBridge() {}

    public static void install(WidgetsConsumer consumer) {
        widgetsConsumer = consumer == null
                ? (dimension, pos, hash, drawListBytes) -> {}
                : consumer;
    }

    public static void deliver(String dimension, BlockPos pos, long hash, byte[] drawListBytes) {
        widgetsConsumer.accept(dimension, pos, hash, drawListBytes);
    }
}
