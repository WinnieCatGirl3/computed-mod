package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.internal.node.api.WNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** Pure alignment, distribution, and auto-layout math over canvas node lists. */
public final class CanvasLayoutOps {
    public enum Align {
        LEFT,
        CENTER_H,
        RIGHT,
        TOP,
        CENTER_V,
        BOTTOM
    }

    private CanvasLayoutOps() {}

    /** Aligns every node in {@code nodes} (size ≥ 2) against the group's bounding box. */
    public static void align(List<WNode> nodes, Align mode) {
        if (nodes.size() < 2) {
            return;
        }
        nodes.forEach(WNode::ensureLayoutUpToDate);
        int minX = nodes.stream().mapToInt(WNode::getX).min().orElse(0);
        int maxX = nodes.stream().mapToInt(node -> node.getX() + node.getWidth()).max().orElse(0);
        int minY = nodes.stream().mapToInt(WNode::getY).min().orElse(0);
        int maxY = nodes.stream().mapToInt(node -> node.getY() + node.getHeight()).max().orElse(0);
        for (WNode node : nodes) {
            switch (mode) {
                case LEFT -> node.setPos(minX, node.getY());
                case CENTER_H -> node.setPos(minX + (maxX - minX - node.getWidth()) / 2, node.getY());
                case RIGHT -> node.setPos(maxX - node.getWidth(), node.getY());
                case TOP -> node.setPos(node.getX(), minY);
                case CENTER_V -> node.setPos(node.getX(), minY + (maxY - minY - node.getHeight()) / 2);
                case BOTTOM -> node.setPos(node.getX(), maxY - node.getHeight());
            }
        }
    }

    /**
     * Spaces the outermost nodes evenly along one axis (size ≥ 3). Horizontal distribution sorts
     * by the left edge; vertical distribution sorts by the top edge.
     */
    public static void distribute(List<WNode> nodes, boolean horizontal) {
        if (nodes.size() < 3) {
            return;
        }
        nodes.forEach(WNode::ensureLayoutUpToDate);
        List<WNode> ordered = new ArrayList<>(nodes);
        if (horizontal) {
            ordered.sort(Comparator.comparingInt(WNode::getX).thenComparing(node -> node.getId().toString()));
            int span = lastRight(ordered) - ordered.get(0).getX();
            int total = ordered.stream().mapToInt(WNode::getWidth).sum();
            int gap = (span - total) / (ordered.size() - 1);
            int cursor = ordered.get(0).getX();
            for (WNode node : ordered) {
                node.setPos(cursor, node.getY());
                cursor += node.getWidth() + gap;
            }
        } else {
            ordered.sort(Comparator.comparingInt(WNode::getY).thenComparing(node -> node.getId().toString()));
            int span = lastBottom(ordered) - ordered.get(0).getY();
            int total = ordered.stream().mapToInt(WNode::getHeight).sum();
            int gap = (span - total) / (ordered.size() - 1);
            int cursor = ordered.get(0).getY();
            for (WNode node : ordered) {
                node.setPos(node.getX(), cursor);
                cursor += node.getHeight() + gap;
            }
        }
    }

    /**
     * Lays nodes out in topological columns (depth from {@link WNode#getTopoDepth()}), stacking
     * each column top-to-bottom. Nodes in cycles (unvisited depth) collect in a final column.
     */
    public static void autoLayout(List<WNode> nodes, int columnGap, int rowGap) {
        if (nodes.isEmpty()) {
            return;
        }
        nodes.forEach(WNode::ensureLayoutUpToDate);
        TreeMap<Integer, List<WNode>> columns = new TreeMap<>();
        for (WNode node : nodes) {
            int depth = node.getTopoDepth();
            if (depth < 0 || depth == Integer.MAX_VALUE) {
                depth = Integer.MAX_VALUE;
            }
            columns.computeIfAbsent(depth, ignored -> new ArrayList<>()).add(node);
        }
        Comparator<WNode> withinColumn = Comparator.comparingInt(WNode::getY)
                .thenComparingInt(WNode::getX)
                .thenComparing(node -> node.getId().toString());
        int x = 0;
        for (List<WNode> column : columns.values()) {
            column.sort(withinColumn);
            int columnWidth = column.stream().mapToInt(WNode::getWidth).max().orElse(0);
            int y = 0;
            for (WNode node : column) {
                node.setPos(x, y);
                y += node.getHeight() + rowGap;
            }
            x += columnWidth + columnGap;
        }
    }

    private static int lastRight(List<WNode> ordered) {
        WNode last = ordered.get(ordered.size() - 1);
        return last.getX() + last.getWidth();
    }

    private static int lastBottom(List<WNode> ordered) {
        WNode last = ordered.get(ordered.size() - 1);
        return last.getY() + last.getHeight();
    }

    /**
     * Zoom that fits {@code boundsWidth × boundsHeight} into the viewport, inflated by
     * {@code padding} pixels on every side and clamped between the viewport minimum and
     * {@code maxZoom}. Returns {@code 1} for degenerate input.
     */
    public static float fitZoom(
            double boundsWidth,
            double boundsHeight,
            int viewWidth,
            int viewHeight,
            float maxZoom,
            double padding) {
        if (boundsWidth <= 0 || boundsHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return 1f;
        }
        float zoom = (float) Math.min(
                viewWidth / (boundsWidth + padding * 2.0),
                viewHeight / (boundsHeight + padding * 2.0));
        return Math.max(InertialViewport.MIN_ZOOM, Math.min(maxZoom, zoom));
    }

    /** Pan X that centers the given graph coordinate in a viewport of {@code viewWidth}. */
    public static double centerPanX(double graphX, int viewWidth) {
        return viewWidth / 2.0 - graphX;
    }

    /** Pan Y that centers the given graph coordinate in a viewport of {@code viewHeight}. */
    public static double centerPanY(double graphY, int viewHeight) {
        return viewHeight / 2.0 - graphY;
    }
}
