package dev.propulsionteam.computed.internal.node.api;

import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

public class WNode {
    private static final int PIN_SIZE = 5;
    private static final int PIN_HOVER_SIZE = 7;

    private UUID id = UUID.randomUUID();
    private final ResourceLocation typeId;
    private String title;
    private int x;
    private int y;
    private int width = 120;
    private int height = 40;
    private int topoDepth;
    private boolean selected;
    private boolean layoutDirty = true;
    private boolean pinSchemaUpdate;
    private boolean pinSchemaChanged;
    private final List<WPin> inputs = new ArrayList<>();
    private final List<WPin> outputs = new ArrayList<>();
    private transient WGraph owningGraph;

    public WNode(ResourceLocation typeId, String title, int x, int y) {
        this.typeId = typeId;
        this.title = title == null ? "" : title;
        this.x = x;
        this.y = y;
    }

    public void addInput(String name, int color) {
        addInput(null, name, WPin.DataType.NUMBER, color);
    }

    public void addInput(String name, WPin.DataType dataType, int color) {
        addInput(null, name, dataType, color);
    }

    public void addInput(String stableKey, String name, WPin.DataType dataType, int color) {
        inputs.add(new WPin(stableKey, name, WPin.Type.INPUT, dataType, color));
        markPinSchemaChanged();
    }

    public void addOutput(String name, int color) {
        addOutput(null, name, WPin.DataType.NUMBER, color);
    }

    public void addOutput(String name, WPin.DataType dataType, int color) {
        addOutput(null, name, dataType, color);
    }

    public void addOutput(String stableKey, String name, WPin.DataType dataType, int color) {
        outputs.add(new WPin(stableKey, name, WPin.Type.OUTPUT, dataType, color));
        markPinSchemaChanged();
    }

    public void markLayoutDirty() {
        layoutDirty = true;
    }

    public void markPinSchemaChanged() {
        layoutDirty = true;
        if (pinSchemaUpdate) {
            pinSchemaChanged = true;
        } else if (owningGraph != null) {
            owningGraph.onNodePinSchemaChanged(this);
        }
    }

    protected final void beginPinSchemaUpdate() {
        pinSchemaUpdate = true;
    }

    protected final void endPinSchemaUpdate() {
        pinSchemaUpdate = false;
        if (pinSchemaChanged) {
            pinSchemaChanged = false;
            if (owningGraph != null) {
                owningGraph.onNodePinSchemaChanged(this);
            }
        }
    }

    void bindOwningGraph(WGraph graph) {
        owningGraph = graph;
    }

    public final void ensureLayoutUpToDate() {
        if (layoutDirty) {
            updateLayout();
        }
    }

    public void updateLayout() {
        int left = inputs.stream().mapToInt(pin -> measureTextWidth(pin.getName())).max().orElse(0);
        int right = outputs.stream().mapToInt(pin -> measureTextWidth(pin.getName())).max().orElse(0);
        width = Math.max(measureTextWidth(title) + 20, Math.max(96, left + right + 44));
        height = 21 + Math.max(inputs.size(), outputs.size()) * 12;
        layoutDirty = false;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureLayoutUpToDate();
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        graphics.fill(x, y, x + width, y + height, ComputedEditorTheme.nodeBody(hovered, selected, false));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 14, ComputedEditorTheme.ACCENT_HEADER);
        graphics.drawString(Minecraft.getInstance().font, title, x + 5, y + 3, ComputedEditorTheme.TEXT_HEADER, false);
        for (int i = 0; i < inputs.size(); i++) {
            renderPin(graphics, x - 4, y + 18 + i * 12, inputs.get(i), true, mouseX, mouseY);
        }
        for (int i = 0; i < outputs.size(); i++) {
            renderPin(graphics, x + width - 1, y + 18 + i * 12, outputs.get(i), false, mouseX, mouseY);
        }
    }

    public int getPinAt(int px, int py, boolean input) {
        int startX = input ? -4 : width - 1;
        List<WPin> pins = input ? inputs : outputs;
        for (int i = 0; i < pins.size(); i++) {
            int pinY = 18 + i * 12;
            if (px >= startX - 1 && px <= startX + PIN_SIZE && py >= pinY - 1 && py <= pinY + PIN_SIZE) {
                return i;
            }
        }
        return -1;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        return false;
    }

    public boolean hasInteractiveElementAt(double mouseX, double mouseY) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    public boolean hasFocusedElement() {
        return false;
    }

    public void clearElementFocus() {}

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("typeId", typeId.toString());
        tag.putString("id", id.toString());
        tag.putString("title", title);
        tag.putInt("x", x);
        tag.putInt("y", y);
        ListTag inputTags = new ListTag();
        for (int i = 0; i < inputs.size(); i++) {
            WPin pin = inputs.get(i);
            if (pin.getStableKey() == null) {
                pin.setStableKey(stablePortId(inputs, i, "input"));
            }
            inputTags.add(pin.save());
        }
        tag.put("inputs", inputTags);
        ListTag outputTags = new ListTag();
        for (int i = 0; i < outputs.size(); i++) {
            WPin pin = outputs.get(i);
            if (pin.getStableKey() == null) {
                pin.setStableKey(stablePortId(outputs, i, "output"));
            }
            outputTags.add(pin.save());
        }
        tag.put("outputs", outputTags);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("id")) {
            id = UUID.fromString(tag.getString("id"));
        }
        if (tag.contains("title")) {
            title = tag.getString("title");
        }
        x = tag.getInt("x");
        y = tag.getInt("y");
        ListTag inputTags = tag.getList("inputs", 10);
        for (int i = 0; i < Math.min(inputs.size(), inputTags.size()); i++) {
            inputs.get(i).load(inputTags.getCompound(i));
        }
        ListTag outputTags = tag.getList("outputs", 10);
        for (int i = 0; i < Math.min(outputs.size(), outputTags.size()); i++) {
            outputs.get(i).load(outputTags.getCompound(i));
        }
        layoutDirty = true;
    }

    static String stablePortId(List<WPin> pins, int index, String direction) {
        String explicit = pins.get(index).getStableKey();
        if (explicit != null) {
            return explicit;
        }
        String label = pins.get(index).getName().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("^[^a-z]+", "")
                .replaceAll("_+$", "");
        if (label.isEmpty()) {
            label = "port";
        }
        String base = direction + "." + label;
        int duplicate = 0;
        for (int i = 0; i <= index; i++) {
            String other = pins.get(i).getName().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_.-]+", "_")
                    .replaceAll("^[^a-z]+", "")
                    .replaceAll("_+$", "");
            if (other.isEmpty()) {
                other = "port";
            }
            if (other.equals(label)) {
                duplicate++;
            }
        }
        return duplicate <= 1 ? base : base + "." + duplicate;
    }

    public List<WPin> getInputs() {
        return inputs;
    }

    public List<WPin> getOutputs() {
        return outputs;
    }

    public UUID getId() {
        return id;
    }

    public ResourceLocation getTypeId() {
        return typeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
        layoutDirty = true;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    protected final void setMeasuredSize(int width, int height) {
        this.width = width;
        this.height = height;
        layoutDirty = false;
    }

    public int getTopoDepth() {
        return topoDepth;
    }

    public void setTopoDepth(int topoDepth) {
        this.topoDepth = topoDepth;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isStateBoundary() {
        return false;
    }

    /** Draw-order layer; lower layers render behind regular nodes (group frames use 0). */
    public int renderLayer() {
        return 10;
    }

    /** Whether dragging this node also moves nodes fully contained inside it (group frames). */
    public boolean movesContainedNodes() {
        return false;
    }

    /** Whether align and auto-layout operations should move this node (frames stay put). */
    public boolean participatesInLayout() {
        return true;
    }

    private void renderPin(
            GuiGraphics graphics,
            int pinX,
            int pinY,
            WPin pin,
            boolean input,
            int mouseX,
            int mouseY) {
        boolean hovered = mouseX >= pinX - 1
                && mouseX <= pinX + PIN_SIZE
                && mouseY >= pinY - 1
                && mouseY <= pinY + PIN_SIZE;
        int size = hovered ? PIN_HOVER_SIZE : PIN_SIZE;
        int left = pinX - (size - PIN_SIZE) / 2;
        int top = pinY - (size - PIN_SIZE) / 2;
        int color = pin.getColor();
        graphics.fill(left, top, left + size, top + size, pin.isConnected() || hovered ? color : color & 0x66FFFFFF);
        ComputedEditorStyle.drawPixelOutline(
                graphics,
                left,
                top,
                size,
                size,
                hovered ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.SOCKET_BORDER);
        int centerLeft = left + Math.max(1, size / 2 - 1);
        int centerTop = top + Math.max(1, size / 2 - 1);
        graphics.fill(centerLeft, centerTop, centerLeft + 2, centerTop + 2, ComputedEditorTheme.SOCKET_CENTER);
        String name = pin.getName();
        int textX = input ? pinX + 8 : pinX - 4 - measureTextWidth(name);
        graphics.drawString(Minecraft.getInstance().font, name, textX, pinY - 2, ComputedEditorTheme.TEXT_SECONDARY, false);
    }

    private static int measureTextWidth(String text) {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            return Math.max(8, text.length() * 6);
        }
        return Minecraft.getInstance().font.width(text);
    }
}
