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
    void frameColorCyclesAndRoundTripsThroughGraphNode() {
        FrameNote frame = new FrameNote(0, 0, "Group");
        int initial = frame.frameColor();
        frame.cycleColor();
        assertTrue(frame.frameColor() != initial);

        GraphNode node = frame.toGraphNode(0, 0, frame.getId());
        FrameNote restored = new FrameNote(node);
        assertEquals(frame.frameColor(), restored.frameColor());

        FrameNote copy = FrameNote.duplicate(frame, 5, 5);
        assertEquals(frame.frameColor(), copy.frameColor());

        WGraph graph = new WGraph();
        graph.addNode(frame);
        frame.cycleColor();
        int cycled = frame.frameColor();
        CompoundTag snapshot = graph.save();
        frame.cycleColor();
        graph.load(snapshot);
        FrameNote fromSnapshot = (FrameNote) graph.getNode(frame.getId());
        assertEquals(cycled, fromSnapshot.frameColor());
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
    void commentOnlyCapturesClicksWhileEditing() {
        CommentNote note = new CommentNote(0, 0, "move me");
        assertFalse(note.hasInteractiveElementAt(4, 4));
        assertFalse(note.mouseClicked(4, 4, 0));
        note.setSelected(true);
        note.beginEditing(4, 4);
        assertTrue(note.isEditing());
        assertTrue(note.hasInteractiveElementAt(4, 4));
        assertTrue(note.mouseClicked(4, 4, 0));
        // Clicks outside the note fall through so the screen can start a drag or clear the selection.
        assertFalse(note.mouseClicked(note.getWidth() + 10, 4, 0));
        note.clearElementFocus();
        assertFalse(note.isEditing());
        assertFalse(note.hasInteractiveElementAt(4, 4));
    }

    @Test
    void commentBackspaceAtEndKeepsCursorValid() {
        int keyBackspace = 259; // GLFW.GLFW_KEY_BACKSPACE — lwjgl is not on the test classpath
        CommentNote note = new CommentNote(0, 0, "");
        note.setSelected(true);
        note.beginEditing(4, 4);
        note.charTyped('a', 0);
        assertEquals("a", note.getText());
        note.keyPressed(keyBackspace, 0, 0);
        assertEquals("", note.getText());
        // Used to leave the cursor at -1, crashing the next insertText with StringIndexOOB.
        note.charTyped('b', 0);
        assertEquals("b", note.getText());
        note.keyPressed(keyBackspace, 0, 0);
        note.keyPressed(keyBackspace, 0, 0);
        assertEquals("", note.getText());
        note.charTyped('c', 0);
        assertEquals("c", note.getText());
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
