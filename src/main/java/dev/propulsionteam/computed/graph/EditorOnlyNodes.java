package dev.propulsionteam.computed.graph;

/**
 * Canvas-only node kinds (comments, group frames) that persist inside a program's graph but never
 * compile or execute. The scheduler skips them silently; the editor renders them as annotations.
 */
public final class EditorOnlyNodes {
    public static final String COMMENT = "computed:comment";
    public static final String FRAME = "computed:frame";

    private EditorOnlyNodes() {}

    public static boolean isEditorOnly(String definitionId) {
        return COMMENT.equals(definitionId) || FRAME.equals(definitionId);
    }
}
