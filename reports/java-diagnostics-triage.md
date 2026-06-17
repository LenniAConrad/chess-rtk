# Java Diagnostics Triage

Last checked: 2026-06-16.

## Scope

- `scripts/inspect_java_diagnostics.sh`
- `javac -Xlint:all --release 17`
- Current VS Code JDT marker files for the `chess-rtk` workspace
- SonarLint local H2 issue database under `~/.sonarlint/storage/h2`
- Recent SonarQube for IDE logs under the local VS Code log directory

## Findings

- `javac -Xlint:all --release 17` reports no warnings.
- Java suppression markers are limited to two explicit unchecked casts in tests:
  - `src/testing/BuiltInEngineRegressionTest.java:235`
  - `src/testing/WorkbenchBackendRegression.java:2577`
- The current repo-scoped JDT marker storage reports no marker messages.
- SonarLint issue tables for this workspace are clean:
  - `KNOWN_FINDINGS`: 0
  - `LOCAL_ONLY_ISSUES`: 0
  - `SERVER_FINDINGS`: 0
- Recent SonarQube for IDE logs show no review-needed messages for this
  repository.

## Triage Result

The Java diagnostics pass is clean after the Theme facade split and the current
broad worktree. No source cleanup is required from `javac`, JDT markers,
SonarLint issue storage, or SonarQube for IDE logs.

New Java diagnostics from `scripts/inspect_java_diagnostics.sh`, the VS Code
Problems view, or the SonarQube view should still be treated as actionable until
they are reproduced, fixed, or documented as false positives.
