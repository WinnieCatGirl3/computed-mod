package dev.propulsionteam.computed.client.editor.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.graph.ComputedProgramV3;
import dev.propulsionteam.computed.graph.EditorOnlyNodes;
import dev.propulsionteam.computed.graph.GraphNode;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class EditorAnnotationNodesTest {
    @Test
    void editorOnlyIdsAreRecognized() {
        assertTrue(EditorOnlyNodes.isEditorOnly(EditorOnlyNodes.COMMENT));
        assertTrue(EditorOnlyNodes.isEditorOnly(EditorOnlyNodes.FRAME));
        assertFalse(EditorOnlyNodes.isEditorOnly("computed:math_add"));
    }

    @Test
    void commentTextRoundTripsThroughGraphNode() {
        CommentNote note = new CommentNote(3, 4, "hello world");
        GraphNode node = note.toGraphNode(3, 4, note.getId());
        assertEquals(EditorOnlyNodes.COMMENT, node.definitionId());
        assertEquals(3, node.x());
        assertEquals(4, node.y());
        CommentNote restored = new CommentNote(node);
        assertEquals("hello world", restored.getText());
        assertEquals(note.getId(), restored.getId());
    }

    @Test
    void frameSizeAndTitleRoundTripThroughGraphNode() {
        FrameNote frame = new FrameNote(10, 10, "Sensors");
        frame.setSelected(true);
        // Grab the bottom-right resize handle and drag it 50/30 graph units larger.
        assertTrue(frame.mouseClicked(frame.frameWidth() - 5, frame.frameHeight() - 5, 0));
        assertTrue(frame.mouseDragged(0, 0, 0, 50, 30));
        assertEquals(240 + 50, frame.frameWidth());
        assertEquals(160 + 30, frame.frameHeight());

        GraphNode node = frame.toGraphNode(10, 10, frame.getId());
        assertEquals(EditorOnlyNodes.FRAME, node.definitionId());
        FrameNote restored = new FrameNote(node);
        assertEquals("Sensors", restored.getTitle());
        assertEquals(290, restored.frameWidth());
        assertEquals(190, restored.frameHeight());
    }

    @Test
    void annotationsSurviveCanvasUndoSnapshot() {
        WGraph graph = new WGraph();
        CommentNote note = new CommentNote(0, 0, "original");
        FrameNote frame = new FrameNote(40, 40, "Group");
        graph.addNode(note);
        graph.addNode(frame);
        CompoundTag snapshot = graph.save();

        note.setText("changed");
        note.setPos(500, 500);
        frame.setPos(900, 900);

        graph.load(snapshot);
        CommentNote restoredNote = (CommentNote) graph.getNode(note.getId());
        FrameNote restoredFrame = (FrameNote) graph.getNode(frame.getId());
        assertEquals("original", restoredNote.getText());
        assertEquals(0, restoredNote.getX());
        assertEquals(40, restoredFrame.getX());
    }

    @Test
    void framesRenderBehindAndStayOutOfLayout() {
        FrameNote frame = new FrameNote(0, 0, "Group");
        CommentNote comment = new CommentNote(0, 0, "");
        WNode regular = new WNode(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("computed", "test"),
                "N",
                0,
                0);
        assertTrue(frame.renderLayer() < comment.renderLayer());
        assertTrue(comment.renderLayer() < regular.renderLayer());
        assertTrue(frame.movesContainedNodes());
        assertFalse(frame.participatesInLayout());
        assertTrue(comment.participatesInLayout());
    }

    @Test
    void annotationsSurviveAdapterAndCodecRoundTrip() {
        WGraph canvas = new WGraph();
        CommentNote note = new CommentNote(3, 4, "persist me");
        FrameNote frame = new FrameNote(10, 10, "Group");
        canvas.addNode(note);
        canvas.addNode(frame);

        ComputedProgramV3 program = LuaEditorGraphAdapter.fromEditorGraph(
                canvas, ComputedProgramV3.empty(UUID.randomUUID()), 7);
        assertEquals(2, program.rootGraph().nodes().size());

        ComputedProgramV3 decoded = ProgramV3Codec.decodeV3(ProgramV3Codec.encode(program));
        WGraph reloaded = LuaEditorGraphAdapter.toEditorGraph(decoded);

        WNode restoredComment = reloaded.getNodes().stream()
                .filter(node -> node instanceof CommentNote)
                .findFirst()
                .orElseThrow();
        WNode restoredFrame = reloaded.getNodes().stream()
                .filter(node -> node instanceof FrameNote)
                .findFirst()
                .orElseThrow();
        assertEquals("persist me", ((CommentNote) restoredComment).getText());
        assertEquals("Group", ((FrameNote) restoredFrame).getTitle());
        assertInstanceOf(FrameNote.class, restoredFrame);
    }

    @Test
    void regularNodesStillRoundTripBesideAnnotations() {
        WGraph canvas = new WGraph();
        CommentNote note = new CommentNote(0, 0, "note");
        canvas.addNode(note);
        ComputedProgramV3 program = LuaEditorGraphAdapter.fromEditorGraph(
                canvas, ComputedProgramV3.empty(UUID.randomUUID()), 1);
        assertEquals(1, program.rootGraph().nodes().size());
        assertEquals(
                EditorOnlyNodes.COMMENT,
                program.rootGraph().nodes().get(0).definitionId());
    }
}
