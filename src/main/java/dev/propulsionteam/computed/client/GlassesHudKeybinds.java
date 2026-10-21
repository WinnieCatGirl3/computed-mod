package dev.propulsionteam.computed.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.propulsionteam.computed.Computed;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Registers the glasses HUD settings keybind (default K) on the mod event bus. */
@EventBusSubscriber(modid = Computed.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
final class GlassesHudKeybinds {
    static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.computed.glasses_hud",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            "key.categories.computed");

    private GlassesHudKeybinds() {}

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
    }
}
