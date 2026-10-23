package dev.propulsionteam.computed.content.blocks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.propulsionteam.computed.content.ComputedRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.state.BlockBehaviour.propertiesCodec;

public class SpeakerBlock extends BaseEntityBlock {
    public static final MapCodec<SpeakerBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(propertiesCodec()).apply(instance, SpeakerBlock::new));

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SpeakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
        return new SpeakerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ComputedRegistries.SPEAKER_BLOCK_ENTITY.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> SpeakerBlockEntity.serverTick(lvl, pos, st, (SpeakerBlockEntity) be);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!SpeakerBlockEntity.isMusicDisc(level, stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (speaker.tryInsertDisc(stack, player)) {
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)
                || (!speaker.hasDisc() && speaker.queueSize() == 0)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack disc = speaker.ejectDisc();
        if (!disc.isEmpty()) {
            popResource(level, pos, disc);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            speaker.stop();
            speaker.detachContents().forEach(item -> popResource(level, pos, item));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
