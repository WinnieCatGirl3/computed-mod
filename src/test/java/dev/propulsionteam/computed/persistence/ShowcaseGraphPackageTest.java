package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.LuaGraphScheduler;
import dev.propulsionteam.computed.graph.LuaGraphTickResult;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpointHost;
import dev.propulsionteam.computed.lua.endpoint.BuiltinWidget;
import dev.propulsionteam.computed.lua.endpoint.EndpointResult;
import dev.propulsionteam.computed.lua.endpoint.ServerEndpointExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Loads the showcase {@code .computed} packages from {@code ../showcase}, proves they import as
 * format-3 programs, and runs the widget graphs headless so every node definition, port snapshot,
 * and connection is checked by the real scheduler.
 */
class ShowcaseGraphPackageTest {
    private static final Path SHOWCASE = Path.of("..", "showcase");

    @Test
    void consoleIsTheOneShowcaseGraph() throws IOException {
        ComputedProgramV3 program = importPackage("showcase_console", 21, 22);
        Host host = new Host();
        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid("showcase-console"), host);
        assertNoDefinitionProblems(scheduler);

        LuaGraphTickResult first = scheduler.tick(false);
        assertClean(first);
        assertTrue(host.hasTasks(), "monitor show endpoint should be queued");
        drain(host);

        List<BuiltinWidget> widgets = host.monitorWidgets.getLast();
        assertEquals("front", host.monitorTargets.getLast());
        assertEquals(6, widgets.size(), () -> "widgets: " + widgets);
        assertEquals(List.of("text", "clock", "progress", "number", "button", "number"),
                widgets.stream().map(BuiltinWidget::type).toList());

        // the custom wave node drives the progress bar through the graph
        double start = progressValue(widgets);
        for (int tick = 0; tick < 10; tick++) {
            assertClean(scheduler.tick(false));
            drain(host);
        }
        double later = progressValue(host.monitorWidgets.getLast());
        assertTrue(later > start, () -> "wave must advance: " + start + " -> " + later);
        assertTrue(later <= 100, () -> "wave must stay in range: " + later);

        // the custom melody node is parked until the lever (redstone input) is on;
        // in the headless host the lever reads 0, so assertClean above already proves
        // the sequencer runs without errors while disabled
    }

    @Test
    void dashboardImportsAndDrivesSixMonitorWidgets() throws IOException {
        ComputedProgramV3 program = importPackage("showcase_dashboard", 14, 16);
        Host host = new Host();
        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid("showcase-dashboard"), host);
        assertNoDefinitionProblems(scheduler);

        LuaGraphTickResult first = scheduler.tick(false);
        assertClean(first);
        assertTrue(host.hasTasks(), "monitor show endpoint should be queued");
        drain(host);

        LuaGraphTickResult second = scheduler.tick(false);
        assertClean(second);

        List<BuiltinWidget> widgets = host.monitorWidgets.getLast();
        assertEquals("front", host.monitorTargets.getLast());
        assertEquals(6, widgets.size(), () -> "widgets: " + widgets);
        assertEquals(List.of("text", "clock", "progress", "number", "button", "number"),
                widgets.stream().map(BuiltinWidget::type).toList());

        // the custom wave node must drive the progress bar through the graph
        double start = progressValue(widgets);
        for (int tick = 0; tick < 10; tick++) {
            assertClean(scheduler.tick(false));
            drain(host);
        }
        double later = progressValue(host.monitorWidgets.getLast());
        assertTrue(later > start, () -> "wave must advance: " + start + " -> " + later);
        assertTrue(later <= 100, () -> "wave must stay in range: " + later);
    }

    @Test
    void melodyImportsAndParksUntilEnabled() throws IOException {
        ComputedProgramV3 program = importPackage("showcase_melody", 6, 7);
        Host host = new Host();
        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid("showcase-melody"), host);
        assertNoDefinitionProblems(scheduler);

        for (int tick = 0; tick < 5; tick++) {
            assertClean(scheduler.tick(false));
        }
        assertTrue(host.hasTasks(), "monitor show endpoint should be queued");
        drain(host);
        assertClean(scheduler.tick(false));

        List<BuiltinWidget> widgets = host.monitorWidgets.getLast();
        assertEquals(3, widgets.size(), () -> "widgets: " + widgets);
        assertEquals(List.of("text", "checkbox", "number"),
                widgets.stream().map(BuiltinWidget::type).toList());
        assertEquals(false, widgets.get(1).properties().get("checked"));
    }

    @Test
    void worldIoImportsAndStartsClean() throws IOException {
        ComputedProgramV3 program = importPackage("showcase_world_io", 18, 19);
        Host host = new Host();
        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid("showcase-world-io"), host);
        assertNoDefinitionProblems(scheduler);
        assertClean(scheduler.tick(false));
    }

    @Test
    void kineticImportsAndStartsClean() throws IOException {
        ComputedProgramV3 program = importPackage("showcase_kinetic", 15, 16);
        Host host = new Host();
        LuaGraphScheduler scheduler = new LuaGraphScheduler(program, uuid("showcase-kinetic"), host);
        assertNoDefinitionProblems(scheduler);
        // Create is not on the unit-test classpath, so the kinetic endpoint is unregistered here.
        assertNoErrorsExcept(scheduler.tick(false), "Unknown endpoint: create:kinetic");
    }

    private static double progressValue(List<BuiltinWidget> widgets) {
        BuiltinWidget progress = widgets.stream()
                .filter(widget -> "progress".equals(widget.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no progress widget: " + widgets));
        Object value = progress.properties().get("value");
        assertTrue(value instanceof Number, () -> "progress value is not numeric: " + value);
        return ((Number) value).doubleValue();
    }

    private static void assertNoErrorsExcept(LuaGraphTickResult result, String allowedMessagePart) {
        List<ComputedDiagnostic> errors = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == ComputedDiagnostic.Severity.ERROR)
                .filter(diagnostic -> !diagnostic.message().contains(allowedMessagePart))
                .toList();
        assertTrue(errors.isEmpty(), () -> "tick diagnostics: " + errors);
    }

    private static ComputedProgramV3 importPackage(String name, int nodes, int connections)
            throws IOException {
        Path file = SHOWCASE.resolve(name + ".computed");
        assertTrue(Files.isRegularFile(file), "missing showcase package " + file.toAbsolutePath());
        ComputedProgramV3 program = ComputedProgramPackage.importArchive(Files.readAllBytes(file));
        assertEquals(nodes, program.rootGraph().nodes().size(), "node count for " + name);
        assertEquals(connections, program.rootGraph().connections().size(), "connection count for " + name);
        return program;
    }

    private static void assertNoDefinitionProblems(LuaGraphScheduler scheduler) {
        List<ComputedDiagnostic> diagnostics = scheduler.validationDiagnostics();
        assertTrue(diagnostics.isEmpty(), () -> "graph problems: " + diagnostics);
    }

    private static void assertClean(LuaGraphTickResult result) {
        List<ComputedDiagnostic> errors = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == ComputedDiagnostic.Severity.ERROR)
                .toList();
        assertTrue(errors.isEmpty(), () -> "tick diagnostics: " + errors);
    }

    private static void drain(Host host) {
        while (host.hasTasks()) {
            host.runNext();
        }
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(("computed:showcase:" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final class Host implements BuiltinEndpointHost, ServerEndpointExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private final List<String> monitorTargets = new ArrayList<>();
        private final List<List<BuiltinWidget>> monitorWidgets = new ArrayList<>();

        @Override
        public double worldTime() {
            return 6000;
        }

        @Override
        public void runCommand(String command) {}

        @Override
        public void showWidgets(String target, List<BuiltinWidget> widgets) {
            monitorTargets.add(target);
            monitorWidgets.add(widgets);
        }

        @Override
        public CompletionStage<EndpointResult> submitServerEndpoint(Callable<EndpointResult> endpointCall) {
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
                task = tasks.pollFirst();
            }
            task.run();
        }
    }
}
