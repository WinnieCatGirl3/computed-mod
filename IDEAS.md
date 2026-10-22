# Computed — Ideen-Backlog

Offene Ideen und Requests, sortiert nach Reife. Wird beim Bauen neuer Features ergänzt.
Neue Ideen am besten im GitHub-Discussion-Thread hinterlassen
(<https://github.com/WinnieCatGirl3/computed-mod/discussions/categories/ideas>) — diese Datei ist
die rohe Arbeitsliste daraus.

## Offene Ideen

### Speaker / Audio
- **Envelope für `play_wave`** — Attack/Decay-Parameter, damit Töne nicht hart abbeißen.
- **Sweep/Oszillator** — `play_sweep(wave, f1, f2, seconds)`: Frequenz wandert linear
  von f1 nach f2 (Riser, Alarme, Motoren).
- **Disc-Loop** — SpeakerEndpoint `loop(true/false)` oder `play(loop)` für endlose Platten.
- **`play_sequence`** — Node/Endpoint der eine Liste von Noten mit Timing abspielt
  (kleine Melodien ohne externe Taktdaten).
- **Disc-Playlist** — mehrere Discs in die Warteschlange legen, `play` spielt der Reihe nach.
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
- **Redstone-Endpoint** — Signal am Computer-Face lesen/schreiben (`get_redstone`,
  `set_redstone`), damit Graphen mit der Welt schalten.
- **Monitor-Endpoint `has_display()`** — für Programme, die prüfen ob ein Monitor-Block
  existiert; Portable Monitor und Monitor-Blöcke antworten beide.
- **Energie-Endpoint** — FE/EU lesen und entnehmen (Create/Forge-Energy-Integration).
- **Autostart auf Disk** — Programm das beim Einschalten automatisch läuft.

### Diskutiert
- **Portable Monitor Screen-Animation** — Öffnungs-Animation (Scale/Fade) im
  `PortableMonitorScreen`; User baut sie als Lernprojekt selbst (siehe Umgesetzt).

### Integrationen
- **Create-Screens als Monitor** — Create-Displays als Monitor-Fläche anbinden
  (Kinetic-Speed/Stress läuft schon über den Create-Kinetic-Node).

## Umgesetzt

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
