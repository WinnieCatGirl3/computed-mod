package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpointHost;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.lua.endpoint.BuiltinWidget;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.ServerEndpointExecutor;
import dev.propulsionteam.computed.lua.runtime.LuaComputerRuntime;
import dev.propulsionteam.computed.lua.runtime.LuaInvocationResult;
import dev.propulsionteam.computed.lua.runtime.LuaNodeInstance;
import dev.propulsionteam.computed.lua.runtime.LuaNodeStatus;
import dev.propulsionteam.computed.lua.sandbox.LuaInstructionBudget;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class BundledLuaLibraryTest {
    @Test
    void everyBundledDefinitionCompilesAndReturnsItsRegisteredId() {
        var compiler = new LuaSourceCompiler();
        var loader = new LuaDefinitionLoader();
        var sandbox = new LuaSandbox(new LuaInstructionBudget());
        var definitions = BundledLuaLibrary.load();

        assertTrue(definitions.size() >= 30);
        definitions.forEach((id, source) -> {
            assertTrue(!source.source().contains("\r"), id + " contains a carriage return");
            var compiled = compiler.compile(source.apiVersion(), source.source());
            var definition = loader.load(compiled, sandbox);
            assertEquals(id, definition.id());
        });
    }

    @Test
    void everyBundledDefinitionRunsWithItsDefaults() {
        BuiltinEndpoints.register();
        QueuedBuiltinHost host = new QueuedBuiltinHost();
        var runtime = new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        runtime.beginTick(1);

        BundledLuaLibrary.load().forEach((id, source) -> {
            if (id.startsWith("computed:aeronautics_")
                    && dev.propulsionteam.computed.lua.endpoint.ComputedEndpoints
                            .find("aeronautics:telemetry")
                            .isEmpty()) {
                return; // registered only by AeronauticsIntegration in-game
            }
            if (id.startsWith("computed:speaker_")) {
                return; // requires a real ComputerBlockEntity host with an adjacent speaker block
            }
            var instance = runtime.createNode(UUID.randomUUID(), source.apiVersion(), source.source());
            var inputs = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
            instance.definition().inputs().forEach(port -> inputs.put(port.id(), port.defaultValue()));
            var fields = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
            instance.definition().fields().forEach(field -> fields.put(field.id(), field.defaultValue()));
            LuaInvocationResult result =
                    instance.run(inputs, fields, 1, runtime.nextGraphStep(), false, (name, values) -> {});
            result = completeQueuedInvocation(host, instance, result);
            assertEquals(LuaNodeStatus.IDLE, result.status(), id + ": " + result.diagnostics());
            assertEquals(List.of(), result.diagnostics(), id);
        });
    }

    @Test
    void redstoneOutputRunsOnlyAfterServerDispatch() {
        BuiltinEndpoints.register();
        QueuedBuiltinHost host = new QueuedBuiltinHost();
        var runtime = new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        runtime.beginTick(1);
        var source = BundledLuaLibrary.load().get("computed:redstone_emitter");
        LuaNodeInstance instance =
                runtime.createNode(UUID.randomUUID(), source.apiVersion(), source.source());
        var inputs = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
        instance.definition().inputs().forEach(port -> inputs.put(port.id(), port.defaultValue()));
        inputs.put("trigger", org.luaj.vm2.LuaValue.TRUE);
        inputs.put("level", org.luaj.vm2.LuaValue.valueOf(15));
        var fields = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
        instance.definition().fields().forEach(field -> fields.put(field.id(), field.defaultValue()));

        LuaInvocationResult yielded =
                instance.run(inputs, fields, 1, runtime.nextGraphStep(), false, null);

        assertEquals(LuaNodeStatus.YIELDED, yielded.status());
        assertTrue(host.redstoneLevels.isEmpty());

        host.runNext();
        LuaInvocationResult resumed = instance.resumeIfReady();

        assertEquals(LuaNodeStatus.IDLE, resumed.status());
        assertEquals(List.of(15), host.redstoneLevels);
        assertTrue(!host.redstoneThread.startsWith("Coroutine-"));
    }

    @Test
    void monitorDefinitionCallsItsProductionEndpoint() {
        BuiltinEndpoints.register();
        QueuedBuiltinHost host = new QueuedBuiltinHost();
        var source = BundledLuaLibrary.load().get("computed:peripheral");
        var runtime = new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        runtime.beginTick(1);
        var instance = runtime.createNode(UUID.randomUUID(), source.apiVersion(), source.source());
        var inputs = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
        instance.definition().inputs().forEach(port -> inputs.put(port.id(), port.defaultValue()));
        UUID widgetId = UUID.randomUUID();
        var widget = new org.luaj.vm2.LuaTable();
        widget.set("id", widgetId.toString());
        widget.set("type", "text");
        widget.set("text", "Monitor output");
        widget.set("x", 4);
        widget.set("y", 6);
        widget.set("width", 48);
        widget.set("height", 12);
        inputs.put("widget_1", widget);
        var fields = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
        instance.definition().fields().forEach(field -> fields.put(field.id(), field.defaultValue()));

        LuaInvocationResult result =
                instance.run(inputs, fields, 1, runtime.nextGraphStep(), false, (name, values) -> {});
        result = completeQueuedInvocation(host, instance, result);

        assertEquals(LuaNodeStatus.IDLE, result.status());
        assertEquals(List.of("front"), host.monitorTargets);
        assertEquals(1, host.monitorWidgets.size());
        assertEquals(1, host.monitorWidgets.getFirst().size());
        BuiltinWidget shown = host.monitorWidgets.getFirst().getFirst();
        assertEquals(widgetId, shown.id());
        assertEquals("text", shown.type());
        assertEquals("Monitor output", shown.properties().get("text"));
        assertEquals(4, shown.x());
        assertEquals(6, shown.y());
        assertEquals("line", shown.properties().get("layout_mode"));
        assertEquals(1.0, shown.properties().get("line"));
        assertEquals(1.0, shown.properties().get("span"));
        assertEquals("auto", shown.properties().get("fit"));
    }

    @Test
    void glassesHudDefinitionStreamsItsWidgetTableThroughTheBuiltinEndpoint() {
        BuiltinEndpoints.register();
        QueuedBuiltinHost host = new QueuedBuiltinHost();
        var source = BundledLuaLibrary.load().get("computed:glasses_hud");
        var runtime = new LuaComputerRuntime(UUID.randomUUID(), new LuaInstructionBudget(), host);
        runtime.beginTick(1);
        var instance = runtime.createNode(UUID.randomUUID(), source.apiVersion(), source.source());
        var inputs = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();
        var widget = new org.luaj.vm2.LuaTable();
        widget.set("type", "text");
        widget.set("text", "Glasses output");
        widget.set("x", 4);
        widget.set("y", 6);
        widget.set("width", 48);
        widget.set("height", 12);
        inputs.put("widget_1", widget);
        var fields = new LinkedHashMap<String, org.luaj.vm2.LuaValue>();

        LuaInvocationResult result =
                instance.run(inputs, fields, 1, runtime.nextGraphStep(), false, (name, values) -> {});
        result = completeQueuedInvocation(host, instance, result);

        assertEquals(LuaNodeStatus.IDLE, result.status(), String.valueOf(result.diagnostics()));
        assertEquals(List.of(), result.diagnostics());
        assertEquals(1, host.glassesHudWidgets.size());
        List<BuiltinWidget> shown = host.glassesHudWidgets.getFirst();
        assertEquals(1, shown.size());
        assertEquals("text", shown.getFirst().type());
        assertEquals("Glasses output", shown.getFirst().properties().get("text"));
        assertEquals(4, shown.getFirst().x());
        assertEquals(6, shown.getFirst().y());
        assertEquals(48, shown.getFirst().width());
        assertEquals(12, shown.getFirst().height());
    }

    private static LuaInvocationResult completeQueuedInvocation(
            QueuedBuiltinHost host,
            LuaNodeInstance instance,
            LuaInvocationResult initial) {
        LuaInvocationResult result = initial;
        for (int resumes = 0; result.status() == LuaNodeStatus.YIELDED; resumes++) {
            if (resumes >= 16) {
                throw new AssertionError("Bundled node exceeded the expected endpoint dispatch count");
            }
            assertTrue(host.hasTasks(), "Yielded node did not queue a server endpoint");
            host.runNext();
            result = instance.resumeIfReady();
        }
        return result;
    }

    private static final class QueuedBuiltinHost
            implements BuiltinEndpointHost, ServerEndpointExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private final List<Integer> redstoneLevels = new ArrayList<>();
        private final List<String> monitorTargets = new ArrayList<>();
        private final List<List<BuiltinWidget>> monitorWidgets = new ArrayList<>();
        private final List<List<BuiltinWidget>> glassesHudWidgets = new ArrayList<>();
        private String redstoneThread;

        @Override
        public double worldTime() {
            return 6000;
        }

        @Override
        public void redstoneOutput(String face, int level) {
            redstoneThread = Thread.currentThread().getName();
            redstoneLevels.add(level);
        }

        @Override
        public void showWidgets(String target, List<BuiltinWidget> widgets) {
            monitorTargets.add(target);
            monitorWidgets.add(widgets);
        }

        @Override
        public void showGlassesHud(List<BuiltinWidget> widgets) {
            glassesHudWidgets.add(widgets);
        }

        @Override
        public void runCommand(String command) {}

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
