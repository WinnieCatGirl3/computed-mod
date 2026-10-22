# Computed — Ideen-Backlog

Offene Ideen und Requests, sortiert nach Reife. Wird beim Bauen neuer Features ergänzt.
Neue Ideen am besten im GitHub-Discussion-Thread hinterlassen
(<https://github.com/WinnieCatGirl3/computed-mod/discussions/categories/ideas>) — diese Datei ist
die rohe Arbeitsliste daraus.

## Offene Ideen

### Speaker / Audio
- **Custom-Instrument-Samples** — Nutzer legen eigene .ogg ins Speaker-Mod-Ordner, die dann
  als Instrumente/Wellenformen verfügbar sind.

### Widgets / Display
- **Verlaufs-/Diagramm-Widget** — Werte über die Zeit als Kurve auf dem Monitor
  (Fortschrittsbalken, Checkbox und Texteingabe sind schon da).
- **Bild-Widget** — Textur/Item-Icon auf dem Monitor anzeigen.
- **Widget-Klicks mit Rechtsbutton** — zweite Aktionsspalte für Widgets (z.B. Kontext-Menü).

### Computer / Welt
- **Radar-/Sensor-Endpoint** — Entities, Lichtlevel, Wetter oder Blöcke in Radius abfragen
  (`scan(radius)` → Tabelle) — passt direkt zum Create-Radar des Users.
- **Energie-Endpoint** — FE/EU lesen und entnehmen (Create/Forge-Energy-Integration).

### Diskutiert
- **Portable Monitor Screen-Animation** — Öffnungs-Animation (Scale/Fade) im
  `PortableMonitorScreen`; User baut sie als Lernprojekt selbst (siehe Umgesetzt).

### Integrationen
- **Create-Screens als Monitor** — Create-Displays als Monitor-Fläche anbinden
  (Kinetic-Speed/Stress läuft schon über den Create-Kinetic-Node).

## Umgesetzt

- **`play_sequence` + Disc-Playlist** — `play_sequence(spec)`-Endpoint und **Speaker Play
  Sequence**-Node spielen getaktete Noten (`instrument:note:seconds`, max 64 Notes, Lücken
  0–10 s, `stop()` bricht ab); rechtsklick mit Disc füllt die Warteschlange (max 8),
  `play` spielt der Reihe nach (fertige Disc wird wie bei der Jukebox fallen gelassen),
  Loop zirkuliert die Queue, `queue_size` + Status-Ausgang `queued` melden den Stand.
- **Autostart-Disk** — Rechtsklick auf beschriebene Program Disk toggelt das Autostart-Flag
  (Tooltip + Glint); der Computer lädt das Disk-Programm beim ersten Tick automatisch über den
  üblichen Apply-Pfad (volle Validierung, bestehendes Programm hat Vorrang).
- **`has_display()`-Endpoint** — `computed:monitor/has_display` + **Display Present**-Node:
  true wenn ein gebildeter Monitor am Ziel-Face hängt oder ein Spieler einen gekoppelten
  Portable Monitor trägt (Link-Card zählt nicht).
- **Redstone-Endpoint** (veraltet — längst eingebaut) — `computed:redstone` liest
  `input`/`comparator` und schreibt `output` am Computer-Face; Nodes: Redstone Input,
  Comparator Read, Redstone Emitter.
- **Speaker Envelope/Sweep/Loop** — `play_wave` mit Attack/Decay (0–2 s, Slice-Restarts),
  neuer `play_sweep(wave, f1, f2, seconds, volume)`-Endpoint + Node, Disc-Loop via
  `set_loop(true)` (Ticker startet die Platte neu), Status meldet `is_looping`.
- **Aeronautics Vertical Speed + Altitude** — Einzel-Nodes für Steigrate (vy) und Y-Höhe;
  dazu der generische **Threshold Trigger**-Node (above/below + Hysteresis) für Alarme.
- **Undo/Redo im Graph-Editor** — Ctrl+Z/Ctrl+Y, 256 History-Stände, jedes Layout-Operation
  rückgängig machbar.
- **Auto-Save** — Graph speichert sich ca. einmal pro Sekunde und beim Schließen; kein
  Save-Button.
- **Widget-Preview (Lua Live Preview)** — rechte Pane rendert den Node-Ausgang mit
  editierbaren Sample-Inputs, ohne dass ein Programm läuft.
- **Beispiel-Disk im Creative Tab** — `widgetDemoDisk` liegt im Haupt-Tab zum Ausprobieren.
- **Kanal-Management am Portable Monitor** — Rechtsklick auf einen Tab entfernt den Kanal.
- **Name im Tab-Label** — Tabs/Labels zeigen den Computernamen, sobald der gesetzt ist.
- **Komputer-zu-Komputer-Link** — `computed:net`-Endpoint (Senden/Empfangen über
  Computer-ID, inkl. Remote-Aufrufen).
- **Aeronautics-Integration** — 15 Telemetry-Nodes von `Aeronautics Telemetry` bis
  Einzelwerten (Speed, Heading, Pitch …), plus `measure()` auf der CC-Seite.
- **Create Kinetic** — Speed/Stress/Stress-Capacity als Sensor-Node.
- **Conditional Nodes (CC-Style)** — `BundledLuaLibrary`-Entries können eine `requiredMod`
  tragen; ohne die Mod erscheinen sie nicht im Explorer. Der Aeronautics-Telemetry-Node
  hängt an Create (ohne Create keine Contraption).
- **Speaker-Synthese `play_wave`** — sine/square/saw/triangle/noise via Pitch-Shift
  (55–3520 Hz), Node „Speaker Play Wave".
- **Portable Monitor 3D** — Item-Model aus Element-Boxen (Front/Back/Side-Texturen,
  Spyglass-artige Display-Transformationen). Die Öffnungs-Animation im Screen ist als
  Lernprojekt beim User: Einstieg in `PortableMonitorScreen.render` (openProgress 0→1,
  Scale um Display-Zentrum + Fade-Overlay).
