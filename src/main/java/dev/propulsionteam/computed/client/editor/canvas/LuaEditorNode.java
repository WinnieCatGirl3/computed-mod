package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.ConfigurableNodePorts;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.client.renderer.node.BedrockNodeRenderer;
import dev.propulsionteam.computed.client.renderer.node.NodePalette;
import dev.propulsionteam.computed.client.renderer.node.NodeRenderLayout;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.node.NodeStyle;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class LuaEditorNode extends WNode {
    private final GraphNode source;
    private final boolean stateBoundary;
    private final String category;
    private final NodeStyle style;
    private final LuaNodeDefinition definition;
    private List<PortSnapshot> ports;
    private final List<LuaNodeFieldControl> fieldControls = new ArrayList<>();
    private final LuaStateCodec fieldCodec = new LuaStateCodec();

    public LuaEditorNode(
            GraphNode source,
            String title,
            boolean stateBoundary,
            String category,
            NodeStyle style,
            LuaNodeDefinition definition) {
        super(ResourceLocation.parse(source.definitionId()), title, source.x(), source.y());
        this.source = source;
        this.stateBoundary = stateBoundary;
        this.category = category == null ? "utility" : category;
        this.style = style == null ? NodeStyle.STANDARD : style;
        this.definition = definition;
        ports = new ArrayList<>(ConfigurableNodePorts.withInitialPort(
                source.definitionId(), source.ports()));
        rebuildPins();
        CompoundTag identity = new CompoundTag();
        identity.putString("id", source.id().toString());
        identity.putString("title", title);
        identity.putInt("x", source.x());
        identity.putInt("y", source.y());
        load(identity);
        if (definition != null) {
            Map<String, CompoundTag> encodedFields = source.fields();
            definition.fields().forEach(schema -> {
                CompoundTag encoded = encodedFields.get(schema.id());
                org.luaj.vm2.LuaValue value = encoded == null
                        ? schema.defaultValue()
                        : fieldCodec.decode(encoded);
                fieldControls.add(new LuaNodeFieldControl(schema, value));
            });
        }
        updateLayout();
    }

    public GraphNode source() {
        return toGraphNode(getX(), getY(), source.id());
    }

    public String category() {
        return category;
    }

    public NodeStyle style() {
        return style;
    }

    LuaNodeDefinition definition() {
        return definition;
    }

    public org.luaj.vm2.LuaValue fieldValue(String id) {
        return fieldControls.stream()
                .filter(control -> control.schema().id().equals(id))
                .map(LuaNodeFieldControl::value)
                .findFirst()
                .orElse(org.luaj.vm2.LuaValue.NIL);
    }

    public boolean setFieldValue(String id, org.luaj.vm2.LuaValue value) {
        for (int index = 0; index < fieldControls.size(); index++) {
            LuaNodeFieldControl control = fieldControls.get(index);
            if (control.schema().id().equals(id)) {
                fieldControls.set(index, new LuaNodeFieldControl(control.schema(), value));
                clearHiddenFocus();
                updateLayout();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BedrockNodeRenderer.render(graphics, this, category, false, false, mouseX, mouseY);
        int accent = NodePalette.category(category).frameArgb();
        payloadTypeControls().forEach(control ->
                control.render(graphics, getX(), getY(), getWidth(), mouseX, mouseY));
        int fieldsY = getY() + fieldsTop();
        List<LuaNodeFieldControl> visible = visibleFieldControls();
        for (int index = 0; index < visible.size(); index++) {
            visible.get(index).render(
                    graphics,
                    getX(),
                    fieldsY + index * LuaNodeFieldControl.ROW_HEIGHT,
                    getWidth(),
                    mouseX,
                    mouseY,
                    accent);
        }
        if (configurablePorts()) {
            renderPortControls(graphics, mouseX, mouseY, accent);
        }
    }

    @Override
    public void updateLayout() {
        if (definition == null) {
            super.updateLayout();
            return;
        }
        NodeRenderLayout layout = NodeRenderLayout.measure(
                definition,
                visibleFieldControls().stream().map(LuaNodeFieldControl::schema).toList());
        int portRows = Math.max(getInputs().size(), getOutputs().size());
        int fieldRows = visibleFieldControls().size();
        int contentHeight = portRows * 12
                + (portRows > 0 && fieldRows > 0 ? 4 : 0)
                + fieldRows * LuaNodeFieldControl.ROW_HEIGHT
                + (configurablePorts() ? LuaNodeFieldControl.ROW_HEIGHT : 0);
        int height = layout.titleHeight() + Math.max(18, contentHeight + 8) + 4;
        setMeasuredSize(layout.width(), height);
    }

    @Override
    public boolean hasInteractiveElementAt(double mouseX, double mouseY) {
        List<LuaNodeFieldControl> visible = visibleFieldControls();
        if (visible.stream().anyMatch(LuaNodeFieldControl::focused)) {
            return true;
        }
        int first = fieldsTop();
        boolean fieldHit = !visible.isEmpty()
                && mouseX >= 6
                && mouseX < getWidth() - 6
                && mouseY >= first
                && mouseY < first + visible.size() * LuaNodeFieldControl.ROW_HEIGHT;
        boolean payloadTypeHit = payloadTypeControls().stream()
                .anyMatch(control -> control.contains(mouseX, mouseY, getWidth()));
        return fieldHit || payloadTypeHit || configurablePorts()
                && mouseX >= 6
                && mouseX < getWidth() - 6
                && mouseY >= portControlsTop()
                && mouseY < portControlsTop() + LuaNodeFieldControl.ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handlePayloadTypeClick(mouseX, mouseY)) {
            clearElementFocus();
            return true;
        }
        if (configurablePorts()
                && handlePortControlsClick(mouseX, mouseY, button)) {
            clearElementFocus();
            return true;
        }
        int first = fieldsTop();
        List<LuaNodeFieldControl> visible = visibleFieldControls();
        for (int index = 0; index < visible.size(); index++) {
            LuaNodeFieldControl control = visible.get(index);
            if (control.mouseClicked(
                    mouseX,
                    mouseY,
                    button,
                    getWidth(),
                    first + index * LuaNodeFieldControl.ROW_HEIGHT)) {
                fieldControls.stream()
                        .filter(other -> other != control)
                        .forEach(LuaNodeFieldControl::clearFocus);
                clearHiddenFocus();
                updateLayout();
                return true;
            }
        }
        clearElementFocus();
        return false;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        for (LuaNodeFieldControl control : visibleFieldControls()) {
            if (control.mouseDragged(mouseX, button, getWidth())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (LuaNodeFieldControl control : visibleFieldControls()) {
            if (control.mouseReleased(button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (LuaNodeFieldControl control : visibleFieldControls()) {
            if (control.keyPressed(keyCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (LuaNodeFieldControl control : visibleFieldControls()) {
            if (control.charTyped(codePoint)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasFocusedElement() {
        return visibleFieldControls().stream().anyMatch(LuaNodeFieldControl::focused);
    }

    @Override
    public void clearElementFocus() {
        fieldControls.forEach(LuaNodeFieldControl::clearFocus);
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        CompoundTag fields = new CompoundTag();
        fieldControls.forEach(control ->
                fields.put(control.schema().id(), fieldCodec.encode(control.value())));
        tag.put("luaFields", fields);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!tag.contains("luaFields") || fieldControls.isEmpty()) {
            return;
        }
        CompoundTag fields = tag.getCompound("luaFields");
        Map<String, LuaNodeFieldControl> byId = new LinkedHashMap<>();
        fieldControls.forEach(control -> byId.put(control.schema().id(), control));
        for (String id : fields.getAllKeys()) {
            LuaNodeFieldControl current = byId.get(id);
            if (current == null) {
                continue;
            }
            int index = fieldControls.indexOf(current);
            fieldControls.set(index, new LuaNodeFieldControl(
                    current.schema(),
                    fieldCodec.decode(fields.getCompound(id))));
        }
    }

    @Override
    public boolean isStateBoundary() {
        return stateBoundary;
    }

    public GraphNode toGraphNode(int x, int y, UUID id) {
        Map<String, CompoundTag> fields = new LinkedHashMap<>();
        fieldControls.forEach(control ->
                fields.put(control.schema().id(), fieldCodec.encode(control.value())));
        if (fieldControls.isEmpty()) {
            fields.putAll(source.fields());
        }
        return new GraphNode(
                id,
                source.definitionId(),
                source.definitionHash(),
                x,
                y,
                ports,
                fields);
    }

    private int fieldsTop() {
        int portRows = Math.max(getInputs().size(), getOutputs().size());
        return 20 + portRows * 12 + (portRows == 0 ? 0 : 4);
    }

    private int portControlsTop() {
        return fieldsTop() + visibleFieldControls().size() * LuaNodeFieldControl.ROW_HEIGHT;
    }

    private boolean configurablePorts() {
        return ConfigurableNodePorts.configurable(source.definitionId());
    }

    private void rebuildPins() {
        beginPinSchemaUpdate();
        try {
            getInputs().clear();
            getOutputs().clear();
            for (PortSnapshot port : ports) {
                WPin.DataType dataType = dataType(port.type());
                int color = color(port.type());
                if (port.direction() == PortDirection.INPUT) {
                    addInput(port.id(), port.label(), dataType, color);
                } else {
                    addOutput(port.id(), port.label(), dataType, color);
                }
            }
            markPinSchemaChanged();
        } finally {
            endPinSchemaUpdate();
        }
    }

    private void renderPortControls(
            GuiGraphics graphics, int mouseX, int mouseY, int accent) {
        int y = getY() + portControlsTop() + 2;
        int minusX = getX() + 8;
        int plusX = minusX + 24;
        int typeX = plusX + 24;
        drawPortButton(graphics, minusX, y, 20, "−", mouseX, mouseY, accent);
        drawPortButton(graphics, plusX, y, 20, "+", mouseX, mouseY, accent);
        List<PortSnapshot> dynamic = ConfigurableNodePorts.dynamicPorts(
                source.definitionId(), ports);
        String text = ConfigurableNodePorts.MONITOR.equals(source.definitionId())
                ? "Widgets " + dynamic.size()
                : ConfigurableNodePorts.TERMINAL.equals(source.definitionId())
                ? "Lines " + dynamic.size()
                : ConfigurableNodePorts.GLASSES_HUD.equals(source.definitionId())
                ? "HUD Widgets " + dynamic.size()
                : ConfigurableNodePorts.TABLE_WIDGET.equals(source.definitionId())
                ? "Rows " + dynamic.size()
                : "Payloads " + dynamic.size();
        int labelWidth = Math.max(20, getWidth() - (typeX - getX()) - 8);
        if (ConfigurableNodePorts.MONITOR.equals(source.definitionId())) {
            drawPortButton(
                    graphics,
                    typeX,
                    y,
                    labelWidth,
                    text,
                    mouseX,
                    mouseY,
                    accent);
        } else {
            graphics.fill(typeX, y, typeX + labelWidth, y + 14, 0xFF1C1C1C);
            graphics.renderOutline(typeX, y, labelWidth, 14, 0xFF3E3E3E);
            graphics.drawCenteredString(
                    net.minecraft.client.Minecraft.getInstance().font,
                    text,
                    typeX + labelWidth / 2,
                    y + 3,
                    0xFFA0A39C);
        }
    }

    private void drawPortButton(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String text,
            int mouseX,
            int mouseY,
            int accent) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 14;
        graphics.fill(x, y, x + width, y + 14, hovered ? 0xFF323232 : 0xFF252525);
        graphics.renderOutline(x, y, width, 14, hovered ? accent : 0xFF4A4A4A);
        graphics.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                text,
                x + width / 2,
                y + 3,
                0xFFE8E8E8);
    }

    private boolean handlePortControlsClick(double mouseX, double mouseY, int button) {
        if (button != 0
                || mouseY < portControlsTop()
                || mouseY >= portControlsTop() + LuaNodeFieldControl.ROW_HEIGHT) {
            return false;
        }
        if (mouseX >= 8 && mouseX < 28) {
            ports = ConfigurableNodePorts.removeLast(source.definitionId(), ports);
        } else if (mouseX >= 32 && mouseX < 52) {
            ports = ConfigurableNodePorts.add(source.definitionId(), ports);
        } else if (ConfigurableNodePorts.MONITOR.equals(source.definitionId())
                && mouseX >= 56
                && mouseX < getWidth() - 8) {
            ports = ConfigurableNodePorts.cycleLastType(source.definitionId(), ports);
        } else {
            return false;
        }
        rebuildPins();
        updateLayout();
        return true;
    }

    private boolean handlePayloadTypeClick(double mouseX, double mouseY) {
        for (EventPayloadTypeControl control : payloadTypeControls()) {
            if (control.contains(mouseX, mouseY, getWidth())) {
                ports = ConfigurableNodePorts.cycleType(
                        source.definitionId(), ports, control.portId());
                rebuildPins();
                updateLayout();
                return true;
            }
        }
        return false;
    }

    private List<EventPayloadTypeControl> payloadTypeControls() {
        if (!ConfigurableNodePorts.EVENT_SENDER.equals(source.definitionId())
                && !ConfigurableNodePorts.EVENT_RECEIVER.equals(source.definitionId())
                && !ConfigurableNodePorts.TERMINAL.equals(source.definitionId())
                && !ConfigurableNodePorts.TABLE_WIDGET.equals(source.definitionId())) {
            return List.of();
        }
        List<PortSnapshot> dynamic =
                ConfigurableNodePorts.dynamicPorts(source.definitionId(), ports);
        List<EventPayloadTypeControl> controls = new ArrayList<>();
        for (PortSnapshot payload : dynamic) {
            int directionIndex = 0;
            for (PortSnapshot port : ports) {
                if (port.equals(payload)) {
                    break;
                }
                if (port.direction() == payload.direction()) {
                    directionIndex++;
                }
            }
            controls.add(new EventPayloadTypeControl(payload, directionIndex));
        }
        return List.copyOf(controls);
    }

    private List<LuaNodeFieldControl> visibleFieldControls() {
        return fieldControls.stream().filter(this::isVisible).toList();
    }

    private boolean isVisible(LuaNodeFieldControl control) {
        String controllingId = control.schema().visibleWhenField();
        if (controllingId == null) {
            return true;
        }
        org.luaj.vm2.LuaValue controllingValue = fieldControls.stream()
                .filter(candidate -> candidate.schema().id().equals(controllingId))
                .map(LuaNodeFieldControl::value)
                .findFirst()
                .orElse(org.luaj.vm2.LuaValue.NIL);
        return control.schema().visibleWhenValue().equals(controllingValue.tojstring());
    }

    private void clearHiddenFocus() {
        fieldControls.stream()
                .filter(control -> !isVisible(control))
                .forEach(LuaNodeFieldControl::clearFocus);
    }

    private static WPin.DataType dataType(ConnectionType type) {
        return switch (type) {
            case NUMBER -> WPin.DataType.NUMBER;
            case BOOLEAN -> WPin.DataType.BOOLEAN;
            case STRING -> WPin.DataType.STRING;
            case EVENT -> WPin.DataType.EVENT;
            case WIDGET -> WPin.DataType.WIDGET;
            case TABLE -> WPin.DataType.TABLE;
        };
    }

    private static int color(ConnectionType type) {
        return switch (type) {
            case NUMBER -> 0xFF4E86E8;
            case BOOLEAN -> 0xFF985AD6;
            case STRING -> 0xFFD653B5;
            case EVENT -> 0xFF27C7D9;
            case WIDGET -> 0xFF9BCB45;
            case TABLE -> 0xFF8A9099;
        };
    }
}
