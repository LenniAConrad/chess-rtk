# Java Diagnostics Triage

Last checked: 2026-05-29.

## Scope

- `scripts/inspect_java_diagnostics.sh`
- Current VS Code JDT marker files for the `chess-rtk` workspace.
- `reports/java-diagnostics-known-stale.txt`
- SonarLint local H2 issue database under `~/.sonarlint/storage/h2`.
- Recent SonarQube for IDE logs under the local VS Code log directory.
- VS Code user MCP configuration at `~/.config/Code/User/mcp.json`.

## Findings

- `javac -Xlint:all --release 17` reports no warnings.
- The current repo-scoped JDT marker storage reports no marker messages.
- Historical repo-scoped JDT markers reported two unused private methods:
  - `ClassifierDatasetExporter.streamRecordJson(Path, Consumer<String>)`
  - `RecordPgnExporter.streamRecordObjects(Path, Consumer<String>)`
- Those signatures do not exist in the current source. Both helpers now take a
  third `LongConsumer` progress argument, and all current call sites use that
  three-argument signature.
- The stale May 27 JDT marker cache was moved aside from VS Code workspace
  storage after confirming it no longer matched source, then removed.
- A second stale Red Hat Java `ss_ws` marker cache still held SonarQube
  diagnostics for an older source layout, including paths such as
  `application/gui/history/...` and old `chess/pdf/...` files. Those marker
  files were moved aside after confirming they were cache state, not current
  source diagnostics, then removed.
- The SonarLint `KNOWN_FINDINGS` table still held 5,367 historical
  `file:///home/lennart/Code/chess-rtk` rows, including findings for files and
  imports that no longer exist. `LOCAL_ONLY_ISSUES` and `SERVER_FINDINGS` were
  empty for this workspace. After backing up the database, the stale
  `KNOWN_FINDINGS` rows for this workspace were cleared.
- Recent SonarQube for IDE logs show no review-needed Java file diagnostics for
  this repository.
- The VS Code user MCP configuration was a zero-byte file, which produced
  `Error reading MCP config: Unexpected end of JSON input` in the current
  SonarQube for IDE extension-host log. It has been replaced with the valid
  empty MCP configuration shape:
  ```json
  {
    "servers": {}
  }
  ```
- The remaining Flyway/H2 warning in older SonarQube for IDE logs is extension
  runtime noise, not a source diagnostic for this workspace.

## Triage Result

The previously visible SonarQube/JDT warnings were stale editor state, not
actionable source warnings. Their stale JDT marker cache and stale SonarLint
known-finding rows have been cleared, and the diagnostics helper now scans both
JDT and secondary Java workspace marker files. The Java and SonarQube language
servers were restarted after the cache cleanup. If equivalent warnings reappear
after a fresh analysis, treat them as new diagnostics and reproduce them before
changing source code.

The exact stale marker messages are tracked in
`reports/java-diagnostics-known-stale.txt` so the inspector can separate them
from active or review-needed diagnostics.

New Java diagnostics from `scripts/inspect_java_diagnostics.sh`, the VS Code
Problems view, or the SonarQube view should still be treated as actionable until
they are reproduced, fixed, or documented as false positives.
