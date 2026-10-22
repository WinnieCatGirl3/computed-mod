package dev.propulsionteam.computed.content.blocks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.content.items.LinkCardItem;
import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import dev.propulsionteam.computed.content.items.ProgramDiskItem;
import dev.propulsionteam.computed.network.ComputedNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.state.BlockBehaviour.propertiesCodec;

import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ComputerBlock extends Block implements EntityBlock {
    public static final MapCodec<ComputerBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(propertiesCodec()).apply(instance, ComputerBlock::new));

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Inset so the computer is not a full block: neighbors keep their textures, ours keep the front. */
    private static final VoxelShape SHAPE = Shapes.box(1, 1, 1, 15, 15, 15);

    public ComputerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ComputedRegistries.COMPUTER_BLOCK_ENTITY.get() == type
                ? (BlockEntityTicker<T>) (lvl, p, st, be) -> ComputerBlockEntity.tick(lvl, p, st, (ComputerBlockEntity) be)
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (holdsLinkItem(player)) {
            // Portable monitor / link card want their own Item.useOn flow (link, remove).
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            return openComputerMenu(level, pos, player);
        }
        return openNodeEditor(level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (stack.getItem() instanceof PortableMonitorItem
                || stack.getItem() instanceof LinkCardItem) {
            // Let the item handle linking (right-click) / channel removal (sneak).
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player.isSecondaryUseActive()) {
            if (Peripherals.isPeripheral(stack)) {
                if (level.isClientSide) {
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
                if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer && computer.tryInsertPeripheralFromHand(stack)) {
                    return ItemInteractionResult.CONSUME;
                }
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (stack.getItem() instanceof ProgramDiskItem) {
                if (level.isClientSide) {
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
                if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer && computer.tryInsertDiskFromHand(stack)) {
                    return ItemInteractionResult.CONSUME;
                }
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            InteractionResult menu = openComputerMenu(level, pos, player);
            return switch (menu) {
                case SUCCESS -> ItemInteractionResult.sidedSuccess(level.isClientSide);
                case CONSUME -> ItemInteractionResult.CONSUME;
                default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            };
        }
        InteractionResult edit = openNodeEditor(level, pos, player);
        return switch (edit) {
            case SUCCESS -> ItemInteractionResult.sidedSuccess(level.isClientSide);
            case CONSUME -> ItemInteractionResult.CONSUME;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    /** True when the main hand holds an item that links to computers instead of opening menus. */
    private static boolean holdsLinkItem(Player player) {
        return player.getMainHandItem().getItem() instanceof PortableMonitorItem
                || player.getMainHandItem().getItem() instanceof LinkCardItem;
    }

    private static InteractionResult openComputerMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ComputerBlockEntity computer)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        serverPlayer.openMenu(computer, buf -> buf.writeBlockPos(computer.getBlockPos()));
        return InteractionResult.CONSUME;
    }

    private static InteractionResult openNodeEditor(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ComputerBlockEntity computer)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        CompoundTag bundle = computer.getGraphData();
        Peripherals.writePeripheralUnlockTag(computer, bundle);
        bundle.putInt("FsComputerId", computer.getOrCreateComputerId());
        bundle.putString("ComputerName", computer.computerName());
        PacketDistributor.sendToPlayer(
                serverPlayer,
                ComputedNetworking.openPayload(pos, computer.getProgramRevision(), bundle));
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ComputerBlockEntity be) {
            be.getOrCreateUuid();
            be.getOrCreateComputerId();
            ItemStack stack = new ItemStack(this.asItem());
            CompoundTag beTag = be.saveCustomOnly(level.registryAccess());
            if (!beTag.isEmpty()) {
                net.minecraft.world.item.BlockItem.setBlockEntityData(stack, be.getType(), beTag);
            }
            popResource(level, pos, stack);
            be.markDropsHandled();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof ComputerBlockEntity computer && computer.dropsHandled()) {
            return List.of();
        }
        List<ItemStack> drops = super.getDrops(state, params);
        if (be instanceof ComputerBlockEntity computer && !drops.isEmpty()) {
            int computerId = computer.getOrCreateComputerId();
            if (computerId >= 0) {
                for (ItemStack stack : drops) {
                    CompoundTag idTag = new CompoundTag();
                    idTag.putInt(ComputerBlockEntity.COMPUTER_ID_TAG, computerId);
                    net.minecraft.world.item.BlockItem.setBlockEntityData(stack, computer.getType(), idTag);
                }
            }
        }
        return drops;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof ComputerBlockEntity be) {
            return be.getEmittedRedstone(direction);
        }
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }
}
