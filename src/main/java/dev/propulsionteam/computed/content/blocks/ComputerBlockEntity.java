package dev.propulsionteam.computed.content.blocks;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import dev.propulsionteam.computed.content.items.ProgramDiskItem;
import dev.propulsionteam.computed.content.monitors.MonitorBlockEntity;
import dev.propulsionteam.computed.content.monitors.widgets.ButtonWidget;
import dev.propulsionteam.computed.content.monitors.widgets.CheckboxWidget;
import dev.propulsionteam.computed.content.monitors.widgets.ClockWidget;
import dev.propulsionteam.computed.content.monitors.widgets.LayoutManagedWidget;
import dev.propulsionteam.computed.content.monitors.widgets.MonitorWidgetLayout;
import dev.propulsionteam.computed.content.monitors.widgets.NumberWidget;
import dev.propulsionteam.computed.content.monitors.widgets.PanelWidget;
import dev.propulsionteam.computed.content.monitors.widgets.ProgressBarWidget;
import dev.propulsionteam.computed.content.monitors.widgets.SliderWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TableWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TerminalWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TextAlignment;
import dev.propulsionteam.computed.content.monitors.widgets.TextBlockWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TextInputWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TextWidget;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.content.monitors.widgets.WidgetDrawList;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.LuaGraphScheduler;
import dev.propulsionteam.computed.graph.LuaGraphTickResult;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpointHost;
import dev.propulsionteam.computed.lua.endpoint.BuiltinWidget;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.ServerEndpointExecutor;
import dev.propulsionteam.computed.lua.fs.ComputerIdAssigner;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import dev.propulsionteam.computed.network.ComputerEditPolicy;
import dev.propulsionteam.computed.network.ComputedNetworking;
import dev.propulsionteam.computed.network.GlassesHudWidgetsPayload;
import dev.propulsionteam.computed.persistence.ProgramDiskData;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class ComputerBlockEntity extends BaseContainerBlockEntity
        implements BuiltinEndpointHost, ServerEndpointExecutor {
    public static final int CONTAINER_SIZE = 9;
    /** Drive slot of the computer's container; holds at most one Program Disk. */
    public static final int DISK_SLOT_INDEX = CONTAINER_SIZE - 1;
    public static final String PROGRAM_TAG = "ComputedProgram";
    public static final String COMPUTER_ID_TAG = "ComputerId";
    /**
     * NBT key for the player-chosen display name. Deliberately not {@code "CustomName"}: the
     * vanilla container code parses that key as a Component JSON and logs a parse warning for
     * our plain strings. Worlds saved with the old key migrate on load.
     */
    private static final String COMPUTER_NAME_TAG = "ComputedName";
    private static final String LEGACY_NAME_TAG = "CustomName";
    public static final String DISK_TAG = "ComputerDisk";
    /** Marks that the drive slot holds a disk, even one without stored program data. */
    public static final String DISK_PRESENT_TAG = "ComputerDiskPresent";

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private ComputedProgramV3 program;
    private LuaGraphScheduler scheduler;
    private CompoundTag unreadableProgramData;
    private UUID computerUuid;
    private int computerId = -1;
    private String customName = "";
    private long programRevision;
    private transient boolean dropsHandled;
    private transient String lastGraphDiagnostics = "";
    private transient String lastWidgetWarning = "";
    private transient List<Widget> lastPortableWidgets = List.of();
    /** Latest glasses HUD lines from the graph; {@code null} entries keep the element's own text. */
    private transient WidgetDrawList glassesHudDrawList = WidgetDrawList.EMPTY;
    private transient long glassesHudHash = WidgetDrawList.EMPTY.hash();
    private transient boolean glassesHudSynced;
    private final int[] emittedRedstone = new int[Direction.values().length];
    private boolean redstoneUpdatePending;

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ComputedRegistries.COMPUTER_BLOCK_ENTITY.get(), pos, state);
        program = ComputedProgramV3.empty(stableGraphId(pos));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity computer) {
        if (level.isClientSide || computer.isRemoved()) {
            return;
        }
        LuaGraphScheduler active = computer.ensureScheduler();
        ComputedProgramV3 before = computer.program;
        LuaGraphTickResult tickResult = active.tick(false);
        computer.reportGraphDiagnostics(tickResult.diagnostics());
        computer.flushRedstoneUpdate();
        ComputedProgramV3 after = active.snapshot(computer.programRevision);
        computer.program = after;
        if (!before.persistentState().equals(after.persistentState())) {
            computer.setChanged();
        }
    }

    public int getEmittedRedstone(Direction fromNeighborTowardSelf) {
        return emittedRedstone[fromNeighborTowardSelf.getOpposite().ordinal()];
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.computed.computer");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        items.clear();
        for (int index = 0; index < Math.min(newItems.size(), items.size()); index++) {
            items.set(index, newItems.get(index));
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new ComputerPeripheralMenu(
                ComputedRegistries.COMPUTER_PERIPHERAL_MENU.get(),
                containerId,
                playerInventory,
                this);
    }

    public CompoundTag getGraphData() {
        CompoundTag envelope = new CompoundTag();
        envelope.put(PROGRAM_TAG, ProgramV3Codec.encode(snapshotProgram()));
        Peripherals.writePeripheralUnlockTag(this, envelope);
        ItemStack disk = diskInSlot();
        if (disk != null) {
            envelope.putBoolean(DISK_PRESENT_TAG, true);
            ProgramDiskData diskData = ProgramDiskItem.read(disk);
            if (diskData != null) {
                envelope.putByteArray(DISK_TAG, diskData.encode());
            }
        }
        return envelope;
    }

    public ComputedProgramV3 getProgram() {
        return snapshotProgram();
    }

    public long getProgramRevision() {
        return programRevision;
    }

    public boolean handleWidgetInput(UUID nodeId, double value) {
        return ensureScheduler().eventNode(
                nodeId,
                "input",
                org.luaj.vm2.LuaValue.valueOf(value));
    }

    /** Routes a submitted text-input value to the node's {@code input} event handler. */
    public boolean handleWidgetTextInput(UUID nodeId, String text) {
        return ensureScheduler().eventNode(
                nodeId,
                "input",
                org.luaj.vm2.LuaValue.valueOf(text == null ? "" : text));
    }

    public record ApplyGraphResult(boolean accepted, long serverRevision, String message) {
        static ApplyGraphResult accepted(long revision) {
            return new ApplyGraphResult(true, revision, "ok");
        }

        static ApplyGraphResult rejected(long revision, String message) {
            return new ApplyGraphResult(false, revision, message);
        }
    }

    public ApplyGraphResult applyGraphFromNetwork(CompoundTag tag, long expectedRevision) {
        String revisionError = ComputerEditPolicy.revision(programRevision, expectedRevision);
        if (revisionError != null) {
            return ApplyGraphResult.rejected(programRevision, revisionError);
        }
        String sizeError = validateEncodedSize(tag);
        if (sizeError != null) {
            return ApplyGraphResult.rejected(programRevision, sizeError);
        }
        ComputedProgramV3 incoming;
        try {
            ProgramV3Codec.LoadResult decoded =
                    ProgramV3Codec.decode(tag, worldPosition.toShortString(), ignored -> {});
            if (decoded.discardedLegacy()) {
                return ApplyGraphResult.rejected(programRevision, "legacy graph and clipboard formats are not accepted");
            }
            incoming = decoded.program();
        } catch (RuntimeException exception) {
            return ApplyGraphResult.rejected(
                    programRevision,
                    "program could not be decoded: " + exception.getMessage());
        }
        String validationError = validateProgram(incoming);
        if (validationError != null) {
            return ApplyGraphResult.rejected(programRevision, validationError);
        }
        ComputedProgramV3 stateMerged = preserveRuntimeState(incoming, snapshotProgram());
        LuaGraphScheduler nextScheduler;
        try {
            nextScheduler = new LuaGraphScheduler(stateMerged, getOrCreateUuid(), this);
            var error = nextScheduler.validationDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.severity()
                            == dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Severity.ERROR)
                    .findFirst();
            if (error.isPresent()) {
                nextScheduler.unload();
                return ApplyGraphResult.rejected(
                        programRevision,
                        "program validation failed: " + error.get().message());
            }
        } catch (RuntimeException exception) {
            return ApplyGraphResult.rejected(
                    programRevision,
                    "program validation failed: " + exception.getMessage());
        }
        if (scheduler != null) {
            scheduler.unload();
        }
        programRevision++;
        program = stateMerged.withRevision(programRevision);
        scheduler = nextScheduler;
        unreadableProgramData = null;
        setChanged();
        return ApplyGraphResult.accepted(programRevision);
    }

    private String validateProgram(ComputedProgramV3 candidate) {
        String shapeError = ComputerEditPolicy.programShape(candidate);
        return shapeError != null
                ? shapeError
                : ComputerEditPolicy.computerType(candidate, isCreativeComputer());
    }

    private static ComputedProgramV3 preserveRuntimeState(
            ComputedProgramV3 incoming,
            ComputedProgramV3 authoritative) {
        Map<UUID, GraphNode> currentNodes = new LinkedHashMap<>();
        authoritative.rootGraph().nodes().forEach(node -> currentNodes.put(node.id(), node));
        Map<UUID, CompoundTag> merged = new LinkedHashMap<>(incoming.persistentState());
        incoming.rootGraph().nodes().forEach(node -> {
            GraphNode current = currentNodes.get(node.id());
            CompoundTag state = authoritative.persistentState().get(node.id());
            if (current != null
                    && state != null
                    && current.definitionId().equals(node.definitionId())
                    && current.definitionHash().equals(node.definitionHash())) {
                merged.put(node.id(), state);
            }
        });
        return new ComputedProgramV3(
                incoming.revision(),
                incoming.rootGraph(),
                incoming.library(),
                merged,
                incoming.metadata());
    }

    private static String validateEncodedSize(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(tag, output);
            }
            return ComputerEditPolicy.encodedSize(bytes.size());
        } catch (IOException | RuntimeException exception) {
            return ComputerEditPolicy.encodedSize(-1);
        }
    }

    public boolean tryInsertPeripheralFromHand(ItemStack stack) {
        if (!Peripherals.isPeripheral(stack)) {
            return false;
        }
        ItemStack one = stack.split(1);
        for (int index = 0; index < CONTAINER_SIZE; index++) {
            if (getItem(index).isEmpty() && Peripherals.mayPlaceInComputer(this, index, one)) {
                setItem(index, one);
                return true;
            }
        }
        stack.grow(1);
        return false;
    }

    /** The program disk sitting in the drive slot, or {@code null} when the drive is empty. */
    @Nullable
    public ItemStack diskInSlot() {
        ItemStack stack = getItem(DISK_SLOT_INDEX);
        return stack.getItem() instanceof ProgramDiskItem ? stack : null;
    }

    /** Moves one program disk from the hand into an empty drive slot. */
    public boolean tryInsertDiskFromHand(ItemStack stack) {
        if (!(stack.getItem() instanceof ProgramDiskItem) || !getItem(DISK_SLOT_INDEX).isEmpty()) {
            return false;
        }
        setItem(DISK_SLOT_INDEX, stack.split(1));
        return true;
    }

    public boolean hasPeripheralEquipped(net.minecraft.resources.ResourceLocation nodeTypeId) {
        return true;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        computerUuid = tag.hasUUID("ComputerUUID") ? tag.getUUID("ComputerUUID") : null;
        computerId = tag.contains(COMPUTER_ID_TAG) ? tag.getInt(COMPUTER_ID_TAG) : -1;
        loadComputerName(tag);
        loadProgramData(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
            ContainerHelper.saveAllItems(tag, items, true, registries);
        writeStoredProgram(tag);
        if (computerUuid != null) {
            tag.putUUID("ComputerUUID", computerUuid);
        }
        if (computerId >= 0) {
            tag.putInt(COMPUTER_ID_TAG, computerId);
        }
        if (!customName.isEmpty()) {
            tag.putString(COMPUTER_NAME_TAG, customName);
        }
    }

    /** Reads {@link #COMPUTER_NAME_TAG}, migrating the pre-rename plain-string {@code CustomName}. */
    private void loadComputerName(CompoundTag tag) {
        if (tag.contains(COMPUTER_NAME_TAG)) {
            customName = tag.getString(COMPUTER_NAME_TAG);
        } else if (tag.contains(LEGACY_NAME_TAG, Tag.TAG_STRING)) {
            customName = tag.getString(LEGACY_NAME_TAG);
        }
    }

    /** Player-chosen display name (empty = unnamed). Named without the Nameable Component clash. */
    public String computerName() {
        return customName;
    }

    /** Sets the sanitized player-chosen display name. */
    public void setComputerName(String name) {
        customName = sanitizeComputerName(name);
    }

    /** Trim, strip control characters, cap length; empty means "unnamed". */
    public static String sanitizeComputerName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder(32);
        for (char c : name.strip().toCharArray()) {
            if (clean.length() >= 32) {
                break;
            }
            if (c >= 0x20 && c != 0x7F) {
                clean.append(c);
            }
        }
        return clean.toString().strip();
    }

    public UUID getOrCreateUuid() {
        if (computerUuid == null) {
            computerUuid = UUID.randomUUID();
            setChanged();
        }
        return computerUuid;
    }

    /** Numeric, ComputerCraft-style id; {@code -1} while the computer has none yet. */
    public int computerId() {
        return computerId;
    }

    /** Allocates the numeric ID on first use for {@code computed:net} addressing. */
    @Override
    public int numericComputerId() {
        return getOrCreateComputerId();
    }

    /**
     * Returns the numeric computer id, allocating and persisting one from
     * {@code <world>/computed/ids.json} on first use (server side only).
     */
    public int getOrCreateComputerId() {
        if (computerId >= 0) {
            return computerId;
        }
        if (level instanceof ServerLevel serverLevel && serverLevel.getServer() != null) {
            Path computedDirectory = serverLevel.getServer().getWorldPath(LevelResource.ROOT).resolve("computed");
            int allocated = ComputerIdAssigner.allocate(computedDirectory);
            if (allocated >= 0) {
                computerId = allocated;
                setChanged();
                pushUpdateToClients();
            }
        }
        return computerId;
    }

    /**
     * Applies a manually chosen numeric id (Set Computer ID dialog, server side only). The
     * filesystem reattaches to the new root on the next tick; the caller is responsible for
     * advancing {@link ComputerIdAssigner} first so future allocations skip this id.
     */
    public void setComputerId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Computer ID must be non-negative");
        }
        if (computerId == id) {
            return;
        }
        computerId = id;
        setChanged();
        pushUpdateToClients();
    }

    /**
     * Marks this computer's container state changed and pushes the update tag (computer id,
     * drive slot, program) to tracking clients.
     */
    public void pushUpdateToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasStoredState() {
        if (unreadableProgramData != null && !unreadableProgramData.isEmpty()) {
            return true;
        }
        if (!program.rootGraph().nodes().isEmpty() || !program.library().isEmpty()) {
            return true;
        }
        return items.stream().anyMatch(stack -> !stack.isEmpty());
    }

    public void markDropsHandled() {
        dropsHandled = true;
    }

    public boolean dropsHandled() {
        return dropsHandled;
    }

    @Override
    public void setRemoved() {
        if (scheduler != null) {
            scheduler.unload();
            scheduler = null;
        }
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(COMPUTER_ID_TAG, computerId);
        if (!customName.isEmpty()) {
            tag.putString(COMPUTER_NAME_TAG, customName);
        }
        writeStoredProgram(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        computerId = tag.contains(COMPUTER_ID_TAG) ? tag.getInt(COMPUTER_ID_TAG) : computerId;
        loadComputerName(tag);
        loadProgramData(tag);
    }

    @Override
    public double worldTime() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return 0;
        }
        requireServerThread(serverLevel);
        return serverLevel.getDayTime();
    }

    @Override
    public double[] position() {
        Vec3 position = Vec3.atCenterOf(worldPosition);
        return new double[] {position.x, position.y, position.z};
    }

    @Override
    public double[] rotation() {
        Direction facing = getBlockState().hasProperty(ComputerBlock.FACING)
                ? getBlockState().getValue(ComputerBlock.FACING)
                : Direction.NORTH;
        return new double[] {facing.toYRot(), 0, 0};
    }

    @Override
    public int redstoneInput(String face) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return 0;
        }
        requireServerThread(serverLevel);
        Direction worldFace = worldFace(face);
        if (worldFace == null) {
            return 0;
        }
        BlockPos neighbor = worldPosition.relative(worldFace);
        return serverLevel.getSignal(neighbor, worldFace);
    }

    @Override
    public int comparatorInput(String face) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return 0;
        }
        requireServerThread(serverLevel);
        Direction worldFace = worldFace(face);
        if (worldFace == null) {
            return 0;
        }
        BlockPos neighbor = worldPosition.relative(worldFace);
        BlockState target = serverLevel.getBlockState(neighbor);
        return target.hasAnalogOutputSignal()
                ? target.getAnalogOutputSignal(serverLevel, neighbor)
                : serverLevel.getSignal(neighbor, worldFace);
    }

    @Override
    public boolean blockPresent(String face) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return false;
        }
        requireServerThread(serverLevel);
        Direction worldFace = worldFace(face);
        return worldFace != null
                && !serverLevel.getBlockState(worldPosition.relative(worldFace)).isAir();
    }

    @Override
    public void redstoneOutput(String face, int power) {
        Level currentLevel = level;
        if (!(currentLevel instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        LuaGraphScheduler expectedScheduler = scheduler;
        if (!server.isSameThread()) {
            server.execute(() -> applyRedstoneOutput(
                    serverLevel,
                    expectedScheduler,
                    face,
                    power));
            return;
        }

        applyRedstoneOutput(serverLevel, expectedScheduler, face, power);
    }

    private void applyRedstoneOutput(
            ServerLevel expectedLevel,
            LuaGraphScheduler expectedScheduler,
            String face,
            int power) {
        requireServerThread(expectedLevel);
        if (isRemoved()
                || level != expectedLevel
                || scheduler != expectedScheduler
                || expectedLevel.getBlockEntity(worldPosition) != this) {
            return;
        }

        Direction worldFace = worldFace(face);
        if (worldFace == null) {
            return;
        }

        int clamped = net.minecraft.util.Mth.clamp(power, 0, 15);
        if (emittedRedstone[worldFace.ordinal()] == clamped) {
            return;
        }
        emittedRedstone[worldFace.ordinal()] = clamped;
        redstoneUpdatePending = true;
        setChanged();
    }

    private void flushRedstoneUpdate() {
        if (!redstoneUpdatePending || level == null || level.isClientSide) {
            return;
        }
        redstoneUpdatePending = false;
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public void showWidgets(String target, List<BuiltinWidget> definitions) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        requireServerThread(serverLevel);
        List<Widget> widgets = new java.util.ArrayList<>();
        int dropped = 0;
        for (BuiltinWidget definition : definitions) {
            Widget widget = toWidget(definition);
            if (widget == null) {
                dropped++;
            } else {
                widgets.add(widget);
            }
        }
        lastPortableWidgets = List.copyOf(widgets);
        applyWidgets(target, widgets, dropped);
    }

    /** Widgets from the last show call, before line layout, for portable monitor streaming. */
    public List<Widget> lastShownWidgets() {
        return lastPortableWidgets;
    }

    @Override
    public void showGlassesHud(List<BuiltinWidget> definitions) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        requireServerThread(serverLevel);
        List<Widget> widgets = new java.util.ArrayList<>();
        for (BuiltinWidget definition : definitions) {
            Widget widget = toWidget(definition);
            if (widget != null) {
                widgets.add(widget);
            }
        }
        widgets = MonitorWidgetLayout.resolveFitted(
                widgets, PortableMonitorItem.CANVAS_W, PortableMonitorItem.CANVAS_H);
        glassesHudDrawList = new WidgetDrawList(widgets);
        long hash = glassesHudDrawList.hash();
        if (glassesHudSynced && hash == glassesHudHash) {
            return;
        }
        glassesHudHash = hash;
        glassesHudSynced = true;
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (ComputedNetworking.playerCarriesLinkTo(player, worldPosition)) {
                sendGlassesHud(player);
            }
        }
    }

    /**
     * Answers a client glasses HUD request when the known hash differs from the current state,
     * mirroring the portable monitor's hash-gated pull.
     */
    public void sendGlassesHudTo(ServerPlayer player, long knownHash) {
        if (glassesHudHash == knownHash) {
            return;
        }
        sendGlassesHud(player);
    }

    private void sendGlassesHud(ServerPlayer player) {
        if (level == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new GlassesHudWidgetsPayload(
                level.dimension().location().toString(),
                worldPosition,
                glassesHudHash,
                wireBytes(glassesHudDrawList)));
    }

    /** Wire encoding of a draw list, identical to the portable display frame bytes. */
    private static byte[] wireBytes(WidgetDrawList drawList) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            drawList.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    /**
     * Logs graph diagnostics whenever the reported set changes, so runtime failures (Lua errors,
     * failed endpoint calls, dropped definitions) are visible in the game log instead of being
     * discarded.
     */
    private void reportGraphDiagnostics(List<ComputedDiagnostic> diagnostics) {
        List<String> lines = diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() != ComputedDiagnostic.Severity.INFO)
                .map(diagnostic -> diagnostic.severity()
                        + " [" + diagnostic.phase() + "/" + diagnostic.code() + "] "
                        + diagnostic.message() + " (node " + diagnostic.nodeId() + ")")
                .sorted()
                .toList();
        String report = String.join("\n", lines);
        if (report.equals(lastGraphDiagnostics)) {
            return;
        }
        lastGraphDiagnostics = report;
        if (!report.isEmpty()) {
            Computed.LOGGER.warn("Computer at {} reported graph diagnostics:\n{}", worldPosition, report);
        }
    }

    /** Logs widget delivery problems once per distinct message to avoid per-tick spam. */
    private void warnWidgets(String message) {
        if (message.equals(lastWidgetWarning)) {
            return;
        }
        lastWidgetWarning = message;
        Computed.LOGGER.warn("Monitor widgets for computer at {}: {}", worldPosition, message);
    }

    private void applyWidgets(String target, List<Widget> widgets, int dropped) {
        Direction direction = worldFace(target);
        if (direction == null || level == null || level.isClientSide) {
            warnWidgets("cannot resolve monitor face '" + target + "' — set the Monitor node's face field to the computer side the monitor is attached to (portable monitors still update)");
            return;
        }
        BlockPos targetPos = worldPosition.relative(direction);
        var targetEntity = level.getBlockEntity(targetPos);
        if (!(targetEntity instanceof MonitorBlockEntity monitor)) {
            warnWidgets("no monitor at " + direction + " (" + targetPos + ") — attach a monitor multiblock to that computer face, or use a portable monitor (paired monitors still update)");
            return;
        }
        MonitorBlockEntity origin = monitor.findOrigin();
        if (origin == null) {
            warnWidgets("monitor origin for cell at " + targetPos + " is not loaded or not part of a formed monitor");
            return;
        }
        int supplied = widgets.size();
        int screenWidth = origin.getWidth() * ComputedNetworking.SCREEN_PX_PER_BLOCK;
        int screenHeight = origin.getHeight() * ComputedNetworking.SCREEN_PX_PER_BLOCK;
        widgets = MonitorWidgetLayout.resolve(widgets, screenWidth, screenHeight);
        if (dropped > 0) {
            warnWidgets(dropped + " widget record(s) had an unknown type and were dropped");
        }
        if (widgets.size() < supplied) {
            warnWidgets((supplied - widgets.size())
                    + " widget(s) were dropped by line layout collisions (two widgets assigned to the same line — change a widget's Line field)");
        }
        origin.bindOwner(worldPosition);
        origin.setDrawList(new WidgetDrawList(widgets));
    }

    @Override
    public void runCommand(String commandText) {
        if (!isCreativeComputer()) {
            throw new IllegalStateException(
                    "Run Command is only available on a Creative Computer");
        }
        if (commandText == null
                || commandText.isBlank()
                || !(level instanceof ServerLevel serverLevel)
                || isRemoved()) {
            return;
        }
        requireServerThread(serverLevel);
        MinecraftServer server = serverLevel.getServer();
        String command = commandText.startsWith("/") ? commandText.substring(1) : commandText;
        if (command.isBlank()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(serverLevel)
                .withPosition(center)
                .withPermission(4)
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    public boolean isCreativeComputer() {
        return false;
    }

    @Override
    public CompletionStage<EndpointResult> submitServerEndpoint(
            Callable<EndpointResult> endpointCall) {
        if (!(level instanceof ServerLevel expectedLevel) || isRemoved()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Computer is not available"));
        }

        MinecraftServer server = expectedLevel.getServer();
        LuaGraphScheduler expectedScheduler = scheduler;
        CompletableFuture<EndpointResult> result = new CompletableFuture<>();
        try {
            server.execute(() -> {
                if (isRemoved()
                        || level != expectedLevel
                        || scheduler != expectedScheduler
                        || expectedLevel.getBlockEntity(worldPosition) != this) {
                    result.completeExceptionally(
                            new IllegalStateException(
                                    "Computer or program changed before endpoint execution"));
                    return;
                }

                try {
                    result.complete(endpointCall.call());
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    private LuaGraphScheduler ensureScheduler() {
        if (scheduler == null) {
            scheduler = new LuaGraphScheduler(program, getOrCreateUuid(), this);
        }
        return scheduler;
    }

    private ComputedProgramV3 snapshotProgram() {
        if (scheduler != null) {
            program = scheduler.snapshot(programRevision);
        }
        return program.withRevision(programRevision);
    }

    private void writeStoredProgram(CompoundTag target) {
        if (unreadableProgramData != null && !unreadableProgramData.isEmpty()) {
            for (String key : unreadableProgramData.getAllKeys()) {
                Tag value = unreadableProgramData.get(key);
                if (value != null) {
                    target.put(key, value.copy());
                }
            }
            return;
        }
        target.put(PROGRAM_TAG, ProgramV3Codec.encode(snapshotProgram()));
    }

    private void loadProgramData(CompoundTag source) {
        if (scheduler != null) {
            scheduler.unload();
            scheduler = null;
        }
        if (!containsProgramData(source)) {
            program = ComputedProgramV3.empty(stableGraphId(worldPosition));
            programRevision = 0;
            unreadableProgramData = null;
            return;
        }
        try {
            ProgramV3Codec.LoadResult decoded = ProgramV3Codec.decode(
                    source,
                    worldPosition.toShortString(),
                    Computed.LOGGER::warn);
            program = decoded.program();
            programRevision = program.revision();
            unreadableProgramData = null;
        } catch (RuntimeException exception) {
            Computed.LOGGER.error(
                    "Could not load Computed format-3 program at {}; preserving raw program data",
                    worldPosition,
                    exception);
            program = ComputedProgramV3.empty(stableGraphId(worldPosition));
            programRevision = rawProgramRevision(source);
            unreadableProgramData = copyProgramFields(source);
        }
    }

    private static CompoundTag copyProgramFields(CompoundTag source) {
        CompoundTag preserved = new CompoundTag();
        for (String key : List.of(PROGRAM_TAG, "formatVersion", "revision", "graph", "library", "states", "metadata")) {
            Tag value = source.get(key);
            if (value != null) {
                preserved.put(key, value.copy());
            }
        }
        return preserved;
    }

    private static boolean containsProgramData(CompoundTag source) {
        return source.contains(PROGRAM_TAG, Tag.TAG_COMPOUND)
                || source.contains("formatVersion")
                || source.contains("ComputerGraph", Tag.TAG_COMPOUND)
                || source.contains("ComputerFunctions")
                || source.contains("graph", Tag.TAG_COMPOUND)
                || source.contains("nodes");
    }

    private static long rawProgramRevision(CompoundTag source) {
        long revision = Math.max(0, source.getLong("revision"));
        if (source.contains(PROGRAM_TAG, Tag.TAG_COMPOUND)) {
            revision = Math.max(revision, source.getCompound(PROGRAM_TAG).getLong("revision"));
        }
        return revision;
    }

    private static UUID stableGraphId(BlockPos pos) {
        return UUID.nameUUIDFromBytes(
                ("computed:graph:" + pos.toShortString()).getBytes(StandardCharsets.UTF_8));
    }

    public Direction worldFaceForEndpoint(String name) {
        if (level instanceof ServerLevel serverLevel) {
            requireServerThread(serverLevel);
        }
        return worldFace(name);
    }

    private static void requireServerThread(ServerLevel serverLevel) {
        if (!serverLevel.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Computer world access attempted outside the server thread");
        }
    }

    private Direction worldFace(String name) {
        if (name == null) {
            return null;
        }
        Direction facing = getBlockState().hasProperty(ComputerBlock.FACING)
                ? getBlockState().getValue(ComputerBlock.FACING)
                : Direction.NORTH;
        return switch (name.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "front" -> facing;
            case "back" -> facing.getOpposite();
            case "left" -> facing.getCounterClockWise();
            case "right" -> facing.getClockWise();
            case "top", "up" -> Direction.UP;
            case "bottom", "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> null;
        };
    }

    private static Widget toWidget(BuiltinWidget widget) {
        Map<String, Object> properties = widget.properties();
        Widget raw = switch (widget.type()) {
            case "text" -> new TextWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    text(properties, "text"),
                    widget.color(),
                    alignment(properties));
            case "number" -> new NumberWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    number(properties, "value"),
                    text(properties, "text"),
                    widget.color(),
                    alignment(properties));
            case "clock" -> new ClockWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    widget.color(),
                    flag(properties, "show_seconds"),
                    alignment(properties));
            case "button" -> new ButtonWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    text(properties, "label"),
                    widget.color());
            case "checkbox" -> new CheckboxWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    text(properties, "label"),
                    widget.color(),
                    flag(properties, "checked"));
            case "text_input" -> new TextInputWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    text(properties, "placeholder"),
                    text(properties, "value"),
                    widget.color());
            case "slider" -> new SliderWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    number(properties, "value"),
                    number(properties, "minimum"),
                    number(properties, "maximum"),
                    widget.color(),
                    number(properties, "step"));
            case "progress" -> new ProgressBarWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    number(properties, "value"),
                    number(properties, "maximum"),
                    widget.color(),
                    (int) number(properties, "segments"));
            case "terminal" -> new TerminalWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    TerminalWidget.resolveRows(
                            properties.get("rows"),
                            widget.color(),
                            argb(properties, "bg", 0xFF000000)),
                    widget.color(),
                    argb(properties, "bg", 0xFF000000),
                    terminalScale(properties),
                    cursorIndex(properties, "cursor_x"),
                    cursorIndex(properties, "cursor_y"));
            case "panel" -> new PanelWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    widget.color(),
                    argb(properties, "border", 0));
            case "text_block" -> new TextBlockWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    text(properties, "text"),
                    widget.color(),
                    argb(properties, "bg", 0),
                    alignment(properties));
            case "table" -> new TableWidget(
                    widget.id(),
                    widget.x(),
                    widget.y(),
                    widget.width(),
                    widget.height(),
                    stringList(properties, "table_columns"),
                    cellRows(properties, "table_rows"),
                    widget.color(),
                    argb(properties, "header_bg", 0xFF202020),
                    argb(properties, "cell_fg", 0xFFFFFFFF),
                    argb(properties, "cell_bg", 0xFF000000));
            default -> null;
        };
        if (raw == null) {
            return null;
        }
        LayoutManagedWidget.LayoutMode mode =
                "manual".equalsIgnoreCase(text(properties, "layout_mode"))
                        ? LayoutManagedWidget.LayoutMode.MANUAL
                        : LayoutManagedWidget.LayoutMode.LINE;
        LayoutManagedWidget.Fit fit =
                "fill".equalsIgnoreCase(text(properties, "fit"))
                        ? LayoutManagedWidget.Fit.FILL
                        : LayoutManagedWidget.Fit.AUTO;
        return new LayoutManagedWidget(
                raw,
                mode,
                Math.max(1, (int) Math.round(number(properties, "line"))),
                Math.max(1, Math.round(number(properties, "span"))),
                fit);
    }

    private static String text(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        return value instanceof String text ? text : "";
    }

    private static double number(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static int argb(Map<String, Object> properties, String key, int fallback) {
        Object value = properties.get(key);
        return value instanceof Number number ? (int) (long) number.doubleValue() : fallback;
    }

    private static float terminalScale(Map<String, Object> properties) {
        double scale = number(properties, "text_scale");
        if (!(scale > 0)) {
            return 1f;
        }
        return (float) Math.min(4, Math.max(0.25, scale));
    }

    private static int cursorIndex(Map<String, Object> properties, String key) {
        int value = (int) (long) number(properties, key);
        return Math.min(4096, Math.max(-1, value));
    }

    private static List<String> stringList(Map<String, Object> properties, String key) {
        if (properties.get(key) instanceof List<?> values) {
            List<String> strings = new java.util.ArrayList<>(values.size());
            for (Object value : values) {
                if (value instanceof String string) {
                    strings.add(string);
                }
            }
            return List.copyOf(strings);
        }
        return List.of();
    }

    private static List<List<String>> cellRows(Map<String, Object> properties, String key) {
        if (properties.get(key) instanceof List<?> values) {
            List<List<String>> rows = new java.util.ArrayList<>(values.size());
            for (Object value : values) {
                if (value instanceof List<?> cells) {
                    List<String> row = new java.util.ArrayList<>(cells.size());
                    for (Object cell : cells) {
                        row.add(cell instanceof String string ? string : "");
                    }
                    rows.add(List.copyOf(row));
                }
            }
            return List.copyOf(rows);
        }
        return List.of();
    }

    private static boolean flag(Map<String, Object> properties, String key) {
        return Boolean.TRUE.equals(properties.get(key));
    }

    private static TextAlignment alignment(Map<String, Object> properties) {
        return switch (text(properties, "alignment").toLowerCase(java.util.Locale.ROOT)) {
            case "right" -> TextAlignment.RIGHT;
            case "center" -> TextAlignment.CENTER;
            default -> TextAlignment.LEFT;
        };
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
