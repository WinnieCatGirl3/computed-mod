package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;

/**
 * Sticky-note node: free multi-line text on the canvas. Never executes; persists through the
 * program codec as a portless {@code computed:comment} graph node.
 */
public final class CommentNote extends WNode {
    public static final String DEFINITION_ID = "computed:comment";

    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int DEFAULT_WIDTH = 160;
    private static final int MIN_HEIGHT = 36;
    private static final int MAX_LENGTH = 2000;
    private static final int NOTE_BODY = 0xE8F6E7A8;
    private static final int NOTE_BODY_HOVER = 0xEFF8EDB8;
    private static final int NOTE_BORDER = 0xFFD6BE6A;
    private static final int NOTE_TEXT = 0xFF3A3220;
    private static final int NOTE_TEXT_DIM = 0xFF8A7A4A;

    private boolean editing;
    private int cursor;

    public CommentNote(int x, int y, String text) {
        super(ResourceLocation.fromNamespaceAndPath("computed", "comment"), text, x, y);
        cursor = getText().length();
    }

    public CommentNote(GraphNode source) {
        this(source.x(), source.y(), textOf(source));
        CompoundTag identity = new CompoundTag();
        identity.putString("id", source.id().toString());
        identity.putString("title", getText());
        identity.putInt("x", source.x());
        identity.putInt("y", source.y());
        load(identity);
    }

    public static CommentNote duplicate(CommentNote source, int x, int y) {
        return new CommentNote(x, y, source.getText());
    }

    public GraphNode toGraphNode(int x, int y, UUID id) {
        CompoundTag text = new CompoundTag();
        text.putString("value", getText());
        return new GraphNode(id, DEFINITION_ID, "", x, y, List.of(), Map.of("text", text));
    }

    public String getText() {
        return getTitle();
    }

    public void setText(String text) {
        setTitle(text == null ? "" : text);
        cursor = Math.min(cursor, getText().length());
    }

    public boolean isEditing() {
        return editing;
    }

    @Override
    public int renderLayer() {
        return 5;
    }

    @Override
    public void updateLayout() {
        int lines = Math.max(1, wrap(getText()).size());
        setMeasuredSize(DEFAULT_WIDTH, Math.max(MIN_HEIGHT, PADDING * 2 + lines * LINE_HEIGHT));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureLayoutUpToDate();
        boolean hovered = mouseX >= getX()
                && mouseX <= getX() + getWidth()
                && mouseY >= getY()
                && mouseY <= getY() + getHeight();
        graphics.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                hovered || isSelected() ? NOTE_BODY_HOVER : NOTE_BODY);
        ComputedEditorStyle.drawPixelOutline(
                graphics,
                getX(),
                getY(),
                getWidth(),
                getHeight(),
                isSelected() || editing ? ComputedEditorTheme.ACCENT : NOTE_BORDER);
        var font = Minecraft.getInstance().font;
        List<WrappedLine> lines = wrap(getText());
        int textY = getY() + PADDING;
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(
                    font,
                    lines.get(index).text(),
                    getX() + PADDING,
                    textY + index * LINE_HEIGHT,
                    getText().isEmpty() && index == 0 && !editing ? NOTE_TEXT_DIM : NOTE_TEXT,
                    false);
        }
        if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
            int[] position = cursorPosition(lines);
            int cursorX = getX() + PADDING + position[0];
            int cursorY = textY + position[1] * LINE_HEIGHT;
            graphics.vLine(cursorX, cursorY - 1, cursorY + LINE_HEIGHT - 2, ComputedEditorTheme.TEXT_PRIMARY);
        }
    }

    /** Starts text editing at the given local coordinates (entered via double-click). */
    public void beginEditing(double localX, double localY) {
        editing = true;
        cursor = clampCursorAt(localX, localY);
    }

    @Override
    public boolean hasInteractiveElementAt(double localX, double localY) {
        return editing;
    }

    @Override
    public boolean mouseClicked(double localX, double localY, int button) {
        if (button != 0) {
            editing = false;
            return false;
        }
        if (localX < 0 || localY < 0 || localX > getWidth() || localY > getHeight()) {
            return false;
        }
        if (!isSelected()) {
            return false;
        }
        editing = true;
        cursor = clampCursorAt(localX, localY);
        return true;
    }

    @Override
    public boolean hasFocusedElement() {
        return editing;
    }

    @Override
    public void clearElementFocus() {
        editing = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing) {
            return false;
        }
        String text = getText();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                editing = false;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER -> {
                insertText("\n");
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    int next = cursor - 1;
                    setText(text.substring(0, next) + text.substring(cursor));
                    // setText already clamped the cursor to the new length — assign explicitly,
                    // otherwise a second decrement here pushes it to -1 at the end of the text.
                    cursor = Math.min(next, getText().length());
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < text.length()) {
                    setText(text.substring(0, cursor) + text.substring(cursor + 1));
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(text.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = lineStart(text, cursor);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = lineEnd(text, cursor);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                cursor = moveVertically(text, cursor, -1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                cursor = moveVertically(text, cursor, 1);
                return true;
            }
            default -> {
                if (net.minecraft.client.gui.screens.Screen.hasControlDown()
                        && keyCode != GLFW.GLFW_KEY_Z
                        && keyCode != GLFW.GLFW_KEY_Y) {
                    return true;
                }
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!editing || Character.isISOControl(codePoint)) {
            return false;
        }
        insertText(String.valueOf(codePoint));
        return true;
    }

    private void insertText(String insertion) {
        String text = getText();
        if (text.length() + insertion.length() > MAX_LENGTH) {
            return;
        }
        cursor = Math.min(Math.max(cursor, 0), text.length());
        setText(text.substring(0, cursor) + insertion + text.substring(cursor));
        cursor += insertion.length();
    }

    private int clampCursorAt(double localX, double localY) {
        String text = getText();
        List<String> rawLines = List.of(text.split("\n", -1));
        int line = net.minecraft.util.Mth.clamp(
                (int) Math.floor((localY - PADDING) / (double) LINE_HEIGHT), 0, rawLines.size() - 1);
        String rawLine = rawLines.get(line);
        int average = Math.max(1, textWidth(rawLine) / Math.max(1, rawLine.length()));
        int column = net.minecraft.util.Mth.clamp(
                (int) Math.round((localX - PADDING) / (double) average), 0, rawLine.length());
        int offset = 0;
        for (int index = 0; index < line; index++) {
            offset += rawLines.get(index).length() + 1;
        }
        return offset + column;
    }

    /** Maps the cursor index to a wrapped display line and pixel offset inside that line. */
    private int[] cursorPosition(List<WrappedLine> lines) {
        int line = 0;
        int column = 0;
        for (int index = 0; index < lines.size(); index++) {
            WrappedLine wrapped = lines.get(index);
            int end = wrapped.sourceStart() + wrapped.text().length();
            if (cursor <= end || index == lines.size() - 1) {
                line = index;
                column = Math.max(0, Math.min(cursor - wrapped.sourceStart(), wrapped.text().length()));
                break;
            }
        }
        return new int[] {textWidth(lines.get(line).text().substring(0, column)), line};
    }

    private static int lineStart(String text, int cursor) {
        return text.lastIndexOf('\n', Math.max(0, cursor - 1)) + 1;
    }

    private static int lineEnd(String text, int cursor) {
        int end = text.indexOf('\n', cursor);
        return end < 0 ? text.length() : end;
    }

    private static int moveVertically(String text, int cursor, int direction) {
        int line = 0;
        for (int index = 0; index < cursor && index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        int targetLine = line + direction;
        if (targetLine < 0) {
            return cursor;
        }
        int start = 0;
        for (int index = 0, seen = 0; index <= text.length(); index++) {
            if (index == text.length() || text.charAt(index) == '\n') {
                if (seen == targetLine) {
                    int column = cursor - lineStart(text, cursor);
                    return start + Math.min(column, index - start);
                }
                seen++;
                start = index + 1;
            }
        }
        return cursor;
    }

    /** Greedy word wrap; each display line records where it starts in the raw text. */
    private static List<WrappedLine> wrap(String text) {
        int maxWidth = DEFAULT_WIDTH - PADDING * 2;
        List<WrappedLine> lines = new ArrayList<>();
        int offset = 0;
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add(new WrappedLine("", offset));
                offset += 1;
                continue;
            }
            int paragraphOffset = offset;
            StringBuilder current = new StringBuilder();
            int currentStart = paragraphOffset;
            for (String word : paragraph.split(" ", -1)) {
                String attempt = current.isEmpty() ? word : current + " " + word;
                if (textWidth(attempt) > maxWidth && !current.isEmpty()) {
                    lines.add(new WrappedLine(current.toString(), currentStart));
                    currentStart += current.length() + 1;
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(attempt);
                }
            }
            lines.add(new WrappedLine(current.toString(), currentStart));
            offset = paragraphOffset + paragraph.length() + 1;
        }
        return lines;
    }

    private static int textWidth(String text) {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            return Math.max(8, text.length() * 6);
        }
        return Minecraft.getInstance().font.width(text);
    }

    private static String textOf(GraphNode source) {
        CompoundTag text = source.fields().get("text");
        return text == null ? "" : text.getString("value");
    }

    /** One visually wrapped line and the raw-text index where it begins. */
    private record WrappedLine(String text, int sourceStart) {}
}
