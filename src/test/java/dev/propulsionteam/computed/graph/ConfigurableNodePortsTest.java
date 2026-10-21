package dev.propulsionteam.computed.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.propulsionteam.computed.lua.node.ConnectionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurableNodePortsTest {
    @Test
    void keepsStableIdsWhileAddingRemovingAndTypingPayloadPorts() {
        List<PortSnapshot> ports = ConfigurableNodePorts.withInitialPort(
                ConfigurableNodePorts.EVENT_SENDER,
                List.of(new PortSnapshot(
                        "trigger", PortDirection.INPUT, ConnectionType.BOOLEAN, "trigger")));

        assertEquals("data_1", ports.getLast().id());
        ports = ConfigurableNodePorts.cycleLastType(
                ConfigurableNodePorts.EVENT_SENDER, ports);
        assertEquals(ConnectionType.BOOLEAN, ports.getLast().type());
        ports = ConfigurableNodePorts.add(ConfigurableNodePorts.EVENT_SENDER, ports);
        assertEquals("data_2", ports.getLast().id());
        assertEquals(ConnectionType.NUMBER, ports.getLast().type());
        ports = ConfigurableNodePorts.cycleType(
                ConfigurableNodePorts.EVENT_SENDER, ports, "data_1");
        assertEquals(
                List.of(ConnectionType.STRING, ConnectionType.NUMBER),
                ConfigurableNodePorts.dynamicPorts(
                                ConfigurableNodePorts.EVENT_SENDER, ports)
                        .stream()
                        .map(PortSnapshot::type)
                        .toList());
        ports = ConfigurableNodePorts.removeLast(
                ConfigurableNodePorts.EVENT_SENDER, ports);
        assertEquals(List.of("data_1"), ConfigurableNodePorts.dynamicPorts(
                ConfigurableNodePorts.EVENT_SENDER, ports).stream()
                .map(PortSnapshot::id)
                .toList());
    }

    @Test
    void monitorPortsAreWidgetInputs() {
        List<PortSnapshot> ports =
                ConfigurableNodePorts.withInitialPort(ConfigurableNodePorts.MONITOR, List.of());
        ports = ConfigurableNodePorts.add(ConfigurableNodePorts.MONITOR, ports);

        assertEquals(List.of("widget_1", "widget_2"), ports.stream()
                .map(PortSnapshot::id)
                .toList());
        assertEquals(List.of(ConnectionType.WIDGET, ConnectionType.WIDGET), ports.stream()
                .map(PortSnapshot::type)
                .toList());
        assertEquals(List.of(PortDirection.INPUT, PortDirection.INPUT), ports.stream()
                .map(PortSnapshot::direction)
                .toList());
    }

    @Test
    void terminalPortsAreStringLineInputsCyclingBetweenStringAndTable() {
        List<PortSnapshot> ports = ConfigurableNodePorts.withInitialPort(
                ConfigurableNodePorts.TERMINAL, List.of());

        assertEquals("line_1", ports.getLast().id());
        assertEquals(ConnectionType.STRING, ports.getLast().type());
        assertEquals(PortDirection.INPUT, ports.getLast().direction());
        assertEquals("Line 1", ports.getLast().label());

        ports = ConfigurableNodePorts.add(ConfigurableNodePorts.TERMINAL, ports);
        assertEquals(List.of("line_1", "line_2"), ports.stream()
                .map(PortSnapshot::id)
                .toList());

        ports = ConfigurableNodePorts.cycleType(ConfigurableNodePorts.TERMINAL, ports, "line_1");
        assertEquals(ConnectionType.TABLE, ports.stream()
                .filter(port -> port.id().equals("line_1"))
                .findFirst()
                .orElseThrow()
                .type());
        ports = ConfigurableNodePorts.cycleType(ConfigurableNodePorts.TERMINAL, ports, "line_1");
        assertEquals(ConnectionType.STRING, ports.stream()
                .filter(port -> port.id().equals("line_1"))
                .findFirst()
                .orElseThrow()
                .type());

        ports = ConfigurableNodePorts.removeLast(ConfigurableNodePorts.TERMINAL, ports);
        assertEquals(List.of("line_1"), ConfigurableNodePorts.dynamicPorts(
                ConfigurableNodePorts.TERMINAL, ports).stream()
                .map(PortSnapshot::id)
                .toList());
    }

    @Test
    void glassesHudPortsAreWidgetInputs() {
        List<PortSnapshot> ports = ConfigurableNodePorts.withInitialPort(
                ConfigurableNodePorts.GLASSES_HUD, List.of());

        assertEquals("widget_1", ports.getLast().id());
        assertEquals(ConnectionType.WIDGET, ports.getLast().type());
        assertEquals(PortDirection.INPUT, ports.getLast().direction());
        assertEquals("Widget 1", ports.getLast().label());

        ports = ConfigurableNodePorts.add(ConfigurableNodePorts.GLASSES_HUD, ports);
        assertEquals(List.of("widget_1", "widget_2"), ports.stream()
                .map(PortSnapshot::id)
                .toList());

        // Widget-only: the type must never cycle.
        assertEquals(ports, ConfigurableNodePorts.cycleType(
                ConfigurableNodePorts.GLASSES_HUD, ports, "widget_1"));
        assertEquals(ports, ConfigurableNodePorts.cycleLastType(
                ConfigurableNodePorts.GLASSES_HUD, ports));

        ports = ConfigurableNodePorts.removeLast(ConfigurableNodePorts.GLASSES_HUD, ports);
        assertEquals(List.of("widget_1"), ConfigurableNodePorts.dynamicPorts(
                ConfigurableNodePorts.GLASSES_HUD, ports).stream()
                .map(PortSnapshot::id)
                .toList());
    }
}
