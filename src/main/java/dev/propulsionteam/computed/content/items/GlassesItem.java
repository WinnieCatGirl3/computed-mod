package dev.propulsionteam.computed.content.items;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import dev.propulsionteam.computed.integration.curios.CuriosIntegration;

/**
 * Wearable glasses that mirror the binding of the first {@link LinkCardItem} in the wearer's
 * inventory as a HUD line ("Connected with: ..."). The glasses themselves carry no binding; the
 * HUD line can be repositioned and rescaled with the glasses HUD settings keybind.
 */
public class GlassesItem extends Item implements Equipable {
    public GlassesItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    /**
     * The first bound link card in the player's main inventory, any curio slot (including the
     * dedicated Link Card slot) or offhand, or {@code null} when the player carries none.
     */
    public static PortableLinkData findFirstBoundLink(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            PortableLinkData link = LinkCardItem.read(stack);
            if (link != null) {
                return link;
            }
        }
        PortableLinkData curiosLink = CuriosIntegration.findBoundLink(player);
        if (curiosLink != null) {
            return curiosLink;
        }
        return LinkCardItem.read(player.getOffhandItem());
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.computed.glasses.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
