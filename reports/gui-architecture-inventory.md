# GUI Architecture Inventory

Date: 2026-06-25

This report records the first architecture extraction slice for the Swing GUI. It is not a visual redesign and it does not change the Workbench behavior. The goal is to make future GUI work move toward stable packages, narrow feature dependencies, and tested boundaries instead of adding more code to the legacy window shell.

## Confirmed Problems

| Problem | Evidence | Long-term risk |
| --- | --- | --- |
| Legacy shell owns too much | `WindowBase` still holds 57 non-static fields and the `Window*Layer` chain still owns feature creation | New features keep coupling to frame state and become hard to test without a visible window |
| Host adapters pass the whole window | Existing adapters such as `WindowPlayHost` and `WindowRunArtifactHost` still extend `WindowHost`; the Publishing and Report host adapters have been removed | Feature panels can reach broad shell state instead of receiving explicit services |
| Shared UI helpers were mixed with feature code | Generic scroll math lived in `application.gui.workbench.ui` | Reusable primitives were tied to the Workbench design-system package even when they had no theme or feature dependency |
| Direct Swing styling is widespread | The baseline has 174 raw Swing control constructions, 396 direct `setFont` calls, and 129 direct `setBorder` calls | New screens can drift from the shared `Theme` and `Ui` facade unless the existing debt is measured |
| Publishing was a good pilot | The Publish tab already used a host interface and owned a coherent vertical workflow | It could be moved behind a feature contract without rewriting the panel |

## New Package Map

| Package | Current role |
| --- | --- |
| `application.gui.app` | Composition helpers that wire legacy shell state to extracted feature modules |
| `application.gui.feature` | Feature module namespace |
| `application.gui.feature.dataset` | Dataset view contract, dependency ports, and legacy panel adapter |
| `application.gui.feature.publishing` | First feature seam: Publishing and report view contracts, dependency ports, and legacy host adapters |
| `application.gui.foundation` | GUI primitives independent of Workbench, features, CLI, chess rules, and shell state |
| `application.gui.foundation.layout` | Shared scroll/layout helpers, starting with `ScrollableSupport` |
| `application.gui.platform` | Platform-service contracts such as clipboard, dialogs, notifications, UI dispatch, and background execution |
| `application.gui.platform.swing` | Swing-backed implementations of generic platform-service contracts |
| `application.gui.workbench.ui` | Workbench design-system facade, theme, controls, and Swing styling internals |
| `application.gui.workbench.window` | Legacy Workbench shell and compatibility adapters |

Every new package has a `package-info.java` file so the package boundary is documented at the source root.

## Moved Classes

| Class | From | To | Reason |
| --- | --- | --- | --- |
| `ScrollableSupport` | `application.gui.workbench.ui` | `application.gui.foundation.layout` | The helper is pure scroll/layout math and has no Workbench, theme, command, or chess dependency |

## Publishing Pilot

| Item | Result |
| --- | --- |
| Feature contract | `PublishingView` abstracts `component`, `updateCommand`, `requestCommandUpdate`, and `runCommand` |
| Dependency bundle | `PublishingDependencies` names the services Publish needs: owner, current FEN, game model, batch input, report service, command runner, clipboard, command control, notifications, and dialogs |
| Compatibility adapter | `PublishingController` adapts the narrow dependencies back to the existing `PublishingPanel.Host` |
| Composition root | `LegacyWorkbenchComposition.publishingView(...)` is now the creation point used by `WindowBase` |
| Concrete panel | `PublishingPanel` now implements `PublishingView` but keeps its existing UI and command-building behavior |
| Shell dependency reduction | `WindowBase` stores `List<PublishingView>` instead of `List<PublishingPanel>` and no longer constructs `new WindowPublishingHost(this)` for Publishing |
| Report contract | `ReportView` abstracts `component`, `generateReport`, `copyReport`, and `saveReportFile` |
| Report dependencies | `ReportDependencies` names the services Report needs: owner, current position, visible moves, game model, tag model, clipboard, console, notifications, and dialogs |
| Report adapter | `ReportController` adapts the narrow dependencies back to the existing `ReportPanel.Host` |
| Report composition | `LegacyWorkbenchComposition.reportView(...)` is now the report creation point used by `WindowBase` and Publishing workflows |
| Removed adapters | `WindowPublishingHost` and `WindowReportHost` were removed; publishing and report dependencies now come from feature dependency records |

## Dataset Pilot

| Item | Result |
| --- | --- |
| Feature contract | `DatasetView` abstracts `component` and `analyzeCurrentSource` |
| Dependency bundle | `DatasetDependencies` names the two Board navigation ports: open in shared Board and open in detached Board |
| Compatibility adapter | `DatasetController` creates the existing `DatasetPanel` behind the feature contract |
| Composition root | `LegacyWorkbenchComposition.datasetView(...)` is now the creation point used by `WindowBase` |
| Concrete panel | `DatasetPanel` now implements `DatasetView` but keeps its existing UI and analysis behavior |
| Shell dependency reduction | `WindowBase` stores `DatasetView` instead of `DatasetPanel` and no longer constructs `new DatasetPanel(...)` directly |

## Baseline Debt

The architecture regression stores current direct Swing/decorating debt in `testdata/gui/architecture-baseline.tsv`. Future changes may reduce these counts freely. Increases fail `testing.GuiArchitectureRegressionTest` unless the baseline is deliberately updated.

| Rule | Files with entries | Total count |
| --- | ---: | ---: |
| `direct-color-construction` | 30 | 233 |
| `direct-set-border` | 48 | 129 |
| `direct-set-font` | 89 | 396 |
| `raw-swing-construction` | 40 | 174 |
| `ui-manager-access` | 3 | 128 |
| `window-base-field` | 1 | 57 |

## Rules Enforced

| Rule | Enforced by |
| --- | --- |
| GUI packages with Java source must have `package-info.java` | `GuiArchitectureRegressionTest` |
| Package declarations under `src/application/gui` must match paths | `GuiArchitectureRegressionTest` |
| Foundation code must not import app, feature, Workbench, CLI, or chess packages | `GuiArchitectureRegressionTest` |
| Feature packages must not import sibling feature implementations | `GuiArchitectureRegressionTest` |
| Feature packages must not receive `WindowBase` or `WindowHost` | `GuiArchitectureRegressionTest` |
| Legacy window inheritance and host adapters must not grow beyond the allowlist | `GuiArchitectureRegressionTest` |
| Dataset must be created through `LegacyWorkbenchComposition.datasetView(...)` | `GuiArchitectureRegressionTest` |
| Publishing must be created through `LegacyWorkbenchComposition.publishingView(...)` and `WindowPublishingHost` must stay removed | `GuiArchitectureRegressionTest` |
| Reports must be created through `LegacyWorkbenchComposition.reportView(...)` and `WindowReportHost` must stay removed | `GuiArchitectureRegressionTest` |
| Shared UI internals must stay package-private or behind the `Ui` facade | `WorkbenchStructureRegressionTest` |

## Component Gallery

`testing.GuiComponentGallery` is a headless development surface for shared controls. It builds representative buttons, inputs, status badges, a segmented switcher, an empty state, and a command block in light and dark modes, then paints both to images and verifies that content is nonblank.

The gallery is not user-facing. It exists to make future UI primitive changes easier to inspect and smoke-test without opening the full Workbench.

## Adapters Remaining

| Adapter | Status |
| --- | --- |
| `WindowPlayHost` | Still used by Play mode |
| `WindowDashboardActions` | Still used by Dashboard actions |
| `WindowRunArtifactHost` | Still used by run artifacts |

## Verification

| Check | Result |
| --- | --- |
| `javac --release 17 -d out @/tmp/chess-rtk-srcs.txt` | Passed |
| `java -cp out testing.GuiArchitectureRegressionTest` | Passed |
| `java -cp out testing.DatasetFeatureRegressionTest` | Passed |
| `java -cp out testing.ReportFeatureRegressionTest` | Passed |
| `java -cp out testing.PublishingFeatureRegressionTest` | Passed |
| `java -Djava.awt.headless=true -cp out testing.GuiComponentGallery` | Passed |
| `java -cp out testing.WorkbenchStructureRegressionTest` | Passed |
| `git diff --check` | Passed |
| `./scripts/run_regression_suite.sh core` | Passed |
| `./scripts/run_regression_suite.sh recommended` | Passed; `shellcheck` skipped because it is not installed, and GPU perft skipped because no native backend is configured |

## Next Slice

| Priority | Step |
| --- | --- |
| 1 | Extract Play behind a `PlayView` / `PlayDependencies` seam |
| 2 | Move more pure layout helpers from `workbench.ui` to `foundation.layout` only when they have no theme or Workbench dependency |
| 3 | Reduce `WindowBase` field count by moving per-feature state into feature controllers and update the baseline downward |
| 4 | Expand the component gallery with screenshots only after the no-deps rule has a local image output convention |
