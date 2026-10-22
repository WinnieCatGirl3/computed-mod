package dev.propulsionteam.computed.content.monitors.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonitorWidgetLayoutTest {
    @Test
    void preservesRawWidgetCoordinates() {
        TextWidget widget = new TextWidget(
                UUID.randomUUID(), 5, 7, 20, 9, "raw", 0xFFFFFFFF, TextAlignment.LEFT);

        List<Widget> resolved = MonitorWidgetLayout.resolve(List.of(widget), 128, 64);

        assertEquals(1, resolved.size());
        assertSame(widget, resolved.getFirst());
    }

    @Test
    void resolvesLineManagedWidgetsAgainstMonitorDimensions() {
        UUID id = UUID.randomUUID();
        LayoutManagedWidget managed = new LayoutManagedWidget(
                new TextWidget(id, 0, 0, 1, 1, "line", 0xFFFFFFFF, TextAlignment.CENTER),
                LayoutManagedWidget.LayoutMode.LINE,
                1,
                1,
                LayoutManagedWidget.Fit.AUTO);

        List<Widget> resolved = MonitorWidgetLayout.resolve(List.of(managed), 128, 64);

        assertEquals(
                new TextWidget(id, 8, 8, 112, 20, "line", 0xFFFFFFFF, TextAlignment.CENTER),
                resolved.getFirst());
    }

    @Test
    void stretchesTheSameLineWidgetAcrossDifferentMonitorWidths() {
        UUID id = UUID.randomUUID();
        LayoutManagedWidget managed = new LayoutManagedWidget(
                new TextWidget(id, 27, 19, 3, 4, "responsive", 0xFFFFFFFF, TextAlignment.CENTER),
                LayoutManagedWidget.LayoutMode.LINE,
                1,
                1,
                LayoutManagedWidget.Fit.AUTO);

        TextWidget oneBlock = (TextWidget) MonitorWidgetLayout.resolve(List.of(managed), 64, 64).getFirst();
        TextWidget threeBlocks = (TextWidget) MonitorWidgetLayout.resolve(List.of(managed), 192, 64).getFirst();

        assertEquals(8, oneBlock.x());
        assertEquals(48, oneBlock.w());
        assertEquals(8, threeBlocks.x());
        assertEquals(176, threeBlocks.w());
        assertEquals(oneBlock.y(), threeBlocks.y());
        assertEquals(oneBlock.h(), threeBlocks.h());
    }

    @Test
    void fitsLineWidgetsToContentOnGlasses() {
        UUID id = UUID.randomUUID();
        LayoutManagedWidget managed = new LayoutManagedWidget(
                new TextWidget(id, 0, 0, 1, 1, "line", 0xFFFFFFFF, TextAlignment.CENTER),
                LayoutManagedWidget.LayoutMode.LINE,
                1,
                1,
                LayoutManagedWidget.Fit.AUTO);

        TextWidget fitted =
                (TextWidget) MonitorWidgetLayout.resolveFitted(List.of(managed), 256, 144).getFirst();

        // slot = 256 - 16 padding = 240; "line" (4 glyphs) * 8 + 16 padding = 48
        assertEquals(48, fitted.w());
        assertEquals(8 + (240 - 48) / 2, fitted.x());
    }

    @Test
    void fillWidgetsStillSpanTheSlotOnGlasses() {
        UUID id = UUID.randomUUID();
        LayoutManagedWidget managed = new LayoutManagedWidget(
                new TextWidget(id, 0, 0, 1, 1, "fill me", 0xFFFFFFFF, TextAlignment.LEFT),
                LayoutManagedWidget.LayoutMode.LINE,
                1,
                1,
                LayoutManagedWidget.Fit.FILL);

        TextWidget filled =
                (TextWidget) MonitorWidgetLayout.resolveFitted(List.of(managed), 256, 144).getFirst();

        assertEquals(240, filled.w());
        assertEquals(8, filled.x());
    }

    @Test
    void contentFitUsesNarrowestKnownTypesAndLeavesOthersFullWidth() {
        UUID id = UUID.randomUUID();
        LayoutManagedWidget emptyText = new LayoutManagedWidget(
                new TextWidget(id, 0, 0, 1, 1, "", 0xFFFFFFFF, TextAlignment.CENTER),
                LayoutManagedWidget.LayoutMode.LINE, 1, 1, LayoutManagedWidget.Fit.AUTO);
        LayoutManagedWidget clock = new LayoutManagedWidget(
                new ClockWidget(id, 0, 0, 1, 1, 0xFFFFFFFF, false, TextAlignment.CENTER),
                LayoutManagedWidget.LayoutMode.LINE, 1, 1, LayoutManagedWidget.Fit.AUTO);
        LayoutManagedWidget terminal = new LayoutManagedWidget(
                new TerminalWidget(id, 0, 0, 1, 1,
                        List.of(new TerminalWidget.TerminalRow("hi", null, null)),
                        0xFFFFFFFF, 0xFF000000, 1f, -1, -1),
                LayoutManagedWidget.LayoutMode.LINE, 1, 1, LayoutManagedWidget.Fit.AUTO);

        Widget empty =
                MonitorWidgetLayout.resolveFitted(List.of(emptyText), 256, 144).getFirst();
        Widget clockResolved =
                MonitorWidgetLayout.resolveFitted(List.of(clock), 256, 144).getFirst();
        Widget terminalResolved =
                MonitorWidgetLayout.resolveFitted(List.of(terminal), 256, 144).getFirst();

        assertEquals(24, empty.w());
        assertEquals(5 * 8 + 16, clockResolved.w());
        assertEquals(240, terminalResolved.w());
    }

    @Test
    void resolvesLineManagedTerminalWidgetsAgainstMonitorDimensions() {
        UUID id = UUID.randomUUID();
        TerminalWidget raw = new TerminalWidget(
                id, 0, 0, 1, 1,
                List.of(new TerminalWidget.TerminalRow("hi", null, null)),
                0xFFFFFFFF, 0xFF000000, 1f, -1, -1);
        LayoutManagedWidget managed = new LayoutManagedWidget(
                raw,
                LayoutManagedWidget.LayoutMode.LINE,
                1,
                2,
                LayoutManagedWidget.Fit.AUTO);

        List<Widget> resolved = MonitorWidgetLayout.resolve(List.of(managed), 128, 64);

        TerminalWidget terminal = (TerminalWidget) resolved.getFirst();
        assertEquals(8, terminal.x());
        assertEquals(112, terminal.w());
        assertEquals("hi", terminal.rows().getFirst().text());
        assertEquals(0xFF000000, terminal.defaultBg());
    }
}
