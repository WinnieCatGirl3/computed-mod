# Graph events

Event Sender and Event Receiver provide named, wireless communication inside one computer graph.
Every receiver whose Event name exactly matches a sender receives that sender's event.

Event Sender has a Boolean trigger and emits on its rising edge. Event Receiver exposes an Event
output named `triggered`. Connect that output to the Event input on side-effect nodes such as
Redstone Output or Run Command.

Both event nodes have configurable payload pins:

- `+` adds a payload pin, up to 16.
- `-` removes the last payload pin without renumbering the remaining stable IDs.
- The payload control cycles the newest pin through number, boolean, string, table, and widget.

Sender and Receiver payload pins correspond by their stable `data_N` names. A receiver may declare
only the fields it needs; additional sender fields are ignored. Use the same type for a given field
on both sides.

Monitor uses the same `+` and `-` controls for one to 16 widget inputs. The Terminal widget accepts one configurable line input per pin (`line_N`), and the Table widget one configurable row input per pin (`row_N`).

Delivery order is deterministic. The scheduler indexes named receivers, so unrelated graph sections
are not scanned for each event. Events emitted by an event handler are deferred to the next graph
tick, and the pending queue is bounded to prevent an event feedback loop from hanging the server.
