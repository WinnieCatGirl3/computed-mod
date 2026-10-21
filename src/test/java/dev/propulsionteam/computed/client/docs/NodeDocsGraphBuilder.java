package dev.propulsionteam.computed.client.docs;

import dev.propulsionteam.computed.client.renderer.node.NodeRenderLayout;
import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.ConfigurableNodePorts;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.node.IntegrationLuaLibrary;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.node.LuaFieldSchema;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.node.LuaPortSchema;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.luaj.vm2.LuaValue;

/**
 * Test-side generator for the Docs screen's example graphs. Builds a minimal runnable graph around
 * one documented node in a strict left-to-right column layout — sources, featured node, display
 * chain, monitor sink — then exports it as a {@code .computed} archive into
 * {@code assets/computed/docs/} (see {@code NodeDocsExamplesTest}). The generated files are the
 * source of truth afterwards; this class only exists to (re)create them.
 */
public final class NodeDocsGraphBuilder {
    private NodeDocsGraphBuilder() {}

    private static final int COLUMN_GAP = 150;
    private static final int NODE_GAP = 48;
    private static final int START_Y = 60;
    private static final int WIDGET_SLOT_BUDGET = 8;
    private static final String DEMO_EVENT_NAME = "demo_event";
    private static final int PORT_ROW = 12;
    private static final int FIELD_ROW = 18;
    private static final LuaStateCodec CODEC = new LuaStateCodec();

    /**
     * @return a complete program for {@code entry}, or {@code null} when the definition is not
     *     available (integration mod missing) or fails to compile.
     */
    public static ComputedProgramV3 build(NodeDocsEntry entry) {
        if (entry == null || entry.id().startsWith("docs:")) {
            return null;
        }
        Map<String, LuaDefinitionSource> defs = new LinkedHashMap<>(BundledLuaLibrary.load());
        defs.putAll(IntegrationLuaLibrary.load());
        LuaDefinitionSource source = defs.get(entry.id());
        if (source == null) {
            return null;
        }
        LuaNodeDefinition featured = compile(source);
        if (featured == null) {
            return null;
        }

        // Sink entries document themselves — no second copy of a peripheral-style hub.
        boolean featuredIsSink = featured.id().equals("computed:peripheral")
                || featured.id().equals("computed:glasses_hud");
        LuaNodeDefinition sink = null;
        if (!featuredIsSink) {
            LuaDefinitionSource sinkSource = defs.get("computed:peripheral");
            if (sinkSource == null) {
                return null;
            }
            sink = compile(sinkSource);
            if (sink == null) {
                return null;
            }
        }

        // ---- IDs first: plans reference target nodes, and every UUID is derived
        // deterministically from its plan key.
        UUID featuredId = uuid(entry.id() + ":featured");
        UUID sinkId = featuredIsSink ? featuredId : uuid(entry.id() + ":peripheral");
        UUID senderId = uuid(entry.id() + ":sender");

        // Featured widget outputs take sink slots after the input widgets.
        List<LuaPortSchema> widgetOutputs = featured.outputs().stream()
                .filter(port -> port.type() == ConnectionType.WIDGET)
                .toList();
        int wiredWidgetOutputs = Math.min(widgetOutputs.size(), WIDGET_SLOT_BUDGET);
        boolean featuredHasWidget = !widgetOutputs.isEmpty();

        // Column A: interactive input widgets wherever the featured node is not itself
        // the interaction (widgets configure themselves from their own defaults — wiring
        // constant(0) into color inputs would render them transparent black). Every other
        // required input gets a clickable/tweakable source whose widget ALSO lives on the
        // monitor: NUMBER -> slider, BOOLEAN level -> checkbox, BOOLEAN trigger -> button,
        // STRING -> text input. Types must match exactly or the editor drops the wire.
        boolean useInteractiveInputs = !featuredHasWidget;
        List<SourcePlan> sourcePlans = new ArrayList<>();
        if (featuredIsSink) {
            // The monitor (and the glasses hub) document themselves: a demo trio, widgets only,
            // no data edges.
            for (String demoId : List.of(
                    "computed:button_widget", "computed:slider_widget", "computed:text_input_widget")) {
                LuaNodeDefinition demo = compile(defs.get(demoId));
                if (demo == null) {
                    continue;
                }
                String widgetOut = firstOutputOfType(demo, ConnectionType.WIDGET);
                if (widgetOut == null) {
                    continue;
                }
                sourcePlans.add(new SourcePlan(
                        "demo:" + demo.id(), demo, fieldDefaults(demo), null, null, null, widgetOut, null));
            }
        } else {
            for (LuaPortSchema port : featured.inputs()) {
                if (!port.required()) {
                    continue;
                }
                if (useInteractiveInputs) {
                    if (isDisplayColorConfig(port.id())) {
                        // Colour inputs default to visible values (white text, dark
                        // backgrounds); a 0..1 slider or constant(0) would only make
                        // the widget transparent. Rely on the Lua defaults.
                        continue;
                    }
                    SourcePlan interactive = interactiveSource(defs, port, featuredId);
                    if (interactive != null) {
                        sourcePlans.add(interactive);
                        continue;
                    }
                } else if (!port.id().equals("text")) {
                    // Widget config (labels, colours, ranges, placeholders) already
                    // carries sensible Lua defaults ("Button", white, 0..1); only the
                    // free-text content input gets a demo source.
                    continue;
                }
                SourcePlan stationary = staticSource(defs, port, featuredId);
                if (stationary != null) {
                    sourcePlans.add(stationary);
                }
            }
            if (featured.id().equals("computed:event_sender")) {
                // Demonstrate the event payload slot alongside the trigger button.
                SourcePlan payload = staticSource(defs,
                        new LuaPortSchema("data_1", ConnectionType.NUMBER, false, LuaValue.NIL), featuredId);
                if (payload != null) {
                    payload.fields().put("value", CODEC.encode(LuaValue.valueOf(1)));
                    sourcePlans.add(payload);
                }
            }
        }

        // Event-receiver examples pair a sender (same event name) so the graph actually fires:
        // button -> sender trigger, constant payload -> sender, receiver payload -> display chain.
        boolean eventPair = featured.id().equals("computed:event_receiver");
        LuaNodeDefinition senderDef = null;
        Map<String, CompoundTag> senderFields = Map.of();
        List<PortSnapshot> senderPorts = List.of();
        if (eventPair) {
            LuaDefinitionSource senderSource = defs.get("computed:event_sender");
            senderDef = senderSource == null ? null : compile(senderSource);
            if (senderDef != null) {
                senderFields = new LinkedHashMap<>(fieldDefaults(senderDef));
                senderFields.put("event_name", CODEC.encode(LuaValue.valueOf(DEMO_EVENT_NAME)));
                senderPorts = ensureDynamicPorts(senderDef.id(), ports(senderDef), 1);
                LuaNodeDefinition triggerButton = compile(defs.get("computed:button_widget"));
                if (triggerButton != null) {
                    sourcePlans.add(new SourcePlan("sender:trigger", triggerButton,
                            fieldDefaults(triggerButton), senderId, "trigger",
                            firstOutputOfType(triggerButton, ConnectionType.BOOLEAN),
                            firstOutputOfType(triggerButton, ConnectionType.WIDGET), "trigger"));
                }
                SourcePlan payload = staticSource(defs,
                        new LuaPortSchema("data_1", ConnectionType.NUMBER, false, LuaValue.NIL), senderId);
                if (payload != null) {
                    payload.fields().put("value", CODEC.encode(LuaValue.valueOf(1)));
                    sourcePlans.add(new SourcePlan("sender:data_1", payload.def(), payload.fields(),
                            senderId, "data_1", payload.outPort(), null, null));
                }
            } else {
                eventPair = false;
            }
        }

        // Column C: display chain for scalar outputs. Every edge must connect equal
        // connection types, so booleans go through bool_to_level and tables into a
        // terminal row slot.
        LuaPortSchema scalarOut = null;
        for (LuaPortSchema port : featured.outputs()) {
            if (port.type() == ConnectionType.STRING
                    || port.type() == ConnectionType.NUMBER
                    || port.type() == ConnectionType.BOOLEAN
                    || port.type() == ConnectionType.TABLE) {
                scalarOut = port;
                break;
            }
        }
        if (eventPair) {
            // The receiver's payload arrives on its dynamic data_1 output — visualize that.
            scalarOut = new LuaPortSchema("data_1", ConnectionType.NUMBER, false, LuaValue.NIL);
        }
        List<DisplayPlan> displayPlans = new ArrayList<>();
        boolean convertBool = false;
        boolean displayTable = false;
        // Widget nodes show their own widget; a scalar side-output (clicked/checked/value)
        // additionally gets the display chain. NUMBER outputs go straight to the number
        // widget, BOOLEAN ones via bool_to_level, STRING to a text widget, TABLE to a terminal.
        if (scalarOut != null && (wiredWidgetOutputs == 0 || featuredHasWidget)) {
            if (scalarOut.type() == ConnectionType.BOOLEAN) {
                LuaDefinitionSource boolSource = defs.get("computed:bool_to_level");
                LuaNodeDefinition boolToLevel = boolSource == null ? null : compile(boolSource);
                if (boolToLevel != null) {
                    displayPlans.add(new DisplayPlan(
                            "bool", boolToLevel, fieldDefaults(boolToLevel), ports(boolToLevel)));
                    convertBool = true;
                }
            }
            if (scalarOut.type() == ConnectionType.NUMBER
                    || (scalarOut.type() == ConnectionType.BOOLEAN && convertBool)) {
                LuaDefinitionSource numberSource = defs.get("computed:number_widget");
                LuaNodeDefinition numberWidget = numberSource == null ? null : compile(numberSource);
                if (numberWidget != null) {
                    displayPlans.add(new DisplayPlan(
                            "display", numberWidget, fieldDefaults(numberWidget), ports(numberWidget)));
                }
            } else if (scalarOut.type() == ConnectionType.STRING) {
                LuaDefinitionSource textWidgetSource = defs.get("computed:text_widget");
                LuaNodeDefinition textWidget = textWidgetSource == null ? null : compile(textWidgetSource);
                if (textWidget != null) {
                    displayPlans.add(new DisplayPlan(
                            "display", textWidget, fieldDefaults(textWidget), ports(textWidget)));
                }
            } else if (scalarOut.type() == ConnectionType.TABLE) {
                LuaDefinitionSource terminalSource = defs.get("computed:terminal_widget");
                LuaNodeDefinition terminal = terminalSource == null ? null : compile(terminalSource);
                if (terminal != null) {
                    displayPlans.add(new DisplayPlan(
                            "display", terminal, fieldDefaults(terminal), tableLinePorts(terminal)));
                    displayTable = true;
                }
            }
        }
        // cc_output consumes a table but shows nothing itself: fan the static row out to
        // a terminal display as well (output fan-out is legal, inputs are single-wired).
        boolean ccFanout = false;
        if (featured.id().equals("computed:cc_output")) {
            LuaDefinitionSource terminalSource = defs.get("computed:terminal_widget");
            LuaNodeDefinition terminal = terminalSource == null ? null : compile(terminalSource);
            if (terminal != null) {
                displayPlans.add(new DisplayPlan(
                        "display", terminal, fieldDefaults(terminal), tableLinePorts(terminal)));
                ccFanout = true;
            }
        }

        // ---- Column positions (strict left-to-right, generous gaps).
        int sourceWidth = sourcePlans.stream().mapToInt(plan -> width(plan.def())).max().orElse(0);
        int featuredWidth = width(featured);
        int displayWidth = displayPlans.stream().mapToInt(plan -> width(plan.def())).max().orElse(0);
        int sinkWidth = sink == null ? 0 : width(sink);

        int xSource = 0;
        int xFeatured = sourcePlans.isEmpty() ? 0 : sourceWidth + COLUMN_GAP;
        int xDisplay = xFeatured + featuredWidth + COLUMN_GAP;
        int xSink = displayPlans.isEmpty()
                ? xFeatured + featuredWidth + COLUMN_GAP
                : xDisplay + displayWidth + COLUMN_GAP;
        if (featuredIsSink) {
            xSink = 0;
        }

        // ---- Vertical stacking: columns centered/stacked without overlap.
        int sourceColumnHeight = 0;
        for (SourcePlan plan : sourcePlans) {
            sourceColumnHeight += height(plan.def()) + NODE_GAP;
        }
        if (!sourcePlans.isEmpty()) {
            sourceColumnHeight -= NODE_GAP;
        }
        int yFeatured = START_Y + Math.max(0, (sourceColumnHeight - height(featured)) / 2);
        int ySink = yFeatured;
        // The display column sits below the featured→sink wire corridor so widget wires stay
        // clear of node bodies.
        int yDisplay = featuredIsSink
                ? START_Y
                : yFeatured + height(featured) + NODE_GAP + 24;

        // ---- Nodes. Configurable nodes (peripheral, terminal, table, event pair) carry
        // their payload slots as dynamic ports. The stored PortSnapshot must contain every
        // wired slot, otherwise the analyzer reports "missing_port" and the editor drops
        // the wire on import. Simulate the slot counter below to size the sink exactly.
        // Slot order on the monitor: input widgets (featured port order), then the
        // featured widget outputs, then the result display.
        int inputWidgetCount = 0;
        for (SourcePlan plan : sourcePlans) {
            if (plan.widgetPort() != null) {
                inputWidgetCount++;
            }
        }
        int slotsNeeded = Math.min(inputWidgetCount + wiredWidgetOutputs, WIDGET_SLOT_BUDGET);
        boolean hasPrimaryDisplayWidget = displayTable
                || (displayPlans.stream().anyMatch(plan -> plan.key().equals("display")));
        if (hasPrimaryDisplayWidget && slotsNeeded < WIDGET_SLOT_BUDGET) {
            slotsNeeded++;
        }

        Map<String, CompoundTag> featuredFields = new LinkedHashMap<>(fieldDefaults(featured));
        if (eventPair) {
            featuredFields.put("event_name", CODEC.encode(LuaValue.valueOf(DEMO_EVENT_NAME)));
        }
        List<PortSnapshot> featuredPorts = ports(featured);
        if (ConfigurableNodePorts.configurable(featured.id())) {
            featuredPorts = ConfigurableNodePorts.withInitialPort(featured.id(), featuredPorts);
        }
        if (featuredIsSink) {
            featuredPorts = ensureDynamicPorts(featured.id(), featuredPorts, slotsNeeded);
        }
        if (eventPair) {
            featuredPorts = ensureDynamicPorts(featured.id(), featuredPorts, 1);
        }
        List<PortSnapshot> sinkPorts = null;
        if (sink != null) {
            sinkPorts = ensureDynamicPorts(sink.id(), ports(sink), slotsNeeded);
        }

        // Label captions for interactive widgets, monitor lines in slot order.
        LuaNodeDefinition labelDef = null;
        List<LabelPlan> labelPlans = new ArrayList<>();
        Map<String, Integer> widgetSlots = new LinkedHashMap<>();
        int slot = 1;
        for (SourcePlan plan : sourcePlans) {
            if (plan.widgetPort() == null || slot > WIDGET_SLOT_BUDGET) {
                continue;
            }
            widgetSlots.put(plan.key(), slot);
            withLine(plan.def(), plan.fields(), slot);
            if (plan.labelText() != null) {
                String labelInput = labelInputId(plan.def());
                if (labelInput != null) {
                    if (labelDef == null) {
                        LuaDefinitionSource labelSource = defs.get("computed:text_source");
                        labelDef = labelSource == null ? null : compile(labelSource);
                    }
                    if (labelDef != null) {
                        labelPlans.add(new LabelPlan("lbl:" + plan.key(), plan.key(), labelInput, plan.labelText()));
                    }
                }
            }
            slot++;
        }
        Map<String, Integer> featuredWidgetSlots = new LinkedHashMap<>();
        if (featuredHasWidget) {
            for (LuaPortSchema port : widgetOutputs) {
                if (slot > WIDGET_SLOT_BUDGET) {
                    break;
                }
                featuredWidgetSlots.put(port.id(), slot);
                slot++;
            }
            if (!featuredWidgetSlots.isEmpty()) {
                withLine(featured, featuredFields, featuredWidgetSlots.values().iterator().next());
            }
        }
        int displaySlot = -1;
        if (hasPrimaryDisplayWidget && slot <= WIDGET_SLOT_BUDGET) {
            displaySlot = slot;
            slot++;
        }
        for (DisplayPlan plan : displayPlans) {
            if (plan.key().equals("display") && displaySlot > 0) {
                withLine(plan.def(), plan.fields(), displaySlot);
            }
        }

        // ---- Nodes.
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphConnection> connections = new ArrayList<>();
        Map<String, UUID> nodeIds = new LinkedHashMap<>();
        Map<String, UUID> displayIds = new LinkedHashMap<>();
        nodeIds.put("featured", featuredId);
        if (sink != null) {
            nodeIds.put("peripheral", sinkId);
        }
        if (eventPair && senderDef != null) {
            nodeIds.put("sender", senderId);
        }

        nodes.add(new GraphNode(
                featuredId, featured.id(), featured.sourceHash(),
                xFeatured, yFeatured, featuredPorts, featuredFields));
        if (sink != null) {
            nodes.add(new GraphNode(
                    sinkId, sink.id(), sink.sourceHash(),
                    xSink, ySink, sinkPorts, fieldDefaults(sink)));
        }
        if (eventPair && senderDef != null) {
            // Sender sits with the sources; its trigger/payload arrive from column A.
            nodes.add(new GraphNode(
                    senderId, senderDef.id(), senderDef.sourceHash(),
                    xSource, START_Y, senderPorts, senderFields));
            nodeIds.put("sender", senderId);
        }

        int y = START_Y + (eventPair && senderDef != null ? height(senderDef) + NODE_GAP : 0);
        for (SourcePlan plan : sourcePlans) {
            UUID sourceNodeId = uuid(entry.id() + ":" + plan.key());
            nodeIds.put(plan.key(), sourceNodeId);
            for (LabelPlan label : labelPlans) {
                if (!label.ownerKey().equals(plan.key())) {
                    continue;
                }
                UUID labelId = uuid(entry.id() + ":" + label.key());
                nodeIds.put(label.key(), labelId);
                Map<String, CompoundTag> labelFields = new LinkedHashMap<>(fieldDefaults(labelDef));
                labelFields.put("text", CODEC.encode(LuaValue.valueOf(label.text())));
                nodes.add(new GraphNode(
                        labelId, labelDef.id(), labelDef.sourceHash(),
                        xSource, y, ports(labelDef), labelFields));
                y += height(labelDef) + NODE_GAP;
            }
            nodes.add(new GraphNode(
                    sourceNodeId, plan.def().id(), plan.def().sourceHash(),
                    xSource, y, ports(plan.def()), plan.fields()));
            y += height(plan.def()) + NODE_GAP;
        }

        y = yDisplay;
        for (DisplayPlan plan : displayPlans) {
            UUID displayNodeId = uuid(entry.id() + ":" + plan.key());
            displayIds.put(plan.key(), displayNodeId);
            nodeIds.put(plan.key(), displayNodeId);
            nodes.add(new GraphNode(
                    displayNodeId, plan.def().id(), plan.def().sourceHash(),
                    xDisplay, y, plan.ports(), plan.fields()));
            y += height(plan.def()) + NODE_GAP;
        }

        // ---- Wiring: data edges, labels, widgets into the sink in slot order.
        for (SourcePlan plan : sourcePlans) {
            UUID sourceNodeId = nodeIds.get(plan.key());
            if (sourceNodeId == null || plan.targetPort() == null || plan.outPort() == null
                    || plan.targetNode() == null) {
                continue; // demo-only widget (monitor trio): widget edge below.
            }
            connections.add(edge(sourceNodeId, plan.outPort(), plan.targetNode(), plan.targetPort()));
            Integer widgetSlot = widgetSlots.get(plan.key());
            if (widgetSlot != null && plan.widgetPort() != null) {
                connections.add(edge(sourceNodeId, plan.widgetPort(), sinkId, "widget_" + widgetSlot));
            }
        }
        // Trio widgets carry no data edge but still take monitor slots.
        for (SourcePlan plan : sourcePlans) {
            if (plan.targetPort() != null || plan.widgetPort() == null) {
                continue;
            }
            UUID sourceNodeId = nodeIds.get(plan.key());
            Integer widgetSlot = widgetSlots.get(plan.key());
            if (sourceNodeId != null && widgetSlot != null) {
                connections.add(edge(sourceNodeId, plan.widgetPort(), sinkId, "widget_" + widgetSlot));
            }
        }
        if (labelDef != null) {
            String labelOut = firstOutputOfType(labelDef, ConnectionType.STRING);
            if (labelOut != null) {
                for (LabelPlan label : labelPlans) {
                    UUID labelId = nodeIds.get(label.key());
                    UUID ownerId = nodeIds.get(label.ownerKey());
                    if (labelId != null && ownerId != null) {
                        connections.add(edge(labelId, labelOut, ownerId, label.labelInputId()));
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> wired : featuredWidgetSlots.entrySet()) {
            connections.add(edge(featuredId, wired.getKey(), sinkId, "widget_" + wired.getValue()));
        }

        UUID boolId = displayIds.get("bool");
        UUID displayWidgetId = displayIds.get("display");
        if (ccFanout && displayWidgetId != null && displaySlot > 0) {
            // Table row fans out to the consumer AND to the monitor display.
            String rowOut = null;
            for (SourcePlan plan : sourcePlans) {
                if (plan.key().equals("src:value")) {
                    rowOut = plan.outPort();
                }
            }
            LuaNodeDefinition displayDef = defOf(displayPlans, "display");
            UUID rowSource = nodeIds.get("src:value");
            if (rowOut != null && rowSource != null && displayDef != null) {
                connections.add(edge(rowSource, rowOut, displayWidgetId, "line_1"));
                String widgetOut = firstOutputOfType(displayDef, ConnectionType.WIDGET);
                if (widgetOut != null) {
                    connections.add(edge(displayWidgetId, widgetOut, sinkId, "widget_" + displaySlot));
                }
            }
        } else if (displayWidgetId != null && scalarOut != null && displaySlot > 0) {
            LuaNodeDefinition displayDef = defOf(displayPlans, "display");
            LuaNodeDefinition boolDef = defOf(displayPlans, "bool");
            if (displayTable && displayDef != null) {
                // TABLE output into a terminal row slot, then the terminal widget to the sink.
                connections.add(edge(featuredId, scalarOut.id(), displayWidgetId, "line_1"));
            } else if (convertBool && boolDef != null && boolId != null && displayDef != null) {
                String boolIn = firstInputOfType(boolDef, ConnectionType.BOOLEAN);
                String boolOut = firstOutputOfType(boolDef, ConnectionType.NUMBER);
                String numberIn = firstInputOfType(displayDef, ConnectionType.NUMBER);
                if (boolIn != null) {
                    connections.add(edge(featuredId, scalarOut.id(), boolId, boolIn));
                }
                if (boolOut != null && numberIn != null) {
                    connections.add(edge(boolId, boolOut, displayWidgetId, numberIn));
                }
            } else if (displayDef != null) {
                // Direct match: NUMBER -> value, STRING -> text.
                String targetIn = firstInputOfType(displayDef, scalarOut.type());
                if (targetIn != null) {
                    connections.add(edge(featuredId, scalarOut.id(), displayWidgetId, targetIn));
                }
            }
            String widgetOut = displayDef == null
                    ? null
                    : firstOutputOfType(displayDef, ConnectionType.WIDGET);
            if (widgetOut != null && displaySlot > 0) {
                connections.add(edge(displayWidgetId, widgetOut, sinkId, "widget_" + displaySlot));
            }
        }

        // Built-in definitions resolve from the bundled library at import time — no embedding.
        return new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(entry.id() + ":graph"), nodes, connections),
                Map.of(),
                Map.of(),
                new CompoundTag());
    }

    /**
     * One column-A node. {@code targetPort == null} marks a demo-only widget (monitor trio)
     * with no data edge. {@code widgetPort != null} marks an interactive widget whose own
     * widget output takes a monitor slot. {@code labelText != null} adds a text_source
     * labeling the widget on the monitor.
     */
    private record SourcePlan(
            String key,
            LuaNodeDefinition def,
            Map<String, CompoundTag> fields,
            UUID targetNode,
            String targetPort,
            String outPort,
            String widgetPort,
            String labelText) {}

    private record DisplayPlan(
            String key, LuaNodeDefinition def, Map<String, CompoundTag> fields, List<PortSnapshot> ports) {}

    private record LabelPlan(String key, String ownerKey, String labelInputId, String text) {}

    /** Interactive widget source for a required input, or {@code null} for TABLE/EVENT/WIDGET. */
    private static SourcePlan interactiveSource(
            Map<String, LuaDefinitionSource> defs, LuaPortSchema port, UUID targetNode) {
        String widgetDefId = switch (port.type()) {
            case NUMBER -> "computed:slider_widget";
            case BOOLEAN -> port.id().equals("trigger") ? "computed:button_widget" : "computed:checkbox_widget";
            case STRING -> "computed:text_input_widget";
            default -> null;
        };
        if (widgetDefId == null) {
            return null;
        }
        LuaDefinitionSource widgetSource = defs.get(widgetDefId);
        LuaNodeDefinition widgetDef = widgetSource == null ? null : compile(widgetSource);
        if (widgetDef == null) {
            return null;
        }
        String dataOut = firstOutputOfType(widgetDef, port.type());
        String widgetOut = firstOutputOfType(widgetDef, ConnectionType.WIDGET);
        if (dataOut == null || widgetOut == null) {
            return null;
        }
        return new SourcePlan("src:" + port.id(), widgetDef, fieldDefaults(widgetDef),
                targetNode, port.id(), dataOut, widgetOut, port.id());
    }

    /** Non-interactive fallback source (static config, TABLE payloads). */
    private static SourcePlan staticSource(
            Map<String, LuaDefinitionSource> defs, LuaPortSchema port, UUID targetNode) {
        String defId = switch (port.type()) {
            case NUMBER -> "computed:constant";
            case STRING -> "computed:text_source";
            case BOOLEAN -> "computed:pulse";
            case TABLE -> "computed:terminal_line";
            default -> null; // WIDGET and EVENT rely on input defaults.
        };
        if (defId == null) {
            return null;
        }
        LuaDefinitionSource defSource = defs.get(defId);
        LuaNodeDefinition def = defSource == null ? null : compile(defSource);
        if (def == null) {
            return null;
        }
        String outPort = firstOutputOfType(def, port.type());
        if (outPort == null) {
            return null;
        }
        Map<String, CompoundTag> fields = new LinkedHashMap<>(fieldDefaults(def));
        if (port.type() == ConnectionType.STRING) {
            fields.put("text", CODEC.encode(LuaValue.valueOf(port.id() + "…")));
        } else if (port.type() == ConnectionType.NUMBER) {
            fields.put("value", CODEC.encode(LuaValue.valueOf(0)));
        }
        return new SourcePlan("src:" + port.id(), def, fields, targetNode, port.id(), outPort, null, null);
    }

    /** Colour/range config inputs whose Lua defaults are already presentable. */
    private static final Set<String> DISPLAY_COLOR_CONFIG = Set.of(
            "color", "fg", "bg", "header_fg", "header_bg", "cell_fg", "cell_bg");

    private static boolean isDisplayColorConfig(String portId) {
        return DISPLAY_COLOR_CONFIG.contains(portId);
    }

    /** Which input carries the on-monitor caption of an interactive widget, if any. */
    private static String labelInputId(LuaNodeDefinition widgetDef) {
        for (LuaPortSchema port : widgetDef.inputs()) {
            if (port.id().equals("label") && port.type() == ConnectionType.STRING) {
                return "label";
            }
        }
        for (LuaPortSchema port : widgetDef.inputs()) {
            if (port.id().equals("placeholder") && port.type() == ConnectionType.STRING) {
                return "placeholder";
            }
        }
        return null;
    }

    /** Assigns the monitor line of a widget node to its sink slot, when it has one. */
    private static void withLine(LuaNodeDefinition def, Map<String, CompoundTag> fields, int slot) {
        for (LuaFieldSchema field : def.fields()) {
            if (field.id().equals("line")) {
                fields.put("line", CODEC.encode(LuaValue.valueOf(slot)));
                return;
            }
        }
    }

    private static LuaNodeDefinition defOf(List<DisplayPlan> plans, String key) {
        for (DisplayPlan plan : plans) {
            if (plan.key().equals(key)) {
                return plan.def();
            }
        }
        return null;
    }

    private static LuaNodeDefinition compile(LuaDefinitionSource source) {
        try {
            return new LuaDefinitionLoader().load(
                    new LuaSourceCompiler().compile(source.apiVersion(), source.source()),
                    new LuaSandbox());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Node body metrics matching {@code LuaEditorNode#updateLayout()}. */
    private static int width(LuaNodeDefinition definition) {
        return NodeRenderLayout.measure(definition).width();
    }

    private static int height(LuaNodeDefinition definition) {
        int portRows = Math.max(definition.inputs().size(), definition.outputs().size());
        int fieldRows = definition.fields().size();
        int contentHeight = portRows * PORT_ROW
                + (portRows > 0 && fieldRows > 0 ? 4 : 0)
                + fieldRows * FIELD_ROW
                + (ConfigurableNodePorts.configurable(definition.id()) ? FIELD_ROW : 0);
        return 18 + Math.max(18, contentHeight + 8) + 4;
    }

    private static List<PortSnapshot> ports(LuaNodeDefinition definition) {
        List<PortSnapshot> list = new ArrayList<>();
        for (LuaPortSchema port : definition.inputs()) {
            list.add(new PortSnapshot(port.id(), PortDirection.INPUT, port.type(), port.id()));
        }
        for (LuaPortSchema port : definition.outputs()) {
            list.add(new PortSnapshot(port.id(), PortDirection.OUTPUT, port.type(), port.id()));
        }
        return List.copyOf(list);
    }

    private static Map<String, CompoundTag> fieldDefaults(LuaNodeDefinition definition) {
        Map<String, CompoundTag> fields = new LinkedHashMap<>();
        for (LuaFieldSchema field : definition.fields()) {
            if (field.defaultValue() != null && !field.defaultValue().isnil()) {
                fields.put(field.id(), CODEC.encode(field.defaultValue()));
            }
        }
        return fields;
    }

    /**
     * Grows the dynamic ports of a configurable node (e.g. {@code widget_1..N} on the
     * peripheral) until {@code count} payload slots exist in the stored snapshot.
     */
    private static List<PortSnapshot> ensureDynamicPorts(
            String definitionId, List<PortSnapshot> ports, int count) {
        if (!ConfigurableNodePorts.configurable(definitionId)) {
            return List.copyOf(ports);
        }
        List<PortSnapshot> grown = ConfigurableNodePorts.withInitialPort(definitionId, ports);
        int guard = 0;
        while (ConfigurableNodePorts.dynamicPorts(definitionId, grown).size() < count
                && guard++ < ConfigurableNodePorts.MAX_PORTS) {
            List<PortSnapshot> next = ConfigurableNodePorts.add(definitionId, grown);
            if (next.size() == grown.size()) {
                break;
            }
            grown = next;
        }
        return grown;
    }

    /**
     * Port snapshot for a terminal used as a TABLE display: its {@code line_1} slot must
     * carry type TABLE (default is STRING) so a table output can legally connect to it.
     */
    private static List<PortSnapshot> tableLinePorts(LuaNodeDefinition terminal) {
        List<PortSnapshot> grown =
                ConfigurableNodePorts.withInitialPort(terminal.id(), ports(terminal));
        for (PortSnapshot port : ConfigurableNodePorts.dynamicPorts(terminal.id(), grown)) {
            if (port.id().equals("line_1") && port.type() != ConnectionType.TABLE) {
                grown = ConfigurableNodePorts.cycleType(terminal.id(), grown, "line_1");
            }
        }
        return grown;
    }

    private static String firstInputOfType(LuaNodeDefinition definition, ConnectionType type) {
        for (LuaPortSchema port : definition.inputs()) {
            if (port.type() == type) {
                return port.id();
            }
        }
        return null;
    }

    private static String firstOutputOfType(LuaNodeDefinition definition, ConnectionType type) {
        for (LuaPortSchema port : definition.outputs()) {
            if (port.type() == type) {
                return port.id();
            }
        }
        return null;
    }

    private static GraphConnection edge(UUID source, String sourcePort, UUID target, String targetPort) {
        return new GraphConnection(
                uuid(source + "/" + sourcePort + "->" + target + "/" + targetPort),
                source, sourcePort, target, targetPort, List.of());
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(("computed:docs-example:" + seed).getBytes(StandardCharsets.UTF_8));
    }
}
