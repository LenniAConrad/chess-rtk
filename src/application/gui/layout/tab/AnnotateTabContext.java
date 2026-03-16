package application.gui.layout.tab;

import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import application.gui.ui.RoundedPanel;

/**
 * AnnotateTabContext interface.
 *
 * Provides interface behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public interface AnnotateTabContext {
	/**
	 * createMutedLabel method.
	 *
	 * @param text parameter.
	 * @return return value.
	 */
	JLabel createMutedLabel(String text);
	/**
	 * createThemedButton method.
	 *
	 * @param text parameter.
	 * @param action parameter.
	 * @return return value.
	 */
	JButton createThemedButton(String text, ActionListener action);
	/**
	 * registerTextArea method.
	 *
	 * @param area parameter.
	 */
	void registerTextArea(JTextArea area);
	/**
	 * registerScrollPane method.
	 *
	 * @param scroll parameter.
	 */
	void registerScrollPane(JScrollPane scroll);
	/**
	 * registerFlatCard method.
	 *
	 * @param card parameter.
	 */
	void registerFlatCard(RoundedPanel card);
	/**
	 * registerButton method.
	 *
	 * @param button parameter.
	 */
	void registerButton(JButton button);
	/**
	 * addNagButton method.
	 *
	 * @param container parameter.
	 * @param label parameter.
	 * @param nag parameter.
	 */
	void addNagButton(JPanel container, String label, int nag);
}
