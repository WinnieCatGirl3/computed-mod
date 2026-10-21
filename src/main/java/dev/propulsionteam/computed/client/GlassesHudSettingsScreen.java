package dev.propulsionteam.computed.client;

import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.client.GlassesHudSettings.HudElement;
import dev.propulsionteam.computed.content.items.GlassesItem;
import dev.propulsionteam.computed.content.items.PortableLinkData;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;

/**
 * WYSIWYG editor for the glasses HUD: a plain canvas without widgets or blur over the live
 * game view, one dark panel per element with Computed's own editor theme, eight resize
 * handles, a visibility toggle and a cursor tooltip. Elements are dragged directly, resized
 * from any edge or corner (mapping the target rect onto the text scale), and double-clicking
 * an element opens {@link GlassesHudElementEditScreen} for text and color while
 * double-clicking empty space creates an element there.
 * Interaction follows the overlay
 * editor patterns of Gadgets &amp; Gizmos, but the visuals are Computed's own.
 * Settings persist via {@link GlassesHudSettings#save()} on close.
 */
public class GlassesHudSettingsScreen extends Screen {
    // Computed's editor palette (same green identity as the node editor).
    private static final int PANEL_BG = 0xC81A1A1A;
    private static final int OUTLINE_IDLE = ComputedEditorTheme.BORDER_DEFAULT;
    private static final int OUTLINE_HOVER = ComputedEditorTheme.ACCENT;
    private static final int TITLE_COLOR = ComputedEditorTheme.TEXT_HEADER;
    private static final int BODY_COLOR = ComputedEditorTheme.TEXT_SECONDARY;
    private static final int HANDLE_FILL = ComputedEditorTheme.BACKGROUND_TERTIARY;
    private static final int HANDLE_OUTLINE = ComputedEditorTheme.BORDER_HIGHLIGHT;
    private static final int HIDDEN_FILL = ComputedEditorTheme.BACKGROUND_MODAL_SCRIM;
    private static final int VIS_VISIBLE = ComputedEditorTheme.ACCENT_DARK;
    private static final int VIS_HIDDEN = ComputedEditorTheme.DANGER_BACKGROUND;
    private static final int VIS_OUTLINE = ComputedEditorTheme.BORDER_HIGHLIGHT;
    private static final int VIS_TEXT = ComputedEditorTheme.TEXT_PRIMARY;

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;
    /** G&G's minimum box size for custom HUD overlays. */
    private static final int BOX_MIN_WIDTH = 60;
    private static final int BOX_MIN_HEIGHT = 26;
    private static final long DOUBLE_CLICK_MS = 400;
    private static final double DOUBLE_CLICK_DISTANCE = 4;

    /** G&amp;G's eight resize handles, same order and ordinals as their ResizeHandle. */
    private enum ResizeHandle {
        TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT
    }

    /** G&amp;G's Rect record. */
    private record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= right() && mouseY >= y && mouseY <= bottom();
        }

        Rect padded(int amount) {
            return new Rect(x - amount, y - amount, width + amount * 2, height + amount * 2);
        }
    }

    private final GlassesHudSettings settings = GlassesHudSettings.get();
    /** Ticks during which a K press is ignored, so the opening press cannot close the screen. */
    private int keyCloseGuardTicks = 2;

    private HudElement dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    private HudElement resizing;
    private ResizeHandle resizingHandle;
    private Rect resizeStartBox;
    private double resizeStartMouseX;
    private double resizeStartMouseY;
    /** Scale at the start of the resize; the target rect is mapped onto it proportionally. */
    private float resizeStartScale;

    /** Double-click bookkeeping: the box target and press position of the last click. */
    private HudElement pendingElement;
    private double pendingMouseX;
    private double pendingMouseY;
    private long pendingClickAt;
    /** Whether the last recorded press sat on the visibility button (its toggle is undone). */
    private boolean pendingOnButton;

    public GlassesHudSettingsScreen() {
        super(Component.translatable("computed.glasses.settings.title"));
    }

    @Override
    protected void init() {
        // G&G's edit canvas adds no widgets: everything is drawn and hit-tested by hand.
    }

    // ---------------------------------------------------------------- geometry

    private PortableLinkData currentLink() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : GlassesItem.findFirstBoundLink(minecraft.player);
    }

    /** Preview text: the element's own placeholder template. */
    private Component previewText(HudElement element, PortableLinkData link) {
        return Component.literal(GlassesHudSettings.resolveTemplate(element.text, link));
    }

    private Component previewText(HudElement element) {
        return previewText(element, currentLink());
    }

    /**
     * The element's box around its text: one line ({@code width + 10} by {@code 17} at 1x)
     * with a 60x26 minimum for usable handles; the text line is centered vertically so the
     * box hugs the HUD text wherever the element sits.
     */
    private Rect boxOf(HudElement element, PortableLinkData link) {
        int textWidth = this.font.width(previewText(element, link));
        float scale = element.scale;
        int width = Math.max(Math.round((textWidth + 10) * scale), BOX_MIN_WIDTH);
        int height = Math.max(Math.round(17 * scale), BOX_MIN_HEIGHT);
        float centerX = element.resolvedX(this.width);
        float boxTop = element.resolvedY(this.height) - (height - 9f * scale) / 2f;
        return new Rect(Math.round(centerX - width / 2f), Math.round(boxTop), width, height);
    }

    private Rect boxOf(HudElement element) {
        return boxOf(element, currentLink());
    }

    /** G&amp;G's visibilityButtonRect. */
    private Rect visibilityButtonRect(Rect rect) {
        int width = Math.min(
                rect.width() - 8,
                Math.max(
                        18,
                        this.font.width(Component.translatable(
                                "computed.glasses.settings.toggle_visibility"))
                                + 8));
        return new Rect(rect.right() - width - 4, rect.y() + 4, width, 14);
    }

    /** G&amp;G's resizeHandleRect: a 7x7 square centered on the edge or corner. */
    private static Rect resizeHandleRect(Rect rect, ResizeHandle handle) {
        int half = 3;
        int centerX = switch (handle.ordinal()) {
            case 0, 6, 7 -> rect.x();
            case 1, 5 -> rect.x() + rect.width() / 2;
            default -> rect.right();
        };
        int centerY = switch (handle.ordinal()) {
            case 0, 1, 2 -> rect.y();
            case 3, 7 -> rect.y() + rect.height() / 2;
            default -> rect.bottom();
        };
        return new Rect(centerX - half, centerY - half, 7, 7);
    }

    /** G&amp;G's resizeHandleAt: handles hit-test two pixels outside their visual square. */
    private static ResizeHandle resizeHandleAt(Rect rect, double mouseX, double mouseY) {
        for (ResizeHandle handle : ResizeHandle.values()) {
            if (resizeHandleRect(rect, handle).padded(2).contains(mouseX, mouseY)) {
                return handle;
            }
        }
        return null;
    }

    /** G&amp;G's clampToScreen. */
    private Rect clampToScreen(Rect rect) {
        return new Rect(
                Mth.clamp(rect.x(), 0, Math.max(0, this.width - rect.width())),
                Mth.clamp(rect.y(), 0, Math.max(0, this.height - rect.height())),
                rect.width(),
                rect.height());
    }

    /** Topmost element under the cursor; G&amp;G iterates newest boxes first as well. */
    private HudElement elementAt(double mouseX, double mouseY) {
        for (int index = settings.elements.size() - 1; index >= 0; index--) {
            HudElement element = settings.elements.get(index);
            if (boxOf(element).contains(mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    /** Topmost element whose visibility button is under the cursor. */
    private HudElement visibilityTargetAt(double mouseX, double mouseY) {
        for (int index = settings.elements.size() - 1; index >= 0; index--) {
            HudElement element = settings.elements.get(index);
            if (visibilityButtonRect(boxOf(element)).contains(mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    private boolean handleAtAny(double mouseX, double mouseY) {
        for (HudElement element : settings.elements) {
            if (resizeHandleAt(boxOf(element), mouseX, mouseY) != null) {
                return true;
            }
        }
        return false;
    }

    /** Writes a screen-space rect back into the element's normalized, center-based position. */
    private void applyRect(HudElement element, Rect rect) {
        float centerX = rect.x() + rect.width() / 2f;
        element.x = centerX / this.width;
        // Inverse of boxOf: resolvedY = box top + (height - text height) / 2.
        element.y = (rect.y() + (rect.height() - 9f * element.scale) / 2f) / this.height;
    }

    /** Creates an element whose text is centered on the given pointer position. */
    private HudElement createElementAt(double mouseX, double mouseY) {
        HudElement element = new HudElement();
        element.x = (float) (mouseX / this.width);
        element.y = (float) ((mouseY - (9f * element.scale) / 2f) / this.height);
        element.clamp();
        return element;
    }

    private void clearPendingClick() {
        pendingClickAt = 0;
        pendingElement = null;
        pendingOnButton = false;
    }

    private void openModal(HudElement element) {
        dragging = null;
        resizing = null;
        resizeStartBox = null;
        clearPendingClick();
        this.minecraft.setScreen(new GlassesHudElementEditScreen(this, element));
        if (GlassesHudOverlay.SELF_TEST) {
            Computed.LOGGER.info("[glasses-hud] self-test: modal open");
        }
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        long now = Util.getMillis();
        // Resolve the cursor position first: boxes, their visibility buttons (G&G checks those
        // before anything else) and their resize handles (G&G checks those before drags).
        HudElement target = elementAt(mouseX, mouseY);
        HudElement visTarget = visibilityTargetAt(mouseX, mouseY);
        boolean onHandle = handleAtAny(mouseX, mouseY);
        // Ours (G&G edits HUD text through its node editor instead): a second press on the
        // same box within 400 ms opens the properties modal; on empty canvas it creates an
        // element at the pointer first. A first press on the visibility button also counts,
        // and its toggle is undone so the double-click never changes visibility.
        if (!onHandle
                && pendingClickAt != 0
                && now - pendingClickAt <= DOUBLE_CLICK_MS
                && Math.hypot(mouseX - pendingMouseX, mouseY - pendingMouseY)
                        <= DOUBLE_CLICK_DISTANCE
                && target == pendingElement) {
            if (pendingOnButton && pendingElement != null) {
                pendingElement.visible = !pendingElement.visible;
                settings.save();
            }
            clearPendingClick();
            dragging = null;
            resizing = null;
            resizeStartBox = null;
            if (target != null) {
                openModal(target);
            } else {
                HudElement created = createElementAt(mouseX, mouseY);
                settings.elements.add(created);
                openModal(created);
            }
            return true;
        }
        // Single press: G&G's visibility toggle first.
        if (visTarget != null) {
            pendingElement = visTarget;
            pendingMouseX = mouseX;
            pendingMouseY = mouseY;
            pendingClickAt = now;
            pendingOnButton = true;
            visTarget.visible = !visTarget.visible;
            settings.save();
            return true;
        }
        if (onHandle) {
            // This press starts a resize, so it must not count for a double-click.
            clearPendingClick();
        } else {
            pendingElement = target;
            pendingMouseX = mouseX;
            pendingMouseY = mouseY;
            pendingClickAt = now;
            pendingOnButton = false;
        }
        // G&G: resize handles before box drags, newest box first.
        for (int index = settings.elements.size() - 1; index >= 0; index--) {
            HudElement element = settings.elements.get(index);
            ResizeHandle handle = resizeHandleAt(boxOf(element), mouseX, mouseY);
            if (handle != null) {
                resizing = element;
                resizingHandle = handle;
                resizeStartBox = boxOf(element);
                resizeStartMouseX = mouseX;
                resizeStartMouseY = mouseY;
                resizeStartScale = element.scale;
                return true;
            }
        }
        // G&G: drag the topmost box under the cursor.
        for (int index = settings.elements.size() - 1; index >= 0; index--) {
            HudElement element = settings.elements.get(index);
            Rect box = boxOf(element);
            if (box.contains(mouseX, mouseY)) {
                dragging = element;
                dragOffsetX = mouseX - box.x();
                dragOffsetY = mouseY - box.y();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (resizing != null) {
            resizeElement(mouseX, mouseY);
            return true;
        }
        if (dragging != null) {
            Rect box = boxOf(dragging);
            Rect candidate = clampToScreen(new Rect(
                    (int) (mouseX - dragOffsetX),
                    (int) (mouseY - dragOffsetY),
                    box.width(),
                    box.height()));
            applyRect(dragging, candidate);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        resizing = null;
        resizingHandle = null;
        resizeStartBox = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * G&amp;G's per-axis resize rules (edge handles move one side, corners both, 60x26 minimum,
     * clampToScreen), then the target rect is mapped back onto our text scale - the same
     * min-of-both-ratios behavior G&amp;G uses when a rect scales its HUD content.
     */
    private void resizeElement(double mouseX, double mouseY) {
        if (resizing == null || resizeStartBox == null || resizingHandle == null) {
            return;
        }
        Rect candidate = resizedBox(mouseX, mouseY);
        float ratioWidth = candidate.width() / (float) resizeStartBox.width();
        float ratioHeight = candidate.height() / (float) resizeStartBox.height();
        float scale = switch (resizingHandle) {
            case LEFT, RIGHT -> ratioWidth;
            case TOP, BOTTOM -> ratioHeight;
            default -> Math.min(ratioWidth, ratioHeight);
        };
        resizing.scale = Math.clamp(resizeStartScale * scale, MIN_SCALE, MAX_SCALE);
        applyRect(resizing, candidate);
    }

    /** Shared per-axis resize math: edge handles move one side, corners both, mins, screen clamp. */
    private Rect resizedBox(double mouseX, double mouseY) {
        int deltaX = (int) mouseX - (int) resizeStartMouseX;
        int deltaY = (int) mouseY - (int) resizeStartMouseY;
        int left = resizeStartBox.x();
        int top = resizeStartBox.y();
        int right = resizeStartBox.right();
        int bottom = resizeStartBox.bottom();
        if (resizingHandle == ResizeHandle.TOP_LEFT
                || resizingHandle == ResizeHandle.LEFT
                || resizingHandle == ResizeHandle.BOTTOM_LEFT) {
            left += deltaX;
        }
        if (resizingHandle == ResizeHandle.TOP_LEFT
                || resizingHandle == ResizeHandle.TOP
                || resizingHandle == ResizeHandle.TOP_RIGHT) {
            top += deltaY;
        }
        if (resizingHandle == ResizeHandle.TOP_RIGHT
                || resizingHandle == ResizeHandle.RIGHT
                || resizingHandle == ResizeHandle.BOTTOM_RIGHT) {
            right += deltaX;
        }
        if (resizingHandle == ResizeHandle.BOTTOM_LEFT
                || resizingHandle == ResizeHandle.BOTTOM
                || resizingHandle == ResizeHandle.BOTTOM_RIGHT) {
            bottom += deltaY;
        }
        if (right - left < BOX_MIN_WIDTH) {
            if (resizingHandle == ResizeHandle.TOP_LEFT
                    || resizingHandle == ResizeHandle.LEFT
                    || resizingHandle == ResizeHandle.BOTTOM_LEFT) {
                left = right - BOX_MIN_WIDTH;
            } else {
                right = left + BOX_MIN_WIDTH;
            }
        }
        if (bottom - top < BOX_MIN_HEIGHT) {
            if (resizingHandle == ResizeHandle.TOP_LEFT
                    || resizingHandle == ResizeHandle.TOP
                    || resizingHandle == ResizeHandle.TOP_RIGHT) {
                top = bottom - BOX_MIN_HEIGHT;
            } else {
                bottom = top + BOX_MIN_HEIGHT;
            }
        }
        return clampToScreen(new Rect(left, top, right - left, bottom - top));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Like G&G's OverlayInteractionScreen: ESC or the mode key (guarded against the
        // opening press) closes the canvas; closing saves.
        if (keyCode == 256
                || (keyCloseGuardTicks <= 0
                        && GlassesHudKeybinds.OPEN_SETTINGS.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (keyCloseGuardTicks > 0) {
            keyCloseGuardTicks--;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !GlassesHudOverlay.isWearingGlasses(minecraft.player)) {
            // Like G&G's edit mode: close (and save) as soon as the glasses come off.
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        // G&G's edit mode keeps the game running so live positions stay current.
        return false;
    }

    @Override
    public void removed() {
        settings.save();
        super.removed();
    }

    @Override
    public void onClose() {
        settings.save();
        super.onClose();
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Like G&G: the live game view stays visible behind the canvas, unblurred.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The hint sits under the element boxes so overlays always read on top of it.
        graphics.drawString(
                this.font,
                Component.translatable("computed.glasses.settings.active"),
                8,
                this.height - 18,
                OUTLINE_HOVER,
                false);
        PortableLinkData link = currentLink();
        for (HudElement element : settings.elements) {
            Rect box = boxOf(element, link);
            boolean hovered = box.contains(mouseX, mouseY);
            Component text = previewText(element, link);
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), PANEL_BG);
            graphics.renderOutline(
                    box.x(), box.y(), box.width(), box.height(),
                    hovered ? OUTLINE_HOVER : OUTLINE_IDLE);
            graphics.pose().pushPose();
            graphics.pose().translate(
                    box.x() + 5,
                    box.y() + (box.height() - 9f * element.scale) / 2f,
                    0f);
            graphics.pose().scale(element.scale, element.scale, 1f);
            graphics.drawString(this.font, text, 0, 0, element.textColor(), false);
            graphics.pose().popPose();
            drawResizeHandles(graphics, box, hovered);
            if (!element.visible) {
                graphics.fill(box.x(), box.y(), box.right(), box.bottom(), HIDDEN_FILL);
            }
            // The toggle only shows while the box is hovered, so the text stays unobstructed.
            if (hovered) {
                drawVisibilityButton(graphics, box, element);
            }
        }
        drawCursorTooltip(graphics, mouseX, mouseY);
    }

    /** Eight dark 7x7 handles around the box, outlined in the accent color while hovered. */
    private void drawResizeHandles(GuiGraphics graphics, Rect rect, boolean hovered) {
        for (ResizeHandle handle : ResizeHandle.values()) {
            Rect handleRect = resizeHandleRect(rect, handle);
            graphics.fill(
                    handleRect.x(), handleRect.y(), handleRect.right(), handleRect.bottom(),
                    HANDLE_FILL);
            graphics.renderOutline(
                    handleRect.x(), handleRect.y(), handleRect.width(), handleRect.height(),
                    hovered ? OUTLINE_HOVER : HANDLE_OUTLINE);
        }
    }

    /** Toggle button on the box's top right: green while visible, danger red while hidden. */
    private void drawVisibilityButton(GuiGraphics graphics, Rect box, HudElement element) {
        Rect button = visibilityButtonRect(box);
        graphics.fill(
                button.x(), button.y(), button.right(), button.bottom(),
                element.visible ? VIS_VISIBLE : VIS_HIDDEN);
        graphics.renderOutline(
                button.x(), button.y(), button.width(), button.height(), VIS_OUTLINE);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("computed.glasses.settings.toggle_visibility"),
                button.x() + button.width() / 2,
                button.y() + 3,
                VIS_TEXT);
    }

    /** Floating help box following the cursor: four short lines in Computed's editor colors. */
    private void drawCursorTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> lines = List.of(
                Component.translatable("computed.glasses.settings.tooltip.title"),
                Component.translatable("computed.glasses.settings.tooltip.drag"),
                Component.translatable("computed.glasses.settings.tooltip.resize"),
                Component.translatable(
                        "computed.glasses.settings.tooltip.exit",
                        GlassesHudKeybinds.OPEN_SETTINGS.getTranslatedKeyMessage().getString()));
        int width = lines.stream().mapToInt(this.font::width).max().orElse(0) + 10;
        int height = lines.size() * 10 + 7;
        int x = Math.clamp(mouseX + 12, 0, Math.max(0, this.width - width));
        int y = Math.clamp(mouseY + 12, 0, Math.max(0, this.height - height));
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        graphics.renderOutline(x, y, width, height, OUTLINE_HOVER);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(
                    this.font,
                    lines.get(index),
                    x + 5,
                    y + 5 + index * 10,
                    index == 0 ? TITLE_COLOR : BODY_COLOR,
                    false);
        }
    }
}
