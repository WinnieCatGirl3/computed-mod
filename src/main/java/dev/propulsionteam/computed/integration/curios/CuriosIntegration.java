package dev.propulsionteam.computed.integration.curios;

import dev.propulsionteam.computed.content.items.LinkCardItem;
import dev.propulsionteam.computed.content.items.PortableLinkData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * Curios bridge for the dedicated {@code link_card} slot. Every Curios class stays behind
 * {@link #isLoaded()} so the mod loads fine without Curios installed.
 */
public final class CuriosIntegration {
    /** Identifier of the dedicated link card slot; matches {@code data/computed/curios/slots}. */
    public static final String SLOT_ID = "link_card";

    private CuriosIntegration() {}

    /** Whether Curios is present, i.e. whether its classes may be touched at all. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    /** The first bound link card in any curio slot of the entity, or {@code null}. */
    public static PortableLinkData findBoundLink(LivingEntity entity) {
        if (!isLoaded() || entity == null) {
            return null;
        }
        for (ItemStack stack : stacks(entity)) {
            PortableLinkData link = LinkCardItem.read(stack);
            if (link != null) {
                return link;
            }
        }
        return null;
    }

    /** Every stack held in any curio slot of the entity; empty when Curios is absent. */
    public static List<ItemStack> stacks(LivingEntity entity) {
        if (!isLoaded() || entity == null) {
            return List.of();
        }
        Map<String, ICurioStacksHandler> handlers =
                CuriosApi.getCuriosInventory(entity).map(ICuriosItemHandler::getCurios).orElse(Map.of());
        List<ItemStack> stacks = new ArrayList<>();
        for (ICurioStacksHandler handler : handlers.values()) {
            var slotStacks = handler.getStacks();
            for (int slot = 0; slot < slotStacks.getSlots(); slot++) {
                stacks.add(slotStacks.getStackInSlot(slot));
            }
        }
        return stacks;
    }

    /** Slot ids assigned to the entity (e.g. {@code link_card}); empty when Curios is absent. */
    public static List<String> slotIds(LivingEntity entity) {
        if (!isLoaded() || entity == null) {
            return List.of();
        }
        return List.copyOf(CuriosApi.getCuriosInventory(entity)
                .map(ICuriosItemHandler::getCurios)
                .orElse(Map.of())
                .keySet());
    }
}
