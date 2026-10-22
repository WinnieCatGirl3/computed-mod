package dev.propulsionteam.computed.content.items;

import dev.propulsionteam.computed.persistence.ProgramDiskData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/** A portable disk that stores one exported package (graph or node) as item components. */
public class ProgramDiskItem extends Item {
    /** Key of the encoded {@link ProgramDiskData} inside the {@code custom_data} component. */
    public static final String DATA_KEY = "ProgramDisk";
    /** Boolean flag inside {@code custom_data}: load and run this disk when a computer boots. */
    public static final String AUTOSTART_KEY = "Autostart";

    public ProgramDiskItem(Properties properties) {
        super(properties);
    }

    /** Whether this disk was flagged for autostart (right-click a written disk to toggle). */
    public static boolean isAutostart(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ProgramDiskItem)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(AUTOSTART_KEY);
    }

    /** Sets or clears the autostart flag on the given stack. No-op on stacks that are not disks. */
    public static void setAutostart(ItemStack stack, boolean autostart) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ProgramDiskItem)) {
            return;
        }
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        tag.putBoolean(AUTOSTART_KEY, autostart);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Right-click while holding to toggle autostart; written disks only. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (read(stack) == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.computed.program_disk.autostart.needs_program"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        boolean next = !isAutostart(stack);
        setAutostart(stack, next);
        if (!level.isClientSide) {
            player.displayClientMessage(
                    Component.translatable(next
                            ? "item.computed.program_disk.autostart.on"
                            : "item.computed.program_disk.autostart.off"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** A written disk carries the enchantment glint; blank ones stay plain. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_DATA) != null;
    }

    /** Stored program data, or {@code null} when the stack is not a disk or holds nothing. */
    public static ProgramDiskData read(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ProgramDiskItem)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        byte[] bytes = data.copyTag().getByteArray(DATA_KEY);
        if (bytes.length == 0) {
            return null;
        }
        try {
            return ProgramDiskData.decode(bytes);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Replaces the stored program data on the given stack. Written disks become unstackable. */
    public static void write(ItemStack stack, ProgramDiskData program) {
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        tag.putByteArray(DATA_KEY, program.encode());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ProgramDiskData program = read(stack);
        if (program == null) {
            tooltip.add(Component.translatable("item.computed.program_disk.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.computed.program_disk.stored", program.name())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.computed.program_disk.autostart_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (isAutostart(stack)) {
            tooltip.add(Component.translatable("item.computed.program_disk.autostart")
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
