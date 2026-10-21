# Using the Editor

Right-click a computer to open its node editor. Shift-right-click instead opens the peripheral bay
(disks). The editor saves automatically about once per second and again when you close it — there
is no save button.

## The toolbar

Top of the screen, left to right:

| Button | What it does |
|---|---|
| **«** / **»** | Show or hide the Node Explorer sidebar. |
| **Undo** / **Redo** | Step through up to 256 history states. |
| **Fit** | Zoom to fit the selection, or the whole graph (**F**). |
| **Docs** | Open the [in-game documentation](in-game-docs.md) with 120+ importable examples. |
| **Import / Export** | Save and load graphs and nodes, set the computer ID and name. |
| **−** / **+** | Zoom out and in. |

The bottom-left corner shows the computer's ID (also used for [networking](lua/endpoint-api.md#computednetsend) and
shared package folders).

## Adding nodes

- **Tab** opens the quick-add picker at your cursor. Type to filter, arrow keys to choose,
  **Enter** to place it.
- **Shift+A** opens the Node Explorer at your cursor.
- Right-click the empty canvas → **Open Node Explorer** or **New Lua Node…**.
- Drag a wire from an output pin and drop it on empty canvas: the picker opens pre-filtered to
  nodes that accept that value type, and wires itself automatically.

## Selecting and editing

| Action | How |
|---|---|
| Rubber-band selection | Left-drag on empty canvas (hold **Shift** to add to the selection) |
| Move nodes | Left-drag a node; dragging a frame moves everything inside it |
| Copy / cut / paste | **Ctrl+C** / **Ctrl+X** / **Ctrl+V** (paste lands at the cursor) |
| Duplicate | **Ctrl+D** (repeated presses form a ladder down-right) |
| Unlink selected nodes | **Ctrl+U** |
| Delete | **Delete** |
| Select everything | **Ctrl+A** |
| Pan | Middle- or right-drag |

## Navigation

- Mouse wheel zooms around the cursor (10% – 300%).
- **F** zooms to fit the selection, or the whole graph.
- Double-click a node to zoom to it.
- The **minimap** (bottom right) shows the whole graph — click or drag it to jump around.

## Undo and redo

**Ctrl+Z** undoes, **Ctrl+Y** redoes (matched to your keyboard layout, so Ctrl+Z works on
QWERTZ). Every layout operation — align, auto layout, frame moves — can be undone. Undoing back to
the last saved state marks the graph as clean again.

## Frames and comments

Right-click the canvas:

- **Add Frame** groups nodes visually. Drag a frame to move everything inside it. Right-click a
  frame for **Select Frame Contents** and five accent colors (**Frame Color**).
- **Add Comment** places a text note above the graph. Double-click it to edit (up to 2000
  characters).

Frames and comments never execute and are ignored by align and auto layout.

## Align and auto layout

Select two or more nodes, right-click → **Align ▸**: left, center, right, top, middle, bottom.
With three or more nodes you also get **Distribute H** and **Distribute V** for even gaps.

**Auto Layout** (same menu) arranges nodes in topological columns from left to right. With a
selection of two or more nodes it only touches the selection; otherwise it arranges the whole
graph.

## Computer ID and name

**Import / Export → Set Computer ID** assigns a numeric ID (shared package folders and network
messaging use it). **Set Computer Name** gives the computer a display name (up to 32 characters)
that shows in the editor, on portable monitor tabs, and for anyone linked to it.

## All keyboard shortcuts

The same list is available in-game as the **Keyboard Shortcuts** page in the Docs screen.

| Key | Action |
|---|---|
| **Tab** | Quick-add picker |
| **Shift+A** | Node explorer at the cursor |
| **F** | Zoom to fit |
| **Ctrl+Z** / **Ctrl+Y** | Undo / redo |
| **Ctrl+A** | Select everything |
| **Ctrl+C** / **Ctrl+X** / **Ctrl+V** | Copy / cut / paste |
| **Ctrl+D** | Duplicate |
| **Ctrl+U** | Unlink |
| **Delete** | Delete selection |
| **Escape** | Close picker, menu, or stop editing |
| Double-click canvas | Add-node menu |
| Double-click node | Zoom to it |
| Double-click comment | Edit its text |
| Right-click wire | Delete the wire |
| Right-click canvas | Context menu |
| Click / drag minimap | Jump around the graph |
