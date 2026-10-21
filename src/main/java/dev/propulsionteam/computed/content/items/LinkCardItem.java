package dev.propulsionteam.computed.content.items;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

/**
 * Pairs a portable monitor with one computer. Right-click a computer to bind; insert the bound card
 * into a portable monitor to stream that computer's widgets. Shift-use in air clears the binding.
 */
public class LinkCardItem extends Item {
    /** Key of the bound {@link PortableLinkData} tag inside the {@code custom_data} component. */
    public static final String DATA_KEY = "LinkCard";

    public LinkCardItem(Properties properties) {
        super(properties);
    }

    /** A bound card carries the enchantment glint. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return read(stack) != null;
    }

    /** Stored binding, or {@code null} when the stack is not a card or holds nothing. */
    public static PortableLinkData read(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof LinkCardItem)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag().getCompound(DATA_KEY);
        if (!tag.contains("dimension")) {
            return null;
        }
        try {
            return PortableLinkData.of(tag);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Replaces the binding on the given stack. */
    public static void write(ItemStack stack, PortableLinkData link) {
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        tag.put(DATA_KEY, link.toTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Removes the binding from the given stack. */
    public static void clear(ItemStack stack) {
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing == null) {
            return;
        }
        CompoundTag tag = existing.copyTag();
        tag.remove(DATA_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ComputerBlockEntity computer)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        PortableLinkData link = new PortableLinkData(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                level.dimension().location().toString(),
                computer.computerName(),
                computer.getOrCreateComputerId());
        write(context.getItemInHand(), link);
        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(
                    Component.translatable("item.computed.link_card.bound", pos.getX(), pos.getY(), pos.getZ()),
                    true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || read(stack) == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        clear(stack);
        player.displayClientMessage(Component.translatable("item.computed.link_card.cleared"), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        PortableLinkData link = read(stack);
        if (link == null) {
            tooltip.add(Component.translatable("item.computed.link_card.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable(
                        "item.computed.link_card.bound.tooltip", link.x(), link.y(), link.z())
                .withStyle(ChatFormatting.GRAY));
    }
}
