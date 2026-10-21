package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.client.renderer.MonitorWidgetPainter;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.content.monitors.widgets.WidgetDrawList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * Geometry and painting of the graph widget region on the glasses HUD: widgets keep their own
 * x/y and size like on a monitor, scaled as one block whose normalized anchor lives in
 * {@link GlassesHudSettings}. The K designer paints the same block so it can be dragged and
 * resized; an empty draw list falls back to a placeholder box.
 */
final class GlassesHudWidgetRegion {
    /** Placeholder size in widget pixels when the graph has not sent widgets yet. */
    static final int PLACEHOLDER_WIDTH = 120;
    static final int PLACEHOLDER_HEIGHT = 64;

    private GlassesHudWidgetRegion() {}

    /** Raw widget-space bounds: offset of the first widget plus the block's size. */
    record RawBounds(int minX, int minY, int width, int height) {}

    /** The region's screen rect: block size times scale, anchored by the settings. */
    record Box(int x, int y, int width, int height) {}

    static RawBounds rawBounds(WidgetDrawList drawList) {
        if (drawList == null || drawList.isEmpty()) {
            return new RawBounds(0, 0, PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Widget widget : drawList.widgets()) {
            minX = Math.min(minX, widget.x());
            minY = Math.min(minY, widget.y());
            maxX = Math.max(maxX, widget.x() + widget.w());
            maxY = Math.max(maxY, widget.y() + widget.h());
        }
        return new RawBounds(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    static Box boxFor(
            GlassesHudSettings settings, WidgetDrawList drawList, int guiWidth, int guiHeight) {
        RawBounds bounds = rawBounds(drawList);
        float scale = settings.widgetsScale;
        int width = Math.max(1, Math.round(bounds.width() * scale));
        int height = Math.max(1, Math.round(bounds.height() * scale));
        int x = Math.round(settings.widgetsX * guiWidth - width / 2f);
        int y = Math.round(settings.widgetsY * guiHeight);
        return new Box(x, y, width, height);
    }

    /** Paints the widgets into the box: {@code screen = box + scale * (widgetPos - min)}. */
    static void paint(GuiGraphics graphics, WidgetDrawList drawList, Box box, float scale) {
        if (drawList == null || drawList.isEmpty()) {
            return;
        }
        RawBounds bounds = rawBounds(drawList);
        graphics.pose().pushPose();
        graphics.pose().translate(
                box.x() - scale * bounds.minX(),
                box.y() - scale * bounds.minY(),
                0f);
        Matrix4f base = new Matrix4f(graphics.pose().last().pose()).scale(scale, scale, 1f);
        MultiBufferSource.BufferSource bufferSource = graphics.bufferSource();
        try {
            for (Widget widget : drawList.widgets()) {
                MonitorWidgetPainter.renderWidget(widget, base, base, base, base, base,
                        bufferSource, net.minecraft.client.Minecraft.getInstance().font);
            }
        } finally {
            graphics.flush();
            graphics.pose().popPose();
        }
    }
}
