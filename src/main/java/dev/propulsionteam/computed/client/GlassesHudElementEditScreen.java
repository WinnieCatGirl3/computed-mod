package dev.propulsionteam.computed.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.client.GlassesHudSettings.HudElement;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;

/**
 * Element properties modal for the glasses HUD canvas: a centered panel with a title, a
 * prompt, a borderless text field and Cancel/Save in the bottom corners - Enter saves,
 * Escape cancels, and closing returns to the parent canvas without background blur. Save
 * commits the text and persists, while Duplicate, Delete and the Color cycle provide the
 * element actions. The panel follows Computed's editor theme, matching the canvas behind it.
 */
public class GlassesHudElementEditScreen extends Screen {
    /** Centered panel geometry, widened for the element action buttons. */
    private static final int PANEL_W = 290;
    private static final int PANEL_H = 87;

    private static final int PANEL_BG = 0xE01A1A1A;
    private static final int OUTLINE = ComputedEditorTheme.ACCENT;
    private static final int TITLE_COLOR = ComputedEditorTheme.TEXT_HEADER;
    private static final int PROMPT_COLOR = ComputedEditorTheme.TEXT_SECONDARY;
    private static final int FIELD_BG = ComputedEditorTheme.BACKGROUND_INPUT;
    private static final int FIELD_OUTLINE = ComputedEditorTheme.BORDER_DEFAULT;

    /** Text colors for the Color cycle; Computed's accent green leads the set. */
    private static final int[] PALETTE = {
        0xFFFFFF, 0x00FF88, 0xA0A0A0, 0x202020, 0xFFAA00, 0xFFFF55, 0x55FF55,
        0x55FFFF, 0x5555FF, 0xFF55FF, 0xFF5555,
    };

    private final GlassesHudSettingsScreen parent;
    private final HudElement element;
    private final Component prompt = Component.translatable("computed.glasses.settings.text");
    /** Text being edited; kept across re-inits like G&amp;G's initializeInput does. */
    private String workingText;
    private EditBox input;
    private Button colorButton;

    public GlassesHudElementEditScreen(GlassesHudSettingsScreen parent, HudElement element) {
        super(Component.translatable("computed.glasses.settings.edit_title"));
        this.parent = parent;
        this.element = element;
        this.workingText = element.text == null ? "" : element.text;
    }

    @Override
    protected void init() {
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        this.input = new EditBox(
                this.font,
                x + 8,
                y + 42,
                PANEL_W - 16,
                10,
                prompt);
        this.input.setBordered(false);
        this.input.setMaxLength(256);
        this.input.setHint(Component.translatable("computed.glasses.settings.placeholders"));
        this.input.setValue(workingText);
        this.input.setResponder(text -> this.workingText = text);
        this.addRenderableWidget(this.input);
        this.setInitialFocus(this.input);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("computed.glasses.settings.cancel"),
                        button -> onClose())
                .bounds(x + 6, y + 72, 50, 14)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("computed.glasses.settings.duplicate"),
                        button -> duplicate())
                .bounds(x + 60, y + 72, 64, 14)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("computed.glasses.settings.delete"),
                        button -> deleteAndClose())
                .bounds(x + 128, y + 72, 52, 14)
                .build());
        this.colorButton = this.addRenderableWidget(Button.builder(
                        colorLabel(), button -> cycleColor())
                .bounds(x + 184, y + 72, 48, 14)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("computed.glasses.settings.save"),
                        button -> confirm())
                .bounds(x + 234, y + 72, 50, 14)
                .build());
    }

    private Component colorLabel() {
        return Component.literal("#" + String.format("%06X", element.textColor()));
    }

    private void cycleColor() {
        int current = element.textColor();
        int index = 0;
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == current) {
                index = i;
                break;
            }
        }
        element.color = PALETTE[(index + 1) % PALETTE.length];
        if (this.colorButton != null) {
            this.colorButton.setMessage(colorLabel());
        }
    }

    /** Copies the element one row up so the duplicate never hides behind its original. */
    private void duplicate() {
        HudElement copy = element.copy();
        float originalY = copy.resolvedY(this.height);
        float raised = originalY - 20f;
        if (raised < 4f) {
            raised = originalY + 20f;
        }
        copy.y = Math.clamp(raised / this.height, 0f, 1f);
        GlassesHudSettings.get().elements.add(copy);
    }

    private void deleteAndClose() {
        GlassesHudSettings.get().elements.remove(element);
        onClose();
    }

    /** G&amp;G's confirm(): commit the text, persist and return to the parent canvas. */
    private void confirm() {
        element.text = workingText;
        element.clamp();
        GlassesHudSettings.get().save();
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Like G&amp;G's TextInputScreen: Enter (and keypad Enter) saves.
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !GlassesHudOverlay.isWearingGlasses(minecraft.player)) {
            // The canvas closes itself when the glasses come off; so does the modal.
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        // Like G&G's base TextInputScreen: inherit the parent's (never pausing).
        return this.parent == null || this.parent.isPauseScreen();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Like G&G's base TextInputScreen: no blur, the world stays visible.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL_BG);
        graphics.renderOutline(x, y, PANEL_W, PANEL_H, OUTLINE);
        graphics.drawCenteredString(
                this.font,
                this.font.plainSubstrByWidth(this.title.getString(), PANEL_W - 16),
                x + PANEL_W / 2,
                y + 3,
                TITLE_COLOR);
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(this.prompt.getString(), PANEL_W - 16),
                x + 8,
                y + 25,
                PROMPT_COLOR,
                false);
        graphics.fill(x + 7, y + 40, x + PANEL_W - 7, y + 56, FIELD_BG);
        graphics.renderOutline(x + 7, y + 40, PANEL_W - 14, 16, FIELD_OUTLINE);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (GlassesHudOverlay.SELF_TEST) {
            Computed.LOGGER.info("[glasses-hud] self-test: modal closed");
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
