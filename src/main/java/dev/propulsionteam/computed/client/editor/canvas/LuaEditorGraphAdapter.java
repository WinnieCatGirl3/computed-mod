package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.graph.ComputedGraph;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.ConfigurableNodePorts;
import dev.propulsionteam.computed.graph.EditorOnlyNodes;
import dev.propulsionteam.computed.graph.GraphConnection;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.graph.GraphPoint;
import dev.propulsionteam.computed.graph.LuaDefinitionSource;
import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.internal.node.api.WConnection;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.IntegrationLuaLibrary;
import dev.propulsionteam.computed.lua.node.LuaDefinitionLoader;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LuaEditorGraphAdapter {
    private LuaEditorGraphAdapter() {}

    public static WGraph toEditorGraph(ComputedProgramV3 program) {
        Map<String, LuaDefinitionSource> definitions = new LinkedHashMap<>(BundledLuaLibrary.load());
        definitions.putAll(IntegrationLuaLibrary.load());
        definitions.putAll(program.library());
        Map<UUID, LuaEditorNode> nodes = new LinkedHashMap<>();
        WGraph graph = new WGraph();
        for (GraphNode node : program.rootGraph().nodes()) {
            if (EditorOnlyNodes.COMMENT.equals(node.definitionId())) {
                graph.addNode(new CommentNote(node));
                continue;
            }
            if (EditorOnlyNodes.FRAME.equals(node.definitionId())) {
                graph.addNode(new FrameNote(node));
                continue;
            }
            LuaDefinitionSource definitionSource = definitions.get(node.definitionId());
            LuaNodeDefinition definition = definition(definitionSource);
            GraphNode editorSource = definitionSource != null
                            && definitionSource.origin() != LuaDefinitionSource.Origin.EMBEDDED
                    ? new GraphNode(
                            node.id(),
                            node.definitionId(),
                            definitionSource.hash(),
                            node.x(),
                            node.y(),
                            reconciledPorts(node.definitionId(), node.ports(), definition),
                            node.fields())
                    : node;
            String title = definition == null ? node.definitionId() : definition.title();
            boolean stateBoundary = definition != null && !definition.stateDefaults().isEmpty();
            LuaEditorNode editorNode = new LuaEditorNode(
                    editorSource,
                    title,
                    stateBoundary,
                    definition == null ? "utility" : definition.category(),
                    definition == null
                            ? dev.propulsionteam.computed.lua.node.NodeStyle.STANDARD
                            : definition.style(),
                    definition);
            nodes.put(node.id(), editorNode);
            graph.addNode(editorNode);
        }
        for (GraphConnection connection : program.rootGraph().connections()) {
            LuaEditorNode source = nodes.get(connection.sourceNode());
            LuaEditorNode target = nodes.get(connection.targetNode());
            if (source == null || target == null) {
                continue;
            }
            int sourcePin = stablePin(source, true, connection.sourcePort());
            int targetPin = stablePin(target, false, connection.targetPort());
            if (sourcePin < 0 || targetPin < 0) {
                continue;
            }
            int[] waypointXs = connection.waypoints().stream().mapToInt(point -> (int) Math.round(point.x())).toArray();
            int[] waypointYs = connection.waypoints().stream().mapToInt(point -> (int) Math.round(point.y())).toArray();
            graph.connect(new WConnection(
                    source.getId(),
                    sourcePin,
                    target.getId(),
                    targetPin,
                    waypointXs,
                    waypointYs,
                    connection.sourcePort(),
                    connection.targetPort()));
        }
        return graph;
    }

    public static ComputedProgramV3 fromEditorGraph(
            WGraph editor,
            ComputedProgramV3 base,
            long revision) {
        Map<UUID, GraphNode> baseNodes = new HashMap<>();
        base.rootGraph().nodes().forEach(node -> baseNodes.put(node.id(), node));
        List<GraphNode> nodes = new ArrayList<>();
        for (WNode editorNode : editor.getNodes()) {
            if (editorNode instanceof CommentNote note) {
                nodes.add(note.toGraphNode(note.getX(), note.getY(), note.getId()));
                continue;
            }
            if (editorNode instanceof FrameNote note) {
                nodes.add(note.toGraphNode(note.getX(), note.getY(), note.getId()));
                continue;
            }
            if (editorNode instanceof LuaEditorNode luaNode) {
                nodes.add(luaNode.toGraphNode(
                        editorNode.getX(),
                        editorNode.getY(),
                        editorNode.getId()));
                continue;
            }
            GraphNode original = baseNodes.get(editorNode.getId());
            if (original == null) {
                continue;
            }
            nodes.add(new GraphNode(
                    original.id(),
                    original.definitionId(),
                    original.definitionHash(),
                    editorNode.getX(),
                    editorNode.getY(),
                    original.ports(),
                    original.fields()));
        }
        Map<String, GraphConnection> oldConnections = new HashMap<>();
        base.rootGraph().connections().forEach(connection ->
                oldConnections.put(identity(connection), connection));
        List<GraphConnection> connections = new ArrayList<>();
        for (WConnection connection : editor.getConnections()) {
            String sourcePort = stablePort(editor, connection.sourceNode(), true, connection.sourcePin());
            String targetPort = stablePort(editor, connection.targetNode(), false, connection.targetPin());
            if (sourcePort == null || targetPort == null) {
                continue;
            }
            String identity = identity(
                    connection.sourceNode(),
                    sourcePort,
                    connection.targetNode(),
                    targetPort);
            GraphConnection previous = oldConnections.get(identity);
            List<GraphPoint> waypoints = new ArrayList<>();
            int[] xs = connection.waypointXs();
            int[] ys = connection.waypointYs();
            for (int index = 0; index < Math.min(xs.length, ys.length); index++) {
                waypoints.add(new GraphPoint(xs[index], ys[index]));
            }
            connections.add(new GraphConnection(
                    previous == null ? UUID.randomUUID() : previous.id(),
                    connection.sourceNode(),
                    sourcePort,
                    connection.targetNode(),
                    targetPort,
                    waypoints));
        }
        return new ComputedProgramV3(
                revision,
                new ComputedGraph(base.rootGraph().id(), nodes, connections),
                base.library(),
                base.persistentState(),
                base.metadata());
    }

    public static LuaEditorNode createEditorNode(
            ComputedProgramV3 program,
            String definitionId,
            int x,
            int y) {
        LuaDefinitionSource source = definitions(program).get(definitionId);
        LuaNodeDefinition definition = definition(source);
        if (source == null || definition == null) {
            return null;
        }
        List<PortSnapshot> ports =
                ConfigurableNodePorts.withInitialPort(definitionId, ports(definition));
        Map<String, net.minecraft.nbt.CompoundTag> fields = defaultFields(definition);
        GraphNode node = new GraphNode(
                UUID.randomUUID(),
                source.id(),
                source.hash(),
                x,
                y,
                ports,
                fields);
        return new LuaEditorNode(
                node,
                definition.title(),
                !definition.stateDefaults().isEmpty(),
                definition.category(),
                definition.style(),
                definition);
    }

    public static ComputedProgramV3 replaceDefinition(
            ComputedProgramV3 program,
            LuaDefinitionSource replacement) {
        LuaNodeDefinition definition = definition(replacement);
        if (definition == null || !definition.id().equals(replacement.id())) {
            throw new IllegalArgumentException("Lua source does not return the replacement definition ID");
        }
        Map<String, LuaDefinitionSource> library = new LinkedHashMap<>(program.library());
        library.put(replacement.id(), replacement);
        List<PortSnapshot> nextPorts = ports(definition);
        Map<String, PortSnapshot> nextPortIndex = new HashMap<>();
        nextPorts.forEach(port -> nextPortIndex.put(port.direction() + "\u0000" + port.id(), port));
        List<GraphNode> nodes = new ArrayList<>();
        Set<UUID> replacedNodes = new java.util.HashSet<>();
        for (GraphNode node : program.rootGraph().nodes()) {
            if (!node.definitionId().equals(replacement.id())) {
                nodes.add(node);
                continue;
            }
            replacedNodes.add(node.id());
            Map<String, net.minecraft.nbt.CompoundTag> fields = defaultFields(definition);
            node.fields().forEach((id, value) -> {
                if (fields.containsKey(id)) {
                    fields.put(id, value);
                }
            });
            nodes.add(new GraphNode(
                    node.id(),
                    replacement.id(),
                    replacement.hash(),
                    node.x(),
                    node.y(),
                    nextPorts,
                    fields));
        }
        Map<UUID, GraphNode> nodeIndex = new HashMap<>();
        nodes.forEach(node -> nodeIndex.put(node.id(), node));
        List<GraphConnection> connections = program.rootGraph().connections().stream()
                .filter(connection -> compatible(connection, nodeIndex, nextPortIndex, replacedNodes))
                .toList();
        Map<UUID, net.minecraft.nbt.CompoundTag> state =
                new LinkedHashMap<>(program.persistentState());
        replacedNodes.forEach(state::remove);
        return new ComputedProgramV3(
                program.revision(),
                new ComputedGraph(program.rootGraph().id(), nodes, connections),
                library,
                state,
                program.metadata());
    }

    public static ComputedProgramV3 addDefinitionAndNode(
            ComputedProgramV3 program,
            LuaDefinitionSource definition,
            int x,
            int y) {
        ComputedProgramV3 updated = replaceDefinition(program, definition);
        LuaEditorNode editorNode = createEditorNode(updated, definition.id(), x, y);
        if (editorNode == null) {
            throw new IllegalArgumentException("Lua definition could not create an editor node");
        }
        List<GraphNode> nodes = new ArrayList<>(updated.rootGraph().nodes());
        nodes.add(editorNode.source());
        return new ComputedProgramV3(
                updated.revision(),
                new ComputedGraph(
                        updated.rootGraph().id(),
                        nodes,
                        updated.rootGraph().connections()),
                updated.library(),
                updated.persistentState(),
                updated.metadata());
    }

    public static ComputedProgramV3 removeDefinitionAndInstances(ComputedProgramV3 program, String definitionId) {
        LuaDefinitionSource source = program.library().get(definitionId);
        if (source == null || source.origin() != LuaDefinitionSource.Origin.EMBEDDED) {
            throw new IllegalArgumentException("Only embedded definitions can be deleted");
        }
        Set<UUID> removed = program.rootGraph().nodes().stream()
                .filter(node -> node.definitionId().equals(definitionId)).map(GraphNode::id)
                .collect(java.util.stream.Collectors.toSet());
        List<GraphNode> nodes = program.rootGraph().nodes().stream().filter(node -> !removed.contains(node.id())).toList();
        List<GraphConnection> connections = program.rootGraph().connections().stream()
                .filter(connection -> !removed.contains(connection.sourceNode()) && !removed.contains(connection.targetNode())).toList();
        Map<String, LuaDefinitionSource> library = new LinkedHashMap<>(program.library()); library.remove(definitionId);
        Map<UUID, net.minecraft.nbt.CompoundTag> state = new LinkedHashMap<>(program.persistentState()); removed.forEach(state::remove);
        return new ComputedProgramV3(program.revision(), new ComputedGraph(program.rootGraph().id(), nodes, connections), library, state, program.metadata());
    }

    public static LuaEditorNode duplicateEditorNode(LuaEditorNode source, int x, int y) {
        GraphNode original = source.source();
        GraphNode duplicate = new GraphNode(
                UUID.randomUUID(),
                original.definitionId(),
                original.definitionHash(),
                x,
                y,
                original.ports(),
                original.fields());
        return new LuaEditorNode(
                duplicate,
                source.getTitle(),
                source.isStateBoundary(),
                source.category(),
                source.style(),
                source.definition());
    }

    public static Map<String, LuaDefinitionSource> definitions(ComputedProgramV3 program) {
        Map<String, LuaDefinitionSource> definitions = new LinkedHashMap<>(BundledLuaLibrary.load());
        definitions.putAll(IntegrationLuaLibrary.load());
        definitions.putAll(program.library());
        return java.util.Collections.unmodifiableMap(definitions);
    }

    private static List<PortSnapshot> ports(LuaNodeDefinition definition) {
        List<PortSnapshot> ports = new ArrayList<>();
        definition.inputs().forEach(port -> ports.add(new PortSnapshot(
                port.id(),
                PortDirection.INPUT,
                port.type(),
                port.id())));
        definition.outputs().forEach(port -> ports.add(new PortSnapshot(
                port.id(),
                PortDirection.OUTPUT,
                port.type(),
                port.id())));
        return List.copyOf(ports);
    }

    private static List<PortSnapshot> reconciledPorts(
            String definitionId,
            List<PortSnapshot> saved,
            LuaNodeDefinition definition) {
        if (definition == null) {
            return List.copyOf(saved);
        }
        List<PortSnapshot> reconciled = new ArrayList<>(ports(definition));
        if (ConfigurableNodePorts.configurable(definitionId)) {
            Set<String> staticIds = reconciled.stream()
                    .map(port -> port.direction() + "\u0000" + port.id())
                    .collect(java.util.stream.Collectors.toSet());
            saved.stream()
                    .filter(port -> !staticIds.contains(port.direction() + "\u0000" + port.id()))
                    .forEach(reconciled::add);
            return ConfigurableNodePorts.withInitialPort(definitionId, reconciled);
        }
        return List.copyOf(reconciled);
    }

    private static Map<String, net.minecraft.nbt.CompoundTag> defaultFields(
            LuaNodeDefinition definition) {
        Map<String, net.minecraft.nbt.CompoundTag> fields = new LinkedHashMap<>();
        LuaStateCodec codec = new LuaStateCodec();
        definition.fields().forEach(field -> fields.put(field.id(), codec.encode(field.defaultValue())));
        return fields;
    }

    private static boolean compatible(
            GraphConnection connection,
            Map<UUID, GraphNode> nodes,
            Map<String, PortSnapshot> replacementPorts,
            Set<UUID> replacedNodes) {
        GraphNode source = nodes.get(connection.sourceNode());
        GraphNode target = nodes.get(connection.targetNode());
        if (source == null || target == null) {
            return false;
        }
        PortSnapshot sourcePort = port(source, PortDirection.OUTPUT, connection.sourcePort());
        PortSnapshot targetPort = port(target, PortDirection.INPUT, connection.targetPort());
        return sourcePort != null && targetPort != null && sourcePort.type() == targetPort.type();
    }

    private static PortSnapshot port(GraphNode node, PortDirection direction, String id) {
        return node.ports().stream()
                .filter(port -> port.direction() == direction && port.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static LuaNodeDefinition definition(LuaDefinitionSource source) {
        if (source == null) {
            return null;
        }
        try {
            var compiled = new LuaSourceCompiler().compile(source.apiVersion(), source.source());
            return new LuaDefinitionLoader().load(compiled, new LuaSandbox());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int stablePin(WNode node, boolean output, String key) {
        var pins = output ? node.getOutputs() : node.getInputs();
        for (int index = 0; index < pins.size(); index++) {
            if (key.equals(pins.get(index).getStableKey())) {
                return index;
            }
        }
        return -1;
    }

    private static String stablePort(WGraph graph, UUID nodeId, boolean output, int index) {
        WNode node = graph.getNode(nodeId);
        if (node == null) {
            return null;
        }
        var pins = output ? node.getOutputs() : node.getInputs();
        if (index < 0 || index >= pins.size()) {
            return null;
        }
        return pins.get(index).getStableKey();
    }

    private static String identity(GraphConnection connection) {
        return identity(
                connection.sourceNode(),
                connection.sourcePort(),
                connection.targetNode(),
                connection.targetPort());
    }

    private static String identity(UUID source, String sourcePort, UUID target, String targetPort) {
        return source + "\u0000" + sourcePort + "\u0000" + target + "\u0000" + targetPort;
    }
}
