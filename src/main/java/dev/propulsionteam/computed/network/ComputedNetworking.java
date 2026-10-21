package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.ComputerEditorBridge;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.PackageTransferBridge;
import dev.propulsionteam.computed.PortableDisplayBridge;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.items.LinkCardItem;
import dev.propulsionteam.computed.content.items.PortableLinkData;
import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import dev.propulsionteam.computed.content.items.ProgramDiskItem;
import dev.propulsionteam.computed.content.monitors.MonitorBlockEntity;
import dev.propulsionteam.computed.content.monitors.widgets.MonitorWidgetLayout;
import dev.propulsionteam.computed.content.monitors.widgets.SliderWidget;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.content.monitors.widgets.WidgetDrawList;
import dev.propulsionteam.computed.integration.curios.CuriosIntegration;
import dev.propulsionteam.computed.lua.fs.ComputerIdAssigner;
import dev.propulsionteam.computed.persistence.LegacyPackageMigration;
import dev.propulsionteam.computed.persistence.PackageTransferService;
import dev.propulsionteam.computed.persistence.ProgramDiskData;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import io.netty.buffer.Unpooled;

public final class ComputedNetworking {
    /** Pixels per monitor block; widget x/y/w/h are in this coordinate system. */
    public static final int SCREEN_PX_PER_BLOCK = 64;
    /** Monitor model/texture pixels reserved by the bezel on each outer screen edge. */
    public static final int BEZEL_MODEL_PIXELS = 2;
    /** Minecraft model pixels per full block edge. */
    public static final int MODEL_PIXELS_PER_BLOCK = 16;
    /** Bezel inset in widget coordinate pixels for one outer edge. */
    public static final int SCREEN_BEZEL_PX =
            SCREEN_PX_PER_BLOCK * BEZEL_MODEL_PIXELS / MODEL_PIXELS_PER_BLOCK;

    private ComputedNetworking() {}

    /** Assembled-but-incomplete package writes keyed by player and operation id. */
    private static final Map<WriteKey, PackageWriteAssembly> WRITE_ASSEMBLIES = new ConcurrentHashMap<>();
    private static final int MAX_WRITE_ASSEMBLIES_PER_PLAYER = 4;
    private static final long WRITE_ASSEMBLY_TIMEOUT_MS = 60_000L;

    private record WriteKey(UUID player, int opId) {}

    private static final class PackageWriteAssembly {
        private final BlockPos pos;
        private final int kind;
        private final int mode;
        private final int target;
        private final String path;
        private final int totalSize;
        private final long createdAt = System.currentTimeMillis();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private PackageWriteAssembly(
                BlockPos pos, int kind, String path, int mode, int target, int totalSize) {
            PackageTransferService.kind(kind);
            PackageTransferService.mode(mode);
            PackageTransferService.target(target);
            this.pos = pos;
            this.kind = kind;
            this.path = path;
            this.mode = mode;
            this.target = target;
            this.totalSize = totalSize;
        }

        private boolean matches(PackageWritePayload payload) {
            return pos.equals(payload.computerPos())
                    && kind == payload.kind()
                    && mode == payload.mode()
                    && target == payload.target()
                    && totalSize == payload.totalSize()
                    && path.equals(payload.path());
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ComputedNetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                OpenComputerEditorPayload.TYPE,
                OpenComputerEditorPayload.STREAM_CODEC,
                ComputedNetworking::handleOpenEditor);
        registrar.playToServer(
                SaveComputerGraphPayload.TYPE,
                SaveComputerGraphPayload.STREAM_CODEC,
                ComputedNetworking::handleSaveGraph);
        registrar.playToClient(
                ComputerGraphSaveResultPayload.TYPE,
                ComputerGraphSaveResultPayload.STREAM_CODEC,
                ComputedNetworking::handleSaveGraphResult);
        registrar.playToServer(
                MonitorClickPayload.TYPE,
                MonitorClickPayload.STREAM_CODEC,
                ComputedNetworking::handleMonitorClick);
        registrar.playToServer(
                PackageListPayload.TYPE,
                PackageListPayload.STREAM_CODEC,
                ComputedNetworking::handlePackageList);
        registrar.playToServer(
                PackageReadPayload.TYPE,
                PackageReadPayload.STREAM_CODEC,
                ComputedNetworking::handlePackageRead);
        registrar.playToServer(
                PackageWritePayload.TYPE,
                PackageWritePayload.STREAM_CODEC,
                ComputedNetworking::handlePackageWrite);
        registrar.playToClient(
                PackageListResultPayload.TYPE,
                PackageListResultPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> PackageTransferBridge.listResult(
                        payload.kind(), payload.folder(), payload.error(), payload.entries())));
        registrar.playToClient(
                PackageReadResultPayload.TYPE,
                PackageReadResultPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> PackageTransferBridge.readResult(
                        payload.kind(), payload.path(), payload.error(), payload.data())));
        registrar.playToClient(
                PackageWriteResultPayload.TYPE,
                PackageWriteResultPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> PackageTransferBridge.writeResult(
                        payload.opId(), payload.status(), payload.fileName(), payload.error())));
        registrar.playToServer(
                SetComputerIdPayload.TYPE,
                SetComputerIdPayload.STREAM_CODEC,
                ComputedNetworking::handleSetComputerId);
        registrar.playToClient(
                SetComputerIdResultPayload.TYPE,
                SetComputerIdResultPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> PackageTransferBridge.computerIdResult(
                        payload.accepted(), payload.computerId(), payload.message())));
        registrar.playToServer(
                PullPortableDisplayPayload.TYPE,
                PullPortableDisplayPayload.STREAM_CODEC,
                ComputedNetworking::handlePullPortableDisplay);
        registrar.playToServer(
                SetPortableSourcePayload.TYPE,
                SetPortableSourcePayload.STREAM_CODEC,
                ComputedNetworking::handleSetPortableSource);
        registrar.playToServer(
                RemovePortableSourcePayload.TYPE,
                RemovePortableSourcePayload.STREAM_CODEC,
                ComputedNetworking::handleRemovePortableSource);
        registrar.playToServer(
                WidgetTextInputPayload.TYPE,
                WidgetTextInputPayload.STREAM_CODEC,
                ComputedNetworking::handleWidgetTextInput);
        registrar.playToClient(
                OpenWidgetTextInputPayload.TYPE,
                OpenWidgetTextInputPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.propulsionteam.computed.WidgetTextInputBridge.open(
                        payload.originPos(), payload.widgetId(), payload.initialValue())));
        registrar.playToServer(
                SetComputerNamePayload.TYPE,
                SetComputerNamePayload.STREAM_CODEC,
                ComputedNetworking::handleSetComputerName);
        registrar.playToClient(
                PortableDisplayPayload.TYPE,
                PortableDisplayPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> PortableDisplayBridge.deliver(
                        payload.computerPos(), payload.drawListBytes(), payload.computerName())));
    }

    public static OpenComputerEditorPayload openPayload(BlockPos pos, long serverRevision, CompoundTag graphTag) {
        return new OpenComputerEditorPayload(pos, serverRevision, graphTag);
    }

    private static void handleOpenEditor(OpenComputerEditorPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ComputerEditorBridge.open(payload.pos(), payload.serverRevision(), payload.graphTag()));
    }

    private static void handleSaveGraphResult(ComputerGraphSaveResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ComputerEditorBridge.saveResult(
                payload.pos(),
                payload.accepted(),
                payload.serverRevision(),
                payload.editorRevision(),
                payload.message()));
    }

    private static void handleSaveGraph(SaveComputerGraphPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pos = payload.pos();
            double distanceSquared = player.distanceToSqr(Vec3.atCenterOf(pos));
            String accessError = ComputerEditPolicy.access(
                    distanceSquared,
                    player.mayBuild(),
                    player.level().mayInteract(player, pos));
            if (accessError != null) {
                Computed.LOGGER.debug(
                        "Rejected graph save from {} at {} ({})",
                        player.getGameProfile().getName(),
                        pos,
                        accessError);
                rejectGraphSave(player, payload, -1L, accessError);
                return;
            }
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof ComputerBlockEntity computer) {
                try {
                    ComputerBlockEntity.ApplyGraphResult result = computer.applyGraphFromNetwork(
                            payload.graphTag(), payload.expectedServerRevision());
                    PacketDistributor.sendToPlayer(player, new ComputerGraphSaveResultPayload(
                            pos,
                            result.accepted(),
                            result.serverRevision(),
                            payload.editorRevision(),
                            result.message()));
                } catch (Exception e) {
                    Computed.LOGGER.error(
                            "Failed to apply graph save from {} at {}",
                            player.getGameProfile().getName(),
                            pos,
                            e);
                    PacketDistributor.sendToPlayer(player, new ComputerGraphSaveResultPayload(
                            pos,
                            false,
                            computer.getProgramRevision(),
                            payload.editorRevision(),
                            "internal validation error"));
                }
            } else {
                Computed.LOGGER.debug(
                        "Rejected graph save from {} at {} (no ComputerBlockEntity)",
                        player.getGameProfile().getName(),
                        pos);
                rejectGraphSave(player, payload, -1L, "computer is no longer available");
            }
        });
    }

    private static void rejectGraphSave(
            ServerPlayer player, SaveComputerGraphPayload payload, long serverRevision, String message) {
        PacketDistributor.sendToPlayer(player, new ComputerGraphSaveResultPayload(
                payload.pos(), false, serverRevision, payload.editorRevision(), message));
    }

    private static void handleMonitorClick(MonitorClickPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            BlockPos originPos = payload.originPos();
            BlockEntity be = player.level().getBlockEntity(originPos);

            ComputerBlockEntity computer;
            List<Widget> widgets;
            int screenW;
            int screenH;
            if (be instanceof MonitorBlockEntity origin) {
                if (player.distanceToSqr(Vec3.atCenterOf(originPos)) > ComputerEditPolicy.MAX_DISTANCE_SQ) {
                    return;
                }
                BlockPos ownerPos = origin.getOwnerComputerPos();
                if (ownerPos == null) return;
                BlockEntity ownerBe = player.level().getBlockEntity(ownerPos);
                if (!(ownerBe instanceof ComputerBlockEntity ownerComputer)) return;
                computer = ownerComputer;
                widgets = origin.getDrawList().widgets();
                screenW = origin.getWidth() * SCREEN_PX_PER_BLOCK;
                screenH = origin.getHeight() * SCREEN_PX_PER_BLOCK;
            } else if (be instanceof ComputerBlockEntity direct) {
                // Portable monitor display: canvas coordinates; authorized by any carried channel,
                // not by distance — the whole point is operating it from far away.
                if (!playerCarriesLinkTo(player, originPos)) {
                    return;
                }
                computer = direct;
                // Must match the resolved layout the client draws, or hit tests miss every widget.
                widgets = portableWidgets(direct);
                screenW = PortableMonitorItem.CANVAS_W;
                screenH = PortableMonitorItem.CANVAS_H;
            } else {
                return;
            }

            int px = Math.min(screenW - 1, Math.max(0, (int) Math.floor(payload.u() * screenW)));
            int py = Math.min(screenH - 1, Math.max(0, (int) Math.floor(payload.v() * screenH)));

                for (Widget w : widgets) {
                    if (px < w.x() || px >= w.x() + w.w() || py < w.y() || py >= w.y() + w.h()) continue;
                    if (w instanceof dev.propulsionteam.computed.content.monitors.widgets.TextInputWidget ti) {
                        PacketDistributor.sendToPlayer(player, new OpenWidgetTextInputPayload(
                                originPos, ti.id(), ti.value()));
                        return;
                    }
                    double value = 1.0;
                    if (w instanceof SliderWidget sw) {
                        double frac = sw.w() <= 0 ? 0.0 : (double) (px - sw.x()) / (double) sw.w();
                        frac = Mth.clamp(frac, 0.0, 1.0);
                        double min = sw.min();
                        double max = sw.max();
                        double raw = min + frac * (max - min);
                        double step = sw.step();
                        if (step > 0.0) {
                            raw = min + Math.round((raw - min) / step) * step;
                        }
                        value = raw;
                    }
                    if (computer.handleWidgetInput(w.id(), value)) return;
                }
        });
    }

    private static void handlePackageList(PackageListPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            int kindId = payload.kind();
            String error = "";
            List<PackageTransferService.Entry> entries = List.of();
            try {
                PackageTransferService service =
                        packageService(player, packageComputerId(player, payload.computerPos()));
                entries = service.list(PackageTransferService.kind(kindId), payload.folder());
            } catch (Exception exception) {
                error = packageError(exception);
                entries = List.of();
            }
            PacketDistributor.sendToPlayer(
                    player, new PackageListResultPayload(kindId, payload.folder(), error, entries));
        });
    }

    private static void handlePackageRead(PackageReadPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            int kindId = payload.kind();
            String error = "";
            byte[] data = new byte[0];
            try {
                PackageTransferService service =
                        packageService(player, packageComputerId(player, payload.computerPos()));
                data = service.read(PackageTransferService.kind(kindId), payload.path());
            } catch (Exception exception) {
                error = packageError(exception);
            }
            PacketDistributor.sendToPlayer(
                    player, new PackageReadResultPayload(kindId, payload.path(), error, data));
        });
    }

    private static void handlePackageWrite(PackageWritePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            purgeStaleWriteAssemblies();
            WriteKey key = new WriteKey(player.getUUID(), payload.opId());
            if (payload.totalSize() < 0 || payload.totalSize() > PackageTransferService.MAX_PACKAGE_BYTES) {
                WRITE_ASSEMBLIES.remove(key);
                rejectWrite(player, payload.opId(), "Package is too large");
                return;
            }
            PackageWriteAssembly assembly = WRITE_ASSEMBLIES.get(key);
            if (assembly == null) {
                long pending = WRITE_ASSEMBLIES.keySet().stream()
                        .filter(existing -> existing.player().equals(key.player()))
                        .count();
                if (pending >= MAX_WRITE_ASSEMBLIES_PER_PLAYER) {
                    rejectWrite(player, payload.opId(), "Too many pending package writes");
                    return;
                }
                try {
                    packageComputerId(player, payload.computerPos());
                    assembly = new PackageWriteAssembly(
                            payload.computerPos(),
                            payload.kind(),
                            payload.path(),
                            payload.mode(),
                            payload.target(),
                            payload.totalSize());
                } catch (Exception exception) {
                    rejectWrite(player, payload.opId(), packageError(exception));
                    return;
                }
                WRITE_ASSEMBLIES.put(key, assembly);
            } else if (!assembly.matches(payload)) {
                WRITE_ASSEMBLIES.remove(key);
                rejectWrite(player, payload.opId(), "Inconsistent package write");
                return;
            }
            if (assembly.buffer.size() + payload.chunk().length > assembly.totalSize) {
                WRITE_ASSEMBLIES.remove(key);
                rejectWrite(player, payload.opId(), "Unexpected package write data");
                return;
            }
            assembly.buffer.writeBytes(payload.chunk());
            if (assembly.buffer.size() < assembly.totalSize) {
                return;
            }
            WRITE_ASSEMBLIES.remove(key);
            try {
                PackageTransferService.WriteResult result;
                if (PackageTransferService.target(assembly.target)
                        == PackageTransferService.WriteTarget.DISK) {
                    result = writeHeldProgramDisk(player, assembly);
                } else {
                    PackageTransferService service =
                            packageService(player, packageComputerId(player, assembly.pos));
                    result = service.write(
                            PackageTransferService.kind(assembly.kind),
                            assembly.path,
                            assembly.buffer.toByteArray(),
                            PackageTransferService.mode(assembly.mode));
                }
                PacketDistributor.sendToPlayer(player, new PackageWriteResultPayload(
                        payload.opId(), result.status().ordinal(), result.fileName(), result.error()));
            } catch (Exception exception) {
                rejectWrite(player, payload.opId(), packageError(exception));
            }
        });
    }

    /**
     * Writes an assembled package onto a program disk, preferring the player's main hand and
     * falling back to the computer's drive slot, replacing whatever the disk held before.
     *
     * @throws IllegalArgumentException when neither location holds a program disk
     */
    private static PackageTransferService.WriteResult writeHeldProgramDisk(
            ServerPlayer player, PackageWriteAssembly assembly) {
        ProgramDiskData data =
                new ProgramDiskData(assembly.kind, assembly.path, assembly.buffer.toByteArray());
        ItemStack handDisk = player.getMainHandItem();
        if (handDisk.getItem() instanceof ProgramDiskItem) {
            writeOneDisk(handDisk, data, player.getInventory());
            return PackageTransferService.WriteResult.ok(assembly.path);
        }
        ComputerBlockEntity computer = requireComputer(player, assembly.pos);
        ItemStack slotDisk = computer.getItem(ComputerBlockEntity.DISK_SLOT_INDEX);
        if (slotDisk.getItem() instanceof ProgramDiskItem) {
            writeOneDisk(slotDisk, data, player.getInventory());
            computer.setChanged();
            computer.pushUpdateToClients();
            return PackageTransferService.WriteResult.ok(assembly.path);
        }
        throw new IllegalArgumentException(
                "Insert a Program Disk in the computer's drive or hold one in your main hand");
    }

    /**
     * Writes {@code data} onto exactly one disk of {@code stack}. The write happens before the
     * extra blanks are returned to the inventory: the blanks are component-identical to the
     * not-yet-written remainder, so returning them first would let them merge back into the same
     * slot and the write would stamp the whole stack instead of one disk.
     */
    private static void writeOneDisk(
            ItemStack stack, ProgramDiskData data, net.minecraft.world.entity.player.Inventory inventory) {
        if (stack.getCount() > 1) {
            ItemStack blanks = stack.split(stack.getCount() - 1);
            ProgramDiskItem.write(stack, data);
            inventory.placeItemBackInInventory(blanks);
        } else {
            ProgramDiskItem.write(stack, data);
        }
    }

    /**
     * Validates that the player may use the package folders of the computer at {@code pos} and
     * returns its ID (assigning one when the computer has none yet).
     *
     * @throws IllegalArgumentException when the computer is unreachable, protected, or missing
     */
    private static int packageComputerId(ServerPlayer player, BlockPos pos) {
        return requireComputer(player, pos).getOrCreateComputerId();
    }

    /**
     * Validates edit access to the computer at {@code pos} with the same rules as graph saves.
     *
     * @throws IllegalArgumentException when the computer is unreachable, protected, or missing
     */
    private static ComputerBlockEntity requireComputer(ServerPlayer player, BlockPos pos) {
        String accessError = ComputerEditPolicy.access(
                player.distanceToSqr(Vec3.atCenterOf(pos)),
                player.mayBuild(),
                player.level().mayInteract(player, pos));
        if (accessError != null) {
            throw new IllegalArgumentException(accessError);
        }
        if (!(player.level().getBlockEntity(pos) instanceof ComputerBlockEntity computer)) {
            throw new IllegalArgumentException("computer is no longer available");
        }
        return computer;
    }

    private static void handleSetComputerId(SetComputerIdPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            try {
                ComputerBlockEntity computer = requireComputer(player, payload.computerPos());
                int requested = payload.newId();
                if (requested < 0 || requested >= Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Computer ID must be between 0 and " + (Integer.MAX_VALUE - 1));
                }
                Path computedDirectory =
                        player.getServer().getWorldPath(LevelResource.ROOT).resolve("computed");
                if (!ComputerIdAssigner.reserve(computedDirectory, requested)) {
                    throw new IllegalArgumentException("could not persist the new computer ID");
                }
                computer.setComputerId(requested);
                Computed.LOGGER.info(
                        "{} set computer id at {} to {}",
                        player.getGameProfile().getName(),
                        payload.computerPos().toShortString(),
                        requested);
                PacketDistributor.sendToPlayer(player, new SetComputerIdResultPayload(
                        true, computer.computerId(), "Computer ID set to " + requested));
            } catch (Exception exception) {
                PacketDistributor.sendToPlayer(player, new SetComputerIdResultPayload(
                        false, -1, packageError(exception)));
            }
        });
    }

    private static long lastPortablePullLogMs;

    private static void handlePullPortableDisplay(PullPortableDisplayPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pos = payload.computerPos();
            boolean authorized = playerCarriesLinkTo(player, pos);
            boolean isComputer = player.level().getBlockEntity(pos) instanceof ComputerBlockEntity;
            if (!authorized || !isComputer) {
                logPortablePull(player, pos, authorized, isComputer, -1, payload.knownHash(), false);
                return;
            }
            ComputerBlockEntity computer = (ComputerBlockEntity) player.level().getBlockEntity(pos);
            int rawCount = computer.lastShownWidgets().size();
            boolean sent = sendPortableDisplayFrame(player, computer, pos, payload.knownHash());
            logPortablePull(player, pos, authorized, isComputer, rawCount, payload.knownHash(), sent);
        });
    }

    /**
     * Resolves the computer's raw widgets for the portable canvas. Compact rows: 24px lines give
     * six instead of four. Frame pulls and click hit tests must share this exact resolution.
     */
    private static List<Widget> portableWidgets(ComputerBlockEntity computer) {
        return MonitorWidgetLayout.resolve(
                computer.lastShownWidgets(),
                PortableMonitorItem.CANVAS_W, PortableMonitorItem.CANVAS_H, 24);
    }

    /**
     * Serializes the computer's portable frame and sends it to the player when the hash differs
     * from {@code knownHash} (pass {@link Long#MIN_VALUE} to force). Returns whether it sent.
     */
    private static boolean sendPortableDisplayFrame(
            ServerPlayer player, ComputerBlockEntity computer, BlockPos pos, long knownHash) {
        WidgetDrawList list = new WidgetDrawList(portableWidgets(computer));
        if (list.hash() == knownHash) {
            return false;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            list.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            PacketDistributor.sendToPlayer(
                    player, new PortableDisplayPayload(pos, list.hash(), computer.computerName(), bytes));
        } finally {
            buf.release();
        }
        return true;
    }

    /** Renames the computer and refreshes portable screens of every player carrying a link. */
    private static void handleSetComputerName(SetComputerNamePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            try {
                ComputerBlockEntity computer = requireComputer(player, payload.computerPos());
                computer.setComputerName(payload.name());
                computer.setChanged();
                player.level().sendBlockUpdated(
                        payload.computerPos(), computer.getBlockState(), computer.getBlockState(), 3);
                for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
                    if (playerCarriesLinkTo(other, payload.computerPos())) {
                        sendPortableDisplayFrame(other, computer, payload.computerPos(), Long.MIN_VALUE);
                    }
                }
            } catch (IllegalArgumentException exception) {
                // Same silent rejection pattern as the Computer ID dialog's invalid range.
                Computed.LOGGER.debug("Set computer name rejected: {}", exception.getMessage());
            }
        });
    }

    /** Stores the channel selected in the portable monitor screen on the carried monitor item. */
    private static void handleSetPortableSource(SetPortableSourcePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof PortableMonitorItem) {
                    PortableMonitorItem.setActiveIndex(stack, payload.index());
                    return;
                }
            }
            for (net.minecraft.world.item.ItemStack stack : player.getInventory().offhand) {
                if (stack.getItem() instanceof PortableMonitorItem) {
                    PortableMonitorItem.setActiveIndex(stack, payload.index());
                    return;
                }
            }
        });
    }

    /** Unlinks the portable monitor channel whose tab was right-clicked. */
    private static void handleRemovePortableSource(RemovePortableSourcePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof PortableMonitorItem) {
                    PortableMonitorItem.removeAt(stack, payload.index());
                    return;
                }
            }
            for (net.minecraft.world.item.ItemStack stack : player.getInventory().offhand) {
                if (stack.getItem() instanceof PortableMonitorItem) {
                    PortableMonitorItem.removeAt(stack, payload.index());
                    return;
                }
            }
        });
    }

    /** Routes a submitted text-input string to the originating computer's node event. */
    private static void handleWidgetTextInput(WidgetTextInputPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            BlockPos originPos = payload.originPos();
            BlockEntity be = player.level().getBlockEntity(originPos);

            ComputerBlockEntity computer;
            if (be instanceof MonitorBlockEntity origin) {
                if (player.distanceToSqr(Vec3.atCenterOf(originPos)) > ComputerEditPolicy.MAX_DISTANCE_SQ) {
                    return;
                }
                BlockPos ownerPos = origin.getOwnerComputerPos();
                if (ownerPos == null) return;
                BlockEntity ownerBe = player.level().getBlockEntity(ownerPos);
                if (!(ownerBe instanceof ComputerBlockEntity ownerComputer)) return;
                computer = ownerComputer;
            } else if (be instanceof ComputerBlockEntity direct) {
                if (!playerCarriesLinkTo(player, originPos)) {
                    return;
                }
                computer = direct;
            } else {
                return;
            }

            String text = payload.text() == null ? "" : payload.text();
            if (text.length() > WidgetTextInputPayload.MAX_LENGTH) {
                text = text.substring(0, WidgetTextInputPayload.MAX_LENGTH);
            }
            computer.handleWidgetTextInput(payload.widgetId(), text);
        });
    }

    /** Throttled diagnostics so an empty portable screen can be explained from the log alone. */
    private static void logPortablePull(ServerPlayer player, BlockPos pos, boolean authorized,
                                        boolean isComputer, int rawWidgets, long knownHash, boolean sentFrame) {
        long now = System.currentTimeMillis();
        if (now - lastPortablePullLogMs < 5000L) {
            return;
        }
        lastPortablePullLogMs = now;
        Computed.LOGGER.info(
                "Portable pull by {} at {}: authorized={}, computerPresent={}, rawWidgets={}, knownHash={}, sentFrame={}",
                player.getGameProfile().getName(), pos.toShortString(), authorized, isComputer,
                rawWidgets, knownHash, sentFrame);
    }

    /** True when the player's inventory, curio slots or offhand hold a link to {@code pos}. */
    private static boolean playerCarriesLinkTo(ServerPlayer player, BlockPos pos) {
        for (ItemStack stack : player.getInventory().items) {
            if (stackLinksTo(stack, pos)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stackLinksTo(stack, pos)) {
                return true;
            }
        }
        for (ItemStack stack : CuriosIntegration.stacks(player)) {
            if (stackLinksTo(stack, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean stackLinksTo(ItemStack stack, BlockPos pos) {
        if (stack.getItem() instanceof LinkCardItem) {
            PortableLinkData link = LinkCardItem.read(stack);
            return link != null && link.blockPos().equals(pos);
        }
        if (stack.getItem() instanceof PortableMonitorItem) {
            // Any stored channel counts, not just the active one, so switching tabs stays authorized.
            for (PortableLinkData link : PortableMonitorItem.readLinks(stack)) {
                if (link.blockPos().equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PackageTransferService packageService(ServerPlayer player, int computerId) {
        MinecraftServer server = player.getServer();
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        try {
            int migrated = LegacyPackageMigration.migrateIfNeeded(
                    legacyPackageSourceDirectory(server), worldRoot, computerId);
            if (migrated > 0) {
                Computed.LOGGER.info(
                        "Migrated {} legacy package files into computer {}'s package folders",
                        migrated,
                        computerId);
            }
        } catch (Exception exception) {
            Computed.LOGGER.warn("Could not migrate legacy package files: {}", exception.toString());
        }
        return new PackageTransferService(PackageTransferService.computerDirectory(worldRoot, computerId));
    }

    /**
     * Directory owning the legacy {@code computed/{graphs,nodes}} exports. The game directory is
     * authoritative ({@code server.getServerDirectory()} returns the working directory, which in
     * dev runs is the project folder, not {@code run/}); the working directory is kept as a
     * fallback for launches that start elsewhere.
     */
    private static Path legacyPackageSourceDirectory(MinecraftServer server) {
        Path gameDirectory = FMLPaths.GAMEDIR.get();
        if (Files.isDirectory(gameDirectory.resolve("computed"))) {
            return gameDirectory;
        }
        Path workingDirectory = server.getServerDirectory().toAbsolutePath().normalize();
        if (!workingDirectory.equals(gameDirectory) && Files.isDirectory(workingDirectory.resolve("computed"))) {
            return workingDirectory;
        }
        return gameDirectory;
    }

    private static void rejectWrite(ServerPlayer player, int opId, String error) {
        PacketDistributor.sendToPlayer(player, new PackageWriteResultPayload(
                opId, PackageTransferService.WriteStatus.ERROR.ordinal(), "", error));
    }

    private static void purgeStaleWriteAssemblies() {
        long now = System.currentTimeMillis();
        WRITE_ASSEMBLIES.entrySet()
                .removeIf(entry -> now - entry.getValue().createdAt > WRITE_ASSEMBLY_TIMEOUT_MS);
    }

    private static String packageError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
