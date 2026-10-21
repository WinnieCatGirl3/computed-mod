# In-Game Documentation and Examples

Computed ships its documentation inside the game. The Docs screen describes every bundled node and
lets you import ready-made example programs with one click.

## Opening the Docs screen

Open any computer's editor and press the **Docs** button in the toolbar (top right). There is no
keybind or command.

## What you see

- **Left column:** every node grouped in folders (Math, Widgets, Logic, Speaker, Integration, …).
  Type in the search box to filter by title, ID, or description.
- **Right column:** the selected node's description, its inputs and outputs, and a Lua example.
- **↑** / **↓** jump between entries, **ESC** closes the screen.

Two extra pages live under **Help**:

- **How to use these docs** — a short introduction.
- **Keyboard Shortcuts** — the full editor shortcut list (also on the
  [Using the Editor](editor.md) page).

## Importing an example

Select any node and press **Import example** (or **No example** if that node has none). The example
graph is placed into the editor canvas of the computer you have open, fully wired and ready to run.
From there it works like any other graph: edit it, extend it, save it to a
[program disk](lua/import-export.md), or copy single nodes out of it.

Every bundled node has an example — there are over 120 of them, including examples for the
integration nodes when the matching mod is installed.

!!! note "Import replaces the canvas"
    Importing replaces the current graph on the canvas. Export or save your work first if you want
    to keep it. The usual limits apply (4096 nodes, 20000 connections, 4 MB per program).
