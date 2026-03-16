package application.gui.history.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.awt.Component;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import chess.struct.Game;

/**
 * Conveniences for the PGN/FEN file dialogs used by the GUI.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class HistoryFileDialogSupport {

	/**
	 * HistoryFileDialogSupport method.
	 */
	private HistoryFileDialogSupport() {
	}

	/**
	 * chooseFenListFile method.
	 *
	 * @param owner parameter.
	 * @return return value.
	 */
	public static Path chooseFenListFile(Component owner) {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("FEN lists", "txt", "fen", "csv", "pgn"));
		int res = chooser.showOpenDialog(owner);
		return res == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
	}

	/**
	 * choosePgnFile method.
	 *
	 * @param owner parameter.
	 * @return return value.
	 */
	public static Path choosePgnFile(Component owner) {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PGN files", "pgn"));
		int res = chooser.showOpenDialog(owner);
		return res == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
	}

	/**
	 * showError method.
	 *
	 * @param owner parameter.
	 * @param title parameter.
	 * @param message parameter.
	 */
	public static void showError(Component owner, String title, String message) {
		JOptionPane.showMessageDialog(owner, message, title, JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * showWarning method.
	 *
	 * @param owner parameter.
	 * @param title parameter.
	 * @param message parameter.
	 */
	public static void showWarning(Component owner, String title, String message) {
		JOptionPane.showMessageDialog(owner, message, title, JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * selectPgnGame method.
	 *
	 * @param owner parameter.
	 * @param games parameter.
	 * @return return value.
	 */
	public static Game selectPgnGame(Component owner, List<Game> games) {
		if (games == null || games.isEmpty()) {
			return null;
		}
		JComboBox<Game> combo = new JComboBox<>();
		for (Game game : games) {
			combo.addItem(game);
		}
		JPanel panel = new JPanel();
		panel.add(new JScrollPane(combo));
		int res = JOptionPane.showConfirmDialog(owner, combo, "Select PGN game", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (res != JOptionPane.OK_OPTION) {
			return null;
		}
		int idx = combo.getSelectedIndex();
		if (idx < 0 || idx >= games.size()) {
			return games.get(0);
		}
		return games.get(idx);
	}
}
