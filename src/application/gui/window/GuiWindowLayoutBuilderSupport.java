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

	@Override
	public JLabel createMutedLabel(String text) {
		return owner.builderMutedLabel(text);
	}

	@Override
	public JButton createThemedButton(String text, ActionListener action) {
		return owner.builderThemedButton(text, action);
	}

	@Override
	public JLabel mutedLabel(String text) {
		return owner.builderMutedLabel(text);
	}

	@Override
	public JButton themedButton(String text, ActionListener action) {
		return owner.builderThemedButton(text, action);
	}

	@Override
	public JCheckBox createThemedCheckbox(String text, boolean selected, ActionListener action) {
		return owner.builderThemedCheckbox(text, selected, action);
	}

	@Override
	public RoundedPanel buildFlatCard(String title) {
		return owner.builderFlatCard(title);
	}

	@Override
	public Dimension scaledDimension(Dimension base) {
		return owner.builderScaledDimension(base);
	}

	@Override
	public int scaledRowHeight(int base) {
		return owner.builderScaledRowHeight(base);
	}

	@Override
	public void registerFlatCard(RoundedPanel card) {
		owner.builderRegisterFlatCard(card);
	}

	@Override
	public void registerComboBox(JComboBox<?> combo) {
		owner.builderRegisterComboBox(combo);
	}

	@Override
	public void registerTextField(JTextField field) {
		owner.builderRegisterTextField(field);
	}

	@Override
	public void registerTextArea(JTextArea area) {
		owner.builderRegisterTextArea(area);
	}

	@Override
	public void registerList(JList<?> list) {
		owner.builderRegisterList(list);
	}

	@Override
	public void registerScrollPane(JScrollPane scroll) {
		owner.builderRegisterScrollPane(scroll);
	}

	@Override
	public void registerButton(JButton button) {
		owner.builderRegisterButton(button);
	}

	@Override
	public RoundedPanel createFlatCard(String title, boolean showTitle) {
		return owner.builderFlatCard(title, showTitle);
	}

	@Override
	public void registerTable(JTable table) {
		owner.builderRegisterTable(table);
	}

	@Override
	public void previewNode(PgnNode node, Point screenPoint) {
		owner.builderPreviewNode(node, screenPoint);
	}

	@Override
	public void clearHoverPreviews() {
		owner.builderClearHoverPreviews();
	}

	@Override
	public void applyPgnNode(PgnNode node) {
		owner.builderApplyPgnNode(node);
	}

	@Override
	public void requestFenToggle() {
		owner.builderRequestFenToggle();
	}

	@Override
	public void requestCommandRun() {
		owner.builderRequestCommandRun();
	}

	@Override
	public void requestCommandStop() {
		owner.builderRequestCommandStop();
	}

	@Override
	public void requestCommandHelp() {
		owner.builderRequestCommandHelp();
	}

	@Override
	public void requestRecentCommand(int index) {
		owner.builderRequestRecentCommand(index);
	}

	@Override
	public void requestCommandFormUpdate() {
		owner.builderRequestCommandFormUpdate();
	}

	@Override
	public void addNagButton(JPanel container, String label, int nag) {
		owner.builderAddNagButton(container, label, nag);
	}
}
