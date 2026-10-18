package dev.propulsionteam.computed.client.editor.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.propulsionteam.computed.internal.node.api.WNode;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CanvasLayoutOpsTest {
    private static WNode node(int x, int y, int topoDepth) {
        WNode node = new WNode(
                ResourceLocation.fromNamespaceAndPath("computed", "test"),
                "N",
                x,
                y);
        node.setTopoDepth(topoDepth);
        node.ensureLayoutUpToDate();
        return node;
    }

    @Test
    void alignSnapshotsNodesToTheGroupBoundingBox() {
        WNode a = node(0, 0, 0);
        WNode b = node(200, 50, 0);
        WNode c = node(100, 100, 0);
        List<WNode> nodes = List.of(a, b, c);
        int width = a.getWidth();
        int height = a.getHeight();

        CanvasLayoutOps.align(nodes, CanvasLayoutOps.Align.LEFT);
        assertEquals(0, a.getX());
        assertEquals(0, b.getX());
        assertEquals(0, c.getX());

        // Each alignment recomputes the bounding box from current positions, so reset first.
        a.setPos(0, 0);
        b.setPos(200, 50);
        c.setPos(100, 100);
        CanvasLayoutOps.align(nodes, CanvasLayoutOps.Align.RIGHT);
        assertEquals(200, a.getX());
        assertEquals(200, b.getX());
        assertEquals(200, c.getX());

        a.setPos(0, 0);
        b.setPos(200, 50);
        c.setPos(100, 100);
        CanvasLayoutOps.align(nodes, CanvasLayoutOps.Align.CENTER_H);
        assertEquals(100, a.getX());
        assertEquals(100, b.getX());
        assertEquals(100, c.getX());

        a.setPos(0, 0);
        b.setPos(200, 50);
        c.setPos(100, 100);
        CanvasLayoutOps.align(nodes, CanvasLayoutOps.Align.TOP);
        assertEquals(0, a.getY());
        assertEquals(0, b.getY());
        assertEquals(0, c.getY());

        a.setPos(0, 0);
        b.setPos(200, 50);
        c.setPos(100, 100);
        CanvasLayoutOps.align(nodes, CanvasLayoutOps.Align.BOTTOM);
        assertEquals(100, a.getY());
        assertEquals(100, b.getY());
        assertEquals(100, c.getY());

        a.setPos(0, 0);
        b.setPos(200, 50);
        c.setPos(100, 100);
        CanvasLayoutOps.align(nodes, CanvasLayoutOps.Align.CENTER_V);
        assertEquals(50, a.getY());
        assertEquals(50, b.getY());
        assertEquals(50, c.getY());

        assertEquals(width, b.getWidth());
        assertEquals(height, b.getHeight());
    }

    @Test
    void distributeSpacesOutermostNodesEvenly() {
        WNode a = node(0, 0, 0);
        WNode b = node(100, 40, 0);
        WNode c = node(300, 80, 0);
        int width = a.getWidth();
        int height = a.getHeight();

        CanvasLayoutOps.distribute(List.of(a, b, c), true);
        int horizontalGap = (300 - width * 3 + width) / 2;
        assertEquals(0, a.getX());
        assertEquals(width + horizontalGap, b.getX());
        assertEquals(300, c.getX());

        CanvasLayoutOps.distribute(List.of(a, b, c), false);
        int verticalGap = (80 - height * 3 + height) / 2;
        assertEquals(0, a.getY());
        assertEquals(height + verticalGap, b.getY());
        assertEquals(80, c.getY());
    }

    @Test
    void autoLayoutColumnsFollowTopoDepthAndStackByY() {
        WNode first = node(500, 900, 0);
        WNode second = node(0, 0, 0);
        WNode third = node(999, 999, 1);
        WNode cyclic = node(1, 1, Integer.MAX_VALUE);

        CanvasLayoutOps.autoLayout(List.of(first, second, third, cyclic), 60, 24);

        int columnWidth = Math.max(first.getWidth(), second.getWidth());
        // Depth 0 sorts by current Y: second (y=0) stacks above first (y=900).
        assertEquals(0, second.getX());
        assertEquals(0, second.getY());
        assertEquals(0, first.getX());
        assertEquals(second.getHeight() + 24, first.getY());
        // Depth 1 moves to the next column.
        assertEquals(columnWidth + 60, third.getX());
        assertEquals(0, third.getY());
        // Unvisited (cyclic) nodes collect in the final column.
        assertEquals(columnWidth + 60 + Math.max(third.getWidth(), cyclic.getWidth()) + 60, cyclic.getX());
    }

    @Test
    void alignIgnoresSingleNodesAndAutoLayoutAcceptsEmptyGraphs() {
        WNode only = node(10, 10, 0);
        CanvasLayoutOps.align(List.of(only), CanvasLayoutOps.Align.LEFT);
        CanvasLayoutOps.autoLayout(List.of(), 60, 24);
        assertEquals(10, only.getX());
        assertEquals(10, only.getY());
    }

    @Test
    void layoutOperationsAreIdempotent() {
        WNode a = node(13, 7, 1);
        WNode b = node(211, 48, 0);
        List<WNode> nodes = List.of(a, b);
        CanvasLayoutOps.align(nodes, CanvasLayoutOps.Align.LEFT);
        int firstPassX = a.getX();
        CanvasLayoutOps.align(nodes, CanvasLayoutOps.Align.LEFT);
        assertEquals(firstPassX, a.getX());
    }
}
