package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.client.editor.canvas.CanvasLayoutOps;
import dev.propulsionteam.computed.client.editor.canvas.CommentNote;
import dev.propulsionteam.computed.client.editor.canvas.FrameNote;
import dev.propulsionteam.computed.client.editor.canvas.LuaEditorGraphAdapter;
import dev.propulsionteam.computed.client.editor.canvas.LuaEditorNode;
import dev.propulsionteam.computed.client.editor.explorer.ExplorerNode;
import dev.propulsionteam.computed.client.editor.explorer.ExplorerRow;
import dev.propulsionteam.computed.client.editor.explorer.NodeExplorerModel;
import dev.propulsionteam.computed.client.editor.TextInputHandler;
import dev.propulsionteam.computed.client.editor.lua.LuaNodeStarter;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.items.ProgramDiskItem;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import dev.propulsionteam.computed.network.SaveComputerGraphPayload;
import dev.propulsionteam.computed.network.ComputerEditPolicy;
import dev.propulsionteam.computed.network.PackageListPayload;
import dev.propulsionteam.computed.network.PackageReadPayload;
import dev.propulsionteam.computed.network.PackageWritePayload;
import dev.propulsionteam.computed.network.SetComputerIdPayload;
import dev.propulsionteam.computed.network.SetComputerNamePayload;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
import dev.propulsionteam.computed.persistence.ComputedProgramPackage;
import dev.propulsionteam.computed.persistence.ComputedPackageStorage;
import dev.propulsionteam.computed.persistence.PackageTransferService;
import dev.propulsionteam.computed.persistence.ProgramDiskData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class ComputerEditorScreen extends WNodeScreen {
    private static final int AUTO_SAVE_INTERVAL_TICKS = 20;
    private static final int EXPLORER_WIDTH = 168;
    private static final int EXPLORER_ROW_HEIGHT = 15;
    private static final int CONTEXT_WIDTH = 132;
    private static final int CONTEXT_ROW = 18;
    private static final int CONTEXT_SEPARATOR = 7;
    private static final String ALIGN_MENU_LABEL = "Align  ▸";
    private static final String NEW_LUA_NODE_ACTION = "computed:editor/new_lua_node";
    private static final String COMMAND_NODE_ID = "computed:command";
    private static final int MINIMAP_WIDTH = 62;
    private static final int MINIMAP_HEIGHT = 42;
    private static final int MINIMAP_MARGIN = 6;

    private final BlockPos computerPos;
    private WGraph editorGraph;
    private final Map<Long, ComputedProgramV3> pendingPrograms = new HashMap<>();
    private final Map<Long, Long> pendingHistoryRevisions = new HashMap<>();
    private ComputedProgramV3 baseProgram;
    private int autoSaveCountdown;
    private long serverRevision;
    private long acknowledgedEditorRevision;
    private long acknowledgedHistoryRevision;
    private long inFlightEditorRevision = -1;
    private boolean saveInFlight;
    private boolean saveBlocked;
    private long blockedEditorRevision = -1;
    private long blockedHistoryRevision = -1;
    private NodeExplorerModel explorer;
    private boolean explorerOpen = true;
    private boolean explorerSearchFocused;
    private String explorerSearch = "";
    private final TextInputHandler explorerSearchInput = new TextInputHandler(64);
    private int explorerAnchorX;
    private int explorerAnchorY;
    private int explorerScroll;
    private boolean contextOpen;
    private int contextX;
    private int contextY;
    private boolean contextAlignOpen;
    private ExplorerNode explorerPressNode;
    private double explorerPressX;
    private double explorerPressY;
    private boolean explorerDragging;
    private ExplorerNode selectedExplorerNode;
    private int explorerHorizontalScroll;
    private Modal modal = Modal.NONE;
    private int fsComputerId = -1;
    private String computerName = "";
    private List<PackageTransferService.Entry> packageChoices = List.of();
    private boolean packageLoading;
    private ComputedPackageStorage.Kind importKind = ComputedPackageStorage.Kind.GRAPHS;
    private String importFolder = "";
    private ProgramDiskData diskImport;
    /** Disk content of the computer's drive slot as captured when the editor opened. */
    private ProgramDiskData slotDisk;
    /** Whether the drive slot holds a disk at all (blank disks have no content yet). */
    private boolean slotDiskPresent;
    private final TextInputHandler exportNameInput = new TextInputHandler(160);
    private final TextInputHandler idInput = new TextInputHandler(16);
    private final TextInputHandler nameInput = new TextInputHandler(32);
    private PendingExport pendingExport;
    private int pendingWriteOpId = -1;
    private int writeOpSequence;
    private ExplorerNode contextExplorerNode;
    private int explorerContextX;
    private int explorerContextY;
    private boolean transferMenuOpen;
    private boolean minimapDragging;
    private MinimapTransform minimapDragTransform;
    private double minimapDragOffsetX;
    private double minimapDragOffsetY;
    private Map<String, String> definitionTitles;
    private Map<String, List<WPin.DataType>> definitionInputs;

    private enum Modal {
        NONE,
        IMPORT_GRAPH,
        IMPORT_NODE,
        EXPORT_GRAPH_NAME,
        OVERWRITE_GRAPH,
        OVERWRITE_NODE,
        DELETE_NODE,
        SET_COMPUTER_ID,
        SET_COMPUTER_NAME
    }

    /** Captured export content awaiting the server write result (exists check, overwrite, completion). */
    private record PendingExport(
            ComputedPackageStorage.Kind kind,
            String relativePath,
            byte[] content,
            boolean toDisk,
            boolean toSlot) {}

    /** One row of the canvas context menu; separators are drawn but never clickable. */
    private record ContextEntry(String label, boolean separator, boolean enabled, Runnable action) {}

    public ComputerEditorScreen(
            BlockPos computerPos,
            ComputedProgramV3 program,
            long serverRevision) {
        this(computerPos, program, serverRevision, -1, null, false);
    }

    public ComputerEditorScreen(
            BlockPos computerPos,
            ComputedProgramV3 program,
            long serverRevision,
            int fsComputerId) {
        this(computerPos, program, serverRevision, fsComputerId, null, false);
    }

    public ComputerEditorScreen(
            BlockPos computerPos,
            ComputedProgramV3 program,
            long serverRevision,
            int fsComputerId,
            String computerName,
            ProgramDiskData slotDisk,
            boolean slotDiskPresent) {
        this(computerPos, program, serverRevision, fsComputerId, slotDisk, slotDiskPresent);
        this.computerName = computerName == null ? "" : computerName;
    }

    public ComputerEditorScreen(
            BlockPos computerPos,
            ComputedProgramV3 program,
            long serverRevision,
            int fsComputerId,
            ProgramDiskData slotDisk,
            boolean slotDiskPresent) {
        this(computerPos, program, LuaEditorGraphAdapter.toEditorGraph(program), serverRevision);
        this.fsComputerId = fsComputerId;
        this.slotDisk = slotDisk;
        this.slotDiskPresent = slotDiskPresent;
    }

    private ComputerEditorScreen(
            BlockPos computerPos,
            ComputedProgramV3 program,
            WGraph editorGraph,
            long serverRevision) {
        super(editorGraph);
        this.computerPos = computerPos;
        this.editorGraph = editorGraph;
        this.serverRevision = serverRevision;
        baseProgram = program.withRevision(serverRevision);
        explorer = new NodeExplorerModel(explorerNodes(program));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null) {
            ComputerEditorViewState.load(
                            minecraft.player.getUUID(),
                            minecraft.level.dimension(),
                            computerPos,
                            EDITOR_VIEWPORT_ROOT)
                    .ifPresent(view -> restoreEditorViewport(view.panX(), view.panY(), view.zoom()));
        }
    }

    @Override
    protected boolean minimalCanvasMode() {
        return true;
    }

    @Override
    protected void openNodeExplorer(int screenX, int screenY, int graphX, int graphY) {
        explorerAnchorX = graphX;
        explorerAnchorY = graphY;
        contextAlignOpen = false;
        if (selectNodeAtGraphPoint(graphX, graphY) || hasSelectedNodes()) {
            contextOpen = true;
            contextX = screenX;
            contextY = screenY;
            return;
        }
        contextOpen = true;
        contextX = screenX;
        contextY = screenY;
    }

    @Override
    protected WNode createDuplicateNode(WNode source, int x, int y) {
        if (source instanceof LuaEditorNode luaNode) {
            return LuaEditorGraphAdapter.duplicateEditorNode(luaNode, x, y);
        }
        if (source instanceof CommentNote note) {
            return CommentNote.duplicate(note, x, y);
        }
        if (source instanceof FrameNote note) {
            return FrameNote.duplicate(note, x, y);
        }
        return super.createDuplicateNode(source, x, y);
    }

    @Override
    protected boolean copySelectedNodesToClipboard() {
        var selectedIds = selectedCanvasNodeIds();
        if (selectedIds.isEmpty()) {
            return false;
        }
        try {
            ComputedProgramV3 fragment = ComputedProgramPackage.copySelection(
                    LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, serverRevision),
                    selectedIds);
            minecraft.keyboardHandler.setClipboard(ComputedProgramPackage.exportClipboard(fragment));
        } catch (IllegalArgumentException exception) {
            notify("Could not copy nodes: " + exception.getMessage());
        }
        return true;
    }

    @Override
    protected boolean pasteNodesFromClipboard(int graphX, int graphY) {
        try {
            ComputedProgramV3 current =
                    LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, serverRevision);
            ComputedProgramV3 fragment = ComputedProgramPackage.importClipboard(
                    minecraft.keyboardHandler.getClipboard());
            ComputedProgramPackage.PasteResult pasted =
                    ComputedProgramPackage.pasteSelection(current, fragment, graphX, graphY);
            String typeError =
                    ComputerEditPolicy.computerType(pasted.program(), isCreativeComputer());
            if (typeError != null) {
                throw new IllegalArgumentException(typeError);
            }
            baseProgram = pasted.program();
            editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram);
            replaceCanvasGraph(editorGraph);
            selectCanvasNodes(pasted.pastedNodeIds());
            explorer = new NodeExplorerModel(explorerNodes(baseProgram));
            acknowledgedEditorRevision = -1;
        } catch (IllegalArgumentException exception) {
            notify("Could not paste nodes: " + exception.getMessage());
        }
        return true;
    }

    @Override
    protected void persistEditorViewport(String contextKey) {
        saveEditorViewport();
    }

    @Override
    protected boolean loadEditorViewport(String contextKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        return ComputerEditorViewState.load(
                        minecraft.player.getUUID(),
                        minecraft.level.dimension(),
                        computerPos,
                        contextKey)
                .map(view -> {
                    restoreEditorViewport(view.panX(), view.panY(), view.zoom());
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (--autoSaveCountdown <= 0) {
            autoSaveCountdown = AUTO_SAVE_INTERVAL_TICKS;
            sendDirtyProgram(false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 5000);
        if (explorerOpen) {
            renderExplorer(graphics, mouseX, mouseY);
        }
        renderControls(graphics, mouseX, mouseY);
        if (modal == Modal.NONE) {
            renderMinimap(graphics);
        }
        if (contextOpen) {
            renderContext(graphics, mouseX, mouseY);
        }
        if (contextExplorerNode != null) {
            renderExplorerContext(graphics, mouseX, mouseY);
        }
        if (transferMenuOpen) {
            renderTransferMenu(graphics, mouseX, mouseY);
        }
        if (modal != Modal.NONE) {
            renderModal(graphics, mouseX, mouseY);
        }
        if (explorerDragging && explorerPressNode != null) {
            graphics.fill(mouseX + 5, mouseY + 5, mouseX + 17, mouseY + 17, ComputedEditorTheme.ACCENT);
            graphics.drawString(
                    font,
                    explorerPressNode.title(),
                    mouseX + 21,
                    mouseY + 7,
                    ComputedEditorTheme.TEXT_HEADER,
                    true);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modal != Modal.NONE) return handleModalClick(mouseX, mouseY, button);
        if (isEditorModalOpen()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (explorerSearchFocused
                && !contains(mouseX, mouseY, 6, 27, EXPLORER_WIDTH - 12, 16)) {
            explorerSearchFocused = false;
            explorerSearchInput.blur();
        }
        if (button == 0 && handleControlsClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && handleMinimapClick(mouseX, mouseY)) {
            return true;
        }
        if (transferMenuOpen) {
            return handleTransferMenuClick(mouseX, mouseY, button);
        }
        if (contextOpen) {
            return handleContextClick(mouseX, mouseY, button);
        }
        if (contextExplorerNode != null) {
            return handleExplorerContextClick(mouseX, mouseY, button);
        }
        if (explorerOpen && mouseX < EXPLORER_WIDTH) {
            return handleExplorerClick(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        if (modal != Modal.NONE) {
            return true;
        }
        if (button == 0 && minimapDragging) {
            panMinimapViewport(mouseX, mouseY);
            return true;
        }
        if (button == 0 && explorerOpen && explorerSearchFocused) {
            explorerSearchInput.dragTo(searchColumn(mouseX));
            return true;
        }
        if (button == 0 && explorerPressNode != null) {
            if (Math.hypot(mouseX - explorerPressX, mouseY - explorerPressY) >= 4) {
                explorerDragging = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modal != Modal.NONE) {
            return true;
        }
        if (button == 0 && minimapDragging) {
            minimapDragging = false;
            minimapDragTransform = null;
            return true;
        }
        if (button == 0 && explorerPressNode != null) {
            ExplorerNode released = explorerPressNode;
            boolean dragged = explorerDragging;
            explorerPressNode = null;
            explorerDragging = false;
            if (dragged && mouseX >= EXPLORER_WIDTH) {
                explorerAnchorX = editorGraphX(mouseX);
                explorerAnchorY = editorGraphY(mouseY);
                place(released);
            } else if (!dragged) {
                explorerAnchorX = editorGraphX(width / 2.0);
                explorerAnchorY = editorGraphY(height / 2.0);
                place(released);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        if (modal != Modal.NONE) {
            return true;
        }
        if (isEditorModalOpen()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (explorerOpen && mouseX < EXPLORER_WIDTH) {
            if (hasShiftDown()) {
                explorerHorizontalScroll = net.minecraft.util.Mth.clamp(explorerHorizontalScroll - (int) Math.signum(scrollY) * 12, 0, explorerMaxHorizontalScroll());
                return true;
            }
            int visible = Math.max(1, (height - 49) / EXPLORER_ROW_HEIGHT);
            explorerScroll = net.minecraft.util.Mth.clamp(
                    explorerScroll - (int) Math.signum(scrollY),
                    0,
                    Math.max(0, explorer.visibleRows().size() - visible));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modal != Modal.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeModal(); return true; }
            if (modal == Modal.EXPORT_GRAPH_NAME && exportNameInput.keyPressed(keyCode)) return true;
            if (modal == Modal.EXPORT_GRAPH_NAME && keyCode == GLFW.GLFW_KEY_ENTER) { beginGraphExport(false); return true; }
            if (modal == Modal.SET_COMPUTER_ID && idInput.keyPressed(keyCode)) return true;
            if (modal == Modal.SET_COMPUTER_ID && keyCode == GLFW.GLFW_KEY_ENTER) { confirmSetComputerId(); return true; }
            if (modal == Modal.SET_COMPUTER_NAME && nameInput.keyPressed(keyCode)) return true;
            if (modal == Modal.SET_COMPUTER_NAME && keyCode == GLFW.GLFW_KEY_ENTER) { confirmSetComputerName(); return true; }
            return true;
        }
        if (isEditorModalOpen()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (contextOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            contextOpen = false;
            contextAlignOpen = false;
            return true;
        }
        if (transferMenuOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            transferMenuOpen = false;
            return true;
        }
        if (explorerOpen && explorerSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                explorerSearchFocused = false;
                explorerSearchInput.blur();
                return true;
            }
            if (explorerSearchInput.keyPressed(keyCode)) {
                updateExplorerSearch();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modal == Modal.EXPORT_GRAPH_NAME && exportNameInput.charTyped(codePoint)) return true;
        if (modal == Modal.SET_COMPUTER_ID && idInput.charTyped(codePoint)) return true;
        if (modal == Modal.SET_COMPUTER_NAME && nameInput.charTyped(codePoint)) return true;
        if (isEditorModalOpen()) {
            return super.charTyped(codePoint, modifiers);
        }
        if (explorerOpen
                && explorerSearchFocused
                && explorerSearchInput.charTyped(codePoint)) {
            updateExplorerSearch();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    public void onServerSaveResult(
            boolean accepted,
            long newServerRevision,
            long savedEditorRevision,
            String message) {
        if (accepted || newServerRevision >= 0) {
            serverRevision = newServerRevision;
        }
        long savedHistoryRevision = pendingHistoryRevisions.getOrDefault(savedEditorRevision, -1L);
        ComputedProgramV3 acknowledged = pendingPrograms.remove(savedEditorRevision);
        pendingHistoryRevisions.remove(savedEditorRevision);
        saveInFlight = !pendingPrograms.isEmpty();
        if (savedEditorRevision == inFlightEditorRevision) {
            inFlightEditorRevision = -1;
        }
        if (accepted) {
            if (acknowledged != null) {
                baseProgram = acknowledged.withRevision(newServerRevision);
            }
            acknowledgedEditorRevision = Math.max(acknowledgedEditorRevision, savedEditorRevision);
            if (savedHistoryRevision >= 0) {
                acknowledgedHistoryRevision = savedHistoryRevision;
            }
            acknowledgeEditorHistorySaved(savedEditorRevision);
            clearEditorSaveFailureDiagnostic();
            saveBlocked = false;
            return;
        }
        saveBlocked = true;
        blockedEditorRevision = editorRevision();
        blockedHistoryRevision = editorHistoryRevision();
        setEditorSaveFailureDiagnostic("Save rejected: " + message);
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("Computed graph was not saved: " + message),
                    false);
        }
    }

    public boolean editsComputer(BlockPos pos) {
        return computerPos.equals(pos);
    }

    String definitionHash(String id) {
        LuaDefinitionSource source = LuaEditorGraphAdapter.definitions(baseProgram).get(id);
        return source == null ? "" : source.hash();
    }

    void applyLuaSource(
            String source,
            String id,
            boolean creationMode,
            int placementX,
            int placementY) {
        LuaDefinitionSource replacement = LuaDefinitionSource.embedded(1, id, source);
        ComputedProgramV3 current = LuaEditorGraphAdapter.fromEditorGraph(
                editorGraph,
                baseProgram,
                serverRevision);
        baseProgram = creationMode
                ? LuaEditorGraphAdapter.addDefinitionAndNode(
                        current,
                        replacement,
                        placementX,
                        placementY)
                : LuaEditorGraphAdapter.replaceDefinition(current, replacement);
        editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram);
        replaceCanvasGraph(editorGraph);
        explorer = new NodeExplorerModel(explorerNodes(baseProgram));
        explorerSearch = "";
        explorerSearchInput.setText("");
        explorer.search("");
        explorerScroll = 0;
        acknowledgedEditorRevision = -1;
    }

    @Override
    public void removed() {
        saveEditorViewport();
        super.removed();
        sendDirtyProgram(true);
    }

    private ComputedProgramV3 programForNetwork(long revision) {
        return LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, revision);
    }

    private void sendDirtyProgram(boolean closing) {
        long localRevision = editorRevision();
        long historyRevision = editorHistoryRevision();
        if (saveBlocked) {
            if (localRevision == blockedEditorRevision && historyRevision == blockedHistoryRevision) {
                return;
            }
            saveBlocked = false;
            clearEditorSaveFailureDiagnostic();
        }
        if (localRevision == acknowledgedEditorRevision
                && historyRevision == acknowledgedHistoryRevision
                && !editorHistoryDirty()) {
            return;
        }
        if (saveInFlight && !closing) {
            return;
        }
        long expectedRevision = serverRevision + (saveInFlight && closing ? 1 : 0);
        ComputedProgramV3 outgoing = programForNetwork(expectedRevision);
        CompoundTag envelope = new CompoundTag();
        envelope.put(ComputerBlockEntity.PROGRAM_TAG, ProgramV3Codec.encode(outgoing));
        PacketDistributor.sendToServer(new SaveComputerGraphPayload(
                computerPos,
                expectedRevision,
                localRevision,
                envelope));
        saveInFlight = true;
        pendingPrograms.put(localRevision, outgoing);
        pendingHistoryRevisions.put(localRevision, historyRevision);
        inFlightEditorRevision = localRevision;
        saveEditorViewport();
    }

    private void saveEditorViewport() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null) {
            ComputerEditorViewState.save(
                    minecraft.player.getUUID(),
                    minecraft.level.dimension(),
                    computerPos,
                    EDITOR_VIEWPORT_ROOT,
                    editorPanX(),
                    editorPanY(),
                    editorZoom());
        }
    }

    private void renderControls(GuiGraphics graphics, int mouseX, int mouseY) {
        drawButton(graphics, 6, 6, 22, 18, explorerOpen ? "«" : "»", mouseX, mouseY);
        drawButton(graphics, width - 288, 6, 24, 18, "Undo", mouseX, mouseY);
        drawButton(graphics, width - 262, 6, 24, 18, "Redo", mouseX, mouseY);
        drawButton(graphics, width - 236, 6, 24, 18, "Fit", mouseX, mouseY);
        drawButton(graphics, width - 212, 6, 48, 18, "Docs", mouseX, mouseY);
        drawButton(graphics, width - 158, 6, 98, 18, "Import / Export", mouseX, mouseY);
        drawButton(graphics, width - 54, 6, 22, 18, "−", mouseX, mouseY);
        drawButton(graphics, width - 28, 6, 22, 18, "+", mouseX, mouseY);
        if (fsComputerId >= 0) {
            graphics.drawString(
                    font,
                    "Computer ID: " + fsComputerId,
                    6,
                    height - 12,
                    ComputedEditorTheme.TEXT_TERTIARY,
                    false);
        }
    }

    private void renderExplorer(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, EXPLORER_WIDTH, height, 0xF2111111);
        graphics.vLine(EXPLORER_WIDTH - 1, 0, height, ComputedEditorTheme.BORDER_MENU);
        graphics.drawString(font, "Node Explorer", 34, 11, ComputedEditorTheme.TEXT_HEADER, false);
        int searchColor = explorerSearchFocused
                ? ComputedEditorTheme.ACCENT
                : ComputedEditorTheme.BORDER_DEFAULT;
        graphics.fill(6, 27, EXPLORER_WIDTH - 6, 43, ComputedEditorTheme.BACKGROUND_INPUT);
        graphics.renderOutline(6, 27, EXPLORER_WIDTH - 12, 16, searchColor);
        boolean cursorVisible = explorerSearchFocused
                && (System.currentTimeMillis() / 500) % 2 == 0;
        String searchText = explorerSearch.isEmpty() && !explorerSearchFocused
                ? "Search nodes"
                : explorerSearch;
        graphics.drawString(
                font,
                searchText,
                11,
                31,
                explorerSearch.isEmpty()
                        ? ComputedEditorTheme.TEXT_TERTIARY
                        : ComputedEditorTheme.TEXT_PRIMARY,
                false);
        if (explorerSearchFocused) {
            int start = explorerSearchInput.selectionStart();
            int end = explorerSearchInput.selectionEnd();
            if (start != end) {
                int startX = 11 + font.width(explorerSearch.substring(0, start));
                int endX = 11 + font.width(explorerSearch.substring(0, end));
                graphics.fill(startX, 29, endX, 41, ComputedEditorTheme.SELECTION_TEXT_BACKGROUND);
                graphics.drawString(font, explorerSearch, 11, 31, ComputedEditorTheme.TEXT_PRIMARY, false);
            }
            if (cursorVisible) {
                int cursorX = 11 + font.width(explorerSearch.substring(0, explorerSearchInput.cursor()));
                graphics.vLine(cursorX, 29, 40, ComputedEditorTheme.TEXT_HEADER);
            }
        }
        List<ExplorerRow> rows = explorer.visibleRows();
        explorerHorizontalScroll = net.minecraft.util.Mth.clamp(explorerHorizontalScroll, 0, explorerMaxHorizontalScroll());
        int visible = Math.max(1, (height - 49) / EXPLORER_ROW_HEIGHT);
        explorerScroll = net.minecraft.util.Mth.clamp(
                explorerScroll,
                0,
                Math.max(0, rows.size() - visible));
        String unavailableTooltip = "";
        graphics.enableScissor(0, 46, EXPLORER_WIDTH - 1, height - 4);
        for (int index = explorerScroll; index < Math.min(rows.size(), explorerScroll + visible); index++) {
            ExplorerRow row = rows.get(index);
            int y = 46 + (index - explorerScroll) * EXPLORER_ROW_HEIGHT;
            boolean hovered = mouseX >= 0
                    && mouseX < EXPLORER_WIDTH
                    && mouseY >= y
                    && mouseY < y + EXPLORER_ROW_HEIGHT;
            if (hovered) {
                graphics.fill(1, y, EXPLORER_WIDTH - 1, y + EXPLORER_ROW_HEIGHT, ComputedEditorTheme.MENU_HOVER);
            }
            int x = 8 + row.depth() * 12 - explorerHorizontalScroll;
            if (row.depth() > 0) {
                graphics.vLine(x - 5, y, y + EXPLORER_ROW_HEIGHT, ComputedEditorTheme.BORDER_SUBTLE);
            }
            if (row.folder()) {
                graphics.drawString(
                        font,
                        row.expanded() ? "▾" : "▸",
                        x,
                        y + 3,
                        ComputedEditorTheme.TEXT_SECONDARY,
                        false);
                graphics.drawString(
                        font,
                        row.label(),
                        x + 10,
                        y + 3,
                        ComputedEditorTheme.TEXT_PRIMARY,
                        false);
            } else {
                int color = row.node().available()
                        ? ComputedEditorTheme.ACCENT
                        : ComputedEditorTheme.TEXT_DISABLED;
                graphics.fill(x + 1, y + 4, x + 7, y + 10, color);
                graphics.drawString(
                        font,
                        row.label(),
                        x + 11,
                        y + 3,
                        row.node().available()
                                ? ComputedEditorTheme.TEXT_PRIMARY
                                : ComputedEditorTheme.TEXT_DISABLED,
                        false);
                if (hovered && !row.node().available()) {
                    unavailableTooltip = row.node().unavailableReason();
                }
            }
        }
        if (!unavailableTooltip.isEmpty()) {
            graphics.renderTooltip(font, Component.literal(unavailableTooltip), mouseX, mouseY);
        }
        graphics.disableScissor();
        int maximumHorizontal = explorerMaxHorizontalScroll();
        if (maximumHorizontal > 0) {
            int track = EXPLORER_WIDTH - 12;
            int thumb = Math.max(20, track * track / (track + maximumHorizontal));
            int thumbX = 6 + (track - thumb) * explorerHorizontalScroll / maximumHorizontal;
            graphics.fill(6, height - 4, 6 + track, height - 2, ComputedEditorTheme.BORDER_SUBTLE);
            graphics.fill(thumbX, height - 4, thumbX + thumb, height - 2, ComputedEditorTheme.BORDER_HIGHLIGHT);
        }
    }

    private void renderContext(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ContextEntry> entries = buildContextMenu();
        int menuHeight = contextMenuHeight(entries);
        int x = Math.min(contextX, width - CONTEXT_WIDTH - 2);
        int y = Math.min(contextY, height - menuHeight - 2);
        renderContextPanel(graphics, entries, x, y, mouseX, mouseY);
        if (contextAlignOpen) {
            List<ContextEntry> flyout = alignMenu();
            int flyoutHeight = contextMenuHeight(flyout);
            int flyoutX = x + CONTEXT_WIDTH - 2;
            int flyoutY = Math.min(contextAlignRowY(entries, y), height - flyoutHeight - 2);
            renderContextPanel(graphics, flyout, flyoutX, flyoutY, mouseX, mouseY);
        }
    }

    private void renderContextPanel(
            GuiGraphics graphics,
            List<ContextEntry> entries,
            int x,
            int y,
            int mouseX,
            int mouseY) {
        int menuHeight = contextMenuHeight(entries);
        ComputedEditorStyle.drawMenuPanel(graphics, x, y, CONTEXT_WIDTH, menuHeight);
        int cursor = y + 2;
        for (ContextEntry entry : entries) {
            if (entry.separator()) {
                ComputedEditorStyle.drawSeparator(graphics, x + 6, cursor + 3, CONTEXT_WIDTH - 12);
                cursor += CONTEXT_SEPARATOR;
                continue;
            }
            boolean hovered = entry.enabled()
                    && contains(mouseX, mouseY, x, cursor, CONTEXT_WIDTH, CONTEXT_ROW);
            ComputedEditorStyle.drawMenuRow(graphics, x, cursor, CONTEXT_WIDTH, CONTEXT_ROW, hovered, false);
            int color = !entry.enabled()
                    ? ComputedEditorTheme.TEXT_DISABLED
                    : entry.label().equals("Delete")
                            ? ComputedEditorTheme.STATUS_ERROR_TEXT
                            : ComputedEditorTheme.TEXT_PRIMARY;
            graphics.drawString(font, entry.label(), x + 7, cursor + 5, color, false);
            cursor += CONTEXT_ROW;
        }
    }

    private List<ContextEntry> buildContextMenu() {
        List<ContextEntry> entries = new ArrayList<>();
        if (hasSelectedNodes()) {
            entries.add(new ContextEntry("Clone", false, true, this::cloneSelectedNodes));
            entries.add(new ContextEntry("Unlink", false, true, this::unlinkSelectedNodes));
            entries.add(new ContextEntry("Delete", false, true, this::removeSelectedNodes));
            entries.add(new ContextEntry("", true, false, () -> {}));
            entries.add(new ContextEntry(
                    ALIGN_MENU_LABEL, false, true, () -> contextAlignOpen = !contextAlignOpen));
            entries.add(new ContextEntry("Auto Layout", false, true, this::autoLayoutCanvasNodes));
            entries.add(new ContextEntry("", true, false, () -> {}));
        }
        entries.add(new ContextEntry("Add Comment", false, true, () -> addCommentNote(explorerAnchorX, explorerAnchorY)));
        entries.add(new ContextEntry("Add Frame", false, true, () -> addFrameNote(explorerAnchorX, explorerAnchorY)));
        FrameNote selectedFrame = selectedFrame();
        if (selectedFrame != null) {
            entries.add(new ContextEntry("", true, false, () -> {}));
            entries.add(new ContextEntry("Select Frame Contents", false, true, () -> selectFrameContents(selectedFrame)));
            entries.add(new ContextEntry("Frame Color", false, true, () -> {
                checkpoint();
                selectedFrame.cycleColor();
            }));
        }
        if (!hasSelectedNodes()) {
            entries.add(new ContextEntry("", true, false, () -> {}));
            entries.add(new ContextEntry("Open Node Explorer", false, true, () -> {
                explorerOpen = true;
                explorerSearchFocused = true;
            }));
            entries.add(new ContextEntry("New Lua Node…", false, true, () -> openNewLuaNode(explorerAnchorX, explorerAnchorY)));
            entries.add(new ContextEntry("Paste Lua", false, true, this::openLuaFromClipboard));
        }
        return entries;
    }

    private List<ContextEntry> alignMenu() {
        return List.of(
                new ContextEntry("Align Left", false, true, () -> alignSelectedNodes(CanvasLayoutOps.Align.LEFT)),
                new ContextEntry("Align Center", false, true, () -> alignSelectedNodes(CanvasLayoutOps.Align.CENTER_H)),
                new ContextEntry("Align Right", false, true, () -> alignSelectedNodes(CanvasLayoutOps.Align.RIGHT)),
                new ContextEntry("Align Top", false, true, () -> alignSelectedNodes(CanvasLayoutOps.Align.TOP)),
                new ContextEntry("Align Middle", false, true, () -> alignSelectedNodes(CanvasLayoutOps.Align.CENTER_V)),
                new ContextEntry("Align Bottom", false, true, () -> alignSelectedNodes(CanvasLayoutOps.Align.BOTTOM)),
                new ContextEntry("", true, false, () -> {}),
                new ContextEntry("Distribute H", false, true, () -> distributeSelectedNodes(true)),
                new ContextEntry("Distribute V", false, true, () -> distributeSelectedNodes(false)));
    }

    private int contextMenuHeight(List<ContextEntry> entries) {
        int height = 4;
        for (ContextEntry entry : entries) {
            height += entry.separator() ? CONTEXT_SEPARATOR : CONTEXT_ROW;
        }
        return height;
    }

    /** Screen Y of the clickable row at {@code index}, or -1 for separators. */
    private int contextRowAt(List<ContextEntry> entries, double mouseY, int menuY) {
        int cursor = menuY + 2;
        for (int index = 0; index < entries.size(); index++) {
            ContextEntry entry = entries.get(index);
            int rowHeight = entry.separator() ? CONTEXT_SEPARATOR : CONTEXT_ROW;
            if (mouseY >= cursor && mouseY < cursor + rowHeight) {
                return entry.separator() ? -1 : index;
            }
            cursor += rowHeight;
        }
        return -1;
    }

    private int contextAlignRowY(List<ContextEntry> entries, int menuY) {
        int cursor = menuY + 2;
        for (ContextEntry entry : entries) {
            if (entry.label().equals(ALIGN_MENU_LABEL)) {
                return cursor;
            }
            cursor += entry.separator() ? CONTEXT_SEPARATOR : CONTEXT_ROW;
        }
        return menuY + 2;
    }

    private boolean handleControlsClick(double mouseX, double mouseY) {
        if (contains(mouseX, mouseY, 6, 6, 22, 18)) {
            explorerOpen = !explorerOpen;
            explorerSearchFocused = false;
            explorerSearchInput.blur();
            return true;
        }
        if (contains(mouseX, mouseY, width - 288, 6, 24, 18)) {
            editorUndo();
            return true;
        }
        if (contains(mouseX, mouseY, width - 262, 6, 24, 18)) {
            editorRedo();
            return true;
        }
        if (contains(mouseX, mouseY, width - 236, 6, 24, 18)) {
            editorZoomToFit();
            return true;
        }
        if (contains(mouseX, mouseY, width - 54, 6, 22, 18)) {
            transferMenuOpen = false;
            adjustEditorZoom(-0.1, width / 2.0, height / 2.0);
            return true;
        }
        if (contains(mouseX, mouseY, width - 28, 6, 22, 18)) {
            transferMenuOpen = false;
            adjustEditorZoom(0.1, width / 2.0, height / 2.0);
            return true;
        }
        if (contains(mouseX, mouseY, width - 212, 6, 48, 18)) {
            transferMenuOpen = false;
            contextOpen = false;
            contextExplorerNode = null;
            minecraft.setScreen(new dev.propulsionteam.computed.client.docs.NodeDocsScreen(this));
            return true;
        }
        if (contains(mouseX, mouseY, width - 158, 6, 98, 18)) {
            transferMenuOpen = !transferMenuOpen;
            contextOpen = false;
            contextExplorerNode = null;
            return true;
        }
        return false;
    }

    private boolean handleExplorerClick(double mouseX, double mouseY, int button) {
        if (contains(mouseX, mouseY, 6, 27, EXPLORER_WIDTH - 12, 16)) {
            if (button == 0) {
                explorerSearchFocused = true;
                if (!explorerSearchInput.focused()) {
                    explorerSearchInput.focus(explorerSearch);
                }
                explorerSearchInput.click(searchColumn(mouseX), hasShiftDown());
            }
            return true;
        }
        if (mouseY < 46 || mouseY >= height - 4) {
            return true;
        }
        List<ExplorerRow> rows = explorer.visibleRows();
        int index = explorerScroll + ((int) mouseY - 46) / EXPLORER_ROW_HEIGHT;
        if (index < 0 || index >= rows.size()) {
            return true;
        }
        ExplorerRow row = rows.get(index);
        if (!row.folder()) selectedExplorerNode = row.node();
        if (!row.folder()
                && row.node().available()
                && row.node().id().equals(NEW_LUA_NODE_ACTION)
                && button == 0) {
            explorerAnchorX = editorGraphX(width / 2.0);
            explorerAnchorY = editorGraphY(height / 2.0);
            openNewLuaNode(explorerAnchorX, explorerAnchorY);
            return true;
        }
        if (row.folder() && button == 0) {
            explorer.setExpanded(row.stablePath(), !row.expanded());
        } else if (!row.folder() && button == 1) {
            contextExplorerNode = row.node();
            explorerContextX = (int) mouseX;
            explorerContextY = (int) mouseY;
        } else if (!row.folder() && row.node().available() && button == 0) {
            explorerPressNode = row.node();
            explorerPressX = mouseX;
            explorerPressY = mouseY;
            explorerDragging = false;
        }
        return true;
    }

    private boolean handleContextClick(double mouseX, double mouseY, int button) {
        List<ContextEntry> entries = buildContextMenu();
        int menuHeight = contextMenuHeight(entries);
        int x = Math.min(contextX, width - CONTEXT_WIDTH - 2);
        int y = Math.min(contextY, height - menuHeight - 2);
        if (contextAlignOpen) {
            List<ContextEntry> flyout = alignMenu();
            int flyoutHeight = contextMenuHeight(flyout);
            int flyoutX = x + CONTEXT_WIDTH - 2;
            int flyoutY = Math.min(contextAlignRowY(entries, y), height - flyoutHeight - 2);
            if (contains(mouseX, mouseY, flyoutX, flyoutY, CONTEXT_WIDTH, flyoutHeight)) {
                if (button == 0) {
                    int row = contextRowAt(flyout, mouseY, flyoutY);
                    contextOpen = false;
                    contextAlignOpen = false;
                    if (row >= 0) {
                        flyout.get(row).action().run();
                    }
                }
                return true;
            }
        }
        if (button != 0 || !contains(mouseX, mouseY, x, y, CONTEXT_WIDTH, menuHeight)) {
            contextOpen = false;
            contextAlignOpen = false;
            return true;
        }
        int row = contextRowAt(entries, mouseY, y);
        if (row < 0) {
            return true;
        }
        ContextEntry entry = entries.get(row);
        if (entry.label().equals(ALIGN_MENU_LABEL)) {
            contextAlignOpen = !contextAlignOpen;
            return true;
        }
        contextOpen = false;
        contextAlignOpen = false;
        if (entry.enabled()) {
            entry.action().run();
        }
        return true;
    }

    private void addCommentNote(int graphX, int graphY) {
        addNodeToCanvas(new CommentNote(graphX, graphY, ""));
    }

    private void addFrameNote(int graphX, int graphY) {
        addNodeToCanvas(new FrameNote(graphX, graphY, "Group"));
    }

    private void place(ExplorerNode node) {
        if (node == null || !node.available()) {
            return;
        }
        if (node.id().equals(NEW_LUA_NODE_ACTION)) {
            explorerAnchorX = editorGraphX(width / 2.0);
            explorerAnchorY = editorGraphY(height / 2.0);
            openNewLuaNode(explorerAnchorX, explorerAnchorY);
            return;
        }
        LuaEditorNode placed = LuaEditorGraphAdapter.createEditorNode(
                baseProgram,
                node.id(),
                explorerAnchorX,
                explorerAnchorY);
        addNodeToCanvas(placed);
    }

    private void openLuaFromClipboard() {
        String source;
        try {
            source = dev.propulsionteam.computed.persistence.LuaDefinitionClipboard.importSource(
                    minecraft.keyboardHandler.getClipboard());
        } catch (IllegalArgumentException exception) {
            source = """
                    local node = computed.node(1, "example:new_node", "New Node")

                    node:category("lua")
                    node:input("value", "number", { default = 0 })
                    node:output("result", "number")
                    node:on_run(function(ctx)
                        ctx:output("result", ctx:input("value"))
                    end)

                    return node
                    """;
        }
        minecraft.setScreen(new LuaNodeEditorScreen(this, baseProgram, source));
    }

    private void openNewLuaNode(int x, int y) {
        LuaNodeStarter.Starter starter = LuaNodeStarter.create();
        minecraft.setScreen(new LuaNodeEditorScreen(
                this,
                baseProgram,
                starter.source(),
                true,
                x,
                y));
    }

    private void exportSelectedNode() {
        if (selectedExplorerNode == null || selectedExplorerNode.id().equals(NEW_LUA_NODE_ACTION)) {
            notify("Select a user node to export");
            return;
        }
        LuaDefinitionSource source = LuaEditorGraphAdapter.definitions(baseProgram).get(selectedExplorerNode.id());
        if (source == null || source.origin() != LuaDefinitionSource.Origin.EMBEDDED) {
            notify("Only user nodes can be exported");
            return;
        }
        requestExport(
                ComputedPackageStorage.Kind.NODES,
                selectedExplorerNode.title(),
                source.source().getBytes(StandardCharsets.UTF_8),
                diskExportAvailable());
    }

    private void importGraph(byte[] content) {
        try {
            ComputedProgramV3 imported = ComputedProgramPackage.importArchive(content);
            String typeError =
                    ComputerEditPolicy.computerType(imported, isCreativeComputer());
            if (typeError != null) {
                throw new IllegalArgumentException(typeError);
            }
            baseProgram = imported.withRevision(serverRevision);
            editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram);
            replaceCanvasGraph(editorGraph);
            explorer = new NodeExplorerModel(explorerNodes(baseProgram));
            selectedExplorerNode = null;
            explorerScroll = 0;
            acknowledgedEditorRevision = -1;
            notify("Computed graph imported");
        } catch (IllegalArgumentException exception) {
            notify("Graph import failed: " + exception.getMessage());
        }
    }

    /**
     * Replaces the current graph with a docs example program (used by the Node Docs screen's
     * Import button). Returns {@code true} when the import was applied.
     */
    public boolean importDocsExample(ComputedProgramV3 program) {
        try {
            String typeError = ComputerEditPolicy.computerType(program, isCreativeComputer());
            if (typeError != null) {
                notify("Docs import rejected: " + typeError);
                return false;
            }
            baseProgram = program.withRevision(serverRevision);
            editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram);
            replaceCanvasGraph(editorGraph);
            explorer = new NodeExplorerModel(explorerNodes(baseProgram));
            selectedExplorerNode = null;
            explorerScroll = 0;
            acknowledgedEditorRevision = -1;
            notify("Example graph imported — open the docs screen again anytime");
            return true;
        } catch (RuntimeException exception) {
            notify("Docs import failed: " + exception.getMessage());
            return false;
        }
    }

    private void importNode(String source) {
        try {
            minecraft.setScreen(new LuaNodeEditorScreen(this, baseProgram, source));
        } catch (IllegalArgumentException exception) {
            notify("Node import failed: " + exception.getMessage());
        }
    }

    /** Captures export content and asks the server for a write (exists check happens there). */
    private void requestExport(
            ComputedPackageStorage.Kind kind, String requestedName, byte[] content, boolean toDisk) {
        try {
            String relative = ComputedPackageStorage.sanitizeRelativePath(requestedName, kind.extension());
            pendingExport = new PendingExport(kind, relative, content, toDisk, toDisk && !handDiskPresent());
            sendPackageWrite(PackageTransferService.id(PackageTransferService.WriteMode.FAIL_IF_EXISTS));
        } catch (IllegalArgumentException exception) {
            notify("Export failed: " + exception.getMessage());
        }
    }

    private void sendPackageList(ComputedPackageStorage.Kind kind, String folder) {
        packageChoices = List.of();
        packageLoading = true;
        PacketDistributor.sendToServer(
                new PackageListPayload(computerPos, PackageTransferService.id(kind), folder));
    }

    private void sendPackageRead(ComputedPackageStorage.Kind kind, String relativePath) {
        PacketDistributor.sendToServer(
                new PackageReadPayload(computerPos, PackageTransferService.id(kind), relativePath));
    }

    private void sendPackageWrite(int mode) {
        PendingExport export = pendingExport;
        if (export == null) return;
        int opId = ++writeOpSequence;
        pendingWriteOpId = opId;
        int kindId = PackageTransferService.id(export.kind());
        int targetId = PackageTransferService.id(
                export.toDisk()
                        ? PackageTransferService.WriteTarget.DISK
                        : PackageTransferService.WriteTarget.FOLDER);
        byte[] content = export.content();
        int total = content.length;
        if (total == 0) {
            PacketDistributor.sendToServer(new PackageWritePayload(
                    opId, computerPos, kindId, export.relativePath(), mode, targetId, total, new byte[0]));
            return;
        }
        for (int offset = 0; offset < total; offset += PackageTransferService.WRITE_CHUNK_BYTES) {
            byte[] chunk = Arrays.copyOfRange(
                    content, offset, Math.min(total, offset + PackageTransferService.WRITE_CHUNK_BYTES));
            PacketDistributor.sendToServer(new PackageWritePayload(
                    opId, computerPos, kindId, export.relativePath(), mode, targetId, total, chunk));
        }
    }

    private void openImport(Modal type, ComputedPackageStorage.Kind kind) {
        importKind = kind;
        importFolder = "";
        ProgramDiskData disk = diskTarget();
        diskImport = disk != null && disk.kind() == PackageTransferService.id(kind) ? disk : null;
        modal = type;
        sendPackageList(kind, "");
    }

    private void refreshImportChoices() {
        sendPackageList(importKind, importFolder);
    }

    private void beginGraphExport(boolean toDisk) {
        if (toDisk && !diskExportAvailable()) {
            notify("Insert a Program Disk in the computer's drive or hold one in your main hand");
            return;
        }
        try {
            String relative = ComputedPackageStorage.sanitizeRelativePath(
                    exportNameInput.text(), ComputedPackageStorage.Kind.GRAPHS.extension());
            byte[] content = ComputedProgramPackage.exportArchive(
                    LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, serverRevision));
            pendingExport = new PendingExport(
                    ComputedPackageStorage.Kind.GRAPHS,
                    relative,
                    content,
                    toDisk,
                    toDisk && !handDiskPresent());
            sendPackageWrite(PackageTransferService.id(PackageTransferService.WriteMode.FAIL_IF_EXISTS));
            closeModal();
        } catch (IllegalArgumentException exception) {
            notify("Graph export failed: " + exception.getMessage());
        }
    }

    /** Program data on the disk in the main hand, or {@code null} when there is none. */
    private ProgramDiskData heldDisk() {
        return minecraft.player == null
                ? null
                : ProgramDiskItem.read(minecraft.player.getMainHandItem());
    }

    /**
     * Program data available for export/import: the main-hand disk wins, then the computer's
     * drive slot; {@code null} when neither holds one.
     */
    private ProgramDiskData diskTarget() {
        ProgramDiskData held = heldDisk();
        return held != null ? held : slotDisk;
    }

    /** Whether the main hand physically holds a program disk (stored data irrelevant). */
    private boolean handDiskPresent() {
        return minecraft.player != null
                && minecraft.player.getMainHandItem().getItem() instanceof ProgramDiskItem;
    }

    /**
     * Whether an export may target a disk. Presence decides — a blank disk accepts its first
     * write on both sides; content is only needed for imports.
     */
    private boolean diskExportAvailable() {
        return handDiskPresent() || slotDiskPresent;
    }

    /** Imports the program stored on the disk entry of the open import modal. */
    private void importFromDisk() {
        ProgramDiskData data = diskImport;
        if (data == null) return;
        Modal type = modal;
        closeModal();
        if (type == Modal.IMPORT_GRAPH) {
            importGraph(data.content());
        } else {
            importNode(new String(data.content(), StandardCharsets.UTF_8));
        }
    }

    private void closeModal() {
        modal = Modal.NONE;
        packageChoices = List.of();
        importFolder = "";
        diskImport = null;
        packageLoading = false;
        exportNameInput.blur();
        idInput.blur();
        nameInput.blur();
    }

    /** S2C: folder listing for the open import modal. */
    public void onPackageListResult(
            int kindId, String folder, String error, List<PackageTransferService.Entry> entries) {
        if (modal != Modal.IMPORT_GRAPH && modal != Modal.IMPORT_NODE) {
            return;
        }
        ComputedPackageStorage.Kind kind;
        try {
            kind = PackageTransferService.kind(kindId);
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (kind != importKind || !folder.equals(importFolder)) {
            return;
        }
        packageLoading = false;
        if (!error.isEmpty()) {
            notify("Could not open folder: " + error);
            closeModal();
            return;
        }
        packageChoices = entries;
    }

    /** S2C: content of a package file requested for import. */
    public void onPackageReadResult(int kindId, String path, String error, byte[] data) {
        ComputedPackageStorage.Kind kind;
        try {
            kind = PackageTransferService.kind(kindId);
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (!error.isEmpty()) {
            notify((kind == ComputedPackageStorage.Kind.GRAPHS ? "Graph" : "Node")
                    + " import failed: " + error);
            return;
        }
        if (kind == ComputedPackageStorage.Kind.GRAPHS) {
            importGraph(data);
        } else {
            importNode(new String(data, StandardCharsets.UTF_8));
        }
    }

    /** S2C: final result of a requested write (OK, EXISTS opens the overwrite modal, ERROR). */
    public void onPackageWriteResult(int opId, int status, String fileName, String error) {
        if (opId != pendingWriteOpId) {
            return;
        }
        if (status == PackageTransferService.WriteStatus.OK.ordinal()) {
            PendingExport export = pendingExport;
            pendingExport = null;
            if (export == null) {
                notify("Exported as " + fileName);
                return;
            }
            if (export.toDisk()) {
                if (export.toSlot()) {
                    slotDisk = new ProgramDiskData(
                            PackageTransferService.id(export.kind()),
                            export.relativePath(),
                            export.content());
                }
                notify("Saved to disk as " + fileName);
            } else {
                notify("Exported as " + fileName);
            }
        } else if (status == PackageTransferService.WriteStatus.EXISTS.ordinal()) {
            if (pendingExport == null) return;
            modal = pendingExport.kind() == ComputedPackageStorage.Kind.GRAPHS
                    ? Modal.OVERWRITE_GRAPH
                    : Modal.OVERWRITE_NODE;
        } else {
            pendingExport = null;
            notify("Export failed: " + (error == null || error.isEmpty() ? "unknown error" : error));
        }
    }

    /** Validates the Set Computer ID dialog and sends the request to the server. */
    private void confirmSetComputerId() {
        String text = idInput.text().strip();
        int requested;
        try {
            requested = Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            notify("Computer ID must be a whole number");
            return;
        }
        if (requested < 0 || requested >= Integer.MAX_VALUE) {
            notify("Computer ID must be between 0 and " + (Integer.MAX_VALUE - 1));
            return;
        }
        PacketDistributor.sendToServer(new SetComputerIdPayload(computerPos, requested));
        closeModal();
    }

    /** Sends the Set Computer Name dialog to the server (empty name = unnamed). */
    private void confirmSetComputerName() {
        String name = nameInput.text().strip();
        if (name.length() > 32) {
            name = name.substring(0, 32);
        }
        PacketDistributor.sendToServer(new SetComputerNamePayload(computerPos, name));
        computerName = name;
        closeModal();
    }

    /** S2C: outcome of the Set Computer ID dialog. */
    public void onComputerIdResult(boolean accepted, int computerId, String message) {
        if (!message.isEmpty()) {
            notify(message);
        }
        if (accepted) {
            fsComputerId = computerId;
        }
    }

    private void renderTransferMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int menuWidth = 132;
        int menuHeight = 112;
        int x = Math.max(2, width - menuWidth - 60);
        int y = 26;
        boolean canExportNode = canExportSelectedNode();
        boolean nodeToDisk = canExportNode && diskExportAvailable();
        List<String> rows = List.of(
                "Import Graph",
                "Export Graph",
                "Import Node",
                nodeToDisk ? "Export Node to Disk" : "Export Selected Node",
                "Set Computer ID",
                "Set Computer Name");
        ComputedEditorStyle.drawMenuPanel(graphics, x, y, menuWidth, menuHeight);
        for (int index = 0; index < rows.size(); index++) {
            int rowY = y + 2 + index * 18;
            boolean enabled = index != 3 || canExportNode;
            boolean hovered = enabled && contains(mouseX, mouseY, x, rowY, menuWidth, 18);
            ComputedEditorStyle.drawMenuRow(graphics, x, rowY, menuWidth, 18, hovered, false);
            graphics.drawString(
                    font,
                    rows.get(index),
                    x + 7,
                    rowY + 5,
                    enabled ? ComputedEditorTheme.TEXT_PRIMARY : ComputedEditorTheme.TEXT_DISABLED,
                    false);
        }
    }

    private boolean handleTransferMenuClick(double mouseX, double mouseY, int button) {
        int menuWidth = 132;
        int menuHeight = 112;
        int x = Math.max(2, width - menuWidth - 60);
        int y = 26;
        if (button != 0 || !contains(mouseX, mouseY, x, y, menuWidth, menuHeight)) {
            transferMenuOpen = false;
            return true;
        }
        int row = ((int) mouseY - y - 2) / 18;
        if (row < 0
                || row >= 6
                || !contains(mouseX, mouseY, x, y + 2 + row * 18, menuWidth, 18)) {
            transferMenuOpen = false;
            return true;
        }
        transferMenuOpen = false;
        if (row == 0) {
            openImport(Modal.IMPORT_GRAPH, ComputedPackageStorage.Kind.GRAPHS);
        } else if (row == 1) {
            exportNameInput.focus("New Graph");
            modal = Modal.EXPORT_GRAPH_NAME;
        } else if (row == 2) {
            openImport(Modal.IMPORT_NODE, ComputedPackageStorage.Kind.NODES);
        } else if (row == 3 && canExportSelectedNode()) {
            exportSelectedNode();
        } else if (row == 4) {
            idInput.focus(fsComputerId >= 0 ? Integer.toString(fsComputerId) : "0");
            modal = Modal.SET_COMPUTER_ID;
        } else if (row == 5) {
            nameInput.focus(computerName);
            modal = Modal.SET_COMPUTER_NAME;
        }
        return true;
    }

    private boolean canExportSelectedNode() {
        LuaDefinitionSource source = selectedSource();
        return selectedExplorerNode != null
                && !selectedExplorerNode.id().equals(NEW_LUA_NODE_ACTION)
                && source != null
                && source.origin() == LuaDefinitionSource.Origin.EMBEDDED;
    }

    private void renderModal(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, ComputedEditorTheme.BACKGROUND_MODAL_SCRIM);
        int boxWidth = Math.min(300, width - 24);
        int x = (width - boxWidth) / 2;
        int y = Math.max(36, height / 4);
        int rows = importChoiceRows();
        int boxHeight = modalHeight(rows);
        ComputedEditorStyle.drawMenuPanel(graphics, x, y, boxWidth, boxHeight);
        String title = switch (modal) {
            case IMPORT_GRAPH -> importFolder.isEmpty() ? "Import Graph" : "Import Graph — " + importFolder;
            case IMPORT_NODE -> importFolder.isEmpty() ? "Import Node" : "Import Node — " + importFolder;
            case EXPORT_GRAPH_NAME -> "Export Graph";
            case OVERWRITE_GRAPH, OVERWRITE_NODE -> "File already exists";
            case DELETE_NODE -> "Delete user node";
            case SET_COMPUTER_ID -> "Set Computer ID";
            case SET_COMPUTER_NAME -> "Set Computer Name";
            default -> "";
        };
        graphics.drawString(font, title, x + 8, y + 8, ComputedEditorTheme.TEXT_HEADER, false);
        if (modal == Modal.IMPORT_GRAPH || modal == Modal.IMPORT_NODE) {
            int listHeight = importListHeight(rows);
            ComputedEditorStyle.drawField(
                    graphics, x + 6, y + 24, boxWidth - 12, listHeight, false, false);
            if (packageLoading) {
                graphics.drawString(
                        font,
                        "Loading…",
                        x + 11,
                        y + 32,
                        ComputedEditorTheme.TEXT_SECONDARY,
                        false);
            } else if (choiceRowCount() == 0) {
                graphics.drawString(
                        font,
                        importFolder.isEmpty()
                                ? (modal == Modal.IMPORT_GRAPH
                                        ? "No saved graphs — try a Docs example"
                                        : "No .lua files found")
                                : "Empty folder",
                        x + 11,
                        y + 32,
                        ComputedEditorTheme.TEXT_SECONDARY,
                        false);
            }
            for (int index = 0; index < rows; index++) {
                int rowY = y + 26 + index * 18;
                ComputedEditorStyle.drawMenuRow(
                        graphics,
                        x + 7,
                        rowY,
                        boxWidth - 14,
                        18,
                        contains(mouseX, mouseY, x + 7, rowY, boxWidth - 14, 18),
                        false);
                ChoiceRow choice = choiceRow(index);
                if (choice == null) continue;
                if (choice.disk()) {
                    graphics.drawString(
                            font,
                            diskImport.name() + " — disk",
                            x + 11,
                            rowY + 5,
                            ComputedEditorTheme.TEXT_HEADER,
                            false);
                    continue;
                }
                if (choice.parent()) {
                    graphics.drawString(
                            font, "..", x + 11, rowY + 5, ComputedEditorTheme.TEXT_SECONDARY, false);
                    continue;
                }
                PackageTransferService.Entry entry = packageChoices.get(choice.entryIndex());
                String label = entry.directory() ? entry.name() + "/" : entry.name();
                graphics.drawString(
                        font,
                        label,
                        x + 11,
                        rowY + 5,
                        entry.directory() ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.TEXT_PRIMARY,
                        false);
            }
        } else if (modal == Modal.EXPORT_GRAPH_NAME) {
            ComputedEditorStyle.drawField(
                    graphics,
                    x + 8,
                    y + 27,
                    boxWidth - 16,
                    18,
                    exportNameInput.focused(),
                    contains(mouseX, mouseY, x + 8, y + 27, boxWidth - 16, 18));
            graphics.drawString(font, exportNameInput.text(), x + 12, y + 32, ComputedEditorTheme.TEXT_PRIMARY, false);
        } else if (modal == Modal.SET_COMPUTER_ID) {
            ComputedEditorStyle.drawField(
                    graphics,
                    x + 8,
                    y + 27,
                    boxWidth - 16,
                    18,
                    idInput.focused(),
                    contains(mouseX, mouseY, x + 8, y + 27, boxWidth - 16, 18));
            graphics.drawString(font, idInput.text(), x + 12, y + 32, ComputedEditorTheme.TEXT_PRIMARY, false);
            graphics.drawString(
                    font,
                    "Computers with this ID share their files.",
                    x + 8,
                    y + 50,
                    ComputedEditorTheme.TEXT_SECONDARY,
                    false);
        } else if (modal == Modal.SET_COMPUTER_NAME) {
            ComputedEditorStyle.drawField(
                    graphics,
                    x + 8,
                    y + 27,
                    boxWidth - 16,
                    18,
                    nameInput.focused(),
                    contains(mouseX, mouseY, x + 8, y + 27, boxWidth - 16, 18));
            graphics.drawString(font, nameInput.text(), x + 12, y + 32, ComputedEditorTheme.TEXT_PRIMARY, false);
            graphics.drawString(
                    font,
                    "Shown on portable monitors. Empty = unnamed.",
                    x + 8,
                    y + 50,
                    ComputedEditorTheme.TEXT_SECONDARY,
                    false);
        } else if (modal == Modal.DELETE_NODE) {
            graphics.drawString(font, "Delete definition and all its instances?", x + 8, y + 28, ComputedEditorTheme.TEXT_SECONDARY, false);
        } else {
            int firstRowY = y + 24;
            int secondRowY = y + 42;
            ComputedEditorStyle.drawMenuRow(
                    graphics, x + 4, firstRowY, boxWidth - 8, 18,
                    contains(mouseX, mouseY, x + 4, firstRowY, boxWidth - 8, 18), false);
            ComputedEditorStyle.drawMenuRow(
                    graphics, x + 4, secondRowY, boxWidth - 8, 18,
                    contains(mouseX, mouseY, x + 4, secondRowY, boxWidth - 8, 18), false);
            graphics.drawString(font, "Overwrite existing file", x + 8, firstRowY + 5, ComputedEditorTheme.STATUS_WARNING_TEXT, false);
            graphics.drawString(
                    font,
                    "Save with next available name",
                    x + 8,
                    secondRowY + 5,
                    ComputedEditorTheme.TEXT_PRIMARY,
                    false);
        }
        int cancelX = x + boxWidth - 66;
        int buttonY = y + boxHeight - 24;
        drawButton(graphics, cancelX, buttonY, 58, 18, "Cancel", mouseX, mouseY);
        if (modal == Modal.EXPORT_GRAPH_NAME) {
            drawButton(graphics, cancelX - 62, buttonY, 58, 18, "Save", mouseX, mouseY);
            drawButton(graphics, cancelX - 124, buttonY, 58, 18, "Disk", mouseX, mouseY);
        } else if (modal == Modal.SET_COMPUTER_ID) {
            drawButton(graphics, cancelX - 62, buttonY, 58, 18, "Set", mouseX, mouseY);
        } else if (modal == Modal.SET_COMPUTER_NAME) {
            drawButton(graphics, cancelX - 62, buttonY, 58, 18, "Set", mouseX, mouseY);
        } else if (modal == Modal.DELETE_NODE) {
            boolean hovered = contains(mouseX, mouseY, x + 8, buttonY, 58, 18);
            ComputedEditorStyle.drawDangerButton(graphics, x + 8, buttonY, 58, 18, hovered);
            ComputedEditorStyle.drawCenteredString(
                    graphics, font, "Delete", x + 8, buttonY, 58, 18, ComputedEditorTheme.STATUS_ERROR_TEXT);
        }
    }

    private boolean handleModalClick(double mouseX, double mouseY, int button) {
        if (button != 0) { closeModal(); return true; }
        int boxWidth = Math.min(300, width - 24);
        int x = (width - boxWidth) / 2;
        int y = Math.max(36, height / 4);
        int rows = importChoiceRows();
        int boxHeight = modalHeight(rows);
        if (!contains(mouseX, mouseY, x, y, boxWidth, boxHeight)) {
            closeModal();
            return true;
        }
        int cancelX = x + boxWidth - 66;
        int buttonY = y + boxHeight - 24;
        if (contains(mouseX, mouseY, cancelX, buttonY, 58, 18)) {
            closeModal();
            return true;
        }
        if (modal == Modal.IMPORT_GRAPH || modal == Modal.IMPORT_NODE) {
            int index = ((int) mouseY - y - 26) / 18;
            if (index >= 0
                    && index < rows
                    && contains(mouseX, mouseY, x + 7, y + 26 + index * 18, boxWidth - 14, 18)) {
                ChoiceRow choice = choiceRow(index);
                if (choice == null) return true;
                if (choice.disk()) {
                    importFromDisk();
                    return true;
                }
                if (choice.parent()) {
                    int separator = importFolder.lastIndexOf('/');
                    importFolder = separator < 0 ? "" : importFolder.substring(0, separator);
                    refreshImportChoices();
                    return true;
                }
                PackageTransferService.Entry entry = packageChoices.get(choice.entryIndex());
                if (entry.directory()) {
                    importFolder = importFolder.isEmpty()
                            ? entry.name()
                            : importFolder + "/" + entry.name();
                    refreshImportChoices();
                    return true;
                }
                String relativePath = importFolder.isEmpty()
                        ? entry.name()
                        : importFolder + "/" + entry.name();
                Modal type = modal;
                closeModal();
                sendPackageRead(
                        type == Modal.IMPORT_GRAPH
                                ? ComputedPackageStorage.Kind.GRAPHS
                                : ComputedPackageStorage.Kind.NODES,
                        relativePath);
            }
        } else if (modal == Modal.EXPORT_GRAPH_NAME) {
            if (contains(mouseX, mouseY, x + 8, y + 27, boxWidth - 16, 18)) {
                exportNameInput.click(exportNameColumn(mouseX, x + 12), hasShiftDown());
            } else if (contains(mouseX, mouseY, cancelX - 62, buttonY, 58, 18)) {
                beginGraphExport(false);
            } else if (contains(mouseX, mouseY, cancelX - 124, buttonY, 58, 18)) {
                beginGraphExport(true);
            }
        } else if (modal == Modal.SET_COMPUTER_ID) {
            if (contains(mouseX, mouseY, x + 8, y + 27, boxWidth - 16, 18)) {
                idInput.click(inputColumn(mouseX, x + 12, idInput.text()), hasShiftDown());
            } else if (contains(mouseX, mouseY, cancelX - 62, buttonY, 58, 18)) {
                confirmSetComputerId();
            }
        } else if (modal == Modal.SET_COMPUTER_NAME) {
            if (contains(mouseX, mouseY, x + 8, y + 27, boxWidth - 16, 18)) {
                nameInput.click(inputColumn(mouseX, x + 12, nameInput.text()), hasShiftDown());
            } else if (contains(mouseX, mouseY, cancelX - 62, buttonY, 58, 18)) {
                confirmSetComputerName();
            }
        } else if (modal == Modal.OVERWRITE_GRAPH || modal == Modal.OVERWRITE_NODE) {
            boolean overwrite = contains(mouseX, mouseY, x + 4, y + 24, boxWidth - 8, 18);
            boolean suffix = contains(mouseX, mouseY, x + 4, y + 42, boxWidth - 8, 18);
            if (overwrite || suffix) {
                completePendingExport(overwrite);
            }
        } else if (modal == Modal.DELETE_NODE
                && contains(mouseX, mouseY, x + 8, buttonY, 58, 18)) {
            deleteSelectedNode();
            closeModal();
        }
        return true;
    }

    private void completePendingExport(boolean overwrite) {
        if (pendingExport == null) {
            closeModal();
            return;
        }
        sendPackageWrite(PackageTransferService.id(
                overwrite
                        ? PackageTransferService.WriteMode.OVERWRITE
                        : PackageTransferService.WriteMode.NEXT_AVAILABLE));
        closeModal();
    }

    private int importChoiceRows() {
        if (modal != Modal.IMPORT_GRAPH && modal != Modal.IMPORT_NODE) return 0;
        return Math.min(8, choiceRowCount());
    }

    /** The available disk (hand or drive) appears as the first row while the root folder is open. */
    private boolean hasDiskEntry() {
        return diskImport != null && importFolder.isEmpty();
    }

    private int choiceRowCount() {
        int count = packageChoices.size();
        if (!importFolder.isEmpty()) count += 1;
        if (hasDiskEntry()) count += 1;
        return count;
    }

    /** One row of the import list: the held disk (root only), the parent folder, or an entry. */
    private record ChoiceRow(boolean disk, boolean parent, int entryIndex) {}

    private ChoiceRow choiceRow(int index) {
        int next = 0;
        if (hasDiskEntry()) {
            if (index == next) return new ChoiceRow(true, false, -1);
            next += 1;
        }
        if (!importFolder.isEmpty()) {
            if (index == next) return new ChoiceRow(false, true, -1);
            next += 1;
        }
        int entryIndex = index - next;
        if (entryIndex >= 0 && entryIndex < packageChoices.size()) {
            return new ChoiceRow(false, false, entryIndex);
        }
        return null;
    }

    private int modalHeight(int importRows) {
        if (modal == Modal.EXPORT_GRAPH_NAME || modal == Modal.DELETE_NODE) return 82;
        if (modal == Modal.OVERWRITE_GRAPH
                || modal == Modal.OVERWRITE_NODE
                || modal == Modal.SET_COMPUTER_ID
                || modal == Modal.SET_COMPUTER_NAME) return 94;
        return importListHeight(importRows) + 52;
    }

    private int importListHeight(int rows) {
        return Math.max(24, rows * 18 + 4);
    }

    private int exportNameColumn(double mouseX, int textX) {
        return inputColumn(mouseX, textX, exportNameInput.text());
    }

    private int inputColumn(double mouseX, int textX, String text) {
        int relative = (int) mouseX - textX;
        int measuredWidth = 0;
        for (int index = 0; index < text.length(); index++) {
            int characterWidth = font.width(text.substring(index, index + 1));
            if (relative < measuredWidth + characterWidth / 2) {
                return index;
            }
            measuredWidth += characterWidth;
        }
        return text.length();
    }

    private LuaDefinitionSource selectedSource() { return selectedExplorerNode == null ? null : LuaEditorGraphAdapter.definitions(baseProgram).get(selectedExplorerNode.id()); }

    private void renderExplorerContext(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = Math.max(2, Math.min(explorerContextX, width - 102));
        int y = Math.max(2, Math.min(explorerContextY, height - 78));
        boolean user = contextExplorerNode.ownership() == ExplorerNode.Ownership.USER && contextExplorerNode.available();
        ComputedEditorStyle.drawMenuPanel(graphics, x, y, 100, 76);
        String[] labels = {"Add to Canvas", user ? "Edit" : "View", "Export", "Delete"};
        for (int index = 0; index < labels.length; index++) {
            int rowY = y + 2 + index * 18;
            boolean enabled = index == 0 || index == 1
                    ? contextExplorerNode.available()
                    : user;
            ComputedEditorStyle.drawMenuRow(
                    graphics,
                    x,
                    rowY,
                    100,
                    18,
                    enabled && contains(mouseX, mouseY, x, rowY, 100, 18),
                    false);
            int color = !enabled
                    ? ComputedEditorTheme.TEXT_DISABLED
                    : index == 3 ? ComputedEditorTheme.STATUS_ERROR_TEXT : ComputedEditorTheme.TEXT_PRIMARY;
            graphics.drawString(font, labels[index], x + 7, rowY + 5, color, false);
        }
    }

    private boolean handleExplorerContextClick(double mouseX, double mouseY, int button) {
        ExplorerNode node = contextExplorerNode;
        int x = Math.max(2, Math.min(explorerContextX, width - 102));
        int y = Math.max(2, Math.min(explorerContextY, height - 78));
        contextExplorerNode = null;
        if (button != 0 || !contains(mouseX, mouseY, x, y, 100, 76)) return true;
        int row = ((int) mouseY - y - 2) / 18;
        if (row < 0
                || row >= 4
                || !contains(mouseX, mouseY, x, y + 2 + row * 18, 100, 18)) {
            return true;
        }
        if (row == 0 && node.available()) {
            selectedExplorerNode = node;
            explorerAnchorX = editorGraphX(width / 2.0);
            explorerAnchorY = editorGraphY(height / 2.0);
            place(node);
            return true;
        }
        LuaDefinitionSource source = LuaEditorGraphAdapter.definitions(baseProgram).get(node.id());
        if (row == 1 && source != null) minecraft.setScreen(new LuaNodeEditorScreen(this, baseProgram, source.source(), source.origin(), false, 0, 0));
        else if (row == 2 && node.ownership() == ExplorerNode.Ownership.USER) {
            selectedExplorerNode = node;
            exportSelectedNode();
        } else if (row == 3 && node.ownership() == ExplorerNode.Ownership.USER) {
            selectedExplorerNode = node;
            modal = Modal.DELETE_NODE;
        }
        return true;
    }

    private void deleteSelectedNode() {
        if (selectedExplorerNode == null) return;
        String id = selectedExplorerNode.id();
        ComputedProgramV3 current = LuaEditorGraphAdapter.fromEditorGraph(editorGraph, baseProgram, serverRevision);
        baseProgram = LuaEditorGraphAdapter.removeDefinitionAndInstances(current, id);
        editorGraph = LuaEditorGraphAdapter.toEditorGraph(baseProgram); replaceCanvasGraph(editorGraph);
        explorer = new NodeExplorerModel(explorerNodes(baseProgram)); selectedExplorerNode = null; acknowledgedEditorRevision = -1;
        notify("Deleted user node " + id);
    }

    private void notify(String message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private int searchColumn(double mouseX) {
        int relative = (int) mouseX - 11;
        int width = 0;
        for (int index = 0; index < explorerSearch.length(); index++) {
            int characterWidth = font.width(explorerSearch.substring(index, index + 1));
            if (relative < width + characterWidth / 2) {
                return index;
            }
            width += characterWidth;
        }
        return explorerSearch.length();
    }

    private int explorerMaxHorizontalScroll() {
        int widest = explorer.visibleRows().stream()
                .mapToInt(row -> 8 + row.depth() * 12 + font.width(row.label()) + (row.folder() ? 10 : 11))
                .max().orElse(EXPLORER_WIDTH - 12);
        return Math.max(0, widest - (EXPLORER_WIDTH - 12));
    }

    private void updateExplorerSearch() {
        explorerSearch = explorerSearchInput.text();
        explorer.search(explorerSearch);
        explorerScroll = 0;
    }

    private static void drawButton(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            String text,
            int mouseX,
            int mouseY) {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        ComputedEditorStyle.drawButton(graphics, x, y, width, height, hovered, false);
        ComputedEditorStyle.drawCenteredString(
                graphics,
                Minecraft.getInstance().font,
                text,
                x,
                y,
                width,
                height,
                ComputedEditorTheme.TEXT_PRIMARY);
    }

    private static boolean contains(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isCreativeComputer() {
        return false;
    }

    private List<ExplorerNode> explorerNodes(ComputedProgramV3 program) {
        definitionTitles = null;
        definitionInputs = null;
        List<ExplorerNode> nodes = new ArrayList<>();
        nodes.add(new ExplorerNode(
                NEW_LUA_NODE_ACTION,
                "New Lua Node…",
                ExplorerNode.Ownership.USER,
                List.of(),
                true,
                ""));
        LuaSourceCompiler compiler = new LuaSourceCompiler();
        LuaDefinitionLoader loader = new LuaDefinitionLoader();
        LuaSandbox sandbox = new LuaSandbox();
        LuaEditorGraphAdapter.definitions(program).forEach((id, source) -> {
            if (id.equals(COMMAND_NODE_ID) && !isCreativeComputer()) {
                return;
            }
            try {
                var definition = loader.load(compiler.compile(source.apiVersion(), source.source()), sandbox);
                ExplorerNode.Ownership ownership = switch (source.origin()) {
                    case BUNDLED -> ExplorerNode.Ownership.BUNDLED;
                    case INTEGRATION -> ExplorerNode.Ownership.INTEGRATION;
                    case EMBEDDED -> ExplorerNode.Ownership.USER;
                };
                List<String> path = java.util.Arrays.stream(definition.category().split("/"))
                        .filter(segment -> !segment.isBlank())
                        .toList();
                String unavailableReason = source.origin() == LuaDefinitionSource.Origin.INTEGRATION
                        ? dev.propulsionteam.computed.lua.node.IntegrationLuaLibrary.unavailableReason(id)
                        : "";
                nodes.add(new ExplorerNode(
                        id,
                        definition.title(),
                        ownership,
                        path,
                        unavailableReason.isEmpty(),
                        unavailableReason));
            } catch (RuntimeException ignored) {
                nodes.add(new ExplorerNode(
                        id,
                        id,
                        source.origin() == LuaDefinitionSource.Origin.INTEGRATION
                                ? ExplorerNode.Ownership.INTEGRATION
                                : ExplorerNode.Ownership.USER,
                        List.of("invalid"),
                        false,
                        "Definition could not be compiled"));
            }
        });
        return List.copyOf(nodes);
    }

    @Override
    protected List<NodePickerEntry> nodePickerEntries() {
        ensureDefinitionCaches();
        List<NodePickerEntry> entries = new ArrayList<>();
        definitionTitles.forEach((id, title) -> {
            if (id.equals(COMMAND_NODE_ID) && !isCreativeComputer()) {
                return;
            }
            entries.add(new NodePickerEntry(id, title, definitionInputs.getOrDefault(id, List.of())));
        });
        entries.sort(Comparator.comparing(NodePickerEntry::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    @Override
    protected WNode createNodeFromPicker(String id, int graphX, int graphY) {
        LuaEditorNode placed =
                LuaEditorGraphAdapter.createEditorNode(baseProgram, id, graphX, graphY);
        addNodeToCanvas(placed);
        return placed;
    }

    @Override
    protected void onNodeDoubleClick(WNode node, double localX, double localY) {
        if (node instanceof CommentNote note) {
            note.beginEditing(localX, localY);
            return;
        }
        super.onNodeDoubleClick(node, localX, localY);
    }

    private void ensureDefinitionCaches() {
        if (definitionTitles != null && definitionInputs != null) {
            return;
        }
        definitionTitles = new HashMap<>();
        definitionInputs = new HashMap<>();
        LuaSourceCompiler compiler = new LuaSourceCompiler();
        LuaDefinitionLoader loader = new LuaDefinitionLoader();
        LuaSandbox sandbox = new LuaSandbox();
        LuaEditorGraphAdapter.definitions(baseProgram).forEach((id, source) -> {
            try {
                var definition = loader.load(compiler.compile(source.apiVersion(), source.source()), sandbox);
                definitionTitles.put(id, definition.title());
                List<WPin.DataType> types = new ArrayList<>();
                for (var port : definition.inputs()) {
                    types.add(toPinType(port.type()));
                }
                definitionInputs.put(id, List.copyOf(types));
            } catch (RuntimeException ignored) {
                definitionTitles.put(id, id);
                definitionInputs.put(id, List.of());
            }
        });
    }

    private static WPin.DataType toPinType(ConnectionType type) {
        return switch (type) {
            case NUMBER -> WPin.DataType.NUMBER;
            case BOOLEAN -> WPin.DataType.BOOLEAN;
            case STRING -> WPin.DataType.STRING;
            case EVENT -> WPin.DataType.EVENT;
            case WIDGET -> WPin.DataType.WIDGET;
            case TABLE -> WPin.DataType.TABLE;
        };
    }

    private FrameNote selectedFrame() {
        for (WNode node : selectedNodes()) {
            if (node instanceof FrameNote frame) {
                return frame;
            }
        }
        return null;
    }

    private void selectFrameContents(FrameNote frame) {
        frame.ensureLayoutUpToDate();
        double left = frame.getX();
        double top = frame.getY();
        double right = left + frame.getWidth();
        double bottom = top + frame.getHeight();
        Set<UUID> inside = new HashSet<>();
        for (WNode node : editorGraph.getNodes()) {
            if (node == frame || node instanceof FrameNote || node instanceof CommentNote) {
                continue;
            }
            node.ensureLayoutUpToDate();
            if (node.getX() >= left
                    && node.getY() >= top
                    && node.getX() + node.getWidth() <= right
                    && node.getY() + node.getHeight() <= bottom) {
                inside.add(node.getId());
            }
        }
        for (WNode node : editorGraph.getNodes()) {
            node.setSelected(inside.contains(node.getId()));
        }
    }

    private void renderMinimap(GuiGraphics graphics) {
        int x = width - MINIMAP_WIDTH - MINIMAP_MARGIN;
        int y = height - MINIMAP_HEIGHT - MINIMAP_MARGIN;
        graphics.fill(x - 2, y - 2, x + MINIMAP_WIDTH + 2, y + MINIMAP_HEIGHT + 2, 0xC0111111);
        graphics.renderOutline(
                x - 2,
                y - 2,
                MINIMAP_WIDTH + 4,
                MINIMAP_HEIGHT + 4,
                ComputedEditorTheme.BORDER_DEFAULT);
        MinimapTransform transform =
                minimapDragTransform != null ? minimapDragTransform : minimapTransform();
        for (WNode node : editorCanvasNodes()) {
            node.ensureLayoutUpToDate();
            int left = (int) Math.round(transform.mapX(node.getX()));
            int top = (int) Math.round(transform.mapY(node.getY()));
            int right =
                    Math.max(left + 2, (int) Math.round(transform.mapX(node.getX() + node.getWidth())));
            int bottom =
                    Math.max(top + 2, (int) Math.round(transform.mapY(node.getY() + node.getHeight())));
            int color;
            if (node.isSelected()) {
                color = ComputedEditorTheme.ACCENT;
            } else if (node instanceof FrameNote) {
                color = 0x5B8FD0;
            } else if (node instanceof CommentNote) {
                color = 0xD0B840;
            } else {
                color = 0x9A9A9A;
            }
            graphics.fill(left, top, right, bottom, color & 0xFFFFFF | 0xAA000000);
            graphics.renderOutline(left, top, right - left, bottom - top, color);
        }
        MapRect viewport = minimapViewport(transform);
        if (viewport.width() <= 0.0 || viewport.height() <= 0.0) {
            return;
        }
        int left = (int) Math.floor(viewport.left());
        int top = (int) Math.floor(viewport.top());
        int right = Math.max(left + 2, (int) Math.ceil(viewport.right()));
        int bottom = Math.max(top + 2, (int) Math.ceil(viewport.bottom()));
        graphics.fill(left, top, right, bottom, ComputedEditorTheme.ACCENT & 0xFFFFFF | 0x33000000);
        graphics.renderOutline(left, top, right - left, bottom - top, ComputedEditorTheme.ACCENT);
    }

    private boolean handleMinimapClick(double mouseX, double mouseY) {
        int x = width - MINIMAP_WIDTH - MINIMAP_MARGIN;
        int y = height - MINIMAP_HEIGHT - MINIMAP_MARGIN;
        if (mouseX < x
                || mouseX >= x + MINIMAP_WIDTH
                || mouseY < y
                || mouseY >= y + MINIMAP_HEIGHT) {
            return false;
        }
        MinimapTransform transform = minimapTransform();
        if (!transform.contentBounds().contains(mouseX, mouseY)) {
            return true;
        }
        MapRect viewport = minimapViewport(transform);
        boolean inViewport = viewport.contains(mouseX, mouseY);
        minimapDragOffsetX = inViewport ? mouseX - viewport.centerX() : 0.0;
        minimapDragOffsetY = inViewport ? mouseY - viewport.centerY() : 0.0;
        minimapDragTransform = transform;
        minimapDragging = true;
        if (!inViewport) {
            panMinimapViewport(mouseX, mouseY);
        }
        return true;
    }

    private void panMinimapViewport(double mouseX, double mouseY) {
        if (minimapDragTransform == null) {
            return;
        }
        MapRect content = minimapDragTransform.contentBounds();
        double mapCenterX = net.minecraft.util.Mth.clamp(
                mouseX - minimapDragOffsetX, content.left(), content.right());
        double mapCenterY = net.minecraft.util.Mth.clamp(
                mouseY - minimapDragOffsetY, content.top(), content.bottom());
        editorCenterOnGraphPoint(
                minimapDragTransform.worldX(mapCenterX), minimapDragTransform.worldY(mapCenterY));
    }

    /**
     * Fits the union of all nodes and the current view (plus 4% padding) into the minimap panel,
     * mirroring Gadgets &amp; Gizmos: the frame tracks the view so nodes glide through it, and the
     * viewport outline clips against the fitted content bounds.
     */
    private MinimapTransform minimapTransform() {
        double minX = Math.min(editorGraphX(0), editorGraphX(width));
        double maxX = Math.max(editorGraphX(0), editorGraphX(width));
        double minY = Math.min(editorGraphY(0), editorGraphY(height));
        double maxY = Math.max(editorGraphY(0), editorGraphY(height));
        for (WNode node : editorCanvasNodes()) {
            node.ensureLayoutUpToDate();
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + node.getWidth());
            maxY = Math.max(maxY, node.getY() + node.getHeight());
        }
        double padX = Math.max(24.0, (maxX - minX) * 0.04);
        double padY = Math.max(24.0, (maxY - minY) * 0.04);
        int x = width - MINIMAP_WIDTH - MINIMAP_MARGIN;
        int y = height - MINIMAP_HEIGHT - MINIMAP_MARGIN;
        return MinimapTransform.fit(
                minX - padX,
                minY - padY,
                maxX + padX,
                maxY + padY,
                x + 6,
                y + 6,
                MINIMAP_WIDTH - 12,
                MINIMAP_HEIGHT - 12);
    }

    /** The main view's graph rectangle mapped into the minimap, clipped to the content bounds. */
    private MapRect minimapViewport(MinimapTransform transform) {
        MapRect content = transform.contentBounds();
        double x0 = transform.mapX(editorGraphX(0));
        double y0 = transform.mapY(editorGraphY(0));
        double x1 = transform.mapX(editorGraphX(width));
        double y1 = transform.mapY(editorGraphY(height));
        double left = net.minecraft.util.Mth.clamp(
                Math.min(x0, x1), content.left(), content.right());
        double top = net.minecraft.util.Mth.clamp(
                Math.min(y0, y1), content.top(), content.bottom());
        double right = net.minecraft.util.Mth.clamp(
                Math.max(x0, x1), content.left(), content.right());
        double bottom = net.minecraft.util.Mth.clamp(
                Math.max(y0, y1), content.top(), content.bottom());
        return new MapRect(
                Math.min(left, right),
                Math.min(top, bottom),
                Math.max(left, right),
                Math.max(top, bottom));
    }

    private record MinimapTransform(
            double worldLeft,
            double worldTop,
            double worldWidth,
            double worldHeight,
            double originX,
            double originY,
            double scale) {
        static MinimapTransform fit(
                double worldLeft,
                double worldTop,
                double worldRight,
                double worldBottom,
                double mapLeft,
                double mapTop,
                double mapWidth,
                double mapHeight) {
            double worldWidth = Math.max(1.0, worldRight - worldLeft);
            double worldHeight = Math.max(1.0, worldBottom - worldTop);
            double usableWidth = Math.max(1.0, mapWidth);
            double usableHeight = Math.max(1.0, mapHeight);
            double scale = Math.min(usableWidth / worldWidth, usableHeight / worldHeight);
            double originX = mapLeft + (usableWidth - worldWidth * scale) * 0.5;
            double originY = mapTop + (usableHeight - worldHeight * scale) * 0.5;
            return new MinimapTransform(
                    worldLeft, worldTop, worldWidth, worldHeight, originX, originY, scale);
        }

        double mapX(double worldX) {
            return originX + (worldX - worldLeft) * scale;
        }

        double mapY(double worldY) {
            return originY + (worldY - worldTop) * scale;
        }

        double worldX(double mapX) {
            return worldLeft + (mapX - originX) / scale;
        }

        double worldY(double mapY) {
            return worldTop + (mapY - originY) / scale;
        }

        MapRect contentBounds() {
            return new MapRect(
                    originX, originY, originX + worldWidth * scale, originY + worldHeight * scale);
        }
    }

    private record MapRect(double left, double top, double right, double bottom) {
        double width() {
            return Math.max(0.0, right - left);
        }

        double height() {
            return Math.max(0.0, bottom - top);
        }

        double centerX() {
            return (left + right) * 0.5;
        }

        double centerY() {
            return (top + bottom) * 0.5;
        }

        boolean contains(double x, double y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }
}
