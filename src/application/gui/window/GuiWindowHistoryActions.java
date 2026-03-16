package application.gui.window;
import application.Config;
import application.cli.Format;
import application.cli.command.CommandSupport;
import application.gui.model.CommandFieldBinding;
import application.gui.model.CommandFieldSpec;
import application.gui.model.CommandFieldType;
import application.gui.model.CommandSpec;
import application.gui.model.EngineCacheEntry;
import application.gui.model.EngineUpdate;
import application.gui.model.HistoryEntry;
import application.gui.model.MovePair;
import application.gui.model.PaletteCommand;
import application.gui.model.PgnMainline;
import application.gui.model.PvEntry;
import application.gui.model.RecentCommand;
import application.gui.model.ReportEntry;
import application.gui.model.ReportUpdate;
import application.gui.model.TabLabel;
import application.gui.history.AblationSupport;
import application.gui.history.FigurineSanFormatter;
import application.gui.history.PvTextFormatter;
import application.gui.history.variation.HistorySelectionSupport;
import application.gui.history.variation.VariationTreeSupport;
import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;
import application.gui.ui.FocusBorder;
import application.gui.ui.ThemedScrollBarUI;
import application.gui.ui.ThemedSliderUI;
import application.gui.ui.ThemedSplitPaneUI;
import application.gui.ui.ThemedTabbedPaneUI;
import application.gui.ui.ThemedComboBoxUI;
import application.gui.ui.ThemedToggleIcon;
import application.gui.util.TransferableImage;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.AlphaComposite;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.awt.KeyboardFocusManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JTabbedPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.TransferHandler;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import javax.swing.border.Border;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.eval.Evaluator;
import chess.eco.Encyclopedia;
import chess.eco.Entry;
import chess.images.assets.Pictures;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.Tagging;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Engine;
import chess.uci.Output;
import chess.uci.Protocol;

/**
 * GuiWindowHistoryActions class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
abstract class GuiWindowHistoryActions extends GuiWindowHistoryAnalysis {

			/**
			 * refreshEcoCurrent method.
			 */
			protected void refreshEcoCurrent() {
				if (ecoCurrentLabel == null) {
					return;
				}
				if (!ensureEcoBook(false)) {
					ecoCurrentLabel.setText("Current: (ECO book missing)");
					return;
				}
				if (ecoTableModel != null && ecoTableModel.getRowCount() == 0) {
					filterEcoList();
				}
				Entry entry = ecoBook.getNode(position);
				if (entry == null) {
					ecoCurrentLabel.setText("Current: (unknown)");
				} else {
					ecoCurrentLabel.setText("Current: " + entry.getECO() + " — " + entry.getName());
				}
			}

			/**
			 * ensureEcoBook method.
			 * @param showErrors parameter.
			 * @return return value.
			 */
			protected boolean ensureEcoBook(boolean showErrors) {
				if (ecoBook != null) {
					return true;
				}
				try {
					ecoBook = Encyclopedia.defaultBook();
					ecoEntries = ecoBook.entries();
					if (ecoStatusLabel != null) {
						ecoStatusLabel.setText("ECO book loaded (" + ecoEntries.size() + " entries)");
					}
					return true;
				} catch (Exception ex) {
					if (ecoStatusLabel != null) {
						ecoStatusLabel.setText("ECO book not available");
					}
					if (showErrors) {
						recordProblem("ECO", "Failed to load ECO book: " + ex.getMessage());
						JOptionPane.showMessageDialog(frame,
								"Failed to load ECO book: " + ex.getMessage(),
								"ECO", JOptionPane.ERROR_MESSAGE);
					}
					return false;
				}
			}

			/**
			 * filterEcoList method.
			 */
			protected void filterEcoList() {
				if (ecoTableModel == null && ecoListModel == null) {
					return;
				}
				if (!ensureEcoBook(false)) {
					if (ecoTableModel != null) {
						ecoTableModel.setRowCount(0);
					}
					if (ecoListModel != null) {
						ecoListModel.clear();
					}
					ecoFilteredEntries.clear();
					return;
				}
				String query = ecoSearchField != null ? ecoSearchField.getText().trim().toLowerCase() : "";
				if (ecoTableModel != null) {
					ecoTableModel.setRowCount(0);
				}
				if (ecoListModel != null) {
					ecoListModel.clear();
				}
				ecoFilteredEntries.clear();
				int limit = 10000;
				int added = 0;
				for (Entry entry : ecoEntries) {
					if (!query.isBlank()) {
						String eco = entry.getECO().toLowerCase();
						String name = entry.getName().toLowerCase();
						String moves = entry.getMovetext().toLowerCase();
						if (!eco.contains(query) && !name.contains(query) && !moves.contains(query)) {
							continue;
						}
					}
					if (ecoTableModel != null) {
						ecoTableModel.addRow(new Object[] { entry.getECO(), entry.getName(), entry.getMovetext() });
					}
					if (ecoListModel != null) {
						ecoListModel.addElement(entry);
					}
					ecoFilteredEntries.add(entry);
					added++;
					if (added >= limit) {
						break;
					}
				}
				if (ecoStatusLabel != null) {
					if (query.isBlank()) {
						ecoStatusLabel.setText("ECO book loaded (" + ecoEntries.size()
								+ " entries). Showing first " + added + ".");
					} else {
						ecoStatusLabel.setText("ECO book loaded (" + ecoEntries.size()
								+ " entries). Showing " + added + " matches.");
					}
				}
			}

			/**
			 * updateEcoDetails method.
			 */
			protected void updateEcoDetails() {
				if (ecoDetailArea == null) {
					return;
				}
				if (ecoSelected == null) {
					ecoDetailArea.setText("Select an opening to see details.");
					return;
				}
				StringBuilder sb = new StringBuilder();
				sb.append(ecoSelected.getECO()).append(" — ").append(ecoSelected.getName()).append("\n");
				sb.append("Moves: ").append(ecoSelected.getMovetext());
				ecoDetailArea.setText(sb.toString());
				ecoDetailArea.setCaretPosition(0);
			}

			/**
			 * applyEcoEntry method.
			 * @param entry parameter.
			 */
			protected void applyEcoEntry(Entry entry) {
				if (entry == null) {
					return;
				}
				try {
					Position start = Setup.getStandardStartPosition();
					if (start == null) {
						throw new IllegalStateException("Start position unavailable");
					}
					short[] moves = entry.getMoves();
					PgnNode root = new PgnNode(null, null, start.copyOf(), Move.NO_MOVE, 0);
					PgnNode cursor = root;
					Position current = start.copyOf();
					if (moves != null) {
						for (short move : moves) {
							if (move == Move.NO_MOVE) {
								continue;
							}
							if (!current.isLegalMove(move)) {
								throw new IllegalArgumentException("Illegal ECO move: " + Move.toString(move));
							}
							String san = Format.safeSan(current, move);
							Position after = current.copyOf().play(move);
							PgnNode next = new PgnNode(san, cursor, after.copyOf(), move, cursor.ply + 1);
							cursor.mainNext = next;
							cursor = next;
							current = after;
						}
					}
					pgnNavigator = new PgnNavigator(root);
					applyPgnNode(cursor);
				} catch (Exception ex) {
					String message = ex.getMessage();
					if (message == null || message.isBlank()) {
						message = ex.getClass().getSimpleName();
					}
					recordProblem("ECO", "Failed to apply ECO line: " + message);
					JOptionPane.showMessageDialog(frame,
							"Failed to apply ECO line: " + message,
							"ECO", JOptionPane.ERROR_MESSAGE);
				}
			}

			/**
			 * copyEcoMovetext method.
			 */
			protected void copyEcoMovetext() {
				if (ecoSelected == null) {
					return;
				}
				String movetext = ecoSelected.getMovetext();
				ecoDetailArea.setText(ecoDetailArea.getText());
				copyText(movetext);
			}

			/**
			 * copyText method.
			 * @param text parameter.
			 */
			protected void copyText(String text) {
				if (text == null) {
					return;
				}
				try {
					StringSelection selection = new StringSelection(text);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, null);
				} catch (Exception ex) {
					recordProblem("Copy error", "Failed to copy text: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Failed to copy text: " + ex.getMessage(), "Copy error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

			/**
			 * maybeShowHistoryMenu method.
			 * @param list parameter.
			 * @param e parameter.
			 */
			protected void maybeShowHistoryMenu(JList<HistoryEntry> list, MouseEvent e) {
				if (historyMenu == null || list == null || !e.isPopupTrigger()) {
					return;
				}
				int idx = list.locationToIndex(e.getPoint());
				if (idx >= 0) {
					list.setSelectedIndex(idx);
					configureHistoryMenu(list.getSelectedValue());
					historyMenu.show(list, e.getX(), e.getY());
				}
			}

			/**
			 * maybeShowUnderboardHistoryMenu method.
			 * @param e parameter.
			 */
			protected void maybeShowUnderboardHistoryMenu(MouseEvent e) {
				if (historyMenu == null || underboardMoveList == null || !e.isPopupTrigger()) {
					return;
				}
				int idx = historyIndexAtPoint(underboardMoveList, e.getPoint());
				if (idx >= 0) {
					PgnNode node = nodeAtHistoryIndex(idx);
					selectHistoryNode(node);
					configureHistoryMenu(historyList != null ? historyList.getSelectedValue() : null);
					historyMenu.show(underboardMoveList, e.getX(), e.getY());
				}
			}

			/**
			 * selectHistoryNode method.
			 * @param node parameter.
			 */
			protected void selectHistoryNode(PgnNode node) {
				HistorySelectionSupport.selectHistoryNode(historyListModel, historyList, node);
			}

			/**
			 * historyIndexAtPoint method.
			 * @param list parameter.
			 * @param point parameter.
			 * @return return value.
			 */
			protected int historyIndexAtPoint(JList<MovePair> list, Point point) {
				return HistorySelectionSupport.historyIndexAtPoint(list, underboardMoveModel, point, uiScale);
			}

			/**
			 * nodeAtHistoryIndex method.
			 * @param idx parameter.
			 * @return return value.
			 */
			protected PgnNode nodeAtHistoryIndex(int idx) {
				return HistorySelectionSupport.nodeAtHistoryIndex(idx, currentLineNodes);
			}

			/**
			 * previewPositionAtHistoryIndex method.
			 * @param idx parameter.
			 * @return return value.
			 */
			protected Position previewPositionAtHistoryIndex(int idx) {
				return HistorySelectionSupport.previewPositionAtHistoryIndex(idx, currentLineNodes, history, moveHistory);
			}

			/**
			 * jumpToSelectedHistory method.
			 */
			protected void jumpToSelectedHistory() {
				if (historyList == null) {
					return;
				}
				HistoryEntry entry = historyList.getSelectedValue();
				if (entry == null || entry.node() == null) {
					return;
				}
				applyPgnNode(entry.node());
			}

			/**
			 * copyHistorySan method.
			 */
			protected void copyHistorySan() {
				if (historyList == null) {
					return;
				}
				HistoryEntry entry = historyList.getSelectedValue();
				if (entry == null || entry.node() == null) {
					return;
				}
				copyText(entry.san());
			}

			/**
			 * copyHistoryUci method.
			 */
			protected void copyHistoryUci() {
				if (historyList == null) {
					return;
				}
				HistoryEntry entry = historyList.getSelectedValue();
				if (entry == null || entry.node() == null) {
					return;
				}
				short move = entry.node().move;
				if (move == Move.NO_MOVE) {
					return;
				}
				copyText(Move.toString(move));
			}

			/**
			 * configureHistoryMenu method.
			 * @param entry parameter.
			 */
			protected void configureHistoryMenu(HistoryEntry entry) {
				PgnNode node = entry != null ? entry.node() : null;
				VariationTreeSupport.VariationUiState state = VariationTreeSupport.uiState(node);
				if (historyJumpItem != null) {
					historyJumpItem.setEnabled(state.hasNode());
				}
				if (historyCopySanItem != null) {
					historyCopySanItem.setEnabled(state.hasNode());
				}
				if (historyCopyUciItem != null) {
					historyCopyUciItem.setEnabled(state.hasNode());
				}
				if (historyPromoteItem != null) {
					historyPromoteItem.setEnabled(state.isVariation());
				}
				if (historyDeleteItem != null) {
					historyDeleteItem.setEnabled(state.hasNode());
				}
				if (historyVarUpItem != null) {
					historyVarUpItem.setEnabled(state.canUp());
				}
				if (historyVarDownItem != null) {
					historyVarDownItem.setEnabled(state.canDown());
				}
				if (historyCommentItem != null) {
					historyCommentItem.setEnabled(state.hasNode());
				}
				if (historyClearCommentItem != null) {
					historyClearCommentItem.setEnabled(state.hasComment());
				}
				if (historyClearNagItem != null) {
					historyClearNagItem.setEnabled(state.hasNag());
				}
				updateVariationButtons();
			}

			/**
			 * updateVariationButtons method.
			 */
			protected void updateVariationButtons() {
				PgnNode node = selectedHistoryNode();
				VariationTreeSupport.VariationUiState state = VariationTreeSupport.uiState(node);
				if (historyDeleteButton != null) {
					historyDeleteButton.setEnabled(state.hasNode());
				}
				if (historyPromoteButton != null) {
					historyPromoteButton.setEnabled(state.isVariation());
				}
				if (historyVarUpButton != null) {
					historyVarUpButton.setEnabled(state.canUp());
				}
				if (historyVarDownButton != null) {
					historyVarDownButton.setEnabled(state.canDown());
				}
			}

			/**
			 * selectedHistoryNode method.
			 * @return return value.
			 */
			protected PgnNode selectedHistoryNode() {
				if (historyList == null || historyList.getSelectedValue() == null) {
					return null;
				}
				return historyList.getSelectedValue().node();
			}

			/**
			 * promoteSelectedVariation method.
			 */
			protected void promoteSelectedVariation() {
				PgnNode node = selectedHistoryNode();
				if (!VariationTreeSupport.promoteToMainline(node)) {
					return;
				}
				applyPgnNode(node);
			}

			/**
			 * deleteSelectedVariation method.
			 */
			protected void deleteSelectedVariation() {
				PgnNode node = selectedHistoryNode();
				VariationTreeSupport.DeleteResult result = VariationTreeSupport.deleteVariationNode(node,
						pgnNavigator != null ? pgnNavigator.current : null);
				if (!result.changed()) {
					return;
				}
				if (result.currentWasInsideDeletedBranch() && result.fallbackNode() != null) {
					applyPgnNode(result.fallbackNode());
				} else {
					refreshAll();
				}
			}

			/**
			 * moveSelectedVariation method.
			 * @param delta parameter.
			 */
			protected void moveSelectedVariation(int delta) {
				PgnNode node = selectedHistoryNode();
				if (!VariationTreeSupport.moveVariationSibling(node, delta)) {
					return;
				}
				refreshHistory();
				updateVariationButtons();
			}

			/**
			 * isAncestor method.
			 * @param ancestor parameter.
			 * @param node parameter.
			 * @return return value.
			 */
			protected boolean isAncestor(PgnNode ancestor, PgnNode node) {
				return VariationTreeSupport.isAncestor(ancestor, node);
			}

			/**
			 * editSelectedComment method.
			 */
			protected void editSelectedComment() {
				PgnNode node = selectedHistoryNode();
				if (node == null) {
					return;
				}
				String initial = node.comment == null ? "" : node.comment;
				String text = JOptionPane.showInputDialog(frame, "Move comment", initial);
				if (text == null) {
					return;
				}
				String trimmed = text.trim();
				node.comment = trimmed.isEmpty() ? null : trimmed;
				refreshHistory();
			}

			/**
			 * clearSelectedComment method.
			 */
			protected void clearSelectedComment() {
				PgnNode node = selectedHistoryNode();
				if (node == null) {
					return;
				}
				node.comment = null;
				refreshHistory();
			}

			/**
			 * clearSelectedNag method.
			 */
			protected void clearSelectedNag() {
				PgnNode node = selectedHistoryNode();
				if (node == null) {
					return;
				}
				node.nag = 0;
				refreshHistory();
			}

			/**
			 * createNagItem method.
			 * @param label parameter.
			 * @param nag parameter.
			 * @return return value.
			 */
			protected JMenuItem createNagItem(String label, int nag) {
				JMenuItem item = new JMenuItem(label);
				item.addActionListener(e -> {
					PgnNode node = selectedHistoryNode();
					if (node == null) {
						return;
					}
					node.nag = nag;
					refreshHistory();
				});
				return item;
			}

			/**
			 * resolveHistoryMove method.
			 * @param idx parameter.
			 * @return return value.
			 */
			protected short resolveHistoryMove(int idx) {
				return HistorySelectionSupport.resolveHistoryMove(idx, currentLineNodes, moveHistory, history);
			}

			/**
			 * jumpToHistoryIndex method.
			 * @param idx parameter.
			 */
			protected void jumpToHistoryIndex(int idx) {
				PgnNode node = nodeAtHistoryIndex(idx);
				if (node == null) {
					return;
				}
				applyPgnNode(node);
			}

			/**
			 * bumpPosition method.
			 */
			protected void bumpPosition() {
				positionVersion++;
				engineBestMove = Move.NO_MOVE;
				if (engineBestArea != null) {
					engineBestArea.setText("");
				}
				if (boardBestButton != null) {
					boardBestButton.setEnabled(false);
				}
				if (boardBestIconButton != null) {
					boardBestIconButton.setEnabled(false);
				}
				engineLockPv = false;
				lockedBestMoves = null;
				lockedBestMove = Move.NO_MOVE;
				if (engineLockPvToggle != null) {
					engineLockPvToggle.setSelected(false);
				}
				updateEngineStats(null);
				if (evalBar != null) {
					if (engineWorker != null) {
						evalBar.markPending();
					} else {
						evalBar.clear();
					}
				}
				if (engineWorker == null) {
					setEvalBarVisible(true);
				}
				latestAnalysis = null;
				latestAnalysisVersion = -1;
				clearHoverPreviews();
			}

			/**
			 * loadFenFromField method.
			 */
			protected void loadFenFromField() {
				String text = fenField.getText().trim();
				if (text.isEmpty()) {
					return;
				}
				applyFen(text);
			}

			/**
			 * applyFen method.
			 * @param fen parameter.
			 */
			protected void applyFen(String fen) {
				String activeEditorTab = currentEditorTabTitle();
				pgnNavigator = null;
				try {
					position = new Position(fen.trim());
					pgnTags.clear();
					pgnResult = "*";
					pgnStartPosition = position.copyOf();
					history.clear();
					moveHistory.clear();
					currentLineNodes.clear();
					clearLastMove();
					updateHistoryStart(position);
					fenField.setText(position.toString());
					bumpPosition();
					refreshAll();
					requestVsEngineReplyIfNeeded();
					if (activeEditorTab != null) {
						selectEditorTab(activeEditorTab);
					}
				} catch (IllegalArgumentException ex) {
					recordProblem("FEN error", "Invalid FEN: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Invalid FEN: " + ex.getMessage(), "FEN error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

			/**
			 * resetPosition method.
			 */
			protected void resetPosition() {
				applyFen(DEFAULT_FEN);
				fenList.clear();
				fenIndex = -1;
				fenSource = "";
				updateFenStatus();
			}

			/**
			 * flipBoard method.
			 */
			protected void flipBoard() {
				whiteDown = !whiteDown;
				if (guiState != null) {
					guiState.whiteDown = whiteDown;
				}
				refreshBoard();
				if (editorDialog != null) {
					editorDialog.repaintBoard();
				}
				if (boardEditorPane != null) {
					boardEditorPane.repaintBoard();
				}
			}

			/**
			 * openBoardEditor method.
			 */
			protected void openBoardEditor() {
				if (boardEditorPane != null) {
					boardEditorPane.loadFromOwnerPosition();
				}
				selectEditorTab("board.editor");
			}

			/**
			 * openPgnPasteDialog method.
			 */
			protected void openPgnPasteDialog() {
				// Single-window workflow: paste directly into the PGN editor tab.
				selectEditorTab("game.pgn");
				if (pgnEditorArea != null) {
					pgnEditorArea.requestFocusInWindow();
					try {
						String clip = Toolkit.getDefaultToolkit().getSystemClipboard()
								.getData(DataFlavor.stringFlavor).toString();
						if (!clip.isBlank()) {
							pgnEditorArea.setText(clip);
							if (loadPgnText(clip)) {
								refreshPgnEditor();
							}
						}
					} catch (Exception ex) {
						recordProblem("PGN Paste", "Clipboard text unavailable: " + ex.getMessage());
					}
				}
			}

			/**
			 * refreshPgnEditor method.
			 */
			protected void refreshPgnEditor() {
				String current = buildCurrentPgnText();
				if (pgnViewerArea != null) {
					pgnViewerArea.setText(current);
					pgnViewerArea.setCaretPosition(0);
				}
				updatePgnSummaryLabel();
			}

			/**
			 * useCurrentGamePgnAsInput method.
			 */
			protected void useCurrentGamePgnAsInput() {
				if (pgnEditorArea == null) {
					return;
				}
				pgnEditorArea.setText(buildCurrentPgnText());
				pgnEditorArea.setCaretPosition(0);
				pgnEditorArea.requestFocusInWindow();
			}

			/**
			 * applyPgnEditor method.
			 */
			protected void applyPgnEditor() {
				if (pgnEditorArea == null) {
					return;
				}
				if (loadPgnText(pgnEditorArea.getText())) {
					refreshPgnEditor();
				}
			}

			/**
			 * copyPgnText method.
			 */
			protected void copyPgnText() {
				String text = buildCurrentPgnText();
				if (text == null) {
					return;
				}
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			}

			/**
			 * importPgnFromFile method.
			 */
			protected void importPgnFromFile() {
				if (frame == null || pgnEditorArea == null) {
					return;
				}
				JFileChooser chooser = new JFileChooser();
				chooser.setFileFilter(new FileNameExtensionFilter("PGN files", "pgn"));
				int result = chooser.showOpenDialog(frame);
				if (result != JFileChooser.APPROVE_OPTION) {
					return;
				}
				Path path = chooser.getSelectedFile().toPath();
				try {
					String text = Files.readString(path, StandardCharsets.UTF_8);
					pgnEditorArea.setText(text);
					if (loadPgnText(text)) {
						fenSourcePath = path;
						updateFenSourceButton();
						refreshPgnEditor();
					}
				} catch (IOException ex) {
					recordProblem("PGN Import", "Failed to read PGN: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Failed to read PGN: " + ex.getMessage(),
							"PGN Import", JOptionPane.ERROR_MESSAGE);
				}
			}

			/**
			 * exportPgnToFile method.
			 */
			protected void exportPgnToFile() {
				if (frame == null) {
					return;
				}
				JFileChooser chooser = new JFileChooser();
				chooser.setFileFilter(new FileNameExtensionFilter("PGN files", "pgn"));
				int result = chooser.showSaveDialog(frame);
				if (result != JFileChooser.APPROVE_OPTION) {
					return;
				}
				Path path = chooser.getSelectedFile().toPath();
				try {
					String text = buildCurrentPgnText();
					Files.writeString(path, text, StandardCharsets.UTF_8);
					refreshPgnEditor();
				} catch (IOException ex) {
					recordProblem("PGN Export", "Failed to write PGN: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Failed to write PGN: " + ex.getMessage(),
							"PGN Export", JOptionPane.ERROR_MESSAGE);
				}
			}

			/**
			 * openEngineManager method.
			 */
			protected void openEngineManager() {
				// Single-window workflow: engine settings live in the Controls tab.
				selectRightTab("Controls");
				if (engineProtocolField != null) {
					engineProtocolField.requestFocusInWindow();
				}
			}

			/**
			 * openCommandPalette method.
			 */
			protected void openCommandPalette() {
				// Single-window workflow: open the built-in Commands tab instead of a floating dialog.
				selectRightTab("Commands");
				if (commandSelect != null) {
					commandSelect.requestFocusInWindow();
					commandSelect.showPopup();
				}
			}

			/**
			 * buildPaletteCommands method.
			 * @return return value.
			 */
			protected List<PaletteCommand> buildPaletteCommands() {
				List<PaletteCommand> commands = new ArrayList<>();
				if (recentCommandModel != null && recentCommandModel.getSize() > 0) {
					int limit = Math.min(3, recentCommandModel.getSize());
					for (int i = 0; i < limit; i++) {
						RecentCommand entry = recentCommandModel.getElementAt(i);
						if (entry == null) {
							continue;
						}
						String label = "Recent: " + entry.label();
						commands.add(PaletteCommand.of(label, "Run recent command",
								new String[] { "recent", entry.command() },
								() -> runCliCommand(entry.command(), entry.args())));
					}
				}
				commands.add(PaletteCommand.of("Copy FEN", "Copy current position", new String[] { "fen", "copy" },
						this::copyFen));
				commands.add(PaletteCommand.of("Copy Board Image", "Copy board image to clipboard",
						new String[] { "image", "board", "copy" }, this::copyBoardImage));
				commands.add(PaletteCommand.of("Save Board Image", "Save board image to file",
						new String[] { "image", "board", "save" }, this::saveBoardImage));
				commands.add(PaletteCommand.of("Reset Position", "Reset to standard start",
						new String[] { "reset", "start" }, this::resetPosition));
				commands.add(PaletteCommand.of("Undo Move", "Undo last move",
						new String[] { "undo", "move" }, this::undoMove));
				commands.add(PaletteCommand.of("Flip Board", "Invert board orientation",
						new String[] { "flip", "board" }, this::flipBoard));
				commands.add(PaletteCommand.of("Open Board Editor", "Edit position",
						new String[] { "editor", "board" }, this::openBoardEditor));
				commands.add(PaletteCommand.of("Play Best Move", "Play engine best move",
						new String[] { "engine", "best", "move" }, this::playEngineBest));
				commands.add(PaletteCommand.of("Play Vs Engine", "Continue from current position",
						new String[] { "vs", "engine", "continue" }, this::continueVsEngineFromCurrent));
				commands.add(PaletteCommand.of("Play Vs Engine (New Game)", "Start from initial position",
						new String[] { "vs", "engine", "new" }, this::startVsEngineNewGame));
				commands.add(PaletteCommand.of("Stop Vs Engine", "Disable play-vs-engine mode",
						new String[] { "vs", "engine", "stop" }, this::stopVsEngineGame));
				commands.add(PaletteCommand.of(engineWorker == null ? "Start Engine" : "Stop Engine",
						"Toggle engine analysis", new String[] { "engine", "analysis" }, () -> {
							if (engineWorker == null) {
								startEngine();
							} else {
								stopEngine();
							}
						}));
				commands.add(PaletteCommand.of("Open Engine Manager", "Configure engine settings",
						new String[] { "engine", "manage" }, this::openEngineManager));
				commands.add(PaletteCommand.of("Load FEN List", "Load a file of FENs",
						new String[] { "fen", "list", "explorer" }, this::loadFenList));
				commands.add(PaletteCommand.of("Paste PGN", "Paste PGN text",
						new String[] { "pgn", "paste" }, this::openPgnPasteDialog));
				commands.add(PaletteCommand.of("Copy PGN", "Copy current PGN text",
						new String[] { "pgn", "copy" }, this::copyPgnText));
				commands.add(PaletteCommand.of("Open PGN Editor", "Switch to PGN tab",
						new String[] { "pgn", "editor", "tab" }, () -> selectEditorTab("game.pgn")));
				commands.add(PaletteCommand.of("Open Board", "Switch to board tab",
						new String[] { "board", "editor", "tab" }, () -> selectEditorTab("board.pgn")));
				commands.add(PaletteCommand.of("Open Analysis", "Switch to analysis tab",
						new String[] { "analysis", "engine", "tab" }, () -> selectRightTab("Analysis")));
				commands.add(PaletteCommand.of("Open ECO", "Switch to ECO tab",
						new String[] { "eco", "editor", "tab" }, () -> selectEditorTab("eco.tsv")));
				commands.add(PaletteCommand.of("Open Source File", "Open current FEN/PGN source",
						new String[] { "open", "file" }, this::openFenSourceFile));
				commands.add(PaletteCommand.of("Toggle Sidebar", "Show or hide right sidebar",
						new String[] { "sidebar", "layout" }, this::toggleSidebar));
				commands.add(PaletteCommand.of("Toggle Focus Mode", "Hide UI chrome",
						new String[] { "focus", "layout" }, this::toggleFocusMode));
				commands.add(PaletteCommand.of("Toggle Zen Mode", "Minimal chrome (Focus + sidebar)",
						new String[] { "zen", "layout" }, this::toggleZenMode));
				commands.add(PaletteCommand.of("Navigate Previous", "Go to previous move",
						new String[] { "prev", "previous", "move" }, this::navigatePrev));
				commands.add(PaletteCommand.of("Navigate Next", "Go to next move",
						new String[] { "next", "move" }, this::navigateNext));
				commands.add(PaletteCommand.of("Navigate Start", "Go to line start",
						new String[] { "start", "begin", "move" }, this::navigateStart));
				commands.add(PaletteCommand.of("Navigate End", "Go to line end",
						new String[] { "end", "last", "move" }, this::navigateEnd));
				if (legalToggle != null) {
					commands.add(PaletteCommand.of(legalToggle.isSelected() ? "Hide Legal Hints" : "Show Legal Hints",
							"Toggle legal move dots", new String[] { "legal", "hints" }, () -> {
								legalToggle.setSelected(!legalToggle.isSelected());
								guiState.showLegal = legalToggle.isSelected();
								refreshBoard();
							}));
				}
				if (coordsToggle != null) {
					commands.add(PaletteCommand.of(coordsToggle.isSelected() ? "Hide Coordinates" : "Show Coordinates",
							"Toggle board coordinates", new String[] { "coords", "coordinates" }, () -> {
								coordsToggle.setSelected(!coordsToggle.isSelected());
								showCoords = coordsToggle.isSelected();
								guiState.showCoords = showCoords;
								refreshBoard();
							}));
				}
				if (movesToggle != null) {
					commands.add(PaletteCommand.of(movesToggle.isSelected() ? "Hide Moves Panel" : "Show Moves Panel",
							"Toggle moves list", new String[] { "moves", "history" }, () -> {
								movesToggle.setSelected(!movesToggle.isSelected());
								toggleMoves();
							}));
				}
				if (tagsToggle != null) {
					commands.add(PaletteCommand.of(tagsToggle.isSelected() ? "Hide Tags Panel" : "Show Tags Panel",
							"Toggle tags list", new String[] { "tags", "annotations" }, () -> {
								tagsToggle.setSelected(!tagsToggle.isSelected());
								toggleTags();
							}));
				}
				if (bestMoveToggle != null) {
					commands.add(PaletteCommand.of(bestMoveToggle.isSelected() ? "Hide Best Move" : "Show Best Move",
							"Toggle best move hint", new String[] { "best", "engine" }, () -> {
								bestMoveToggle.setSelected(!bestMoveToggle.isSelected());
								guiState.showBestMove = bestMoveToggle.isSelected();
								refreshBoard();
							}));
				}
				commands.add(PaletteCommand.of("Open Commands Tab", "Show Command Center",
						new String[] { "commands", "cli" }, () -> selectRightTab("Commands")));
				commands.add(PaletteCommand.of("Open Controls Tab", "Show controls",
						new String[] { "controls", "settings" }, () -> selectRightTab("Controls")));
				commands.add(PaletteCommand.of("Open Explorer Tab", "Show ECO explorer",
						new String[] { "explorer", "eco" }, () -> selectRightTab("Explorer")));
				commands.add(PaletteCommand.of("Open Ablation Tab", "Show ablation view",
						new String[] { "ablation", "analysis" }, () -> selectRightTab("Ablation")));
				return commands;
			}

			/**
			 * selectRightTab method.
			 * @param title parameter.
			 */
			protected void selectRightTab(String title) {
				if (rightTabs == null || title == null) {
					return;
				}
				for (int i = 0; i < rightTabs.getTabCount(); i++) {
					if (title.equals(rightTabs.getTitleAt(i))) {
						clearHoverPreviews();
						rightTabs.setSelectedIndex(i);
						return;
					}
				}
			}

			/**
			 * copyFen method.
			 */
			protected void copyFen() {
				fenField.setText(position.toString());
				fenField.selectAll();
				fenField.copy();
			}

			/**
			 * undoMove method.
			 */
			protected void undoMove() {
				if (pgnNavigator != null && pgnNavigator.current != null && pgnNavigator.current.parent != null) {
					applyPgnNode(pgnNavigator.current.parent);
					requestVsEngineReplyIfNeeded();
					return;
				}
				if (history.isEmpty()) {
					return;
				}
				position = history.remove(history.size() - 1);
				if (!moveHistory.isEmpty()) {
					moveHistory.remove(moveHistory.size() - 1);
				}
				updateLastMoveFromHistory();
				fenField.setText(position.toString());
				bumpPosition();
				refreshAll();
				requestVsEngineReplyIfNeeded();
			}

			/**
			 * toggleMoves method.
			 */
			protected void toggleMoves() {
				if (movesToggle != null && guiState != null) {
					guiState.showMoves = movesToggle.isSelected();
				}
				refreshMoves();
				frame.revalidate();
			}

			/**
			 * toggleTags method.
			 */
			protected void toggleTags() {
				if (tagsToggle != null && guiState != null) {
					guiState.showTags = tagsToggle.isSelected();
				}
				refreshTags();
				frame.revalidate();
			}

			/**
			 * applyFigurineSanSetting method.
			 * @param enabled parameter.
			 */
			protected void applyFigurineSanSetting(boolean enabled) {
				figurineSan = enabled;
				if (guiState != null) {
					guiState.figurineSan = enabled;
				}
				if (figurineSanToggle != null && figurineSanToggle.isSelected() != enabled) {
					figurineSanToggle.setSelected(enabled);
				}
				refreshMoves();
				refreshVariations();
				refreshInfo();
				updateAnnotatePanel();
				refreshReport();
				refreshStatusBar();
				refreshFigurineSanEngineViews();
				frame.revalidate();
				frame.repaint();
				scheduleGuiStateSave();
			}

			/**
			 * refreshFigurineSanEngineViews method.
			 */
			protected void refreshFigurineSanEngineViews() {
				// Implemented in GuiWindowEngine to refresh engine-only SAN output views.
			}

			/**
			 * toggleTheme method.
			 */
			protected void toggleTheme() {
				lightMode = !darkToggle.isSelected();
				guiState.lightMode = lightMode;
				applyTheme();
				refreshStatusBar();
			}

			/**
			 * toggleContrast method.
			 */
			protected void toggleContrast() {
				highContrast = contrastToggle != null && contrastToggle.isSelected();
				guiState.highContrast = highContrast;
				applyTheme();
			}

			/**
			 * toggleSidebar method.
			 */
			protected void toggleSidebar() {
				setSidebarVisible(!sidebarVisible, true);
				captureGuiState();
				scheduleGuiStateSave();
			}

			/**
			 * toggleFocusMode method.
			 */
			protected void toggleFocusMode() {
				setFocusMode(!focusMode);
			}

			/**
			 * exitFocusModes method.
			 */
			protected void exitFocusModes() {
				if (!focusMode && !zenMode) {
					return;
				}
				focusMode = false;
				zenMode = false;
				applyChromeVisibility();
			}

			/**
			 * setFocusMode method.
			 * @param enabled parameter.
			 */
			protected void setFocusMode(boolean enabled) {
				focusMode = enabled;
				applyChromeVisibility();
			}

			/**
			 * toggleZenMode method.
			 */
			protected void toggleZenMode() {
				setZenMode(!zenMode);
			}

			/**
			 * setZenMode method.
			 * @param enabled parameter.
			 */
			protected void setZenMode(boolean enabled) {
				zenMode = enabled;
				applyChromeVisibility();
			}

			/**
			 * applyChromeVisibility method.
			 */
			protected void applyChromeVisibility() {
				clearHoverPreviews();
				boolean hideChrome = focusMode || zenMode;
				if (topBar != null) {
					topBar.setVisible(!hideChrome);
				}
				if (boardControlsRow != null) {
					boardControlsRow.setVisible(!hideChrome);
				}
				if (boardBars != null) {
					boardBars.setVisible(!hideChrome);
				}
				if (boardActionStrip != null) {
					boardActionStrip.setVisible(!hideChrome);
				}
				if (activityBar != null) {
					activityBar.setVisible(!hideChrome);
				}
				if (focusCornerHost != null) {
					focusCornerHost.setVisible(hideChrome);
				}
				setSidebarVisible(sidebarVisible, false);
				setPanelVisible(panelVisible, false);
				updateFocusToggleLabel();
				frame.revalidate();
				frame.repaint();
			}

			/**
			 * setSidebarVisible method.
			 * @param visible parameter.
			 * @param persist parameter.
			 */
			protected void setSidebarVisible(boolean visible, boolean persist) {
				if (persist) {
					sidebarVisible = visible;
				}
				if (rightSidebarScroll == null || mainSplit == null) {
					updateSidebarToggleLabel();
					return;
				}
				boolean effective = visible && !(focusMode || zenMode);
				if (!effective) {
					clearHoverPreviews();
				}
				rightSidebarScroll.setVisible(effective);
				if (effective) {
					mainSplit.setDividerSize(1);
					int target = sidebarLastDivider > 0 ? sidebarLastDivider : sidebarComfortWidthPx();
					target = clampSidebarWidthPx(Math.max(target, sidebarComfortWidthPx()));
					mainSplit.setDividerLocation(target);
				} else {
					if (mainSplit.getDividerLocation() > 0) {
						sidebarLastDivider = mainSplit.getDividerLocation();
					}
					mainSplit.setDividerSize(0);
					mainSplit.setDividerLocation(0);
				}
				updateSidebarToggleLabel();
				frame.revalidate();
				frame.repaint();
			}

			/**
			 * togglePanel method.
			 */
			protected void togglePanel() {
				setPanelVisible(!panelVisible, true);
			}

			/**
			 * setPanelVisible method.
			 * @param visible parameter.
			 * @param persist parameter.
			 */
			protected void setPanelVisible(boolean visible, boolean persist) {
				if (persist) {
					panelVisible = visible;
				}
				boolean effective = visible && !(focusMode || zenMode);
				if (panelTabs != null) {
					panelTabs.setVisible(effective);
				}
				if (statusPanelLabel != null) {
					statusPanelLabel.setText(effective ? "Panel: On" : "Panel: Off");
				}
				frame.revalidate();
				frame.repaint();
			}

			/**
			 * openPanelTab method.
			 * @param title parameter.
			 */
			protected void openPanelTab(String title) {
				if (panelTabs == null || title == null) {
					return;
				}
				for (int i = 0; i < panelTabs.getTabCount(); i++) {
					if (title.equalsIgnoreCase(panelTabs.getTitleAt(i))) {
						panelTabs.setSelectedIndex(i);
						return;
					}
				}
			}

			/**
			 * toggleEnginePower method.
			 */
			protected void toggleEnginePower() {
				if (engineWorker == null) {
					startEngine();
				} else {
					stopEngine();
				}
			}

			/**
			 * updateSidebarToggleLabel method.
			 */
			protected void updateSidebarToggleLabel() {
				if (sidebarToggleButton == null) {
					return;
				}
				sidebarToggleButton.setText(sidebarVisible ? "Hide Sidebar" : "Show Sidebar");
			}

			/**
			 * updateFocusToggleLabel method.
			 */
			protected void updateFocusToggleLabel() {
				if (focusToggleButton == null) {
					return;
				}
				focusToggleButton.setText(focusMode ? "Exit Focus" : "Focus Mode");
			}


}
