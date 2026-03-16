package application.gui.model;

import javax.swing.JLabel;

/**
 * Tab label bundle for custom tab rendering.
 *
 * Couples the tab index with icon and text components so the tab header renderer can layout each label consistently.
 *
 * @param index tab index tracked by the renderer.
 * @param icon icon label component.
 * @param text text label component.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record TabLabel(int index, JLabel icon, JLabel text) {
}
