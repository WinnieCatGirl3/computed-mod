package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.items.GlassesItem;
import dev.propulsionteam.computed.content.items.LinkCardItem;
import dev.propulsionteam.computed.content.items.PortableLinkData;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import dev.propulsionteam.computed.integration.curios.CuriosIntegration;

/**
 * Draws the glasses HUD line ("Connected with: ...") while the local player wears glasses and
 * carries a bound link card, and opens {@link GlassesHudSettingsScreen} on the keybind.
 */
@EventBusSubscriber(modid = Computed.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class GlassesHudOverlay {
    private GlassesHudOverlay() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (GlassesHudKeybinds.OPEN_SETTINGS.consumeClick()) {
            if (minecraft.player == null) {
                continue;
            }
            if (minecraft.screen instanceof GlassesHudSettingsScreen settingsScreen) {
                // A second press that was queued before the screen opened toggles it
                // closed again, mirroring Gadgets & Gizmos' openInteractionScreen.
                settingsScreen.onClose();
                continue;
            }
            if (minecraft.screen != null || !isWearingGlasses(minecraft.player)) {
                continue;
            }
            minecraft.setScreen(new GlassesHudSettingsScreen());
            minecraft.player.displayClientMessage(
                    Component.translatable("computed.glasses.settings.opened"), true);
        }
        runSelfTest(minecraft);
    }

    /** True while the player wears the computed glasses, gating the K menu like G&amp;G's goggles check. */
    static boolean isWearingGlasses(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof GlassesItem;
    }

    /**
     * Diagnostic harness, only active when the {@code COMPUTED_HUD_SELFTEST} environment variable
     * is set: equips the glasses, stages distinct HUD elements, opens the designer and then
     * verifies the G&amp;G-style K toggle and the auto-close when the glasses come off.
     */
    /** Also read by the designer and the modal so the harness can observe them. */
    static final boolean SELF_TEST = System.getenv("COMPUTED_HUD_SELFTEST") != null;
    /** Flag file the harness script writes to trigger the unequip stage. */
    private static final Path SELF_TEST_UNEQUIP_FLAG = Path.of("/tmp/opencode/unequip.request");
    private static int selfTestStage;
    private static int selfTestTicks;
    /** 0 = no screen, 1 = designer canvas, 2 = element modal, 3 = any other screen. */
    private static int selfTestScreenState;

    private static void runSelfTest(Minecraft minecraft) {
        if (!SELF_TEST || minecraft.player == null || selfTestStage >= 7) {
            return;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        ServerPlayer host = server == null
                ? null
                : server.getPlayerList().getPlayer(minecraft.player.getUUID());
        if (host == null) {
            return;
        }
        if (selfTestStage == 0) {
            selfTestStage = 1;
            selfTestTicks = 0;
            Computed.LOGGER.info("[glasses-hud] self-test: client {}", cardReport(minecraft.player));
            Computed.LOGGER.info("[glasses-hud] self-test: server {}", cardReport(host));
            if (CuriosIntegration.isLoaded()) {
                Computed.LOGGER.info(
                        "[glasses-hud] self-test: curios slots registered={}",
                        top.theillusivec4.curios.common.data.CuriosSlotManager.SERVER
                                .getSlots()
                                .keySet());
                Computed.LOGGER.info(
                        "[glasses-hud] self-test: player curios slots={}",
                        CuriosIntegration.slotIds(host));
                Computed.LOGGER.info(
                        "[glasses-hud] self-test: player curios stacks={}",
                        CuriosIntegration.stacks(host));
            } else {
                Computed.LOGGER.info("[glasses-hud] self-test: curios NOT loaded");
            }
            if (host.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof GlassesItem) {
                Computed.LOGGER.info("[glasses-hud] self-test: glasses already worn");
                return;
            }
            ItemStack glasses = new ItemStack(ComputedRegistries.GLASSES_ITEM.get());
            server.execute(() -> host.setItemSlot(EquipmentSlot.HEAD, glasses));
            Computed.LOGGER.info("[glasses-hud] self-test: equipping glasses server-side");
            return;
        }
        if (selfTestStage == 1) {
            // Wait until the client sees the glasses, then stage distinct elements so a
            // screenshot proves whether every element renders or only the default line.
            selfTestTicks++;
            boolean wearing = minecraft.player.getItemBySlot(EquipmentSlot.HEAD).getItem()
                    instanceof GlassesItem;
            if (!wearing && selfTestTicks < 200) {
                return;
            }
            selfTestStage = 2;
            selfTestTicks = 0;
            GlassesHudSettings settings = GlassesHudSettings.get();
            settings.elements.clear();
            GlassesHudSettings.HudElement coords = new GlassesHudSettings.HudElement();
            coords.text = "Coords {x} {y} {z}";
            coords.x = 0.5f;
            coords.y = 0.25f;
            settings.elements.add(coords);
            GlassesHudSettings.HudElement dim = new GlassesHudSettings.HudElement();
            dim.text = "Dim {dimension}";
            dim.x = 0.32f;
            dim.y = 0.6f;
            dim.scale = 1.5f;
            dim.color = 0x55FF55;
            settings.elements.add(dim);
            GlassesHudSettings.HudElement big = new GlassesHudSettings.HudElement();
            big.text = "Big bottom-left";
            big.x = 0.16f;
            big.y = 0.9f;
            big.scale = 1.5f;
            big.color = 0x55FFFF;
            settings.elements.add(big);
            Computed.LOGGER.info(
                    "[glasses-hud] self-test: staged elements={}", settings.elements.size());
            return;
        }
        selfTestTicks++;
        if (selfTestStage == 2) {
            // Give the HUD a moment to render (and terrain to finish loading), then
            // open the designer and report how many elements it lays out.
            if (selfTestTicks < 300) {
                return;
            }
            GlassesHudSettingsScreen screen = new GlassesHudSettingsScreen();
            minecraft.setScreen(screen);
            selfTestScreenState = 1;
            selfTestTicks = 0;
            selfTestStage = 3;
            Computed.LOGGER.info(
                    "[glasses-hud] self-test: designer open, elements={}",
                    GlassesHudSettings.get().elements.size());
            return;
        }
        int screenState = minecraft.screen == null ? 0
                : minecraft.screen instanceof GlassesHudSettingsScreen ? 1
                : minecraft.screen instanceof GlassesHudElementEditScreen ? 2
                : 3;
        if (screenState != selfTestScreenState) {
            selfTestScreenState = screenState;
            Computed.LOGGER.info("[glasses-hud] self-test: screen -> {}", switch (screenState) {
                case 1 -> "canvas";
                case 2 -> "modal";
                case 3 -> "other";
                default -> "closed";
            });
            if (selfTestStage == 3 && screenState == 0) {
                selfTestStage = 4;
                selfTestTicks = 0;
                Computed.LOGGER.info("[glasses-hud] self-test: K-close confirmed");
                return;
            }
            if (selfTestStage == 4 && screenState == 1) {
                selfTestStage = 5;
                selfTestTicks = 0;
                Computed.LOGGER.info("[glasses-hud] self-test: K-reopen confirmed");
                return;
            }
            if (selfTestStage == 6 && screenState == 0) {
                selfTestStage = 7;
                logSelfTestConfig();
                Computed.LOGGER.info("[glasses-hud] self-test: auto-close on unequip confirmed");
                Computed.LOGGER.info("[glasses-hud] self-test: complete");
                return;
            }
            if (selfTestStage == 5 && screenState == 0) {
                failSelfTest("designer closed unexpectedly before the unequip");
            }
            return;
        }
        if (selfTestStage == 3 || selfTestStage == 4) {
            if (selfTestTicks > 600) {
                failSelfTest(selfTestStage == 3
                        ? "no K-close within 30s (keybind press missing)"
                        : "no K-reopen within 30s (keybind press missing)");
            }
            return;
        }
        if (selfTestStage == 5) {
            if (Files.isRegularFile(SELF_TEST_UNEQUIP_FLAG)) {
                selfTestStage = 6;
                selfTestTicks = 0;
                Computed.LOGGER.info(
                        "[glasses-hud] self-test: unequip flag seen, removing glasses");
                ItemStack empty = ItemStack.EMPTY;
                server.execute(() -> host.setItemSlot(EquipmentSlot.HEAD, empty));
                return;
            }
            if (selfTestTicks > 1200) {
                failSelfTest("unequip flag never appeared");
            }
            return;
        }
        // Stage 6: the designer must close itself once the glasses are gone.
        if (selfTestTicks > 200) {
            failSelfTest("designer did not auto-close after the glasses were removed");
        }
    }

    private static void failSelfTest(String reason) {
        Computed.LOGGER.info("[glasses-hud] self-test: FAIL: {}", reason);
        selfTestStage = 80;
    }

    private static void logSelfTestConfig() {
        try {
            Path config = FMLPaths.CONFIGDIR.get().resolve("computed-glasses-hud.json");
            boolean exists = Files.isRegularFile(config);
            Computed.LOGGER.info(
                    "[glasses-hud] self-test: config {} exists={} size={} elements={}",
                    config,
                    exists,
                    exists ? Files.size(config) : -1,
                    GlassesHudSettings.get().elements.size());
        } catch (Exception exception) {
            Computed.LOGGER.info(
                    "[glasses-hud] self-test: config check failed: {}", exception.toString());
        }
    }

    @SubscribeEvent
    static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.screen != null) {
            // Like G&amp;G: the HUD never draws behind any screen; the designer shows its own
            // styled boxes instead, every other screen hides the HUD completely.
            return;
        }
        if (!(minecraft.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof GlassesItem)) {
            logState("no glasses in head slot (head=" + describe(
                    minecraft.player.getItemBySlot(EquipmentSlot.HEAD)) + ")");
            return;
        }
        PortableLinkData link = GlassesItem.findFirstBoundLink(minecraft.player);
        if (link == null) {
            logState("glasses worn but no bound link card (" + cardReport(minecraft.player) + ")");
            return;
        }
        logState("rendering HUD: link=" + link.displayName());
        GlassesHudSettings settings = GlassesHudSettings.get();
        GuiGraphics graphics = event.getGuiGraphics();
        for (GlassesHudSettings.HudElement element : settings.elements) {
            if (!element.visible) {
                continue;
            }
            drawElement(minecraft, graphics, element, link);
        }
    }

    private static void drawElement(
            Minecraft minecraft,
            GuiGraphics graphics,
            GlassesHudSettings.HudElement element,
            PortableLinkData link) {
        Component text = Component.literal(
                GlassesHudSettings.resolveTemplate(element.text, link));
        float scale = element.scale;
        int textWidth = (int) (minecraft.font.width(text) * scale);
        float x = element.resolvedX(graphics.guiWidth()) - textWidth / 2f;
        float y = element.resolvedY(graphics.guiHeight());
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(minecraft.font, text, 0, 0, element.textColor(), true);
        graphics.pose().popPose();
    }

    /** Debug aid: logs only when the HUD state changes, so a test session shows exactly why nothing draws. */
    private static String lastState = "";

    private static void logState(String state) {
        if (!state.equals(lastState)) {
            lastState = state;
            Computed.LOGGER.info("[glasses-hud] {}", state);
        }
    }

    private static String describe(ItemStack stack) {
        return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String cardReport(net.minecraft.world.entity.player.Player player) {
        int cards = 0;
        int withData = 0;
        int bound = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof LinkCardItem)) {
                continue;
            }
            cards++;
            if (stack.get(DataComponents.CUSTOM_DATA) != null) {
                withData++;
            }
            if (LinkCardItem.read(stack) != null) {
                bound++;
            }
        }
        for (ItemStack stack : CuriosIntegration.stacks(player)) {
            if (!(stack.getItem() instanceof LinkCardItem)) {
                continue;
            }
            cards++;
            if (LinkCardItem.read(stack) != null) {
                bound++;
            }
        }
        return cards + " card(s), " + withData + " with custom_data, " + bound + " bound";
    }
}