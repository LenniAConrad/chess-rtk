/**
 * Native Swing workbench shell and top-level orchestration.
 *
 * <p>The root package keeps the frame, shared session state, and view assembly.
 * Focused feature clusters should move into subpackages as they are untangled:
 * launcher entry points under {@code launch}, publishing under
 * {@code publish}, network visualisation under {@code network}, reusable Swing
 * controls under {@code ui}, and tab/split layout under {@code layout}.</p>
 */
package application.gui.workbench;
