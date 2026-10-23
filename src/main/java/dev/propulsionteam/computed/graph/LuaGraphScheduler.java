package dev.propulsionteam.computed.graph;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Phase;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic.Severity;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.IntegrationLuaLibrary;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.node.LuaExecutionPolicy;
import dev.propulsionteam.computed.lua.node.LuaFieldSchema;
import dev.propulsionteam.computed.lua.node.LuaNodeDefinition;
import dev.propulsionteam.computed.lua.node.LuaPortSchema;
import dev.propulsionteam.computed.lua.runtime.LuaComputerRuntime;
import dev.propulsionteam.computed.lua.runtime.LuaInvocationResult;
import dev.propulsionteam.computed.lua.runtime.LuaNodeInstance;
import dev.propulsionteam.computed.lua.runtime.LuaNodeStatus;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import dev.propulsionteam.computed.lua.runtime.LuaValueCopies;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class LuaGraphScheduler {
    private static final int MAX_QUEUED_EVENTS = 1024;
    private static final String EVENT_BUS = "event_bus";
    private static final String EVENT_RECEIVER = "computed:event_receiver";

    private final ComputedProgramV3 program;
    private final LuaComputerRuntime runtime;
    private final LuaStateCodec stateCodec = new LuaStateCodec();
    private final Map<UUID, LuaNodeInstance> instances = new LinkedHashMap<>();
    private final Map<UUID, GraphNode> nodes = new LinkedHashMap<>();
    private final Map<UUID, List<GraphConnection>> incoming = new HashMap<>();
    private final Map<UUID, Map<String, LuaValue>> lastInputs = new HashMap<>();
    private final Map<UUID, Map<String, LuaValue>> resolvedFields = new HashMap<>();
    private final Map<UUID, Map<String, LuaValue>> outputs = new LinkedHashMap<>();
    private final List<ComputedDiagnostic> definitionDiagnostics = new ArrayList<>();
    private final ArrayDeque<GraphEvent> events = new ArrayDeque<>();
    private final GraphAnalysisResult analysis;
    private final Map<String, List<UUID>> eventSubscribers;
    private final Map<String, List<UUID>> namedEventReceivers;
    private long tick;
    private boolean stepRequested;

    public LuaGraphScheduler(
            ComputedProgramV3 program,
            UUID computerId,
            Object endpointHost) {
        this.program = Objects.requireNonNull(program, "program");
        runtime = new LuaComputerRuntime(
                Objects.requireNonNull(computerId, "computerId"),
                new dev.propulsionteam.computed.lua.sandbox.LuaInstructionBudget(),
                endpointHost);
        BuiltinEndpoints.register();
        program.rootGraph().nodes().forEach(node -> {
            nodes.put(node.id(), node);
            incoming.put(node.id(), new ArrayList<>());
        });
        program.rootGraph().connections().forEach(connection -> {
            List<GraphConnection> edges = incoming.get(connection.targetNode());
            if (edges != null) {
                edges.add(connection);
            }
        });
        instantiateNodes();
        analysis = GraphAnalyzer.analyze(
                program.rootGraph(),
                node -> {
                    LuaNodeInstance instance = instances.get(node.id());
                    return instance != null && !instance.definition().stateDefaults().isEmpty();
                });
        eventSubscribers = indexEventSubscribers();
        namedEventReceivers = indexNamedEventReceivers();
    }

    public LuaGraphTickResult tick(boolean preview) {
        tick++;
        runtime.beginTick(tick);
        List<ComputedDiagnostic> diagnostics = new ArrayList<>(definitionDiagnostics);
        diagnostics.addAll(analysis.diagnostics());
        resumeYielded(diagnostics);
        Set<UUID> cyclicNodes = new HashSet<>();
        analysis.combinationalCycles().forEach(cyclicNodes::addAll);
        for (UUID nodeId : analysis.executionOrder()) {
            if (cyclicNodes.contains(nodeId)) {
                continue;
            }
            LuaNodeInstance instance = instances.get(nodeId);
            GraphNode node = nodes.get(nodeId);
            if (instance == null || node == null || instance.status() == LuaNodeStatus.YIELDED) {
                continue;
            }
            Map<String, LuaValue> inputs = inputs(instance.definition(), node);
            if (!shouldRun(instance.definition().executionPolicy(), nodeId, inputs)) {
                continue;
            }
            LuaInvocationResult result = instance.run(
                    inputs,
                    fields(instance.definition(), node),
                    tick,
                    runtime.nextGraphStep(),
                    preview,
                    (name, values) -> enqueue(new GraphEvent(null, name, values)));
            outputs.put(nodeId, result.outputs());
            diagnostics.addAll(result.diagnostics());
            if (instance.definition().executionPolicy() == LuaExecutionPolicy.INPUT) {
                lastInputs.put(nodeId, LuaValueCopies.copyMap(inputs));
            }
        }
        dispatchEvents(preview, diagnostics);
        stepRequested = false;
        return new LuaGraphTickResult(outputs, diagnostics, runtime.graphStep());
    }

    public void requestStep() {
        stepRequested = true;
    }

    public void emit(String eventName, LuaValue... arguments) {
        enqueue(new GraphEvent(null, eventName, List.of(arguments)));
    }

    public boolean eventNode(UUID nodeId, String eventName, LuaValue... arguments) {
        LuaNodeInstance instance = instances.get(nodeId);
        if (instance == null
                || !instance.definition().eventHandlers().containsKey(eventName)
                || events.size() >= MAX_QUEUED_EVENTS) {
            return false;
        }
        enqueue(new GraphEvent(nodeId, eventName, List.of(arguments)));
        return true;
    }

    public ComputedProgramV3 snapshot(long revision) {
        Map<UUID, CompoundTag> persistentState = new LinkedHashMap<>();
        instances.forEach((nodeId, instance) -> persistentState.put(
                nodeId,
                stateCodec.encode(toTable(instance.state()))));
        return new ComputedProgramV3(
                revision,
                program.rootGraph(),
                program.library(),
                persistentState,
                program.metadata());
    }

    public GraphAnalysisResult analysis() {
        return analysis;
    }

    public List<ComputedDiagnostic> validationDiagnostics() {
        List<ComputedDiagnostic> diagnostics = new ArrayList<>(definitionDiagnostics);
        diagnostics.addAll(analysis.diagnostics());
        return List.copyOf(diagnostics);
    }

    public void unload() {
        runtime.unload();
    }

    private void instantiateNodes() {
        Map<String, LuaDefinitionSource> definitions = new LinkedHashMap<>(BundledLuaLibrary.load());
        definitions.putAll(IntegrationLuaLibrary.load());
        for (Map.Entry<String, LuaDefinitionSource> entry : program.library().entrySet()) {
            if (entry.getValue().origin() == LuaDefinitionSource.Origin.EMBEDDED
                    || !definitions.containsKey(entry.getKey())) {
                definitions.put(entry.getKey(), entry.getValue());
            }
        }
        for (GraphNode node : program.rootGraph().nodes()) {
            if (EditorOnlyNodes.isEditorOnly(node.definitionId())) {
                continue;
            }
            LuaDefinitionSource source = definitions.get(node.definitionId());
            if (source == null) {
                definitionDiagnostics.add(error(
                        "missing_definition",
                        "Missing Lua definition " + node.definitionId(),
                        node.id()));
                continue;
            }
            if (source.origin() == LuaDefinitionSource.Origin.EMBEDDED
                    && !node.definitionHash().isBlank()
                    && !node.definitionHash().equals(source.hash())) {
                definitionDiagnostics.add(error(
                        "definition_hash_mismatch",
                        "Definition hash changed for " + node.definitionId(),
                        node.id()));
                continue;
            }
            try {
                LuaNodeInstance instance =
                        runtime.createNode(node.id(), source.apiVersion(), source.source());
                if (!instance.definition().id().equals(node.definitionId())) {
                    throw new IllegalArgumentException("Definition source returned id " + instance.definition().id());
                }
                validatePortSnapshot(node, instance.definition());
                restoreState(node, instance);
                instances.put(node.id(), instance);
                resolvedFields.put(node.id(), resolveFields(instance.definition(), node));
                outputs.put(node.id(), instance.outputs());
            } catch (RuntimeException exception) {
                definitionDiagnostics.add(error(
                        "definition_load_failed",
                        exception.getMessage(),
                        node.id()));
            }
        }
    }

    private void validatePortSnapshot(GraphNode node, LuaNodeDefinition definition) {
        Map<String, PortSnapshot> snapshots = new HashMap<>();
        node.ports().forEach(port -> snapshots.put(port.direction() + "\u0000" + port.id(), port));
        for (LuaPortSchema input : definition.inputs()) {
            PortSnapshot snapshot = snapshots.get(PortDirection.INPUT + "\u0000" + input.id());
            if (snapshot != null && snapshot.type() != input.type()) {
                throw new IllegalArgumentException("Input port type changed: " + input.id());
            }
        }
        for (LuaPortSchema output : definition.outputs()) {
            PortSnapshot snapshot = snapshots.get(PortDirection.OUTPUT + "\u0000" + output.id());
            if (snapshot != null && snapshot.type() != output.type()) {
                throw new IllegalArgumentException("Output port type changed: " + output.id());
            }
        }
    }

    private void restoreState(GraphNode node, LuaNodeInstance instance) {
        CompoundTag encoded = program.persistentState().get(node.id());
        if (encoded == null) {
            return;
        }
        LuaValue value = stateCodec.decode(encoded);
        if (!value.istable()) {
            throw new IllegalArgumentException("Persistent node state must be a table");
        }
        instance.restoreState(fromTable(value.checktable()));
    }

    private void resumeYielded(List<ComputedDiagnostic> diagnostics) {
        instances.forEach((nodeId, instance) -> {
            if (instance.status() != LuaNodeStatus.YIELDED) {
                return;
            }
            LuaInvocationResult result = instance.resumeIfReady();
            outputs.put(nodeId, result.outputs());
            diagnostics.addAll(result.diagnostics());
        });
    }

    private Map<String, LuaValue> inputs(LuaNodeDefinition definition, GraphNode node) {
        Map<String, LuaValue> values = new LinkedHashMap<>();
        definition.inputs().forEach(input -> values.put(input.id(), LuaValueCopies.copy(input.defaultValue())));
        node.ports().stream()
                .filter(port -> port.direction() == PortDirection.INPUT)
                .forEach(port -> values.putIfAbsent(port.id(), LuaValue.NIL));
        for (GraphConnection connection : incoming.getOrDefault(node.id(), List.of())) {
            Map<String, LuaValue> sourceOutputs = outputs.get(connection.sourceNode());
            if (sourceOutputs == null) {
                continue;
            }
            LuaValue value = sourceOutputs.get(connection.sourcePort());
            if (value != null) {
                values.put(connection.targetPort(), LuaValueCopies.copy(value));
            }
        }
        return values;
    }

    private Map<String, LuaValue> fields(LuaNodeDefinition definition, GraphNode node) {
        return resolvedFields.getOrDefault(node.id(), Map.of());
    }

    private Map<String, LuaValue> resolveFields(LuaNodeDefinition definition, GraphNode node) {
        Map<String, LuaValue> values = new LinkedHashMap<>();
        for (LuaFieldSchema field : definition.fields()) {
            CompoundTag encoded = node.fields().get(field.id());
            values.put(
                    field.id(),
                    encoded == null ? LuaValueCopies.copy(field.defaultValue()) : stateCodec.decode(encoded));
        }
        return values;
    }

    private boolean shouldRun(
            LuaExecutionPolicy policy,
            UUID nodeId,
            Map<String, LuaValue> inputs) {
        return switch (policy) {
            case TICK -> true;
            case INPUT -> !same(lastInputs.get(nodeId), inputs);
            case STEP -> stepRequested;
            case EVENT -> false;
        };
    }

    private void dispatchEvents(boolean preview, List<ComputedDiagnostic> diagnostics) {
        int remaining = events.size();
        while (remaining-- > 0) {
            GraphEvent event = events.removeFirst();
            List<UUID> subscribers = subscribers(event);
            for (UUID nodeId : subscribers) {
                LuaNodeInstance instance = instances.get(nodeId);
                GraphNode node = nodes.get(nodeId);
                if (instance == null
                        || node == null
                        || (event.targetNode() != null && !event.targetNode().equals(nodeId))
                        || !instance.definition().eventHandlers().containsKey(event.name())
                        || instance.status() == LuaNodeStatus.YIELDED) {
                    continue;
                }
                LuaInvocationResult result = instance.event(
                        event.name(),
                        event.arguments(),
                        inputs(instance.definition(), node),
                        fields(instance.definition(), node),
                        tick,
                        runtime.nextGraphStep(),
                        preview,
                        (name, values) -> enqueue(new GraphEvent(null, name, values)));
                outputs.put(nodeId, result.outputs());
                diagnostics.addAll(result.diagnostics());
            }
        }
    }

    private List<UUID> subscribers(GraphEvent event) {
        if (event.targetNode() != null) {
            return List.of(event.targetNode());
        }
        if (EVENT_BUS.equals(event.name())
                && !event.arguments().isEmpty()
                && event.arguments().getFirst().isstring()) {
            return namedEventReceivers.getOrDefault(
                    event.arguments().getFirst().tojstring(), List.of());
        }
        return eventSubscribers.getOrDefault(event.name(), List.of());
    }

    private Map<String, List<UUID>> indexEventSubscribers() {
        Map<String, List<UUID>> indexed = new LinkedHashMap<>();
        for (UUID nodeId : analysis.executionOrder()) {
            LuaNodeInstance instance = instances.get(nodeId);
            if (instance == null) {
                continue;
            }
            for (String eventName : instance.definition().eventHandlers().keySet()) {
                indexed.computeIfAbsent(eventName, ignored -> new ArrayList<>()).add(nodeId);
            }
        }
        indexed.replaceAll((name, subscribers) -> List.copyOf(subscribers));
        return java.util.Collections.unmodifiableMap(indexed);
    }

    private Map<String, List<UUID>> indexNamedEventReceivers() {
        Map<String, List<UUID>> indexed = new LinkedHashMap<>();
        for (UUID nodeId : analysis.executionOrder()) {
            GraphNode node = nodes.get(nodeId);
            if (node == null || !EVENT_RECEIVER.equals(node.definitionId())) {
                continue;
            }
            LuaValue eventName = resolvedFields
                    .getOrDefault(nodeId, Map.of())
                    .getOrDefault("event_name", LuaValue.NIL);
            if (eventName.isstring() && !eventName.tojstring().isBlank()) {
                indexed.computeIfAbsent(eventName.tojstring(), ignored -> new ArrayList<>()).add(nodeId);
            }
        }
        indexed.replaceAll((name, subscribers) -> List.copyOf(subscribers));
        return java.util.Collections.unmodifiableMap(indexed);
    }

    private void enqueue(GraphEvent event) {
        if (events.size() >= MAX_QUEUED_EVENTS) {
            throw new IllegalStateException(
                    "Graph event queue exceeded " + MAX_QUEUED_EVENTS + " pending events");
        }
        events.addLast(event);
    }

    private boolean same(Map<String, LuaValue> left, Map<String, LuaValue> right) {
        return LuaValueCopies.equivalent(left, right);
    }

    private LuaTable toTable(Map<String, LuaValue> values) {
        LuaTable table = new LuaTable();
        values.forEach(table::set);
        return table;
    }

    private Map<String, LuaValue> fromTable(LuaTable table) {
        Map<String, LuaValue> values = new LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return values;
            }
            if (!key.isstring()) {
                throw new IllegalArgumentException("Persistent state ids must be strings");
            }
            values.put(key.tojstring(), next.arg(2));
        }
    }

    private ComputedDiagnostic error(String code, String message, UUID nodeId) {
        return new ComputedDiagnostic(
                Severity.ERROR,
                Phase.DEFINITION,
                code,
                message == null ? "" : message,
                nodeId,
                null,
                null);
    }

    private record GraphEvent(UUID targetNode, String name, List<LuaValue> arguments) {
        private GraphEvent {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Event name is required");
            }
            arguments = arguments == null ? List.of() : List.copyOf(arguments);
        }
    }
}
