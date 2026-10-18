package dev.propulsionteam.computed.internal.node.client.ui;

import dev.propulsionteam.computed.client.editor.EditorCommand;
import dev.propulsionteam.computed.client.editor.EditorDetailLevel;
import dev.propulsionteam.computed.client.editor.EditorHistory;
import dev.propulsionteam.computed.client.editor.GraphPoint;
import dev.propulsionteam.computed.client.editor.GraphRect;
import dev.propulsionteam.computed.client.editor.canvas.CanvasLayoutOps;
import dev.propulsionteam.computed.client.editor.canvas.InertialViewport;
import dev.propulsionteam.computed.internal.node.api.WConnection;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import dev.propulsionteam.computed.internal.node.client.editor.PointerGestureClassifier;
import dev.propulsionteam.computed.internal.node.client.editor.WireEditorController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class WNodeScreen extends Screen {
    public static final String EDITOR_VIEWPORT_ROOT = "root";

    private static final float ZOOM_STEP = 0.05f;
    private static final int MAX_HISTORY = 80;
    private static final int GRID_SPACING = 20;
    private static final int ITEM_PICKER_WIDTH = 260;
    private static final int ITEM_PICKER_ROWS = 9;
    private static final int ITEM_PICKER_ROW_HEIGHT = 20;

    private static WNodeScreen activeScreen;

    private final WireEditorController wires = new WireEditorController();
    private final EditorHistory<WNodeScreen> history = new EditorHistory<>(this, MAX_HISTORY);
    private WGraph graph;
    private final InertialViewport viewport = new InertialViewport();
    private long revision;
    private String saveFailure = "";
    private WNode selectedNode;
    private WNode draggingNode;
    private double dragOffsetX;
    private double dragOffsetY;
    private WNode linkingNode;
    private int linkingPin = -1;
    private int draggingConnection = -1;
    private int draggingWaypoint = -1;
    private boolean selecting;
    private int selectionStartX;
    private int selectionStartY;
    private int selectionEndX;
    private int selectionEndY;
    private boolean panning;
    private double panLastX;
    private double panLastY;
    private long panLastNanos;
    private int rightPressX = -1;
    private int rightPressY = -1;
    private long rightPressAt;
    private boolean rightDragged;
    private int mouseX;
    private int mouseY;
    private long lastFrameNanos;
    private boolean itemPickerOpen;
    private String itemPickerQuery = "";
    private int itemPickerScroll;
    private Consumer<ItemStack> itemPickerCallback;
    private final List<ItemStack> itemPickerItems = new ArrayList<>();

    public WNodeScreen(WGraph graph) {
        super(Component.literal("Winnie's Computed Node Editor"));
        this.graph = graph;
    }

    public static void requestItemPick(Consumer<ItemStack> callback) {
        if (activeScreen != null) {
            activeScreen.openItemPicker(callback);
        }
    }

    protected boolean minimalCanvasMode() {
        return true;
    }

    protected void openNodeExplorer(int screenX, int screenY, int graphX, int graphY) {}

    protected WNode createDuplicateNode(WNode source, int x, int y) {
        return null;
    }

    protected boolean copySelectedNodesToClipboard() {
        return false;
    }

    protected boolean pasteNodesFromClipboard(int graphX, int graphY) {
        return false;
    }

    protected void persistEditorViewport(String contextKey) {}

    protected boolean loadEditorViewport(String contextKey) {
        return false;
    }

    protected final void restoreEditorViewport(double panX, double panY, float zoom) {
        viewport.restore(panX, panY, zoom);
        wires.invalidate();
    }

    protected final double editorPanX() {
        return viewport.panX();
    }

    protected final double editorPanY() {
        return viewport.panY();
    }

    protected final float editorZoom() {
        return viewport.zoom();
    }

    protected final long editorRevision() {
        return revision;
    }

    protected final long editorHistoryRevision() {
        return history.currentRevision();
    }

    protected final boolean editorHistoryDirty() {
        return history.isDirty();
    }

    protected final void acknowledgeEditorHistorySaved(long acknowledgedEditGeneration) {
        history.markSaved();
    }

    protected final void setEditorSaveFailureDiagnostic(String message) {
        saveFailure = message == null ? "" : message;
    }

    protected final void clearEditorSaveFailureDiagnostic() {
        saveFailure = "";
    }

    protected final int editorGraphX(double screenX) {
        return screenToGraphX(screenX);
    }

    protected final int editorGraphY(double screenY) {
        return screenToGraphY(screenY);
    }

    protected final void adjustEditorZoom(double amount, double screenX, double screenY) {
        viewport.addZoomImpulse(amount, screenX, screenY);
        wires.invalidate();
    }

    protected final void addNodeToCanvas(WNode node) {
        if (node == null) {
            return;
        }
        checkpoint();
        clearSelection();
        graph.addNode(node);
        node.setSelected(true);
        selectedNode = node;
    }

    protected final void replaceCanvasGraph(WGraph replacement) {
        checkpoint();
        graph = replacement;
        selectedNode = null;
        wires.invalidate();
    }

    protected final boolean hasSelectedNodes() {
        return graph.getNodes().stream().anyMatch(WNode::isSelected);
    }

    protected final Set<UUID> selectedCanvasNodeIds() {
        Set<UUID> ids = new HashSet<>();
        selectedNodes().forEach(node -> ids.add(node.getId()));
        return Set.copyOf(ids);
    }

    protected final void selectCanvasNodes(Set<UUID> nodeIds) {
        clearSelection();
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        for (WNode node : graph.getNodes()) {
            if (nodeIds.contains(node.getId())) {
                node.setSelected(true);
                selectedNode = node;
            }
        }
    }

    protected final boolean selectNodeAtGraphPoint(int graphX, int graphY) {
        WNode node = topNodeAt(graphX, graphY);
        if (node == null) {
            return false;
        }
        if (!node.isSelected()) {
            clearSelection();
            node.setSelected(true);
        }
        selectedNode = node;
        return true;
    }

    protected final void cloneSelectedNodes() {
        List<WNode> selected = selectedNodes();
        if (selected.isEmpty()) {
            return;
        }
        checkpoint();
        clearSelection();
        WNode last = null;
        Map<UUID, UUID> replacements = new HashMap<>();
        for (WNode source : selected) {
            WNode copy = createDuplicateNode(source, source.getX() + 24, source.getY() + 24);
            if (copy != null) {
                graph.addNode(copy);
                copy.setSelected(true);
                replacements.put(source.getId(), copy.getId());
                last = copy;
            }
        }
        List<WConnection> internalConnections = graph.getConnections().stream()
                .filter(connection -> replacements.containsKey(connection.sourceNode())
                        && replacements.containsKey(connection.targetNode()))
                .toList();
        for (WConnection connection : internalConnections) {
            int[] xs = connection.waypointXs().clone();
            int[] ys = connection.waypointYs().clone();
            for (int index = 0; index < Math.min(xs.length, ys.length); index++) {
                xs[index] += 24;
                ys[index] += 24;
            }
            graph.connect(new WConnection(
                    replacements.get(connection.sourceNode()),
                    connection.sourcePin(),
                    replacements.get(connection.targetNode()),
                    connection.targetPin(),
                    xs,
                    ys,
                    connection.sourcePortKey(),
                    connection.targetPortKey()));
        }
        selectedNode = last;
    }

    protected final void unlinkSelectedNodes() {
        Set<UUID> ids = new HashSet<>();
        selectedNodes().forEach(node -> ids.add(node.getId()));
        if (!ids.isEmpty()) {
            checkpoint();
            graph.disconnectNodes(ids);
        }
    }

    protected final void removeSelectedNodes() {
        List<WNode> selected = selectedNodes();
        if (selected.isEmpty()) {
            return;
        }
        checkpoint();
        selected.forEach(graph::removeNode);
        selectedNode = null;
    }

    /** Aligns the selected nodes (frames excluded); requires at least two movable nodes. */
    protected final boolean alignSelectedNodes(CanvasLayoutOps.Align align) {
        return mutateLayout(layoutSelection(), nodes -> CanvasLayoutOps.align(nodes, align));
    }

    /** Spaces the selected nodes evenly (frames excluded); requires at least three nodes. */
    protected final boolean distributeSelectedNodes(boolean horizontal) {
        return mutateLayout(layoutSelection(), nodes -> CanvasLayoutOps.distribute(nodes, horizontal));
    }

    /**
     * Topologically arranges the selection, or the whole canvas when fewer than two nodes are
     * selected. Frames and comments stay where they are.
     */
    protected final boolean autoLayoutCanvasNodes() {
        List<WNode> source = selectedNodes().size() >= 2 ? selectedNodes() : graph.getNodes();
        return mutateLayout(
                source.stream().filter(WNode::participatesInLayout).toList(),
                nodes -> CanvasLayoutOps.autoLayout(nodes, 60, 24));
    }

    private List<WNode> layoutSelection() {
        return selectedNodes().stream().filter(WNode::participatesInLayout).toList();
    }

    private boolean mutateLayout(List<WNode> nodes, java.util.function.Consumer<List<WNode>> operation) {
        if (nodes.size() < 2) {
            return false;
        }
        checkpoint();
        Map<UUID, int[]> before = new HashMap<>();
        for (WNode node : nodes) {
            before.put(node.getId(), new int[] {node.getX(), node.getY()});
        }
        operation.accept(nodes);
        for (WNode node : nodes) {
            int[] previous = before.get(node.getId());
            int deltaX = node.getX() - previous[0];
            int deltaY = node.getY() - previous[1];
            if (deltaX != 0 || deltaY != 0) {
                graph.shiftWaypointsForConnectionsTouching(List.of(node.getId()), deltaX, deltaY);
            }
        }
        wires.invalidate();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0
                ? 0.016f
                : (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        viewport.advance(delta, width, height);
        wires.advanceAnimation(delta);
        graphics.drawManaged(() -> renderCanvas(graphics, mouseX, mouseY, partialTick));
        if (itemPickerOpen) {
            renderItemPicker(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCanvas(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.fill(0, 0, width, height, ComputedEditorTheme.BACKGROUND_PRIMARY);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, height / 2f, 0);
        graphics.pose().scale(viewport.zoom(), viewport.zoom(), 1);
        graphics.pose().translate(
                -width / 2f + viewport.panX(),
                -height / 2f + viewport.panY(),
                0);
        drawGrid(graphics);
        int graphMouseX = screenToGraphX(mouseX);
        int graphMouseY = screenToGraphY(mouseY);
        boolean mouseInsideCanvas =
                mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height;
        if (mouseInsideCanvas
                && draggingConnection < 0
                && linkingNode == null
                && !panning) {
            updateWireHover(graphMouseX, graphMouseY);
        } else {
            wires.clearHover();
        }
        wires.render(
                graphics,
                graph,
                viewport(),
                viewport.zoom(),
                revision,
                geometryMoving(),
                EditorDetailLevel.FULL);
        if (linkingNode != null
                && linkingPin >= 0
                && linkingPin < linkingNode.getOutputs().size()) {
            wires.renderCurve(
                    graphics,
                    linkingNode.getX() + linkingNode.getWidth(),
                    linkingNode.getY() + 18 + linkingPin * 12,
                    graphMouseX,
                    graphMouseY,
                    0xAAFFFFFF,
                    1.5f);
        }
        List<WNode> drawNodes = new ArrayList<>(graph.getNodes());
        drawNodes.sort(Comparator.comparingInt(WNode::renderLayer)
                .thenComparing(WNode::isSelected)
                .thenComparingInt(WNode::getY)
                .thenComparingInt(WNode::getX)
                .thenComparing(WNode::getId));
        drawNodes.forEach(node -> node.render(graphics, graphMouseX, graphMouseY, partialTick));
        if (selecting) {
            int left = Math.min(selectionStartX, selectionEndX);
            int top = Math.min(selectionStartY, selectionEndY);
            int right = Math.max(selectionStartX, selectionEndX);
            int bottom = Math.max(selectionStartY, selectionEndY);
            graphics.fill(left, top, right, bottom, 0x2233AAFF);
            graphics.renderOutline(left, top, right - left, bottom - top, 0xFF77CCFF);
        }
        graphics.pose().popPose();
        if (!saveFailure.isEmpty()) {
            graphics.fill(4, height - 17, width - 4, height - 3, 0xDD321818);
            graphics.drawString(font, saveFailure, 8, height - 14, 0xFFFF9999, false);
        }
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // The editor paints an opaque canvas before Screen#render is invoked. Letting
        // the vanilla background pass run here would blur that canvas every frame.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (itemPickerOpen) {
            return handleItemPickerClick(mouseX, mouseY, button);
        }
        int graphX = screenToGraphX(mouseX);
        int graphY = screenToGraphY(mouseY);
        if (selectedNode != null && selectedNode.hasFocusedElement()) {
            boolean handled = recordInteraction(() -> selectedNode.mouseClicked(
                    graphX - selectedNode.getX(),
                    graphY - selectedNode.getY(),
                    button));
            if (handled) {
                return true;
            }
        }
        if (button == 1) {
            rightPressX = (int) mouseX;
            rightPressY = (int) mouseY;
            rightPressAt = net.minecraft.Util.getMillis();
            rightDragged = false;
            panLastX = mouseX;
            panLastY = mouseY;
            panLastNanos = System.nanoTime();
            return true;
        }
        if (button == 2) {
            panning = true;
            viewport.beginPan();
            panLastX = mouseX;
            panLastY = mouseY;
            panLastNanos = System.nanoTime();
            return true;
        }
        if (button != 0) {
            return false;
        }
        updateWireHover(graphX, graphY);
        WireEditorController.Hover hover = wires.hover();
        if (Screen.hasAltDown()) {
            if (hover.is(WireEditorController.HoverKind.WAYPOINT)) {
                checkpoint();
                removeWaypoint(hover.connectionIndex(), hover.waypointIndex());
                return true;
            }
            if (hover.is(WireEditorController.HoverKind.INSERT_GHOST)
                    || hover.is(WireEditorController.HoverKind.CURVE_ONLY)) {
                checkpoint();
                graph.getConnections().remove(hover.connectionIndex());
                graph.updateTopology();
                wires.invalidate();
                return true;
            }
        }
        if (hover.is(WireEditorController.HoverKind.INSERT_GHOST)) {
            checkpoint();
            insertWaypoint(hover);
            return true;
        }
        if (hover.is(WireEditorController.HoverKind.WAYPOINT)) {
            checkpoint();
            draggingConnection = hover.connectionIndex();
            draggingWaypoint = hover.waypointIndex();
            return true;
        }
        WNode node = topNodeAt(graphX, graphY);
        if (node != null) {
            int output = node.getPinAt(graphX - node.getX(), graphY - node.getY(), false);
            if (output >= 0) {
                linkingNode = node;
                linkingPin = output;
                return true;
            }
            if (!Screen.hasShiftDown() && !node.isSelected()) {
                clearSelection();
            }
            node.setSelected(true);
            selectedNode = node;
            double localX = graphX - node.getX();
            double localY = graphY - node.getY();
            if (node.hasInteractiveElementAt(localX, localY)
                    && recordInteraction(() -> node.mouseClicked(localX, localY, button))) {
                return true;
            }
            checkpoint();
            draggingNode = node;
            dragOffsetX = graphX - node.getX();
            dragOffsetY = graphY - node.getY();
            return true;
        }
        if (!Screen.hasShiftDown()) {
            clearSelection();
        }
        selecting = true;
        selectionStartX = graphX;
        selectionStartY = graphY;
        selectionEndX = graphX;
        selectionEndY = graphY;
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        if (selectedNode != null
                && selectedNode.hasFocusedElement()
                && recordInteraction(() -> selectedNode.mouseDragged(
                        screenToGraphX(mouseX) - selectedNode.getX(),
                        screenToGraphY(mouseY) - selectedNode.getY(),
                        button,
                        dragX / viewport.zoom(),
                        dragY / viewport.zoom()))) {
            return true;
        }
        if (button == 1 && rightPressX >= 0) {
            if (!rightDragged) {
                viewport.beginPan();
            }
            rightDragged = true;
            panBy(mouseX, mouseY);
            return true;
        }
        if (button == 2 && panning) {
            panBy(mouseX, mouseY);
            return true;
        }
        int graphX = screenToGraphX(mouseX);
        int graphY = screenToGraphY(mouseY);
        if (draggingConnection >= 0
                && draggingConnection < graph.getConnections().size()
                && draggingWaypoint >= 0) {
            WConnection connection = graph.getConnections().get(draggingConnection);
            int[] xs = connection.waypointXs();
            int[] ys = connection.waypointYs();
            if (draggingWaypoint < xs.length) {
                xs[draggingWaypoint] = graphX;
                ys[draggingWaypoint] = graphY;
                graph.getConnections().set(
                        draggingConnection,
                        connection.withWaypoints(xs, ys));
                graph.markConnectionGeometryChanged();
                wires.invalidateHoverCache();
            }
            return true;
        }
        if (selecting) {
            selectionEndX = graphX;
            selectionEndY = graphY;
            return true;
        }
        if (draggingNode != null) {
            int targetX = graphX - (int) dragOffsetX;
            int targetY = graphY - (int) dragOffsetY;
            int deltaX = targetX - draggingNode.getX();
            int deltaY = targetY - draggingNode.getY();
            if (deltaX != 0 || deltaY != 0) {
                List<WNode> moving = new ArrayList<>(selectedNodes());
                if (draggingNode.movesContainedNodes()) {
                    for (WNode node : graph.getNodes()) {
                        if (moving.contains(node) || node == draggingNode) {
                            continue;
                        }
                        node.ensureLayoutUpToDate();
                        if (node.getX() >= draggingNode.getX()
                                && node.getX() + node.getWidth()
                                        <= draggingNode.getX() + draggingNode.getWidth()
                                && node.getY() >= draggingNode.getY()
                                && node.getY() + node.getHeight()
                                        <= draggingNode.getY() + draggingNode.getHeight()) {
                            moving.add(node);
                        }
                    }
                }
                List<UUID> moved = moving.stream().map(WNode::getId).toList();
                graph.shiftWaypointsForConnectionsTouching(moved, deltaX, deltaY);
                moving.forEach(node ->
                        node.setPos(node.getX() + deltaX, node.getY() + deltaY));
                wires.invalidate();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int graphX = screenToGraphX(mouseX);
        int graphY = screenToGraphY(mouseY);
        if (button == 1 && rightPressX >= 0) {
            boolean contextClick = !rightDragged && PointerGestureClassifier.isContextClick(
                    rightPressX,
                    rightPressY,
                    rightPressAt,
                    (int) mouseX,
                    (int) mouseY,
                    net.minecraft.Util.getMillis());
            if (contextClick) {
                updateWireHover(graphX, graphY);
                WireEditorController.Hover hover = wires.hover();
                if (hover.connectionIndex() >= 0
                        && hover.connectionIndex() < graph.getConnections().size()) {
                    checkpoint();
                    graph.getConnections().remove(hover.connectionIndex());
                    graph.updateTopology();
                    wires.invalidate();
                } else {
                    openNodeExplorer((int) mouseX, (int) mouseY, graphX, graphY);
                }
            }
            rightPressX = -1;
            rightPressY = -1;
            rightDragged = false;
            viewport.endPan();
            return true;
        }
        if (button == 2) {
            panning = false;
            viewport.endPan();
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (selecting) {
            selectRectangle();
        }
        if (linkingNode != null) {
            WNode target = topNodeAt(graphX, graphY);
            int input = target == null
                    ? -1
                    : target.getPinAt(graphX - target.getX(), graphY - target.getY(), true);
            if (target != null && input >= 0 && compatible(linkingNode, linkingPin, target, input)) {
                checkpoint();
                graph.connect(linkingNode.getId(), linkingPin, target.getId(), input);
                wires.invalidate();
            } else {
                openNodeExplorer((int) mouseX, (int) mouseY, graphX, graphY);
            }
        }
        if (selectedNode != null) {
            selectedNode.mouseReleased(graphX, graphY, button);
        }
        selecting = false;
        draggingNode = null;
        linkingNode = null;
        linkingPin = -1;
        draggingConnection = -1;
        draggingWaypoint = -1;
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        if (itemPickerOpen) {
            itemPickerScroll = Mth.clamp(
                    itemPickerScroll - (int) Math.signum(scrollY) * 3,
                    0,
                    Math.max(0, filteredItemPickerItems().size() - ITEM_PICKER_ROWS));
            return true;
        }
        if (scrollY == 0) {
            return false;
        }
        viewport.addZoomImpulse(scrollY * ZOOM_STEP, mouseX, mouseY);
        wires.invalidate();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (itemPickerOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeItemPicker();
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !itemPickerQuery.isEmpty()) {
                itemPickerQuery = itemPickerQuery.substring(0, itemPickerQuery.length() - 1);
                itemPickerScroll = 0;
            }
            return true;
        }
        if (selectedNode != null
                && selectedNode.hasFocusedElement()
                && recordInteraction(() -> selectedNode.keyPressed(keyCode, scanCode, modifiers))) {
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
            graph.getNodes().forEach(node -> node.setSelected(true));
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            if (copySelectedNodesToClipboard()) {
                return true;
            }
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_X) {
            if (copySelectedNodesToClipboard()) {
                removeSelectedNodes();
                return true;
            }
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            if (pasteNodesFromClipboard(screenToGraphX(mouseX), screenToGraphY(mouseY))) {
                return true;
            }
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_D) {
            cloneSelectedNodes();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_U) {
            unlinkSelectedNodes();
            return true;
        }
        if (Screen.hasShiftDown() && keyCode == GLFW.GLFW_KEY_A) {
            openNodeExplorer(
                    mouseX,
                    mouseY,
                    screenToGraphX(mouseX),
                    screenToGraphY(mouseY));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            removeSelectedNodes();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (itemPickerOpen) {
            if (!Character.isISOControl(codePoint) && itemPickerQuery.length() < 80) {
                itemPickerQuery += Character.toLowerCase(codePoint);
                itemPickerScroll = 0;
            }
            return true;
        }
        if (selectedNode != null
                && selectedNode.hasFocusedElement()
                && recordInteraction(() -> selectedNode.charTyped(codePoint, modifiers))) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        persistEditorViewport(EDITOR_VIEWPORT_ROOT);
        if (activeScreen == this) {
            activeScreen = null;
        }
        super.removed();
    }

    @Override
    public void added() {
        activeScreen = this;
        super.added();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected final boolean isEditorModalOpen() {
        return itemPickerOpen;
    }

    protected final void checkpoint() {
        revision++;
        history.execute(new Snapshot(graph.save()));
        wires.invalidate();
    }

    private boolean recordInteraction(BooleanSupplier interaction) {
        CompoundTag before = graph.save();
        if (!interaction.getAsBoolean()) {
            return false;
        }
        revision++;
        history.execute(new Snapshot(before));
        wires.invalidate();
        return true;
    }

    private void openItemPicker(Consumer<ItemStack> callback) {
        itemPickerCallback = callback;
        itemPickerQuery = "";
        itemPickerScroll = 0;
        itemPickerItems.clear();
        BuiltInRegistries.ITEM.stream()
                .map(ItemStack::new)
                .sorted(java.util.Comparator.comparing(
                        stack -> stack.getHoverName().getString(),
                        String.CASE_INSENSITIVE_ORDER))
                .forEach(itemPickerItems::add);
        itemPickerOpen = true;
    }

    private void closeItemPicker() {
        itemPickerOpen = false;
        itemPickerCallback = null;
        itemPickerItems.clear();
    }

    private List<ItemStack> filteredItemPickerItems() {
        if (itemPickerQuery.isBlank()) {
            return itemPickerItems;
        }
        String query = itemPickerQuery.toLowerCase(java.util.Locale.ROOT);
        return itemPickerItems.stream()
                .filter(stack -> stack.getHoverName()
                                .getString()
                                .toLowerCase(java.util.Locale.ROOT)
                                .contains(query)
                        || BuiltInRegistries.ITEM
                                .getKey(stack.getItem())
                                .toString()
                                .contains(query))
                .toList();
    }

    private void renderItemPicker(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        int panelHeight = 28 + ITEM_PICKER_ROWS * ITEM_PICKER_ROW_HEIGHT + 8;
        int x = (width - ITEM_PICKER_WIDTH) / 2;
        int y = (height - panelHeight) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 7000);
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(
                x,
                y,
                x + ITEM_PICKER_WIDTH,
                y + panelHeight,
                ComputedEditorTheme.MENU_BACKGROUND);
        graphics.renderOutline(
                x,
                y,
                ITEM_PICKER_WIDTH,
                panelHeight,
                ComputedEditorTheme.BORDER_MENU);
        graphics.fill(
                x + 6,
                y + 6,
                x + ITEM_PICKER_WIDTH - 6,
                y + 22,
                ComputedEditorTheme.BACKGROUND_INPUT);
        graphics.drawString(
                font,
                itemPickerQuery.isEmpty() ? "Search items..." : itemPickerQuery + "_",
                x + 10,
                y + 10,
                itemPickerQuery.isEmpty()
                        ? ComputedEditorTheme.TEXT_TERTIARY
                        : ComputedEditorTheme.TEXT_PRIMARY,
                false);
        List<ItemStack> items = filteredItemPickerItems();
        itemPickerScroll = Mth.clamp(
                itemPickerScroll,
                0,
                Math.max(0, items.size() - ITEM_PICKER_ROWS));
        for (int row = 0; row < ITEM_PICKER_ROWS && itemPickerScroll + row < items.size(); row++) {
            ItemStack stack = items.get(itemPickerScroll + row);
            int rowY = y + 28 + row * ITEM_PICKER_ROW_HEIGHT;
            boolean hovered = mouseX >= x + 4
                    && mouseX < x + ITEM_PICKER_WIDTH - 4
                    && mouseY >= rowY
                    && mouseY < rowY + ITEM_PICKER_ROW_HEIGHT;
            if (hovered) {
                graphics.fill(
                        x + 4,
                        rowY,
                        x + ITEM_PICKER_WIDTH - 4,
                        rowY + ITEM_PICKER_ROW_HEIGHT,
                        ComputedEditorTheme.MENU_HOVER);
            }
            graphics.renderItem(stack, x + 7, rowY + 2);
            graphics.drawString(
                    font,
                    stack.getHoverName(),
                    x + 29,
                    rowY + 6,
                    ComputedEditorTheme.TEXT_PRIMARY,
                    false);
        }
        graphics.pose().popPose();
    }

    private boolean handleItemPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            closeItemPicker();
            return true;
        }
        int panelHeight = 28 + ITEM_PICKER_ROWS * ITEM_PICKER_ROW_HEIGHT + 8;
        int x = (width - ITEM_PICKER_WIDTH) / 2;
        int y = (height - panelHeight) / 2;
        if (mouseX < x
                || mouseX >= x + ITEM_PICKER_WIDTH
                || mouseY < y
                || mouseY >= y + panelHeight) {
            closeItemPicker();
            return true;
        }
        int row = ((int) mouseY - y - 28) / ITEM_PICKER_ROW_HEIGHT;
        List<ItemStack> items = filteredItemPickerItems();
        int index = itemPickerScroll + row;
        if (row >= 0 && row < ITEM_PICKER_ROWS && index >= 0 && index < items.size()) {
            CompoundTag before = graph.save();
            Consumer<ItemStack> callback = itemPickerCallback;
            ItemStack selected = items.get(index).copyWithCount(1);
            closeItemPicker();
            if (callback != null) {
                callback.accept(selected);
                revision++;
                history.execute(new Snapshot(before));
                wires.invalidate();
            }
        }
        return true;
    }

    private void undo() {
        if (history.undo()) {
            revision++;
            wires.invalidate();
        }
    }

    private void redo() {
        if (history.redo()) {
            revision++;
            wires.invalidate();
        }
    }

    private void clearSelection() {
        graph.getNodes().forEach(node -> node.setSelected(false));
        selectedNode = null;
    }

    protected final List<WNode> selectedNodes() {
        return graph.getNodes().stream().filter(WNode::isSelected).toList();
    }

    private WNode topNodeAt(int graphX, int graphY) {
        List<WNode> nodes = graph.getNodes();
        for (int index = nodes.size() - 1; index >= 0; index--) {
            WNode node = nodes.get(index);
            node.ensureLayoutUpToDate();
            if (graphX >= node.getX() - 5
                    && graphX <= node.getX() + node.getWidth() + 5
                    && graphY >= node.getY()
                    && graphY <= node.getY() + node.getHeight()) {
                return node;
            }
        }
        return null;
    }

    private void selectRectangle() {
        int left = Math.min(selectionStartX, selectionEndX);
        int top = Math.min(selectionStartY, selectionEndY);
        int right = Math.max(selectionStartX, selectionEndX);
        int bottom = Math.max(selectionStartY, selectionEndY);
        for (WNode node : graph.getNodes()) {
            node.ensureLayoutUpToDate();
            if (node.getX() + node.getWidth() >= left
                    && node.getX() <= right
                    && node.getY() + node.getHeight() >= top
                    && node.getY() <= bottom) {
                node.setSelected(true);
            }
        }
    }

    private void panBy(double mouseX, double mouseY) {
        long now = System.nanoTime();
        double elapsed = panLastNanos == 0
                ? 1.0 / 60.0
                : (now - panLastNanos) / 1_000_000_000.0;
        viewport.dragPan(mouseX - panLastX, mouseY - panLastY, elapsed);
        panLastX = mouseX;
        panLastY = mouseY;
        panLastNanos = now;
        wires.invalidateHoverCache();
    }

    private void updateWireHover(int graphX, int graphY) {
        wires.updateHover(
                graph,
                graphX,
                graphY,
                viewport.zoom(),
                revision,
                geometryMoving(),
                point -> blocksWire(point));
    }

    private boolean blocksWire(GraphPoint point) {
        return topNodeAt((int) point.x(), (int) point.y()) != null;
    }

    private boolean geometryMoving() {
        return draggingNode != null || draggingConnection >= 0;
    }

    private GraphRect viewport() {
        double left = screenToGraphX(0) - 36 / viewport.zoom();
        double top = screenToGraphY(0) - 36 / viewport.zoom();
        double right = screenToGraphX(width) + 36 / viewport.zoom();
        double bottom = screenToGraphY(height) + 36 / viewport.zoom();
        return new GraphRect(left, top, right, bottom);
    }

    private int screenToGraphX(double screenX) {
        return (int) viewport.graphX(screenX, width);
    }

    private int screenToGraphY(double screenY) {
        return (int) viewport.graphY(screenY, height);
    }

    private void drawGrid(GuiGraphics graphics) {
        int left = screenToGraphX(0) - GRID_SPACING;
        int right = screenToGraphX(width) + GRID_SPACING;
        int top = screenToGraphY(0) - GRID_SPACING;
        int bottom = screenToGraphY(height) + GRID_SPACING;
        int startX = Math.floorDiv(left, GRID_SPACING) * GRID_SPACING;
        int startY = Math.floorDiv(top, GRID_SPACING) * GRID_SPACING;
        for (int x = startX; x <= right; x += GRID_SPACING) {
            graphics.fill(x, top, x + 1, bottom, 0x142F3940);
        }
        for (int y = startY; y <= bottom; y += GRID_SPACING) {
            graphics.fill(left, y, right, y + 1, 0x142F3940);
        }
    }

    private static boolean compatible(WNode source, int output, WNode target, int input) {
        if (output < 0
                || output >= source.getOutputs().size()
                || input < 0
                || input >= target.getInputs().size()) {
            return false;
        }
        WPin.DataType sourceType = source.getOutputs().get(output).getDataType();
        WPin.DataType targetType = target.getInputs().get(input).getDataType();
        return sourceType == targetType;
    }

    private void insertWaypoint(WireEditorController.Hover hover) {
        WConnection connection = graph.getConnections().get(hover.connectionIndex());
        int[] oldX = connection.waypointXs();
        int[] oldY = connection.waypointYs();
        int index = Mth.clamp(hover.insertionSegment(), 0, oldX.length);
        int[] nextX = new int[oldX.length + 1];
        int[] nextY = new int[oldY.length + 1];
        System.arraycopy(oldX, 0, nextX, 0, index);
        System.arraycopy(oldY, 0, nextY, 0, index);
        nextX[index] = hover.insertionX();
        nextY[index] = hover.insertionY();
        System.arraycopy(oldX, index, nextX, index + 1, oldX.length - index);
        System.arraycopy(oldY, index, nextY, index + 1, oldY.length - index);
        graph.getConnections().set(
                hover.connectionIndex(),
                connection.withWaypoints(nextX, nextY));
        graph.markConnectionGeometryChanged();
        wires.invalidate();
    }

    private void removeWaypoint(int connectionIndex, int waypointIndex) {
        WConnection connection = graph.getConnections().get(connectionIndex);
        int[] oldX = connection.waypointXs();
        int[] oldY = connection.waypointYs();
        if (waypointIndex < 0 || waypointIndex >= oldX.length) {
            return;
        }
        int[] nextX = new int[oldX.length - 1];
        int[] nextY = new int[oldY.length - 1];
        for (int source = 0, target = 0; source < oldX.length; source++) {
            if (source != waypointIndex) {
                nextX[target] = oldX[source];
                nextY[target++] = oldY[source];
            }
        }
        graph.getConnections().set(
                connectionIndex,
                connection.withWaypoints(nextX, nextY));
        graph.markConnectionGeometryChanged();
        wires.invalidate();
    }

    private final class Snapshot implements EditorCommand<WNodeScreen> {
        private final CompoundTag before;
        private CompoundTag after;

        private Snapshot(CompoundTag before) {
            this.before = before.copy();
        }

        @Override
        public void execute(WNodeScreen screen) {}

        @Override
        public void undo(WNodeScreen screen) {
            after = screen.graph.save();
            screen.graph.load(before.copy());
        }

        @Override
        public void redo(WNodeScreen screen) {
            screen.graph.load(after.copy());
        }

        @Override
        public String description() {
            return "Canvas edit";
        }
    }
}
