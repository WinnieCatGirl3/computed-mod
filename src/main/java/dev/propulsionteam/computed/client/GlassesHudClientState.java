package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.content.items.PortableLinkData;
import dev.propulsionteam.computed.content.monitors.widgets.WidgetDrawList;
import io.netty.buffer.Unpooled;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client cache of the graph-driven glasses HUD widget draw list for the currently bound link:
 * the overlay requests it once when the link resolves and re-checks every few seconds as a
 * fallback, and the server pushes updates whenever the graph's widgets change.
 */
public final class GlassesHudClientState {
    /** Fallback re-request cadence in milliseconds; the push path delivers live updates. */
    private static final long POLL_INTERVAL_MS = 5_000L;

    private static String activeKey = "";
    private static long knownHash = Long.MIN_VALUE;
    private static long lastRequestAt = Long.MIN_VALUE;
    private static WidgetDrawList drawList = WidgetDrawList.EMPTY;

    private GlassesHudClientState() {}

    /** Widgets for the active link; a different link resets the cache and forces a request. */
    public static WidgetDrawList drawListFor(PortableLinkData link) {
        if (link == null) {
            return WidgetDrawList.EMPTY;
        }
        String key = key(link.dimension(), link.blockPos());
        if (!key.equals(activeKey)) {
            activeKey = key;
            knownHash = Long.MIN_VALUE;
            lastRequestAt = Long.MIN_VALUE;
            drawList = WidgetDrawList.EMPTY;
        }
        return drawList;
    }

    /** True when the overlay should (re)ask the server for the active link's widgets. */
    public static boolean shouldRequest() {
        if (activeKey.isEmpty()) {
            return false;
        }
        long now = Util.getMillis();
        if (knownHash != Long.MIN_VALUE && now - lastRequestAt < POLL_INTERVAL_MS) {
            return false;
        }
        lastRequestAt = now;
        return true;
    }

    /** Hash of the currently cached state, sent along with the next request. */
    public static long knownHash() {
        return knownHash;
    }

    /** Stores a server widget push when it belongs to the active link. */
    public static void deliver(String dimension, BlockPos pos, long hash, byte[] drawListBytes) {
        if (activeKey.isEmpty() || !key(dimension, pos).equals(activeKey)) {
            return;
        }
        knownHash = hash;
        drawList = read(drawListBytes);
    }

    /** Drops the cache when the client leaves a world. */
    public static void reset() {
        activeKey = "";
        knownHash = Long.MIN_VALUE;
        lastRequestAt = Long.MIN_VALUE;
        drawList = WidgetDrawList.EMPTY;
    }

    private static String key(String dimension, BlockPos pos) {
        return dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static WidgetDrawList read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return WidgetDrawList.EMPTY;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            return WidgetDrawList.read(buf);
        } finally {
            buf.release();
        }
    }
}
