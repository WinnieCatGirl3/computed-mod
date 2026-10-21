package dev.propulsionteam.computed.content.items;

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
import dev.propulsionteam.computed.PortableDisplayBridge;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Handheld windowed screen that mirrors the widgets of linked computers. Right-click a computer to
 * add it as a channel (up to {@link PortableMonitorLinks#MAX_CHANNELS}); use again to open the
 * display with a channel selector; sneak-use a computer to remove its channel; sneak-use in air
 * clears all channels (or copies a single link from a bound {@link LinkCardItem} in the offhand).
 */
public class PortableMonitorItem extends Item {
    /** Legacy key of the stored link tag; new writes use {@link PortableMonitorLinks}. */
    public static final String CARD_KEY = PortableMonitorLinks.LEGACY_KEY;
    /**
     * Virtual canvas in widget pixels: 16:9 landscape with four 32px widget lines. The windowed
     * screen letterboxes this into whatever GUI size fits.
     */
    public static final int CANVAS_W = 256;
    public static final int CANVAS_H = 144;

    public PortableMonitorItem(Properties properties) {
        super(properties);
    }

    /** Stored channels in slot order, or an empty list. */
    public static List<PortableLinkData> readLinks(ItemStack monitor) {
        CompoundTag tag = readRoot(monitor);
        return tag == null ? List.of() : PortableMonitorLinks.readList(tag);
    }

    /** Index of the active channel (clamped into the stored list). */
    public static int readActiveIndex(ItemStack monitor) {
        CompoundTag tag = readRoot(monitor);
        return tag == null ? 0 : PortableMonitorLinks.readActive(tag);
    }

    /** First (or active) stored link, or {@code null}. */
    @Nullable
    public static PortableLinkData readLink(ItemStack monitor) {
        List<PortableLinkData> links = readLinks(monitor);
        if (links.isEmpty()) {
            return null;
        }
        return links.get(Math.min(readActiveIndex(monitor), links.size() - 1));
    }

    /** Replaces all channels with a single link (used by the link-card copy). */
    public static void writeLink(ItemStack monitor, PortableLinkData link) {
        writeState(monitor, List.of(link), 0);
    }

    /**
     * Adds the link as a channel (or selects it when already present). Returns the channel index,
     * or {@code -1} when the list is full.
     */
    public static int addLink(ItemStack monitor, PortableLinkData link) {
        PortableMonitorLinks.AddResult result =
                PortableMonitorLinks.add(readLinks(monitor), link);
        if (result.index() < 0) {
            return -1;
        }
        writeState(monitor, result.links(), result.index());
        return result.index();
    }

    /** Removes the channel; returns {@code {@code true}} when it was present. */
    public static boolean removeLink(ItemStack monitor, PortableLinkData link) {
        List<PortableLinkData> links = readLinks(monitor);
        if (!links.contains(link)) {
            return false;
        }
        int active = readActiveIndex(monitor);
        List<PortableLinkData> next = PortableMonitorLinks.remove(links, link);
        writeState(monitor, next, Math.min(active, Math.max(0, next.size() - 1)));
        return true;
    }

    /** Removes the channel at {@code index}; returns {@code true} when the index was in range. */
    public static boolean removeAt(ItemStack monitor, int index) {
        List<PortableLinkData> links = readLinks(monitor);
        if (index < 0 || index >= links.size()) {
            return false;
        }
        int active = readActiveIndex(monitor);
        List<PortableLinkData> next = PortableMonitorLinks.remove(links, links.get(index));
        int newActive;
        if (index < active) {
            newActive = active - 1;
        } else if (index == active) {
            newActive = Math.min(active, Math.max(0, next.size() - 1));
        } else {
            newActive = active;
        }
        writeState(monitor, next, Math.max(0, newActive));
        return true;
    }

    /** Selects an existing channel; returns {@code false} when the index is out of range. */
    public static boolean setActiveIndex(ItemStack monitor, int index) {
        List<PortableLinkData> links = readLinks(monitor);
        if (index < 0 || index >= links.size()) {
            return false;
        }
        writeState(monitor, links, index);
        return true;
    }

    /** Clears all channels. */
    public static void clearLinks(ItemStack monitor) {
        CustomData existing = monitor.get(DataComponents.CUSTOM_DATA);
        if (existing == null) {
            return;
        }
        CompoundTag tag = existing.copyTag();
        PortableMonitorLinks.writeList(tag, List.of(), 0);
        if (tag.isEmpty()) {
            monitor.remove(DataComponents.CUSTOM_DATA);
        } else {
            monitor.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /** True when the given stack is this item with at least one stored channel. */
    public static boolean isPaired(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PortableMonitorItem && readLink(stack) != null;
    }

    /** Bound monitors carry the enchantment glint. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return readLink(stack) != null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ComputerBlockEntity computer)) {
            return InteractionResult.PASS;
        }
        PortableLinkData link = new PortableLinkData(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                level.dimension().location().toString(),
                computer.computerName(),
                computer.getOrCreateComputerId());
        if (context.isSecondaryUseActive()) {
            if (level.isClientSide) {
                return InteractionResult.sidedSuccess(true);
            }
            if (removeLink(context.getItemInHand(), link)) {
                context.getPlayer().displayClientMessage(Component.translatable(
                        "item.computed.portable_monitor.removed",
                        pos.getX(), pos.getY(), pos.getZ()), true);
            }
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        int index = addLink(context.getItemInHand(), link);
        if (index < 0) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable(
                        "item.computed.portable_monitor.full",
                        PortableMonitorLinks.MAX_CHANNELS), true);
            }
            return InteractionResult.CONSUME;
        }
        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "item.computed.portable_monitor.linked", pos.getX(), pos.getY(), pos.getZ()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) {
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
            PortableLinkData cardLink = LinkCardItem.read(player.getOffhandItem());
            if (cardLink != null) {
                writeLink(stack, cardLink);
                player.displayClientMessage(Component.translatable(
                        "item.computed.portable_monitor.copied",
                        cardLink.x(), cardLink.y(), cardLink.z()), true);
            } else {
                clearLinks(stack);
                player.displayClientMessage(
                        Component.translatable("item.computed.portable_monitor.cleared"), true);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (level.isClientSide) {
            List<PortableLinkData> links = readLinks(stack);
            if (!links.isEmpty()) {
                PortableDisplayBridge.openScreen(
                        links.stream().map(PortableLinkData::blockPos).toList(),
                        readActiveIndex(stack));
            } else {
                player.displayClientMessage(
                        Component.translatable("item.computed.portable_monitor.hint"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        List<PortableLinkData> links = readLinks(stack);
        if (links.isEmpty()) {
            tooltip.add(Component.translatable("item.computed.portable_monitor.unlinked_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        PortableLinkData link = links.get(Math.min(readActiveIndex(stack), links.size() - 1));
        tooltip.add(Component.translatable(
                        "item.computed.portable_monitor.paired", link.x(), link.y(), link.z())
                .withStyle(ChatFormatting.GRAY));
        if (links.size() > 1) {
            tooltip.add(Component.translatable(
                            "item.computed.portable_monitor.channels", links.size())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Nullable
    private static CompoundTag readRoot(ItemStack monitor) {
        if (monitor.isEmpty() || !(monitor.getItem() instanceof PortableMonitorItem)) {
            return null;
        }
        CustomData data = monitor.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static void writeState(ItemStack monitor, List<PortableLinkData> links, int active) {
        CustomData existing = monitor.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        PortableMonitorLinks.writeList(tag, links, active);
        if (tag.isEmpty()) {
            monitor.remove(DataComponents.CUSTOM_DATA);
        } else {
            monitor.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
