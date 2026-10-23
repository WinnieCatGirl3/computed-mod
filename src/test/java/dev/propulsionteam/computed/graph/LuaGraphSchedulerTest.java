package dev.propulsionteam.computed.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpointHost;
import dev.propulsionteam.computed.lua.endpoint.BuiltinWidget;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.ServerEndpointExecutor;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import dev.propulsionteam.computed.lua.runtime.LuaStateCodec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

class LuaGraphSchedulerTest {
    private final LuaStateCodec codec = new LuaStateCodec();

    @Test
    void executesBundledAndEmbeddedNodesInDeterministicDataflowOrder() {
        LuaDefinitionSource sourceDefinition =
                LuaDefinitionSource.embedded(1, "example:number", """
                        local node = computed.node(1, "example:number", "Number")
                        node:category("utility")
                        node:style("source")
                        node:field("value", "number", { default = 0 })
                        node:output("value", "number")
                        node:on_run(function(ctx)
                            ctx:output("value", ctx:field("value"))
                        end)
                        return node
                        """);
        Map<String, LuaDefinitionSource> bundled = BundledLuaLibrary.load();
        UUID sourceId = uuid(1);
        UUID addId = uuid(2);
        UUID counterId = uuid(3);
        GraphNode source = new GraphNode(
                sourceId,
                sourceDefinition.id(),
                sourceDefinition.hash(),
                0,
                0,
                List.of(port("value", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of("value", codec.encode(LuaValue.valueOf(3))));
        GraphNode add = new GraphNode(
                addId,
                "computed:add",
                bundled.get("computed:add").hash(),
                80,
                0,
                List.of(
                        port("a", PortDirection.INPUT, ConnectionType.NUMBER),
                        port("b", PortDirection.INPUT, ConnectionType.NUMBER),
                        port("result", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        GraphNode counter = new GraphNode(
                counterId,
                "computed:counter",
                bundled.get("computed:counter").hash(),
                160,
                0,
                List.of(
                        port("increment", PortDirection.INPUT, ConnectionType.NUMBER),
                        port("count", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of("step", codec.encode(LuaValue.valueOf(2))));
        List<GraphConnection> connections = List.of(
                edge(sourceId, "value", addId, "a"),
                edge(sourceId, "value", addId, "b"),
                edge(addId, "result", counterId, "increment"));
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(100), List.of(counter, add, source), connections),
                Map.of(sourceDefinition.id(), sourceDefinition),
                Map.of(),
                null);

        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid(200), null);
        LuaGraphTickResult first = scheduler.tick(false);
        LuaGraphTickResult second = scheduler.tick(false);

        assertTrue(first.diagnostics().isEmpty());
        assertEquals(3, first.graphSteps());
        assertEquals(3.0, first.outputs().get(sourceId).get("value").todouble());
        assertEquals(6.0, first.outputs().get(addId).get("result").todouble());
        assertEquals(12.0, first.outputs().get(counterId).get("count").todouble());
        assertEquals(12.0, second.outputs().get(counterId).get("count").todouble());

        ComputedProgramV3 snapshot = scheduler.snapshot(9);
        assertEquals(9, snapshot.revision());
        assertFalse(snapshot.persistentState().isEmpty());

        LuaGraphScheduler restored = new LuaGraphScheduler(snapshot, uuid(201), null);
        LuaGraphTickResult afterReload = restored.tick(false);
        assertEquals(24.0, afterReload.outputs().get(counterId).get("count").todouble());
    }

    @Test
    void usesPreviewFixturesAndProductionEndpointHosts() {
        LuaDefinitionSource world = BundledLuaLibrary.load().get("computed:world_time");
        UUID nodeId = uuid(10);
        GraphNode node = new GraphNode(
                nodeId,
                world.id(),
                world.hash(),
                0,
                0,
                List.of(port("time", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(101), List.of(node), List.of()),
                Map.of(),
                Map.of(),
                null);
        Host host = new Host();
        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid(202), host);

        assertEquals(6000.0, scheduler.tick(true).outputs().get(nodeId).get("time").todouble());
        scheduler.tick(false);
        assertTrue(host.hasTasks());
        host.runNext();
        assertEquals(18000.0, scheduler.tick(false).outputs().get(nodeId).get("time").todouble());
    }

    @Test
    void sendsClockWidgetToMonitorEndpoint() {
        Map<String, LuaDefinitionSource> bundled = BundledLuaLibrary.load();
        UUID colorId = uuid(10);
        UUID clockId = uuid(11);
        UUID monitorId = uuid(12);
        GraphNode color = new GraphNode(
                colorId,
                "computed:color_source",
                bundled.get("computed:color_source").hash(),
                -100,
                0,
                List.of(port("color", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of("color", codec.encode(LuaValue.valueOf(0xffffffffL))));
        GraphNode clock = new GraphNode(
                clockId,
                "computed:clock_widget",
                bundled.get("computed:clock_widget").hash(),
                0,
                0,
                List.of(
                        port("color", PortDirection.INPUT, ConnectionType.NUMBER),
                        port("widget", PortDirection.OUTPUT, ConnectionType.WIDGET)),
                Map.of());
        GraphNode monitor = new GraphNode(
                monitorId,
                "computed:peripheral",
                bundled.get("computed:peripheral").hash(),
                100,
                0,
                List.of(port("widget_1", PortDirection.INPUT, ConnectionType.WIDGET)),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(
                        uuid(105),
                        List.of(monitor, clock, color),
                        List.of(
                                edge(colorId, "color", clockId, "color"),
                                edge(clockId, "widget", monitorId, "widget_1"))),
                Map.of(),
                Map.of(),
                null);
        Host host = new Host();

        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid(206), host);
        LuaGraphTickResult yielded = scheduler.tick(false);

        assertTrue(yielded.diagnostics().isEmpty());
        assertTrue(host.monitorTargets.isEmpty());
        assertTrue(host.hasTasks());
        host.runNext();
        LuaGraphTickResult result = scheduler.tick(false);

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(List.of("front"), host.monitorTargets);
        assertEquals(1, host.monitorWidgets.getFirst().size());
        assertEquals("clock", host.monitorWidgets.getFirst().getFirst().type());
        assertEquals(clockId, host.monitorWidgets.getFirst().getFirst().id());
    }

    @Test
    void routesNamedEventsWithTypedDynamicPayloadPorts() {
        LuaDefinitionSource sourceDefinition =
                LuaDefinitionSource.embedded(1, "example:event_source", """
                        local node = computed.node(1, "example:event_source", "Event Source")
                        node:output("trigger", "boolean")
                        node:output("value", "number")
                        node:on_run(function(ctx)
                            ctx:output("trigger", true)
                            ctx:output("value", 42)
                        end)
                        return node
                        """);
        LuaDefinitionSource sinkDefinition =
                LuaDefinitionSource.embedded(1, "example:event_sink", """
                        local node = computed.node(1, "example:event_sink", "Event Sink")
                        node:input("value", "number", { default = 0 })
                        node:output("seen", "number")
                        node:on_run(function(ctx)
                            ctx:output("seen", ctx:input("value"))
                        end)
                        return node
                        """);
        Map<String, LuaDefinitionSource> bundled = BundledLuaLibrary.load();
        UUID sourceId = uuid(30);
        UUID senderId = uuid(31);
        UUID receiverId = uuid(32);
        UUID displayId = uuid(33);
        GraphNode source = new GraphNode(
                sourceId,
                sourceDefinition.id(),
                sourceDefinition.hash(),
                0,
                0,
                List.of(
                        port("trigger", PortDirection.OUTPUT, ConnectionType.BOOLEAN),
                        port("value", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        GraphNode sender = new GraphNode(
                senderId,
                ConfigurableNodePorts.EVENT_SENDER,
                bundled.get(ConfigurableNodePorts.EVENT_SENDER).hash(),
                80,
                0,
                List.of(
                        port("trigger", PortDirection.INPUT, ConnectionType.BOOLEAN),
                        port("data_1", PortDirection.INPUT, ConnectionType.NUMBER)),
                Map.of("event_name", codec.encode(LuaValue.valueOf("door_opened"))));
        GraphNode receiver = new GraphNode(
                receiverId,
                ConfigurableNodePorts.EVENT_RECEIVER,
                bundled.get(ConfigurableNodePorts.EVENT_RECEIVER).hash(),
                160,
                0,
                List.of(
                        port("triggered", PortDirection.OUTPUT, ConnectionType.EVENT),
                        port("data_1", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of("event_name", codec.encode(LuaValue.valueOf("door_opened"))));
        GraphNode display = new GraphNode(
                displayId,
                sinkDefinition.id(),
                sinkDefinition.hash(),
                240,
                0,
                List.of(
                        port("value", PortDirection.INPUT, ConnectionType.NUMBER),
                        port("seen", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(
                        uuid(106),
                        List.of(display, receiver, sender, source),
                        List.of(
                                edge(sourceId, "trigger", senderId, "trigger"),
                                edge(sourceId, "value", senderId, "data_1"),
                                edge(receiverId, "data_1", displayId, "value"))),
                Map.of(
                        sourceDefinition.id(), sourceDefinition,
                        sinkDefinition.id(), sinkDefinition),
                Map.of(),
                null);

        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid(207), null);
        LuaGraphTickResult first = scheduler.tick(false);
        LuaGraphTickResult second = scheduler.tick(false);

        assertTrue(first.diagnostics().isEmpty(), first.diagnostics().toString());
        assertEquals(42, first.outputs().get(receiverId).get("data_1").toint());
        assertTrue(first.outputs().get(receiverId).get("triggered").toint() > 0);
        assertEquals(42, second.outputs().get(displayId).get("seen").toint());
    }

    @Test
    void keepsMissingDefinitionsAsDiagnosedNonExecutableNodes() {
        UUID nodeId = uuid(20);
        GraphNode missing = new GraphNode(
                nodeId,
                "missing:addon_node",
                "old-hash",
                0,
                0,
                List.of(port("value", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(102), List.of(missing), List.of()),
                Map.of(),
                Map.of(),
                null);

        LuaGraphTickResult result = new LuaGraphScheduler(program, uuid(203), null).tick(false);

        assertTrue(result.outputs().getOrDefault(nodeId, Map.of()).isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("missing_definition")));
    }

    @Test
    void acceptsBundledDefinitionUpdatesButKeepsEmbeddedHashProtection() {
        LuaDefinitionSource bundled = BundledLuaLibrary.load().get("computed:constant");
        UUID bundledId = uuid(22);
        GraphNode bundledNode = new GraphNode(
                bundledId,
                bundled.id(),
                "previous-bundled-hash",
                0,
                0,
                List.of(port("value", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        ComputedProgramV3 bundledProgram = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(104), List.of(bundledNode), List.of()),
                Map.of(),
                Map.of(),
                null);

        LuaGraphTickResult bundledResult =
                new LuaGraphScheduler(bundledProgram, uuid(205), null).tick(false);

        assertEquals(10, bundledResult.outputs().get(bundledId).get("value").toint());
        assertFalse(bundledResult.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("definition_hash_mismatch")));
    }

    @Test
    void rejectsMaliciousEmbeddedSourceBeforeItCanBecomeExecutable() {
        LuaDefinitionSource invalid = LuaDefinitionSource.embedded(
                1,
                "example:malicious",
                "return os.execute('anything')");
        UUID nodeId = uuid(21);
        GraphNode node = new GraphNode(
                nodeId,
                invalid.id(),
                invalid.hash(),
                0,
                0,
                List.of(),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(103), List.of(node), List.of()),
                Map.of(invalid.id(), invalid),
                Map.of(),
                null);

        LuaGraphTickResult result = new LuaGraphScheduler(program, uuid(204), null).tick(false);

        assertTrue(result.outputs().getOrDefault(nodeId, Map.of()).isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("definition_load_failed")));
    }

    @Test
    void usesCurrentBundledSourceOverStaleProgramSnapshot() {
        LuaDefinitionSource current = BundledLuaLibrary.load().get("computed:constant");
        LuaDefinitionSource staleSnapshot = new LuaDefinitionSource(
                current.apiVersion(),
                current.id(),
                """
                        local node = computed.node(1, "computed:constant", "Constant")
                        node:field("value", "number", { default = 0 })
                        node:output("value", "number")
                        node:on_run(function(ctx)
                            ctx:output("value", 99)
                        end)
                        return node
                        """,
                "",
                LuaDefinitionSource.Origin.BUNDLED);
        UUID nodeId = uuid(40);
        GraphNode node = new GraphNode(
                nodeId,
                current.id(),
                current.hash(),
                0,
                0,
                List.of(port("value", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(140), List.of(node), List.of()),
                Map.of(current.id(), staleSnapshot),
                Map.of(),
                null);

        LuaGraphTickResult result = new LuaGraphScheduler(program, uuid(240), null).tick(false);

        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
        assertEquals(10, result.outputs().get(nodeId).get("value").toint());
    }

    @Test
    void keepsEmbeddedOverridesAndRemovedBundledFallbacksFromProgramLibrary() {
        LuaDefinitionSource current = BundledLuaLibrary.load().get("computed:constant");
        LuaDefinitionSource embedded = LuaDefinitionSource.embedded(
                current.apiVersion(),
                current.id(),
                """
                        local node = computed.node(1, "computed:constant", "Constant")
                        node:field("value", "number", { default = 0 })
                        node:output("value", "number")
                        node:on_run(function(ctx)
                            ctx:output("value", 42)
                        end)
                        return node
                        """);
        LuaDefinitionSource removed = new LuaDefinitionSource(
                1,
                "computed:removed_node",
                """
                        local node = computed.node(1, "computed:removed_node", "Removed")
                        node:output("value", "number")
                        node:on_run(function(ctx)
                            ctx:output("value", 7)
                        end)
                        return node
                        """,
                "",
                LuaDefinitionSource.Origin.BUNDLED);
        UUID shadowId = uuid(41);
        UUID removedId = uuid(42);
        GraphNode shadow = new GraphNode(
                shadowId,
                embedded.id(),
                embedded.hash(),
                0,
                0,
                List.of(port("value", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        GraphNode gone = new GraphNode(
                removedId,
                removed.id(),
                removed.hash(),
                80,
                0,
                List.of(port("value", PortDirection.OUTPUT, ConnectionType.NUMBER)),
                Map.of());
        ComputedProgramV3 program = new ComputedProgramV3(
                0,
                new ComputedGraph(uuid(141), List.of(shadow, gone), List.of()),
                Map.of(embedded.id(), embedded, removed.id(), removed),
                Map.of(),
                null);

        LuaGraphTickResult result = new LuaGraphScheduler(program, uuid(241), null).tick(false);

        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
        assertEquals(42, result.outputs().get(shadowId).get("value").toint());
        assertEquals(7, result.outputs().get(removedId).get("value").toint());
    }

    private static PortSnapshot port(String id, PortDirection direction, ConnectionType type) {
        return new PortSnapshot(id, direction, type, id);
    }

    private static GraphConnection edge(
            UUID source,
            String sourcePort,
            UUID target,
            String targetPort) {
        return new GraphConnection(UUID.randomUUID(), source, sourcePort, target, targetPort, List.of());
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }

    private static final class Host implements BuiltinEndpointHost, ServerEndpointExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private final List<String> commands = new ArrayList<>();
        private final List<String> monitorTargets = new ArrayList<>();
        private final List<List<BuiltinWidget>> monitorWidgets = new ArrayList<>();

        @Override
        public double worldTime() {
            return 18000;
        }

        @Override
        public void runCommand(String command) {
            commands.add(command);
        }

        @Override
        public void showWidgets(String target, List<BuiltinWidget> widgets) {
            monitorTargets.add(target);
            monitorWidgets.add(widgets);
        }

        @Override
        public CompletionStage<EndpointResult> submitServerEndpoint(
                Callable<EndpointResult> endpointCall) {
            CompletableFuture<EndpointResult> result = new CompletableFuture<>();
            synchronized (tasks) {
                tasks.addLast(() -> {
                    try {
                        result.complete(endpointCall.call());
                    } catch (Exception exception) {
                        result.completeExceptionally(exception);
                    }
                });
            }
            return result;
        }

        boolean hasTasks() {
            synchronized (tasks) {
                return !tasks.isEmpty();
            }
        }

        void runNext() {
            Runnable task;
            synchronized (tasks) {
                task = tasks.removeFirst();
            }
            task.run();
        }
    }
}
