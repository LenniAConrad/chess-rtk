# GUI Architecture

The Swing GUI is being moved toward explicit packages and narrow feature contracts. The goal is long-term maintainability without changing the visible Workbench behavior: keep the deterministic chess core, keep Swing, keep the existing look and workflows, and make each new slice easier to test without a full frame.

This page defines the target boundaries for GUI code. It complements the [Workbench design guide](workbench-design-guide.md), which covers visual and interaction rules.

## Package Boundaries

| Package | Owns | Must not own |
| --- | --- | --- |
| `application.gui.app` | Composition roots that wire application shells to features | Feature logic, Swing styling internals, chess rules |
| `application.gui.foundation` | GUI primitives with no Workbench, feature, CLI, or chess dependency | Theme colors, Workbench state, command dispatch |
| `application.gui.foundation.layout` | Reusable layout and scroll math | Feature-specific layout decisions |
| `application.gui.platform` | Platform-service contracts such as clipboard, dialogs, notifications, UI-thread dispatch, and background execution | Workbench frame state or feature workflows |
| `application.gui.platform.swing` | Swing-backed implementations of platform contracts | Feature-specific behavior |
| `application.gui.feature.<name>` | Feature contracts, controllers, feature state, dependency ports, and legacy adapters during migration | Direct `WindowBase` access, sibling feature implementations |
| `application.gui.workbench.ui` | Workbench design-system facade, theme, controls, icons, dialogs, toasts, and Swing styling internals | Chess rules, command execution semantics, feature models |
| `application.gui.workbench.window` | Legacy shell, registered views, split/dock behavior, compatibility adapters | New feature logic when a feature package can own it |

## Dependency Direction

| From | May depend on |
| --- | --- |
| `foundation` | JDK and Swing only |
| `platform` | JDK and generic Swing types when needed for contracts |
| `platform.swing` | `platform`, JDK, Swing |
| `feature.<name>` | `foundation`, `platform`, shared domain models required by the feature, its own package, and temporary legacy Swing panels only inside compatibility controllers |
| `app` | feature packages, platform implementations, legacy shell types while wiring compatibility slices |
| `workbench.ui` | `foundation`, Swing, generic Workbench visual primitives |
| `workbench.window` | app composition, feature view contracts, legacy Workbench panels during migration |

Feature packages must not receive `WindowBase` or `WindowHost`. If a feature needs something from the shell, add a narrow dependency port and pass exactly that service.

## Current Slice

| Area | Change |
| --- | --- |
| Foundation | `ScrollableSupport` moved to `application.gui.foundation.layout` |
| Platform | Added clipboard, dialog, notification, UI-executor, and background-executor contracts |
| Swing platform | Added Swing implementations for clipboard, dialogs, UI executor, and background executor |
| Feature | Added `application.gui.feature.publishing` |
| Feature | Added `application.gui.feature.dataset` |
| Dataset | Added `DatasetView`, `DatasetDependencies`, and `DatasetController` |
| Publishing | Added `PublishingView`, `PublishingDependencies`, and `PublishingController` |
| Report | Added `ReportView`, `ReportDependencies`, and `ReportController` |
| Composition | Added `LegacyWorkbenchComposition.datasetView(...)` |
| Composition | Added `LegacyWorkbenchComposition.publishingView(...)` |
| Composition | Added `LegacyWorkbenchComposition.reportView(...)` |
| Shell | `WindowBase` now stores `DatasetView` and creates Dataset through the composition helper |
| Shell | `WindowBase` now stores `PublishingView` and creates Publishing through the composition helper |
| Shell | `WindowBase` now stores the main report as `ReportView` and creates report views through the composition helper |
| Gallery | Added `testing.GuiComponentGallery` for headless light/dark shared-control rendering |
| Regression | Added `testing.GuiArchitectureRegressionTest`, `testing.DatasetFeatureRegressionTest`, `testing.ReportFeatureRegressionTest`, and `testing.PublishingFeatureRegressionTest` |

## Publishing Pilot

Publishing is the first vertical feature seam because it already had a host contract and a coherent workflow. The migration keeps `PublishingPanel` as the concrete Swing implementation, but the Workbench shell now depends on `PublishingView`.

The new dependency bundle names the services Publishing actually uses: owner component, current FEN, game model, batch input, report service, command runner, clipboard, command control, notifications, and dialogs. `PublishingController` adapts those ports to the existing `PublishingPanel.Host`, so the panel can move gradually instead of being rewritten at once.

Remaining compatibility debt is intentional and smaller than before. `PublishingPanel` implements `PublishingView`, `ReportPanel` implements `ReportView`, and both `WindowPublishingHost` and `WindowReportHost` have been removed. The next safe slice is to start a second vertical feature seam.

## Dataset Seam

Dataset is the second vertical feature seam because its shell needs are narrow: it only needs callbacks to open a selected FEN in the shared Board tab or a detached Board tab. The migration keeps `DatasetPanel` as the concrete Swing implementation, but the Workbench shell now depends on `DatasetView`.

`DatasetController` creates the legacy panel from `DatasetDependencies`, and `WindowBase` now routes Dataset construction through `LegacyWorkbenchComposition.datasetView(...)`. The command palette still calls `analyzeCurrentSource()`, but it does so through the feature view contract.

## Architecture Baseline

`testdata/gui/architecture-baseline.tsv` records current GUI debt for direct Swing construction and local decorating calls. The baseline exists because the old Workbench has a lot of direct Swing code. The rule is not "fix all of it now"; the rule is "do not grow it accidentally."

| Rule | Meaning |
| --- | --- |
| `raw-swing-construction` | Direct construction of common Swing controls outside shared factories |
| `direct-color-construction` | Direct `new Color(...)` usage |
| `direct-set-font` | Direct `setFont(...)` usage |
| `direct-set-border` | Direct `setBorder(...)` usage |
| `ui-manager-access` | Direct `UIManager` access |
| `window-base-field` | Non-static field count in `WindowBase` |

When a cleanup reduces a count, regenerate the baseline with:

```bash
java -cp out testing.GuiArchitectureRegressionTest --write-baseline
```

Do this only after reviewing the diff. A baseline increase should be rare and explained in the commit or report.

## Regression Rules

`testing.GuiArchitectureRegressionTest` enforces these constraints:

- GUI packages with Java source must include `package-info.java`.
- GUI package declarations must match source paths.
- Foundation code must not import app, feature, Workbench, CLI, or chess packages.
- Feature packages must not import sibling feature implementations.
- Feature packages must not depend on `WindowBase` or `WindowHost`.
- The legacy `Window*Layer` inheritance chain and `WindowHost` adapter set must not grow outside the allowlist.
- `WindowBase` must create Dataset through `LegacyWorkbenchComposition.datasetView(...)`.
- `WindowBase` must create Publishing through `LegacyWorkbenchComposition.publishingView(...)`.
- `WindowPublishingHost` must stay removed.
- `WindowBase` must create reports through `LegacyWorkbenchComposition.reportView(...)`.
- `WindowReportHost` must stay removed.
- Baseline-tracked Swing construction and decorating debt must not increase.

`testing.PublishingFeatureRegressionTest` verifies the Publishing compatibility adapter delegates through the narrow feature dependencies.

`testing.DatasetFeatureRegressionTest` verifies the Dataset controller creates the current panel behind the feature view contract and validates its dependency bundle.

`testing.ReportFeatureRegressionTest` verifies the report compatibility adapter delegates through the narrow feature dependencies.

`testing.GuiComponentGallery` verifies representative shared controls paint in light and dark modes under headless Swing.

## Migration Rules

| Rule | Rationale |
| --- | --- |
| Move one vertical feature at a time | Big-bang GUI rewrites are too risky in this codebase |
| Keep concrete Swing panels working while adding view contracts | This preserves behavior and avoids visual churn |
| Prefer narrow dependency records over host inheritance | Tests can fake records without constructing a frame |
| Move pure helpers to foundation only when they have no Workbench dependency | Foundation must stay reusable and low-level |
| Keep `workbench.ui` as the design-system boundary | Feature packages should use `Ui`, `Theme`, and public primitives, not package-private helpers |
| Update architecture tests before adding exceptions | The tests are the long-term design contract |

## Migration Ledger

| Date | Slice | Result |
| --- | --- | --- |
| 2026-06-25 | Initial architecture slice | Added foundation, platform, feature, app packages; moved `ScrollableSupport`; created Publishing seam; added architecture baseline, component gallery, and focused regressions |
| 2026-06-25 | Report seam | Added `ReportView`, `ReportDependencies`, and `ReportController`; routed report creation through the composition helper; removed `WindowPublishingHost` and `WindowReportHost`; added report seam regression |
| 2026-06-25 | Dataset seam | Added `DatasetView`, `DatasetDependencies`, and `DatasetController`; routed Dataset creation through the composition helper; added dataset seam regression |

## Next Steps

| Priority | Work |
| --- | --- |
| 1 | Add a Play feature seam after Dataset and Publishing are stable |
| 2 | Move feature state out of `WindowBase` where a feature package can own it |
| 3 | Reduce baseline debt counts as shared factories absorb direct Swing styling |
