package dev.propulsionteam.computed.graph;

import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-instance port rules for bundled nodes whose payload width is authored in the graph editor.
 * Stable numbered IDs deliberately survive label/layout changes and only the last port is removed.
 */
public final class ConfigurableNodePorts {
    public static final String EVENT_SENDER = "computed:event_sender";
    public static final String EVENT_RECEIVER = "computed:event_receiver";
    public static final String MONITOR = "computed:peripheral";
    public static final String GLASSES_HUD = "computed:glasses_hud";
    public static final String TERMINAL = "computed:terminal_widget";
    public static final String TABLE_WIDGET = "computed:table_widget";
    public static final int MAX_PORTS = 16;

    private static final List<ConnectionType> PAYLOAD_TYPES =
            List.of(ConnectionType.NUMBER, ConnectionType.BOOLEAN, ConnectionType.STRING,
                    ConnectionType.TABLE, ConnectionType.WIDGET);

    private ConfigurableNodePorts() {}

    public static boolean configurable(String definitionId) {
        return EVENT_SENDER.equals(definitionId)
                || EVENT_RECEIVER.equals(definitionId)
                || MONITOR.equals(definitionId)
                || GLASSES_HUD.equals(definitionId)
                || TERMINAL.equals(definitionId)
                || TABLE_WIDGET.equals(definitionId);
    }

    public static List<PortSnapshot> withInitialPort(
            String definitionId, List<PortSnapshot> staticPorts) {
        if (!configurable(definitionId)) {
            return List.copyOf(staticPorts);
        }
        List<PortSnapshot> ports = new ArrayList<>(staticPorts);
        if (dynamicPorts(definitionId, ports).isEmpty()) {
            ports.add(newPort(definitionId, 1, defaultType(definitionId)));
        }
        return List.copyOf(ports);
    }

    public static List<PortSnapshot> dynamicPorts(
            String definitionId, List<PortSnapshot> ports) {
        String prefix = prefix(definitionId);
        PortDirection direction = direction(definitionId);
        if (prefix == null || direction == null) {
            return List.of();
        }
        return ports.stream()
                .filter(port -> port.direction() == direction && port.id().startsWith(prefix))
                .toList();
    }

    public static List<PortSnapshot> add(String definitionId, List<PortSnapshot> current) {
        List<PortSnapshot> dynamic = dynamicPorts(definitionId, current);
        if (dynamic.size() >= MAX_PORTS) {
            return List.copyOf(current);
        }
        int next = dynamic.stream()
                .mapToInt(port -> suffix(port.id(), prefix(definitionId)))
                .max()
                .orElse(0) + 1;
        ConnectionType type = defaultType(definitionId);
        List<PortSnapshot> updated = new ArrayList<>(current);
        updated.add(newPort(definitionId, next, type));
        return List.copyOf(updated);
    }

    public static List<PortSnapshot> removeLast(String definitionId, List<PortSnapshot> current) {
        List<PortSnapshot> dynamic = dynamicPorts(definitionId, current);
        if (dynamic.size() <= 1) {
            return List.copyOf(current);
        }
        PortSnapshot removed = dynamic.getLast();
        List<PortSnapshot> updated = new ArrayList<>(current);
        updated.remove(removed);
        return List.copyOf(updated);
    }

    public static List<PortSnapshot> cycleLastType(
            String definitionId, List<PortSnapshot> current) {
        List<PortSnapshot> dynamic = dynamicPorts(definitionId, current);
        if (dynamic.isEmpty()) {
            return List.copyOf(current);
        }
        return cycleType(definitionId, current, dynamic.getLast().id());
    }

    public static List<PortSnapshot> cycleType(
            String definitionId, List<PortSnapshot> current, String portId) {
        if (MONITOR.equals(definitionId) || GLASSES_HUD.equals(definitionId)) {
            // Fixed payload type: widgets only (monitor screens and the glasses HUD).
            return List.copyOf(current);
        }
        PortSnapshot selected = dynamicPorts(definitionId, current).stream()
                .filter(port -> port.id().equals(portId))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return List.copyOf(current);
        }
        ConnectionType next;
        if (TERMINAL.equals(definitionId) || TABLE_WIDGET.equals(definitionId)) {
            next = selected.type() == ConnectionType.STRING
                    ? ConnectionType.TABLE
                    : ConnectionType.STRING;
        } else {
            int typeIndex = PAYLOAD_TYPES.indexOf(selected.type());
            next = PAYLOAD_TYPES.get((Math.max(0, typeIndex) + 1) % PAYLOAD_TYPES.size());
        }
        PortSnapshot replacement = new PortSnapshot(
                selected.id(), selected.direction(), next, selected.label());
        List<PortSnapshot> updated = new ArrayList<>(current);
        updated.set(updated.indexOf(selected), replacement);
        return List.copyOf(updated);
    }

    private static PortSnapshot newPort(String definitionId, int index, ConnectionType type) {
        String id = prefix(definitionId) + index;
        return new PortSnapshot(id, direction(definitionId), type, label(definitionId, index));
    }

    private static String prefix(String definitionId) {
        return TERMINAL.equals(definitionId) || TABLE_WIDGET.equals(definitionId)
                ? "line_"
                : MONITOR.equals(definitionId) || GLASSES_HUD.equals(definitionId) ? "widget_"
                : configurable(definitionId) ? "data_"
                : null;
    }

    private static PortDirection direction(String definitionId) {
        return EVENT_RECEIVER.equals(definitionId) ? PortDirection.OUTPUT
                : configurable(definitionId) ? PortDirection.INPUT
                : null;
    }

    private static ConnectionType defaultType(String definitionId) {
        return MONITOR.equals(definitionId) || GLASSES_HUD.equals(definitionId)
                ? ConnectionType.WIDGET
                : TERMINAL.equals(definitionId)
                        || TABLE_WIDGET.equals(definitionId) ? ConnectionType.STRING
                : ConnectionType.NUMBER;
    }

    private static String label(String definitionId, int index) {
        return MONITOR.equals(definitionId) || GLASSES_HUD.equals(definitionId)
                ? "Widget " + index
                : TERMINAL.equals(definitionId) ? "Line " + index
                : TABLE_WIDGET.equals(definitionId) ? "Row " + index
                : "Data " + index;
    }

    private static int suffix(String id, String prefix) {
        try {
            return Integer.parseInt(id.substring(prefix.length()));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
