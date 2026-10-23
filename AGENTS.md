# AGENTS.md

Operative Regeln für die Arbeit in diesem Repo.

## Git / Push-Regel (Wichtig!)

- **Gitea (`git push gitea main`)**: jederzeit erlaubt — nach jedem fertigen Schritt/Schrittstand
  pushen.
- **GitHub (`git push origin main`)**: **nur ganz am Ende, wenn der User es ausdrücklich sagt.**
  Nie ungefragt pushen. GitHub-Pushes lösen die CI-Workflows aus (Build, Wiki-Deploy, ggf.
  Release).
- Commits lokal machen ist jederzeit ok; gehalten wird nur der origin-Push.

## Release-Flow

- `mod_version` in `gradle.properties` steuert den Release — **beide Bäume identisch halten**.
- Push nach GitHub mit neuem `mod_version`: `release.yml` erkennt, dass der Release
  `v{mod_version}` noch nicht existiert, und erstellt ihn automatisch (Tag +
  `computed-v{mod_version}.jar`), sofern nicht schon vorhanden.
- Praxis: Features ansammeln (Commits + Gitea), User testet lokal, **danach** einmal
  `mod_version` bumpen und den GitHub-Push freigeben.

## Zwei Bäume (Linux-Hauptbaum + Windows-Mirror)

- Hauptbaum: `computed/computed-mod-main`
- Mirror: `computed/computed-mod-main-windows/computed-mod-main`
- Nach Quelländerungen spiegeln: `rsync -a src/ <mirror>/src/`, `rsync -a docs/ <mirror>/docs/`
  (plus `IDEAS.md`/`gradle.properties` falls geändert), im Mirror `./gradlew build` und Jars
  byte-vergleichen (`cmp`), dann erst deployen.
- Spiel-Mods-Ordner: `/media/bacon/EB77-8BB0/blimp/nav/new models/libs/` — darin genau **eine**
  `computed-<version>.jar` (alte löschen, sonst Doppel-Version-Crash).

## Docs-Beispiele neu generieren

- `GENERATE_DOCS_EXAMPLES=1 ./gradlew test --tests "*NodeDocsExamplesTest*"` schreibt **alle**
  `.computed`-Dateien neu (gzip-MTIME → alle sehen „geändert" aus, ohne inhaltliche Differenz).
- Danach: gewünschte neue/geänderte Dateien nach `/tmp` legen,
  `git checkout -- src/main/resources/assets/computed/docs/`, Dateien zurückkopieren —
  der Commit enthält dann nur die echten Änderungen.
- Ohne Datei-Env-Variablen zuerst laufen lassen: der Coverage-Test prüft vor dem Generator und
  schlägt fehl, bis die neuen Beispiel-Dateien da sind (zweiter Lauf ist grün).
