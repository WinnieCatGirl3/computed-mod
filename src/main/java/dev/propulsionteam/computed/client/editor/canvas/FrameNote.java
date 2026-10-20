package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;

/**
 * Group frame: a resizable labeled rectangle rendered behind nodes. Dragging the frame moves every
 * node fully contained inside it; the frame itself never participates in align or auto-layout.
 * Persists as a portless {@code computed:frame} graph node.
 */
public final class FrameNote extends WNode {
    public static final String DEFINITION_ID = "computed:frame";

    private static final int TITLE_BAR = 14;
    private static final int HANDLE = 10;
    private static final int MIN_WIDTH = 80;
    private static final int MIN_HEIGHT = 60;
    private static final int DEFAULT_WIDTH = 240;
    private static final int DEFAULT_HEIGHT = 160;
    private static final int MAX_TITLE = 40;
    /** Frame accent colors; index persists with the note. */
    private static final int[] PALETTE = {
        0xFF5B8FD0, 0xFFD08F5B, 0xFF8FD05B, 0xFFD05B8F, 0xFF9B7FD4
    };

    private int frameWidth = DEFAULT_WIDTH;
    private int frameHeight = DEFAULT_HEIGHT;
    private int colorIndex;
    private boolean resizing;
    private boolean titleEditing;
    private int titleCursor;

    public FrameNote(int x, int y, String title) {
        super(ResourceLocation.fromNamespaceAndPath("computed", "frame"), title, x, y);
        titleCursor = getTitle().length();
    }

    public FrameNote(GraphNode source) {
        this(source.x(), source.y(), titleOf(source));
        CompoundTag identity = new CompoundTag();
        identity.putString("id", source.id().toString());
        identity.putString("title", getTitle());
        identity.putInt("x", source.x());
        identity.putInt("y", source.y());
        load(identity);
        CompoundTag width = source.fields().get("width");
        CompoundTag height = source.fields().get("height");
        frameWidth = width == null ? DEFAULT_WIDTH : Math.max(MIN_WIDTH, width.getInt("value"));
        frameHeight = height == null ? DEFAULT_HEIGHT : Math.max(MIN_HEIGHT, height.getInt("value"));
        CompoundTag color = source.fields().get("color");
        colorIndex = color == null ? 0 : Math.floorMod(color.getInt("value"), PALETTE.length);
        setMeasuredSize(frameWidth, frameHeight);
    }

    public static FrameNote duplicate(FrameNote source, int x, int y) {
        FrameNote copy = new FrameNote(x, y, source.getTitle());
        copy.frameWidth = source.frameWidth;
        copy.frameHeight = source.frameHeight;
        copy.colorIndex = source.colorIndex;
        copy.setMeasuredSize(copy.frameWidth, copy.frameHeight);
        return copy;
    }

    public GraphNode toGraphNode(int x, int y, UUID id) {
        CompoundTag title = new CompoundTag();
        title.putString("value", getTitle());
        CompoundTag width = new CompoundTag();
        width.putInt("value", frameWidth);
        CompoundTag height = new CompoundTag();
        height.putInt("value", frameHeight);
        CompoundTag color = new CompoundTag();
        color.putInt("value", colorIndex);
        Map<String, CompoundTag> fields = new LinkedHashMap<>();
        fields.put("title", title);
        fields.put("width", width);
        fields.put("height", height);
        fields.put("color", color);
        return new GraphNode(id, DEFINITION_ID, "", x, y, java.util.List.of(), fields);
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public boolean isTitleEditing() {
        return titleEditing;
    }

    /** Cycles the frame accent color through the palette. */
    public void cycleColor() {
        colorIndex = (colorIndex + 1) % PALETTE.length;
    }

    /** Current frame accent color (opaque RGB). */
    public int frameColor() {
        return PALETTE[Math.floorMod(colorIndex, PALETTE.length)];
    }

    @Override
    public int renderLayer() {
        return 0;
    }

    @Override
    public boolean movesContainedNodes() {
        return true;
    }

    @Override
    public boolean participatesInLayout() {
        return false;
    }

    @Override
    public void updateLayout() {
        setMeasuredSize(frameWidth, frameHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureLayoutUpToDate();
        boolean hovered = mouseX >= getX()
                && mouseX <= getX() + getWidth()
                && mouseY >= getY()
                && mouseY <= getY() + getHeight();
        int color = frameColor();
        graphics.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                withAlpha(color, isSelected() || hovered ? 0x3A : 0x2A));
        graphics.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + TITLE_BAR,
                withAlpha(color, 0x66));
        ComputedEditorStyle.drawPixelOutline(
                graphics,
                getX(),
                getY(),
                getWidth(),
                getHeight(),
                isSelected() || titleEditing ? ComputedEditorTheme.ACCENT : withAlpha(color, 0x88));
        var font = Minecraft.getInstance().font;
        String label = getTitle().isEmpty() ? "Group" : getTitle();
        String shown = font.plainSubstrByWidth(label, Math.max(0, getWidth() - 10));
        graphics.drawString(font, shown, getX() + 5, getY() + 3, ComputedEditorTheme.TEXT_HEADER, false);
        if (titleEditing && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = getX() + 5 + textWidth(getTitle().substring(0, Math.min(titleCursor, getTitle().length())));
            graphics.vLine(cursorX, getY() + 2, getY() + TITLE_BAR - 2, ComputedEditorTheme.TEXT_PRIMARY);
        }
        graphics.fill(
                getX() + getWidth() - HANDLE,
                getY() + getHeight() - HANDLE,
                getX() + getWidth() - 2,
                getY() + getHeight() - 2,
                inResizeHandle(mouseX - getX(), mouseY - getY())
                        ? ComputedEditorTheme.ACCENT
                        : withAlpha(color, 0x88));
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean hasInteractiveElementAt(double localX, double localY) {
        return inResizeHandle(localX, localY) || (isSelected() && localY <= TITLE_BAR);
    }

    @Override
    public boolean mouseClicked(double localX, double localY, int button) {
        if (button != 0) {
            titleEditing = false;
            return false;
        }
        if (inResizeHandle(localX, localY)) {
            resizing = true;
            titleEditing = false;
            return true;
        }
        if (isSelected() && localY <= TITLE_BAR) {
            titleEditing = true;
            resizing = false;
            titleCursor = clampTitleCursorAt(localX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double localX, double localY, int button, double dragX, double dragY) {
        if (resizing) {
            frameWidth = Math.max(MIN_WIDTH, frameWidth + (int) dragX);
            frameHeight = Math.max(MIN_HEIGHT, frameHeight + (int) dragY);
            setMeasuredSize(frameWidth, frameHeight);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double localX, double localY, int button) {
        resizing = false;
        return false;
    }

    @Override
    public boolean hasFocusedElement() {
        return resizing || titleEditing;
    }

    @Override
    public void clearElementFocus() {
        resizing = false;
        titleEditing = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!titleEditing) {
            return false;
        }
        String title = getTitle();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> {
                titleEditing = false;
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (titleCursor > 0) {
                    setTitle(title.substring(0, titleCursor - 1) + title.substring(titleCursor));
                    titleCursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (titleCursor < title.length()) {
                    setTitle(title.substring(0, titleCursor) + title.substring(titleCursor + 1));
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                titleCursor = Math.max(0, titleCursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                titleCursor = Math.min(title.length(), titleCursor + 1);
                return true;
            }
            default -> {
                if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                    return true;
                }
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!titleEditing || Character.isISOControl(codePoint)) {
            return false;
        }
        String title = getTitle();
        if (title.length() >= MAX_TITLE) {
            return true;
        }
        setTitle(title.substring(0, titleCursor) + codePoint + title.substring(titleCursor));
        titleCursor++;
        return true;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putInt("frameW", frameWidth);
        tag.putInt("frameH", frameHeight);
        tag.putInt("frameColor", colorIndex);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        frameWidth = Math.max(MIN_WIDTH, tag.getInt("frameW") == 0 ? DEFAULT_WIDTH : tag.getInt("frameW"));
        frameHeight = Math.max(MIN_HEIGHT, tag.getInt("frameH") == 0 ? DEFAULT_HEIGHT : tag.getInt("frameH"));
        colorIndex = Math.floorMod(tag.getInt("frameColor"), PALETTE.length);
        titleCursor = Math.min(titleCursor, getTitle().length());
        setMeasuredSize(frameWidth, frameHeight);
    }

    private boolean inResizeHandle(double localX, double localY) {
        return localX >= frameWidth - HANDLE
                && localY >= frameHeight - HANDLE
                && localX <= frameWidth - 2
                && localY <= frameHeight - 2;
    }

    private int clampTitleCursorAt(double localX) {
        String title = getTitle();
        int average = Math.max(1, textWidth(title) / Math.max(1, title.length()));
        return net.minecraft.util.Mth.clamp(
                (int) Math.round((localX - 5) / (double) average), 0, title.length());
    }

    private static int textWidth(String text) {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            return Math.max(8, text.length() * 6);
        }
        return Minecraft.getInstance().font.width(text);
    }

    private static String titleOf(GraphNode source) {
        CompoundTag title = source.fields().get("title");
        return title == null ? "Group" : title.getString("value");
    }
}
