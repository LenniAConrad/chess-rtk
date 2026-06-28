/**
 * Reusable Swing controls, theme primitives, icons, layout helpers, and visual
 * utility classes for the Workbench.
 *
 * <p>This package is the Workbench design-system boundary. Feature packages
 * should use the stable public facade ({@code Ui}, {@code Theme}) and reusable
 * public primitives such as {@code SurfacePanel}, {@code WorkspaceHeader},
 * {@code StatusBadge}, {@code ToggleBox}, and {@code SegmentedSwitcher}.
 * Styling internals, Swing UI delegates, palette installers, input chrome, and
 * helper factories stay package-private so feature panels cannot couple to
 * implementation details.</p>
 *
 * <p>The package owns generic Swing presentation only: colors, typography,
 * spacing, borders, surfaces, controls, scroll/table/tree styling, dialogs,
 * toasts, and generic rendering helpers. It must not own chess rules, board
 * state, engine state, session workflows, or feature-specific models.</p>
 */
package application.gui.workbench.ui;
