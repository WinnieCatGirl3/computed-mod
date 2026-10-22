package dev.propulsionteam.computed.content.monitors.widgets;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves line-authored widgets into the same raw pixel widgets used by rendering and hit testing.
 */
public final class MonitorWidgetLayout {
    private static final int LINE_HEIGHT_PX = 32;
    private static final int MONITOR_PADDING_PX = 8;

    private MonitorWidgetLayout() {}

    public static List<Widget> resolve(List<Widget> source, int screenW, int screenH) {
        return resolve(source, screenW, screenH, LINE_HEIGHT_PX);
    }

    /**
     * Resolves line widgets hugging their content instead of spanning the full slot — used by
     * the glasses HUD where full-width rows look far too wide. Widgets explicitly set to
     * {@link LayoutManagedWidget.Fit#FILL} still span the slot.
     */
    public static List<Widget> resolveFitted(List<Widget> source, int screenW, int screenH) {
        return resolve(source, screenW, screenH, LINE_HEIGHT_PX, true);
    }

    /**
     * Resolves line-managed widgets into raw pixel widgets. {@code lineHeightPx} controls how many
     * lines fit vertically (smaller lines = more rows on compact screens). Widgets whose preferred
     * line is occupied flow into the next free line instead of being dropped.
     */
    public static List<Widget> resolve(List<Widget> source, int screenW, int screenH, int lineHeightPx) {
        return resolve(source, screenW, screenH, lineHeightPx, false);
    }

    /** Full variant: {@code fitToContent} shrinks AUTO widgets in line layout to their content. */
    public static List<Widget> resolve(
            List<Widget> source, int screenW, int screenH, int lineHeightPx, boolean fitToContent) {
        List<Widget> out = new ArrayList<>(source.size());
        int lineCount = Math.max(1, screenH / Math.max(1, lineHeightPx));
        boolean[] occupiedLines = new boolean[lineCount];
        for (Widget widget : source) {
            if (!(widget instanceof LayoutManagedWidget managed)) {
                out.add(widget);
                continue;
            }
            if (!managed.isLineLayout()) {
                out.add(managed.raw());
                continue;
            }
            int preferred = Math.min(lineCount, Math.max(1, managed.line())) - 1;
            int lineSpan = Math.max(1, Math.min(lineCount, (int) Math.round(managed.scale())));
            int lineIndex = findFreeLine(occupiedLines, preferred, lineSpan);
            if (lineIndex < 0) {
                continue;
            }
            for (int i = lineIndex; i < lineIndex + lineSpan; i++) {
                occupiedLines[i] = true;
            }
            out.add(resolveLineWidget(
                    managed, screenW, screenH, lineCount, lineIndex, lineSpan, fitToContent));
        }
        return out;
    }

    /** First free run of {@code span} lines at or after {@code preferred}, wrapping once. */
    private static int findFreeLine(boolean[] occupied, int preferred, int span) {
        for (int start = preferred; start + span <= occupied.length; start++) {
            if (isFree(occupied, start, span)) {
                return start;
            }
        }
        for (int start = 0; start < preferred && start + span <= occupied.length; start++) {
            if (isFree(occupied, start, span)) {
                return start;
            }
        }
        return -1;
    }

    private static boolean isFree(boolean[] occupied, int start, int span) {
        for (int i = start; i < start + span; i++) {
            if (occupied[i]) {
                return false;
            }
        }
        return true;
    }

    private static Widget resolveLineWidget(LayoutManagedWidget managed, int screenW, int screenH,
                                            int lineCount, int lineIndex, int lineSpan,
                                            boolean fitToContent) {
        int availableH = Math.max(1, screenH - MONITOR_PADDING_PX * (lineCount + 1));
        int lineH = Math.max(1, availableH / lineCount);
        int lineY = MONITOR_PADDING_PX + lineIndex * (lineH + MONITOR_PADDING_PX);
        int spannedBottom = MONITOR_PADDING_PX + (lineIndex + lineSpan) * lineH
                + (lineIndex + lineSpan - 1) * MONITOR_PADDING_PX;
        int maxLineBottom = Math.max(lineY + 1, screenH - MONITOR_PADDING_PX);
        int effectiveLineH = Math.max(1, Math.min(spannedBottom, maxLineBottom) - lineY);
        Bounds bounds = boundsFor(managed, screenW, lineY, effectiveLineH, fitToContent);
        return withBounds(managed.raw(), bounds.x(), bounds.y(), bounds.w(), bounds.h());
    }

    private static Bounds boundsFor(
            LayoutManagedWidget managed, int screenW, int lineY, int lineH, boolean fitToContent) {
        int slotW = Math.max(1, screenW - MONITOR_PADDING_PX * 2);
        int targetW = slotW;
        if (fitToContent && managed.fit() == LayoutManagedWidget.Fit.AUTO) {
            int estimate = estimateContentWidth(managed.raw());
            if (estimate >= 0) {
                targetW = Math.min(slotW, Math.max(24, estimate));
            }
        }
        int targetH = Math.max(1, lineH);
        int x = MONITOR_PADDING_PX + Math.max(0, (slotW - targetW) / 2);
        int y = lineY + Math.max(0, (lineH - targetH) / 2);
        return new Bounds(x, y, targetW, targetH);
    }

    /**
     * Content width estimate for AUTO widgets: about 8 px per glyph (default MC font worst
     * case) plus padding, plus room for button/checkbox controls. Returns {@code -1} for
     * widget types whose text is not known here (fills the slot instead).
     */
    private static int estimateContentWidth(Widget raw) {
        String content;
        int control = 0;
        if (raw instanceof TextWidget text) {
            content = text.text();
        } else if (raw instanceof NumberWidget number) {
            content = number.text() != null ? number.text() : String.valueOf(number.value());
        } else if (raw instanceof ClockWidget clock) {
            content = clock.showSeconds() ? "00:00:00" : "00:00";
        } else if (raw instanceof ButtonWidget button) {
            content = button.label();
            control = 14;
        } else if (raw instanceof CheckboxWidget checkbox) {
            content = checkbox.label();
            control = 16;
        } else if (raw instanceof TextBlockWidget block) {
            String longest = "";
            if (block.text() != null) {
                for (String line : block.text().split("\n")) {
                    if (line.length() > longest.length()) {
                        longest = line;
                    }
                }
            }
            content = longest;
        } else {
            return -1;
        }
        int glyphs = content == null ? 0 : content.length();
        return glyphs * 8 + MONITOR_PADDING_PX * 2 + control;
    }

    private static Widget withBounds(Widget raw, int x, int y, int w, int h) {
        if (raw instanceof TextWidget tw) {
            return new TextWidget(tw.id(), x, y, w, h, tw.text(), tw.colorArgb(), tw.alignment());
        }
        if (raw instanceof NumberWidget nw) {
            return new NumberWidget(nw.id(), x, y, w, h, nw.value(), nw.text(), nw.colorArgb(), nw.alignment());
        }
        if (raw instanceof ClockWidget cw) {
            return new ClockWidget(cw.id(), x, y, w, h, cw.colorArgb(), cw.showSeconds(), cw.alignment());
        }
        if (raw instanceof ButtonWidget bw) {
            return new ButtonWidget(bw.id(), x, y, w, h, bw.label(), bw.colorArgb());
        }
        if (raw instanceof CheckboxWidget cw) {
            return new CheckboxWidget(cw.id(), x, y, w, h, cw.label(), cw.colorArgb(), cw.checked());
        }
        if (raw instanceof SliderWidget sw) {
            return new SliderWidget(sw.id(), x, y, w, h, sw.value(), sw.min(), sw.max(), sw.colorArgb(), sw.step());
        }
        if (raw instanceof ProgressBarWidget pb) {
            return new ProgressBarWidget(pb.id(), x, y, w, h, pb.value(), pb.max(), pb.colorArgb(), pb.segments());
        }
        if (raw instanceof TerminalWidget tw) {
            return new TerminalWidget(tw.id(), x, y, w, h, tw.rows(), tw.defaultFg(), tw.defaultBg(),
                    tw.textScale(), tw.cursorX(), tw.cursorY());
        }
        if (raw instanceof PanelWidget panel) {
            return new PanelWidget(panel.id(), x, y, w, h, panel.colorArgb(), panel.borderArgb());
        }
        if (raw instanceof TextBlockWidget tb) {
            return new TextBlockWidget(tb.id(), x, y, w, h, tb.text(), tb.fgArgb(), tb.bgArgb(), tb.alignment());
        }
        if (raw instanceof TableWidget table) {
            return new TableWidget(table.id(), x, y, w, h, table.columns(), table.rows(),
                    table.headerFg(), table.headerBg(), table.cellFg(), table.cellBg());
        }
        if (raw instanceof TextInputWidget ti) {
            return new TextInputWidget(ti.id(), x, y, w, h, ti.placeholder(), ti.value(), ti.colorArgb());
        }
        return raw;
    }

    private record Bounds(int x, int y, int w, int h) {}
}
