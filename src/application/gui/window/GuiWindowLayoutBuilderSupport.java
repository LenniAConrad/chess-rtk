package application.gui.window;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import application.gui.layout.command.CommandCenterContext;
import application.gui.layout.tab.AnnotateTabContext;
import application.gui.layout.tab.ReportTabContext;
import application.gui.layout.tab.VariationTabContext;
import application.gui.ui.RoundedPanel;

/**
 * Shared UI-builder bridge for the layout-owned tab/card builders.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class GuiWindowLayoutBuilderSupport
		implements CommandCenterContext, AnnotateTabContext, ReportTabContext, VariationTabContext {

	/**
	 * Owning window layout.
	 */
	private final GuiWindowLayout owner;

	/**
	 * Creates the builder bridge for one layout instance.
	 *
	 * @param owner owning layout
	 */
	GuiWindowLayoutBuilderSupport(GuiWindowLayout owner) {
		this.owner = owner;
	}

	/**
	 * Creates muted label text using the owning layout theme.
	 *
	 * @param text label text
	 * @return themed muted label
	 */
	@Override
	public JLabel createMutedLabel(String text) {
		return owner.builderMutedLabel(text);
	}

	/**
	 * Creates a themed button using the owning layout theme.
	 *
	 * @param text button text
	 * @param action click listener
	 * @return themed button
	 */
	@Override
	public JButton createThemedButton(String text, ActionListener action) {
		return owner.builderThemedButton(text, action);
	}

	/**
	 * Creates muted label text for variation builders.
	 *
	 * @param text label text
	 * @return themed muted label
	 */
	@Override
	public JLabel mutedLabel(String text) {
		return owner.builderMutedLabel(text);
	}

	/**
	 * Creates a themed button for variation builders.
	 *
	 * @param text button text
	 * @param action click listener
	 * @return themed button
	 */
	@Override
	public JButton themedButton(String text, ActionListener action) {
		return owner.builderThemedButton(text, action);
	}

	/**
	 * Creates a themed checkbox using the owning layout theme.
	 *
	 * @param text checkbox text
	 * @param selected initial selected state
	 * @param action change listener
	 * @return themed checkbox
	 */
	@Override
	public JCheckBox createThemedCheckbox(String text, boolean selected, ActionListener action) {
		return owner.builderThemedCheckbox(text, selected, action);
	}

	/**
	 * Builds a flat card with a visible title.
	 *
	 * @param title card title
	 * @return themed flat card
	 */
	@Override
	public RoundedPanel buildFlatCard(String title) {
		return owner.builderFlatCard(title);
	}

	/**
	 * Scales a dimension according to the owning layout scale.
	 *
	 * @param base unscaled dimension
	 * @return scaled dimension
	 */
	@Override
	public Dimension scaledDimension(Dimension base) {
		return owner.builderScaledDimension(base);
	}

	/**
	 * Scales a row height according to the owning layout scale.
	 *
	 * @param base unscaled row height
	 * @return scaled row height
	 */
	@Override
	public int scaledRowHeight(int base) {
		return owner.builderScaledRowHeight(base);
	}

	/**
	 * Registers a flat card for later theme updates.
	 *
	 * @param card card to register
	 */
	@Override
	public void registerFlatCard(RoundedPanel card) {
		owner.builderRegisterFlatCard(card);
	}

	/**
	 * Registers a combo box for later theme updates.
	 *
	 * @param combo combo box to register
	 */
	@Override
	public void registerComboBox(JComboBox<?> combo) {
		owner.builderRegisterComboBox(combo);
	}

	/**
	 * Registers a text field for later theme updates.
	 *
	 * @param field text field to register
	 */
	@Override
	public void registerTextField(JTextField field) {
		owner.builderRegisterTextField(field);
	}

	/**
	 * Registers a text area for later theme updates.
	 *
	 * @param area text area to register
	 */
	@Override
	public void registerTextArea(JTextArea area) {
		owner.builderRegisterTextArea(area);
	}

	/**
	 * Registers a list for later theme updates.
	 *
	 * @param list list to register
	 */
	@Override
	public void registerList(JList<?> list) {
		owner.builderRegisterList(list);
	}

	/**
	 * Registers a scroll pane for later theme updates.
	 *
	 * @param scroll scroll pane to register
	 */
	@Override
	public void registerScrollPane(JScrollPane scroll) {
		owner.builderRegisterScrollPane(scroll);
	}

	/**
	 * Registers a button for later theme updates.
	 *
	 * @param button button to register
	 */
	@Override
	public void registerButton(JButton button) {
		owner.builderRegisterButton(button);
	}

	/**
	 * Builds a flat card and controls whether the title is shown.
	 *
	 * @param title card title
	 * @param showTitle true to show the title in the card
	 * @return themed flat card
	 */
	@Override
	public RoundedPanel createFlatCard(String title, boolean showTitle) {
		return owner.builderFlatCard(title, showTitle);
	}

	/**
	 * Registers a table for later theme updates.
	 *
	 * @param table table to register
	 */
	@Override
	public void registerTable(JTable table) {
		owner.builderRegisterTable(table);
	}

	/**
	 * Shows a PGN-node preview near a screen point.
	 *
	 * @param node node to preview
	 * @param screenPoint screen point near the hover target
	 */
	@Override
	public void previewNode(PgnNode node, Point screenPoint) {
		owner.builderPreviewNode(node, screenPoint);
	}

	/**
	 * Clears any currently visible hover previews.
	 */
	@Override
	public void clearHoverPreviews() {
		owner.builderClearHoverPreviews();
	}

	/**
	 * Applies a PGN node selection in the owning layout.
	 *
	 * @param node node to apply
	 */
	@Override
	public void applyPgnNode(PgnNode node) {
		owner.builderApplyPgnNode(node);
	}

	/**
	 * Requests that the command form toggle FEN input mode.
	 */
	@Override
	public void requestFenToggle() {
		owner.builderRequestFenToggle();
	}

	/**
	 * Requests execution of the current command form.
	 */
	@Override
	public void requestCommandRun() {
		owner.builderRequestCommandRun();
	}

	/**
	 * Requests cancellation of the current command form process.
	 */
	@Override
	public void requestCommandStop() {
		owner.builderRequestCommandStop();
	}

	/**
	 * Requests contextual command-form help.
	 */
	@Override
	public void requestCommandHelp() {
		owner.builderRequestCommandHelp();
	}

	/**
	 * Requests one recent command by index.
	 *
	 * @param index recent-command index
	 */
	@Override
	public void requestRecentCommand(int index) {
		owner.builderRequestRecentCommand(index);
	}

	/**
	 * Requests a command-form refresh after input changes.
	 */
	@Override
	public void requestCommandFormUpdate() {
		owner.builderRequestCommandFormUpdate();
	}

	/**
	 * Adds a NAG shortcut button to a container.
	 *
	 * @param container destination container
	 * @param label button label
	 * @param nag numeric annotation glyph value
	 */
	@Override
	public void addNagButton(JPanel container, String label, int nag) {
		owner.builderAddNagButton(container, label, nag);
	}
}
