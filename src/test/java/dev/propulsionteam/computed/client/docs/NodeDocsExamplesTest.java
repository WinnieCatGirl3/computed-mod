package dev.propulsionteam.computed.client.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.client.editor.canvas.LuaEditorGraphAdapter;
import dev.propulsionteam.computed.diagnostics.ComputedDiagnostic;
import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.GraphAnalysisResult;
import dev.propulsionteam.computed.graph.GraphAnalyzer;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.lua.node.BundledLuaLibrary;
import dev.propulsionteam.computed.persistence.ComputedProgramPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Keeps the Docs screen's example graphs honest: every bundled entry must ship an importable
 * {@code .computed} archive under {@code assets/computed/docs/}. Setting
 * {@code GENERATE_DOCS_EXAMPLES=1} rewrites the whole folder from the generator.
 */
class NodeDocsExamplesTest {
    private static final Path EXAMPLES = Path.of("src/main/resources/assets/computed/docs");

    @Test
    void everyBundledEntryShipsAnImportableExample() throws IOException {
        Set<String> bundled = BundledLuaLibrary.load().keySet();
        List<String> missing = new ArrayList<>();
        List<String> broken = new ArrayList<>();
        List<String> miswired = new ArrayList<>();
        for (NodeDocsEntry entry : NodeDocsLibrary.all()) {
            if (!bundled.contains(entry.id())) {
                continue; // intro page or integration-only node — no bundled definition to build from
            }
            Path file = fileFor(entry);
            if (!Files.isRegularFile(file)) {
                missing.add(entry.id());
                continue;
            }
            try {
                ComputedProgramV3 program =
                        ComputedProgramPackage.importArchive(Files.readAllBytes(file));
                assertFalse(program.rootGraph().nodes().isEmpty(), entry.id() + " has no nodes");
                program.rootGraph().nodes().forEach(node -> assertTrue(
                        bundled.contains(node.definitionId()),
                        entry.id() + " references unknown node " + node.definitionId()));
                // Every stored wire must resolve to existing, type-compatible ports —
                // otherwise the editor silently drops it on import and the example
                // shows unconnected nodes.
                GraphAnalysisResult analysis =
                        GraphAnalyzer.analyze(program.rootGraph(), node -> false);
                for (ComputedDiagnostic diagnostic : analysis.diagnostics()) {
                    if (diagnostic.severity() == ComputedDiagnostic.Severity.ERROR
                            && switch (diagnostic.code()) {
                                case "missing_port", "incompatible_ports", "multiple_input_connections",
                                        "dangling_connection" -> true;
                                default -> false;
                            }) {
                        miswired.add(entry.id() + ": " + diagnostic.code() + " — " + diagnostic.message());
                    }
                }
            } catch (RuntimeException exception) {
                broken.add(entry.id() + ": " + exception.getMessage());
            }
        }
        assertTrue(missing.isEmpty(), () -> "Missing example graphs: " + missing);
        assertTrue(broken.isEmpty(), () -> "Unimportable example graphs: " + broken);
        assertTrue(miswired.isEmpty(), () -> "Miswired example graphs: " + miswired);
    }

    @Test
    void editorImportPreservesEveryNodeAndWire() throws IOException {
        Set<String> bundled = BundledLuaLibrary.load().keySet();
        List<String> dropped = new ArrayList<>();
        for (NodeDocsEntry entry : NodeDocsLibrary.all()) {
            if (!bundled.contains(entry.id())) {
                continue;
            }
            Path file = fileFor(entry);
            if (!Files.isRegularFile(file)) {
                continue; // covered by everyBundledEntryShipsAnImportableExample
            }
            ComputedProgramV3 program =
                    ComputedProgramPackage.importArchive(Files.readAllBytes(file));
            WGraph editorGraph = LuaEditorGraphAdapter.toEditorGraph(program);
            int expectedNodes = program.rootGraph().nodes().size();
            int expectedWires = program.rootGraph().connections().size();
            int actualNodes = editorGraph.getNodes().size();
            int actualWires = editorGraph.getConnections().size();
            if (actualNodes != expectedNodes || actualWires != expectedWires) {
                dropped.add(entry.id() + ": nodes " + actualNodes + "/" + expectedNodes
                        + ", wires " + actualWires + "/" + expectedWires);
            }
        }
        assertTrue(dropped.isEmpty(), () -> "Editor import drops content: " + dropped);
    }

    @Test
    void keyboardShortcutsPageExistsUnderHelp() {
        NodeDocsEntry keys = NodeDocsLibrary.byId("docs:keys");
        assertNotNull(keys, "keyboard shortcuts page is missing");
        assertEquals("Help", keys.folder());
        assertFalse(keys.summary().isBlank());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GENERATE_DOCS_EXAMPLES", matches = "1")
    void regenerateExamples() throws IOException {
        Files.createDirectories(EXAMPLES);
        List<Path> stale;
        try (Stream<Path> files = Files.walk(EXAMPLES)) {
            stale = files.filter(path -> path.getFileName().toString().endsWith(".computed")).toList();
        }
        for (Path path : stale) {
            Files.delete(path);
        }

        List<String> written = new ArrayList<>();
        for (NodeDocsEntry entry : NodeDocsLibrary.all()) {
            ComputedProgramV3 program = NodeDocsGraphBuilder.build(entry);
            if (program == null) {
                continue;
            }
            Path file = fileFor(entry);
            Files.createDirectories(file.getParent());
            Files.write(file, ComputedProgramPackage.exportArchive(program));
            written.add(entry.id());
        }
        assertFalse(written.isEmpty(), "generator produced no examples");
    }

    private static Path fileFor(NodeDocsEntry entry) {
        return EXAMPLES.resolve(entry.id().replace(':', '/') + ".computed");
    }
}
