package dev.propulsionteam.computed.lua.endpoint;

import dev.propulsionteam.computed.content.monitors.widgets.TableWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TerminalWidget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class BuiltinEndpoints {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private BuiltinEndpoints() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ComputedEndpoints.register("computed:world", endpoint -> endpoint.method(
                        "time",
                        EndpointSignature.of(List.of(), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> EndpointResult.immediate(
                                LuaValue.valueOf(requireHost(invocation).worldTime())),
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(6000)),
                        "Returns the current world day time.")
                .method(
                        "position",
                        EndpointSignature.of(
                                List.of(),
                                List.of(EndpointType.NUMBER, EndpointType.NUMBER, EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> numbers(requireHost(invocation).position()),
                        invocation -> numbers(new double[] {0.5, 64.5, 0.5}),
                        "Returns the computer world position.")
                .method(
                        "rotation",
                        EndpointSignature.of(
                                List.of(),
                                List.of(EndpointType.NUMBER, EndpointType.NUMBER, EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> numbers(requireHost(invocation).rotation()),
                        invocation -> numbers(new double[] {0, 0, 0}),
                        "Returns the computer rotation in degrees.")
                .method(
                        "block_present",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.BOOLEAN)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                requireHost(invocation).blockPresent(argument(invocation, 0)))),
                        invocation -> EndpointResult.immediate(LuaValue.FALSE),
                        "Reports whether a block is present at a relative face."));
        ComputedEndpoints.register("computed:redstone", endpoint -> endpoint.method(
                        "input",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                requireHost(invocation).redstoneInput(argument(invocation, 0)))),
                        invocation -> EndpointResult.immediate(LuaValue.ZERO),
                        "Reads weak redstone power from a relative face.")
                .method(
                        "comparator",
                        EndpointSignature.of(List.of(EndpointType.STRING), List.of(EndpointType.NUMBER)),
                        EndpointPolicy.serverThread(false, true),
                        invocation -> EndpointResult.immediate(LuaValue.valueOf(
                                requireHost(invocation).comparatorInput(argument(invocation, 0)))),
                        invocation -> EndpointResult.immediate(LuaValue.ZERO),
                        "Reads comparator power from a relative face.")
                .method(
                        "output",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER),
                                List.of()),
                        EndpointPolicy.serverThread(true, false),
                        invocation -> {
                            requireHost(invocation).redstoneOutput(
                                    argument(invocation, 0),
                                    invocation.arguments().get(1).checkint());
                            return EndpointResult.immediate();
                        },
                        null,
                        "Writes weak redstone power to a relative face."));
        ComputedEndpoints.register("computed:command", endpoint -> endpoint.method(
                "run",
                EndpointSignature.of(List.of(EndpointType.STRING), List.of()),
                EndpointPolicy.serverThread(true, false),
                invocation -> {
                    BuiltinEndpointHost host = requireHost(invocation);
                    host.runCommand(invocation.arguments().getFirst().tojstring());
                    return EndpointResult.immediate();
                },
                null,
                "Runs a command through the computer host."));
        ComputedEndpoints.register("computed:widget", endpoint -> endpoint.method(
                        "text",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(widget(
                                invocation.nodeId(),
                                "text",
                                Map.of(
                                        "text", argument(invocation, 0),
                                        "color", invocation.arguments().get(1).checkint(),
                                        "alignment", "left"))),
                        invocation -> EndpointResult.immediate(widget(
                                invocation.nodeId(),
                                "text",
                                Map.of(
                                        "text", argument(invocation, 0),
                                        "color", invocation.arguments().get(1).checkint(),
                                        "alignment", "left"))),
                        "Creates a text widget value.")
                .method(
                        "number",
                        EndpointSignature.of(
                                List.of(EndpointType.NUMBER, EndpointType.STRING, EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(numberWidget(invocation)),
                        invocation -> EndpointResult.immediate(numberWidget(invocation)),
                        "Creates a number widget value.")
                .method(
                        "clock",
                        EndpointSignature.of(
                                List.of(EndpointType.NUMBER, EndpointType.BOOLEAN),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(clockWidget(invocation)),
                        invocation -> EndpointResult.immediate(clockWidget(invocation)),
                        "Creates a clock widget value.")
                .method(
                        "button",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(buttonWidget(invocation)),
                        invocation -> EndpointResult.immediate(buttonWidget(invocation)),
                        "Creates a button widget value.")
                .method(
                        "checkbox",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER, EndpointType.BOOLEAN),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(checkboxWidget(invocation)),
                        invocation -> EndpointResult.immediate(checkboxWidget(invocation)),
                        "Creates a checkbox widget value.")
                .method(
                        "text_input",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER, EndpointType.STRING),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(textInputWidget(invocation)),
                        invocation -> EndpointResult.immediate(textInputWidget(invocation)),
                        "Creates a text input widget value.")
                .method(
                        "slider",
                        EndpointSignature.of(
                                List.of(
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(sliderWidget(invocation)),
                        invocation -> EndpointResult.immediate(sliderWidget(invocation)),
                        "Creates a slider widget value.")
                .method(
                        "progress",
                        EndpointSignature.of(
                                List.of(
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(progressWidget(invocation)),
                        invocation -> EndpointResult.immediate(progressWidget(invocation)),
                        "Creates a progress bar widget value.")
                .method(
                        "terminal",
                        EndpointSignature.of(
                                List.of(
                                        EndpointType.TABLE,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(terminalWidget(invocation)),
                        invocation -> EndpointResult.immediate(terminalWidget(invocation)),
                        "Creates a terminal widget value from text rows or styled row tables.")
                .method(
                        "line",
                        EndpointSignature.of(
                                List.of(EndpointType.STRING, EndpointType.NUMBER, EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(lineRow(invocation)),
                        invocation -> EndpointResult.immediate(lineRow(invocation)),
                        "Creates a styled terminal line row table.")
                .method(
                        "panel",
                        EndpointSignature.of(
                                List.of(EndpointType.NUMBER, EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(widget(
                                invocation.nodeId(),
                                "panel",
                                Map.of(
                                        "color", invocation.arguments().get(0).checkint(),
                                        "border", invocation.arguments().get(1).checkint()))),
                        invocation -> EndpointResult.immediate(widget(
                                invocation.nodeId(),
                                "panel",
                                Map.of(
                                        "color", invocation.arguments().get(0).checkint(),
                                        "border", invocation.arguments().get(1).checkint()))),
                        "Creates a panel widget value with an optional border color.")
                .method(
                        "text_block",
                        EndpointSignature.of(
                                List.of(
                                        EndpointType.STRING,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.STRING),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(widget(
                                invocation.nodeId(),
                                "text_block",
                                Map.of(
                                        "text", argument(invocation, 0),
                                        "color", invocation.arguments().get(1).checkint(),
                                        "bg", invocation.arguments().get(2).checkint(),
                                        "alignment", invocation.arguments().get(3).checkjstring()))),
                        invocation -> EndpointResult.immediate(widget(
                                invocation.nodeId(),
                                "text_block",
                                Map.of(
                                        "text", argument(invocation, 0),
                                        "color", invocation.arguments().get(1).checkint(),
                                        "bg", invocation.arguments().get(2).checkint(),
                                        "alignment", invocation.arguments().get(3).checkjstring()))),
                        "Creates a multiline text block widget value with a background.")
                .method(
                        "table",
                        EndpointSignature.of(
                                List.of(
                                        EndpointType.STRING,
                                        EndpointType.TABLE,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER,
                                        EndpointType.NUMBER),
                                List.of(EndpointType.TABLE)),
                        EndpointPolicy.computerThread(false, true),
                        invocation -> EndpointResult.immediate(tableWidget(invocation)),
                        invocation -> EndpointResult.immediate(tableWidget(invocation)),
                        "Creates a table widget value from a header and data rows."));
        ComputedEndpoints.register("computed:monitor", endpoint -> endpoint.method(
                "show",
                EndpointSignature.of(List.of(EndpointType.TABLE), List.of()),
                EndpointPolicy.serverThread(true, false),
                invocation -> {
                    requireHost(invocation).showWidgets(
                            invocation.target(),
                            parseWidgets(invocation.arguments().getFirst().checktable(), invocation.nodeId()));
                    return EndpointResult.immediate();
                },
                null,
                "Shows widget values on the monitor at the endpoint target."));
        ComputedEndpoints.register("computed:glasses", endpoint -> endpoint.method(
                "show",
                EndpointSignature.of(List.of(EndpointType.TABLE), List.of()),
                EndpointPolicy.serverThread(true, false),
                invocation -> {
                    requireHost(invocation).showGlassesHud(
                            parseWidgets(invocation.arguments().getFirst().checktable(), invocation.nodeId()));
                    return EndpointResult.immediate();
                },
                null,
                "Shows widget values on the glasses HUD of every player linked to this computer."));
        dev.propulsionteam.computed.lua.fs.FsEndpoints.register();
        dev.propulsionteam.computed.lua.net.NetEndpoints.register();
    }

    private static BuiltinEndpointHost requireHost(EndpointInvocation invocation) {
        if (invocation.host() instanceof BuiltinEndpointHost host) {
            return host;
        }
        throw new IllegalStateException("Computer host does not provide built-in endpoint access");
    }

    private static LuaTable widget(UUID nodeId, String type, Map<String, Object> properties) {
        LuaTable widget = new LuaTable();
        widget.set("id", nodeId.toString());
        widget.set("type", type);
        widget.set("x", 0);
        widget.set("y", 0);
        widget.set("width", 64);
        widget.set("height", 16);
        properties.forEach((key, value) -> widget.set(key, toLua(value)));
        return widget;
    }

    private static LuaTable numberWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "number", Map.of(
                "value", invocation.arguments().get(0).checkdouble(),
                "text", argument(invocation, 1),
                "color", invocation.arguments().get(2).checkint()));
    }

    private static LuaTable clockWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "clock", Map.of(
                "color", invocation.arguments().get(0).checkint(),
                "show_seconds", invocation.arguments().get(1).checkboolean()));
    }

    private static LuaTable buttonWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "button", Map.of(
                "label", argument(invocation, 0),
                "color", invocation.arguments().get(1).checkint()));
    }

    private static LuaTable checkboxWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "checkbox", Map.of(
                "label", argument(invocation, 0),
                "color", invocation.arguments().get(1).checkint(),
                "checked", invocation.arguments().get(2).checkboolean()));
    }

    private static LuaTable textInputWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "text_input", Map.of(
                "placeholder", argument(invocation, 0),
                "value", argument(invocation, 2),
                "color", invocation.arguments().get(1).checkint()));
    }

    private static LuaTable sliderWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "slider", Map.of(
                "value", invocation.arguments().get(0).checkdouble(),
                "minimum", invocation.arguments().get(1).checkdouble(),
                "maximum", invocation.arguments().get(2).checkdouble(),
                "color", invocation.arguments().get(3).checkint(),
                "step", invocation.arguments().get(4).checkdouble()));
    }

    private static LuaTable progressWidget(EndpointInvocation invocation) {
        return widget(invocation.nodeId(), "progress", Map.of(
                "value", invocation.arguments().get(0).checkdouble(),
                "maximum", invocation.arguments().get(1).checkdouble(),
                "color", invocation.arguments().get(2).checkint(),
                "segments", invocation.arguments().get(3).checkint()));
    }

    private static LuaTable terminalWidget(EndpointInvocation invocation) {
        List<LuaValue> args = invocation.arguments();
        double scale = args.get(3).checkdouble();
        LuaTable widget = widget(invocation.nodeId(), "terminal", Map.of(
                "color", args.get(1).checkint(),
                "bg", args.get(2).checkint(),
                "text_scale", Math.min(2, Math.max(0.5, scale)),
                "cursor_x", args.get(4).checkint(),
                "cursor_y", args.get(5).checkint()));
        widget.set("rows", normalizeRows(args.get(0).checktable()));
        return widget;
    }

    private static LuaTable lineRow(EndpointInvocation invocation) {
        LuaTable row = new LuaTable();
        row.set("text", clipText(argument(invocation, 0)));
        row.set("fg", invocation.arguments().get(1).checkint());
        row.set("bg", invocation.arguments().get(2).checkint());
        return row;
    }

    private static LuaTable tableWidget(EndpointInvocation invocation) {
        List<LuaValue> args = invocation.arguments();
        LuaTable widget = widget(invocation.nodeId(), "table", Map.of(
                "color", args.get(2).checkint(),
                "header_bg", args.get(3).checkint(),
                "cell_fg", args.get(4).checkint(),
                "cell_bg", args.get(5).checkint()));
        widget.set("columns", splitCsv(args.get(0).checkjstring()));
        widget.set("rows", normalizeTableRows(args.get(1).checktable()));
        return widget;
    }

    private static LuaTable splitCsv(String line) {
        LuaTable cells = new LuaTable();
        if (line.isBlank()) {
            return cells;
        }
        int next = 1;
        for (String cell : line.split(",", -1)) {
            cells.set(next++, LuaValue.valueOf(cell.trim()));
        }
        return cells;
    }

    private static LuaTable normalizeTableRows(LuaTable source) {
        LuaTable rows = new LuaTable();
        int next = 1;
        int count = Math.min(TableWidget.MAX_TABLE_ROWS, source.length());
        for (int index = 1; index <= count && next <= TableWidget.MAX_TABLE_ROWS; index++) {
            LuaValue entry = source.get(index);
            if (entry.istable()) {
                LuaTable sourceRow = entry.checktable();
                LuaTable row = new LuaTable();
                int cellCount = Math.min(TableWidget.MAX_TABLE_COLUMNS, sourceRow.length());
                for (int cell = 1; cell <= cellCount; cell++) {
                    LuaValue value = sourceRow.get(cell);
                    row.set(cell, LuaValue.valueOf(value.isnil() ? "" : value.tojstring()));
                }
                rows.set(next++, row);
            } else if (!entry.isnil()) {
                rows.set(next++, splitCsv(entry.tojstring()));
            }
        }
        return rows;
    }

    private static LuaTable normalizeRows(LuaTable source) {
        LuaTable rows = new LuaTable();
        int next = 1;
        int count = Math.min(TerminalWidget.MAX_ROWS, source.length());
        for (int index = 1;
                index <= count && next <= TerminalWidget.MAX_ROWS;
                index++) {
            LuaValue entry = source.get(index);
            if (entry.istable()) {
                LuaTable sourceRow = entry.checktable();
                LuaTable row = new LuaTable();
                row.set("text", clipText(sourceRow.get("text").optjstring("")));
                if (!sourceRow.get("fg").isnil()) {
                    row.set("fg", sourceRow.get("fg").optint(0xFFFFFFFF));
                }
                if (!sourceRow.get("bg").isnil()) {
                    row.set("bg", sourceRow.get("bg").optint(0xFF000000));
                }
                rows.set(next++, row);
            } else if (!entry.isnil()) {
                rows.set(next++, LuaValue.valueOf(clipText(entry.tojstring())));
            }
        }
        return rows;
    }

    private static String clipText(String text) {
        return text.length() > TerminalWidget.MAX_COLUMNS ? text.substring(0, TerminalWidget.MAX_COLUMNS) : text;
    }

    private static List<BuiltinWidget> parseWidgets(LuaTable table, UUID fallbackId) {
        java.util.ArrayList<BuiltinWidget> widgets = new java.util.ArrayList<>();
        if (!table.get("type").isnil()) {
            widgets.add(parseWidget(table, fallbackId));
            return List.copyOf(widgets);
        }
        int count = Math.min(64, table.length());
        for (int index = 1; index <= count; index++) {
            LuaValue value = table.get(index);
            if (value.istable() && !value.get("type").isnil()) {
                widgets.add(parseWidget(value.checktable(), fallbackId));
            }
        }
        return List.copyOf(widgets);
    }

    private static BuiltinWidget parseWidget(LuaTable table, UUID fallbackId) {
        UUID id;
        try {
            id = UUID.fromString(table.get("id").optjstring(fallbackId.toString()));
        } catch (IllegalArgumentException exception) {
            id = fallbackId;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        copyText(table, properties, "text", "");
        copyText(table, properties, "label", "");
        copyText(table, properties, "alignment", "left");
        copyText(table, properties, "layout_mode", "line");
        copyText(table, properties, "fit", "auto");
        copyNumber(table, properties, "line", 1);
        copyNumber(table, properties, "span", 1);
        copyValue(table, properties, "value", 0);
        copyNumber(table, properties, "minimum", 0);
        copyNumber(table, properties, "maximum", 1);
        copyNumber(table, properties, "step", 1);
        copyNumber(table, properties, "segments", 0);
        copyNumber(table, properties, "bg", 0xFF000000);
        copyNumber(table, properties, "text_scale", 1);
        copyNumber(table, properties, "cursor_x", -1);
        copyNumber(table, properties, "cursor_y", -1);
        copyNumber(table, properties, "border", 0);
        copyNumber(table, properties, "header_bg", 0xFF202020);
        copyNumber(table, properties, "cell_fg", 0xFFFFFFFF);
        copyNumber(table, properties, "cell_bg", 0xFF000000);
        properties.put("show_seconds", table.get("show_seconds").optboolean(false));
        properties.put("checked", table.get("checked").optboolean(false));
        copyText(table, properties, "placeholder", "");
        copyRows(table, properties);
        copyTableData(table, properties);
        return new BuiltinWidget(
                id,
                table.get("type").checkjstring(),
                table.get("x").optint(0),
                table.get("y").optint(0),
                Math.max(1, table.get("width").optint(64)),
                Math.max(1, table.get("height").optint(16)),
                table.get("color").optint(0xFFFFFFFF),
                properties);
    }

    private static void copyText(
            LuaTable table,
            Map<String, Object> properties,
            String key,
            String fallback) {
        properties.put(key, table.get(key).optjstring(fallback));
    }

    private static void copyNumber(
            LuaTable table,
            Map<String, Object> properties,
            String key,
            double fallback) {
        LuaValue value = table.get(key);
        properties.put(key, value.isnumber() ? value.checkdouble() : fallback);
    }

    /**
     * {@code value} is a text string for input widgets but a number for sliders and progress bars —
     * keep the Lua type instead of coercing, otherwise a non-numeric string throws a Lua type error
     * while the monitor builds its widget list.
     */
    private static void copyValue(
            LuaTable table,
            Map<String, Object> properties,
            String key,
            double fallback) {
        LuaValue value = table.get(key);
        if (value.type() == LuaValue.TSTRING) {
            properties.put(key, value.tojstring());
        } else if (value.type() == LuaValue.TNUMBER) {
            properties.put(key, value.checkdouble());
        } else {
            properties.put(key, fallback);
        }
    }

    private static void copyRows(LuaTable table, Map<String, Object> properties) {
        LuaValue value = table.get("rows");
        if (!value.istable()) {
            return;
        }
        LuaTable source = value.checktable();
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        int count = Math.min(TerminalWidget.MAX_ROWS, source.length());
        for (int index = 1; index <= count; index++) {
            LuaValue entry = source.get(index);
            if (entry.istable()) {
                LuaTable row = entry.checktable();
                Map<String, Object> styled = new LinkedHashMap<>();
                styled.put("text", row.get("text").optjstring(""));
                if (!row.get("fg").isnil()) {
                    styled.put("fg", row.get("fg").optint(0xFFFFFFFF));
                }
                if (!row.get("bg").isnil()) {
                    styled.put("bg", row.get("bg").optint(0xFF000000));
                }
                rows.add(styled);
            } else if (!entry.isnil()) {
                rows.add(Map.of("text", entry.tojstring()));
            }
        }
        properties.put("rows", List.copyOf(rows));
    }

    private static void copyTableData(LuaTable table, Map<String, Object> properties) {
        LuaValue columns = table.get("columns");
        if (columns.istable()) {
            properties.put("table_columns", stringList(columns.checktable(), TableWidget.MAX_TABLE_COLUMNS));
        } else if (!columns.isnil()) {
            properties.put("table_columns", csvList(columns.optjstring("")));
        }
        LuaValue rows = table.get("rows");
        if (rows.istable()) {
            LuaTable source = rows.checktable();
            List<List<String>> parsed = new java.util.ArrayList<>();
            int count = Math.min(TableWidget.MAX_TABLE_ROWS, source.length());
            for (int index = 1; index <= count; index++) {
                LuaValue entry = source.get(index);
                if (entry.istable()) {
                    parsed.add(stringList(entry.checktable(), TableWidget.MAX_TABLE_COLUMNS));
                } else if (!entry.isnil()) {
                    parsed.add(csvList(entry.tojstring()));
                }
            }
            properties.put("table_rows", List.copyOf(parsed));
        }
    }

    private static List<String> stringList(LuaTable source, int max) {
        List<String> values = new java.util.ArrayList<>();
        int count = Math.min(max, source.length());
        for (int index = 1; index <= count; index++) {
            values.add(source.get(index).optjstring(""));
        }
        return List.copyOf(values);
    }

    private static List<String> csvList(String line) {
        if (line.isBlank()) {
            return List.of();
        }
        String[] cells = line.split(",", -1);
        List<String> values = new java.util.ArrayList<>(cells.length);
        for (String cell : cells) {
            values.add(cell.trim());
        }
        return List.copyOf(values);
    }

    private static LuaValue toLua(Object value) {
        return switch (value) {
            case String text -> LuaValue.valueOf(text);
            case Boolean flag -> LuaValue.valueOf(flag);
            case Integer number -> LuaValue.valueOf(number);
            case Double number -> LuaValue.valueOf(number);
            default -> LuaValue.NIL;
        };
    }

    private static String argument(EndpointInvocation invocation, int index) {
        return invocation.arguments().get(index).checkjstring();
    }

    private static EndpointResult.Immediate numbers(double[] values) {
        return EndpointResult.immediate(
                LuaValue.valueOf(values[0]),
                LuaValue.valueOf(values[1]),
                LuaValue.valueOf(values[2]));
    }
}
