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
import application.gui.history.text.HistoryTextSupport;
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
 * GuiWindowHistoryAnalysis class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
abstract class GuiWindowHistoryAnalysis extends GuiWindowHistoryTheme {

			/**
			 * refreshAll method.
			 */
			protected void refreshAll() {
				refreshBoard();
				refreshMoves();
				refreshVariations();
				refreshTags();
				refreshAblation();
				updateEvalGraph();
				refreshInfo();
				updateAnnotatePanel();
				refreshReport();
				refreshEcoCurrent();
				updateFenStatus();
				syncCommandFenField();
				refreshPgnEditor();
				updatePgnSummaryLabel();
				refreshStatusBar();
			}

			/**
			 * refreshBoard method.
			 */
			protected void refreshBoard() {
				boardPanel.setState(position, whiteDown, legalToggle.isSelected(), showCoords,
						hoverLegalToggle != null && hoverLegalToggle.isSelected(),
						hoverHighlightToggle != null && hoverHighlightToggle.isSelected(),
						hoverOnlyToggle != null && hoverOnlyToggle.isSelected());
			}

			/**
			 * refreshStatusBar method.
			 */
			protected void refreshStatusBar() {
				if (statusLeftLabel == null || statusRightLabel == null || position == null) {
					return;
				}
				String side = position.isWhiteTurn() ? "White" : "Black";
				String left = "Move " + position.getFullMove() + " | " + side + " to move";
				statusLeftLabel.setText(left);

				if (statusMiddleLabel != null) {
					if (fenSource != null && !fenSource.isBlank()) {
						statusMiddleLabel.setText("Source: " + truncate(fenSource, 48));
					} else {
						statusMiddleLabel.setText("Ctrl+P Palette");
					}
				}
				if (statusEngineLabel != null) {
					statusEngineLabel.setText(engineWorker != null ? "Engine: On" : "Engine: Off");
					statusEngineLabel.setForeground(engineWorker != null ? theme.accent() : theme.textMuted());
				}
				StringBuilder right = new StringBuilder();
				if (latestAnalysis != null && latestAnalysisVersion == positionVersion) {
					Output out = latestAnalysis.getBestOutput(1);
					if (out != null) {
						right.append("Depth ").append(out.getDepth());
						String evalText = formatEvalLabelForWhite(out.getEvaluation(), position.isWhiteTurn());
						if (!evalText.isBlank()) {
							right.append(" • Eval ").append(evalText);
						}
						String pv = buildPvSnippet(out, 3);
						if (!pv.isBlank()) {
							right.append(" • PV ").append(pv);
						}
					}
				}
				statusRightLabel.setText(right.toString());
				if (statusProblemLabel != null) {
					statusProblemLabel.setText("Problems: " + problemCount);
					statusProblemLabel.setVisible(problemCount > 0);
					if (problemCount > 0) {
						statusProblemLabel.setForeground(lightMode ? new Color(190, 70, 70) : new Color(230, 110, 110));
					} else {
						statusProblemLabel.setForeground(theme.textMuted());
					}
				}
				if (statusPanelLabel != null) {
					boolean effective = panelVisible && !(focusMode || zenMode);
					statusPanelLabel.setText(effective ? "Panel: On" : "Panel: Off");
				}
				if (statusModeLabel != null) {
					if (vsEngineMode) {
						String sideLabel = vsEngineHumanWhite ? "White" : "Black";
						String turn = canUserPlayCurrentTurn() ? "You" : "Engine";
						statusModeLabel.setText("Mode: Vs Engine (" + sideLabel + ", " + turn + ")");
					} else {
						statusModeLabel.setText("Mode: Chess");
					}
				}
				if (statusEolLabel != null) {
					statusEolLabel.setText("LF");
				}
				if (statusEncodingLabel != null) {
					statusEncodingLabel.setText("UTF-8");
				}
				if (statusBranchLabel != null) {
					statusBranchLabel.setText("Branch: —");
				}
				if (statusThemeLabel != null) {
					statusThemeLabel.setText(lightMode ? "Theme: Light" : "Theme: Dark");
				}
				updateStatusTabPickerSelection();
				updateActivitySelection();
			}

			/**
			 * recordProblem method.
			 * @param title parameter.
			 * @param message parameter.
			 */
			protected void recordProblem(String title, String message) {
				problemCount++;
				StringBuilder sb = new StringBuilder();
				if (title != null && !title.isBlank()) {
					sb.append(title.trim()).append(": ");
				}
				if (message != null && !message.isBlank()) {
					sb.append(message.trim());
				} else {
					sb.append("Unknown error");
				}
				lastProblemMessage = sb.toString();
				if (problemListModel != null) {
					problemListModel.addElement(lastProblemMessage);
					while (problemListModel.getSize() > 200) {
						problemListModel.remove(0);
					}
				}
				refreshStatusBar();
			}

			/**
			 * installListHover method.
			 * @param list parameter.
			 */
			protected void installListHover(JList<?> list) {
				if (list == null) {
					return;
				}
				if (Boolean.TRUE.equals(list.getClientProperty("hoverInstalled"))) {
					return;
				}
				list.putClientProperty("hoverInstalled", Boolean.TRUE);
				list.addMouseMotionListener(new MouseAdapter() {
										/**
					 * Handles mouse moved.
					 * @param e e value
					 */
@Override
					public void mouseMoved(MouseEvent e) {
						int idx = list.locationToIndex(e.getPoint());
						Object current = list.getClientProperty("hoverIndex");
						int currentIdx = current instanceof Integer ? (Integer) current : -1;
						if (idx != currentIdx) {
							list.putClientProperty("hoverIndex", idx);
							list.repaint();
						}
					}
				});
				list.addMouseListener(new MouseAdapter() {
										/**
					 * Handles mouse exited.
					 * @param e e value
					 */
@Override
					public void mouseExited(MouseEvent e) {
						list.putClientProperty("hoverIndex", -1);
						list.repaint();
					}
				});
			}

			/**
			 * installTableHover method.
			 * @param table parameter.
			 */
			protected void installTableHover(JTable table) {
				if (table == null) {
					return;
				}
				if (Boolean.TRUE.equals(table.getClientProperty("hoverInstalled"))) {
					return;
				}
				table.putClientProperty("hoverInstalled", Boolean.TRUE);
				table.addMouseMotionListener(new MouseAdapter() {
										/**
					 * Handles mouse moved.
					 * @param e e value
					 */
@Override
					public void mouseMoved(MouseEvent e) {
						int row = table.rowAtPoint(e.getPoint());
						Object current = table.getClientProperty("hoverRow");
						int currentRow = current instanceof Integer ? (Integer) current : -1;
						if (row != currentRow) {
							table.putClientProperty("hoverRow", row);
							table.repaint();
						}
					}
				});
				table.addMouseListener(new MouseAdapter() {
										/**
					 * Handles mouse exited.
					 * @param e e value
					 */
@Override
					public void mouseExited(MouseEvent e) {
						table.putClientProperty("hoverRow", -1);
						table.repaint();
					}
				});
			}

			/**
			 * isListHover method.
			 * @param list parameter.
			 * @param index parameter.
			 * @return return value.
			 */
			public boolean isListHover(JList<?> list, int index) {
				if (list == null || index < 0) {
					return false;
				}
				Object value = list.getClientProperty("hoverIndex");
				return value instanceof Integer && ((Integer) value) == index;
			}

			/**
			 * startPvHoverAnimation method.
			 */
			protected void startPvHoverAnimation() {
				if (pvHoverAnimationTimer == null) {
					pvHoverAnimationTimer = new Timer(40, e -> {
						if (pvHoverAnimationActive) {
							pvHoverHighlightAmount = Math.min(1f, pvHoverHighlightAmount + 0.22f);
						} else {
							pvHoverHighlightAmount = Math.max(0f, pvHoverHighlightAmount - 0.16f);
						}
						if (pvList != null && pvList.isShowing()) {
							pvList.repaint();
						}
						if (!pvHoverAnimationActive && pvHoverHighlightAmount <= 0.001f) {
							clearPvHoverSelection();
							pvHoverAnimationTimer.stop();
						}
					});
				}
				pvHoverAnimationActive = true;
				if (!pvHoverAnimationTimer.isRunning()) {
					pvHoverAnimationTimer.start();
				}
			}

			/**
			 * stopPvHoverAnimation method.
			 */
			protected void stopPvHoverAnimation() {
				pvHoverAnimationActive = false;
				if (pvHoverAnimationTimer != null && !pvHoverAnimationTimer.isRunning()
						&& pvHoverHighlightAmount > 0.001f) {
					pvHoverAnimationTimer.start();
				}
			}

			/**
			 * Clears transient board hover previews.
			 */
			protected void clearHoverPreviews() {
				stopPvHoverAnimation();
				if (boardPanel != null) {
					boardPanel.clearPreview();
				}
				clearMiniBoardPreview();
			}

			/**
			 * Hook for subclasses that render extra preview surfaces.
			 */
			protected void clearMiniBoardPreview() {
				// Implemented in GuiWindowEngine.
			}

			/**
			 * clearPvHoverSelection method.
			 */
			protected void clearPvHoverSelection() {
				if (pvList != null) {
					pvList.putClientProperty("pvHoverMoveIndex", -1);
					pvList.putClientProperty("pvHoverPly", -1);
					pvList.repaint();
				}
			}

			/**
			 * getPvHoverHighlightAmount method.
			 * @return return value.
			 */
			public float getPvHoverHighlightAmount() {
				return pvHoverHighlightAmount;
			}

			/**
			 * isTableHover method.
			 * @param table parameter.
			 * @param row parameter.
			 * @return return value.
			 */
			public boolean isTableHover(JTable table, int row) {
				if (table == null || row < 0) {
					return false;
				}
				Object value = table.getClientProperty("hoverRow");
				return value instanceof Integer && ((Integer) value) == row;
			}

			/**
			 * updateHistoryStart method.
			 * @param pos parameter.
			 */
			protected void updateHistoryStart(Position pos) {
				if (pos == null) {
					historyStartFullmove = 1;
					historyStartWhite = true;
					return;
				}
				String[] parts = pos.toString().split(" ");
				historyStartWhite = parts.length > 1 ? "w".equals(parts[1]) : true;
				if (parts.length > 5) {
					try {
						historyStartFullmove = Integer.parseInt(parts[5]);
					} catch (NumberFormatException ex) {
						historyStartFullmove = 1;
					}
				} else {
					historyStartFullmove = 1;
				}
			}

			/**
			 * clearLastMove method.
			 */
			protected void clearLastMove() {
				lastMove = Move.NO_MOVE;
				pendingAnimationMove = Move.NO_MOVE;
			}

			/**
			 * Queues a move for the next board-state animation pass.
			 *
			 * @param move transition move or {@link Move#NO_MOVE}.
			 */
			protected void queueAnimationMove(short move) {
				pendingAnimationMove = move;
			}

			/**
			 * Requests skipping the next board animation pass.
			 */
			protected void skipNextBoardAnimation() {
				skipNextBoardAnimation = true;
			}

			/**
			 * Consumes and clears the queued animation move.
			 *
			 * @return queued transition move or {@link Move#NO_MOVE}.
			 */
			protected short consumeAnimationMove() {
				short move = pendingAnimationMove;
				pendingAnimationMove = Move.NO_MOVE;
				return move;
			}

			/**
			 * Consumes and clears the one-shot skip-animation flag.
			 *
			 * @return {@code true} if the next board animation should be skipped.
			 */
			protected boolean consumeSkipNextBoardAnimation() {
				boolean skip = skipNextBoardAnimation;
				skipNextBoardAnimation = false;
				return skip;
			}

			/**
			 * Resolves a transition move between two adjacent PGN nodes.
			 *
			 * Returns the forward move for parent->child transitions and a reversed move for child->parent transitions.
			 * For non-adjacent jumps, no transition animation is queued.
			 *
			 * @param previous node before navigation.
			 * @param target node after navigation.
			 * @return transition move or {@link Move#NO_MOVE}.
			 */
			protected short transitionMove(PgnNode previous, PgnNode target) {
				if (previous == null || target == null || previous == target) {
					return Move.NO_MOVE;
				}
				if (target.parent == previous && target.move != Move.NO_MOVE) {
					return target.move;
				}
				if (previous.parent == target && previous.move != Move.NO_MOVE) {
					byte from = Move.getToIndex(previous.move);
					byte to = Move.getFromIndex(previous.move);
					try {
						return Move.of(from, to);
					} catch (IllegalArgumentException ex) {
						return Move.NO_MOVE;
					}
				}
				return Move.NO_MOVE;
			}

			/**
			 * updateLastMoveFromHistory method.
			 */
			protected void updateLastMoveFromHistory() {
				if (moveHistory.isEmpty() || history.isEmpty()) {
					lastMove = Move.NO_MOVE;
					return;
				}
				int idx = moveHistory.size() - 1;
				Position before = history.get(idx);
				if (before == null) {
					lastMove = Move.NO_MOVE;
					return;
				}
				try {
					short move = SAN.fromAlgebraic(before, moveHistory.get(idx));
					lastMove = move;
				} catch (RuntimeException ex) {
					lastMove = Move.NO_MOVE;
				}
			}

			/**
			 * setEvalBarVisible method.
			 * @param visible parameter.
			 */
			protected void setEvalBarVisible(boolean visible) {
				if (evalBar != null) {
					evalBar.setVisible(visible);
				}
			}

			/**
			 * setWdlBarVisible method.
			 * @param visible parameter.
			 */
			protected void setWdlBarVisible(boolean visible) {
				// WDL bar removed from GUI.
			}

			/**
			 * shiftHue method.
			 * @param color parameter.
			 * @param degrees parameter.
			 * @return return value.
			 */
			protected static Color shiftHue(Color color, int degrees) {
				float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
				float hueShift = (degrees % 360) / 360f;
				float hue = hsb[0] + hueShift;
				if (hue > 1f) {
					hue -= 1f;
				}
				int rgb = Color.HSBtoRGB(hue, hsb[1], hsb[2]);
				return new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
			}

			/**
			 * applyRenderHints method.
			 * @param g2 parameter.
			 */
			protected void applyRenderHints(Graphics2D g2) {
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			}

			/**
			 * boardColor method.
			 * @param base parameter.
			 * @return return value.
			 */
			protected Color boardColor(Color base) {
				Color shifted = shiftHue(base, boardHueDegrees);
				float[] hsb = Color.RGBtoHSB(shifted.getRed(), shifted.getGreen(), shifted.getBlue(), null);
				float sat = clamp01(hsb[1] * (boardSaturation / 100f));
				float bri = clamp01(hsb[2] * (boardBrightness / 100f));
				int rgb = Color.HSBtoRGB(hsb[0], sat, bri);
				return new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
			}

			/**
			 * clamp01 method.
			 * @param value parameter.
			 * @return return value.
			 */
			protected float clamp01(float value) {
				return Math.max(0f, Math.min(1f, value));
			}

			/**
			 * withAlpha method.
			 * @param color parameter.
			 * @param alpha parameter.
			 * @return return value.
			 */
			protected Color withAlpha(Color color, int alpha) {
				return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
			}

			/**
			 * formatEvalLabel method.
			 * @param value parameter.
			 * @param mate parameter.
			 * @return return value.
			 */
			protected String formatEvalLabel(int value, boolean mate) {
				return HistoryTextSupport.formatEvalLabel(value, mate);
			}

			/**
			 * formatEvalLabelForWhite method.
			 * @param eval parameter.
			 * @param whiteTurn parameter.
			 * @return return value.
			 */
			protected String formatEvalLabelForWhite(Evaluation eval, boolean whiteTurn) {
				return HistoryTextSupport.formatEvalLabelForWhite(eval, whiteTurn);
			}

			/**
			 * buildPvSnippet method.
			 * @param out parameter.
			 * @param maxMoves parameter.
			 * @return return value.
			 */
			protected String buildPvSnippet(Output out, int maxMoves) {
				return HistoryTextSupport.buildPvSnippet(out, maxMoves, position, this::displaySan);
			}

			/**
			 * truncate method.
			 * @param text parameter.
			 * @param max parameter.
			 * @return return value.
			 */
			public String truncate(String text, int max) {
				if (text == null || text.length() <= max) {
					return text;
				}
				return text.substring(0, Math.max(0, max - 1)) + "…";
			}

			/**
			 * pieceImage method.
			 * @param piece parameter.
			 * @return return value.
			 */
			protected BufferedImage pieceImage(byte piece) {
				switch (piece) {
					case Piece.WHITE_PAWN:
						return Pictures.WhitePawn;
					case Piece.WHITE_KNIGHT:
						return Pictures.WhiteKnight;
					case Piece.WHITE_BISHOP:
						return Pictures.WhiteBishop;
					case Piece.WHITE_ROOK:
						return Pictures.WhiteRook;
					case Piece.WHITE_QUEEN:
						return Pictures.WhiteQueen;
					case Piece.WHITE_KING:
						return Pictures.WhiteKing;
					case Piece.BLACK_PAWN:
						return Pictures.BlackPawn;
					case Piece.BLACK_KNIGHT:
						return Pictures.BlackKnight;
					case Piece.BLACK_BISHOP:
						return Pictures.BlackBishop;
					case Piece.BLACK_ROOK:
						return Pictures.BlackRook;
					case Piece.BLACK_QUEEN:
						return Pictures.BlackQueen;
					case Piece.BLACK_KING:
						return Pictures.BlackKing;
					default:
						return null;
				}
			}

			/**
			 * promotionPieceImage method.
			 * @param promo parameter.
			 * @param white parameter.
			 * @return return value.
			 */
			protected BufferedImage promotionPieceImage(byte promo, boolean white) {
				return switch (promo) {
					case PROMOTION_ROOK -> pieceImage(white ? Piece.WHITE_ROOK : Piece.BLACK_ROOK);
					case PROMOTION_BISHOP -> pieceImage(white ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP);
					case PROMOTION_KNIGHT -> pieceImage(white ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT);
					default -> pieceImage(white ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN);
				};
			}

			/**
			 * scaledIcon method.
			 * @param image parameter.
			 * @param size parameter.
			 * @return return value.
			 */
			protected ImageIcon scaledIcon(BufferedImage image, int size) {
				if (image == null || size <= 0) {
					return null;
				}
				BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2 = scaled.createGraphics();
				applyRenderHints(g2);
				g2.drawImage(image, 0, 0, size, size, null);
				g2.dispose();
				return new ImageIcon(scaled);
			}

			/**
			 * promoLabel method.
			 * @param promo parameter.
			 * @return return value.
			 */
			protected String promoLabel(byte promo) {
				return switch (promo) {
					case PROMOTION_ROOK -> "Promote to rook";
					case PROMOTION_BISHOP -> "Promote to bishop";
					case PROMOTION_KNIGHT -> "Promote to knight";
					default -> "Promote to queen";
				};
			}

			/**
			 * positionPromotionCard method.
			 * @param dialog parameter.
			 * @param card parameter.
			 * @param from parameter.
			 * @param to parameter.
			 * @param tile parameter.
			 * @param pad parameter.
			 */
			protected void positionPromotionCard(JDialog dialog, JComponent card, byte from, byte to, int tile, int pad) {
				try {
					Point panel = boardPanel.getLocationOnScreen();
					int file = boardPanel.screenFile(to);
					int screenRankFrom = boardPanel.screenRank(from);
					int screenRankTo = boardPanel.screenRank(to);
					boolean promoteUp = screenRankTo < screenRankFrom;
					int startRank = promoteUp ? screenRankTo : screenRankTo - 3;
					if (startRank < 0) {
						startRank = 0;
					}
					if (startRank > 4) {
						startRank = 4;
					}
					int x = panel.x + boardPanel.boardX + file * tile - pad;
					int y = panel.y + boardPanel.boardY + startRank * tile - pad;
					Point dialogScreen = dialog.getLocationOnScreen();
					int localX = x - dialogScreen.x;
					int localY = y - dialogScreen.y;
					int maxX = dialog.getWidth() - card.getWidth();
					int maxY = dialog.getHeight() - card.getHeight();
					int finalX = Math.min(Math.max(localX, 0), Math.max(0, maxX));
					int finalY = Math.min(Math.max(localY, 0), Math.max(0, maxY));
					card.setBounds(finalX, finalY, card.getWidth(), card.getHeight());
				} catch (Exception ex) {
					card.setBounds(Math.max(0, (dialog.getWidth() - card.getWidth()) / 2),
							Math.max(0, (dialog.getHeight() - card.getHeight()) / 2),
							card.getWidth(), card.getHeight());
				}
			}

			/**
			 * buildPiecePlacement method.
			 * @param board parameter.
			 * @return return value.
			 */
			protected String buildPiecePlacement(byte[] board) {
				StringBuilder sb = new StringBuilder();
				for (int rank = 7; rank >= 0; rank--) {
					int empty = 0;
					for (int file = 0; file < 8; file++) {
						byte piece = board[Field.toIndex(file, rank)];
						if (Piece.isEmpty(piece)) {
							empty++;
						} else {
							if (empty > 0) {
								sb.append(empty);
								empty = 0;
							}
							sb.append(Piece.toLowerCaseChar(piece));
						}
					}
					if (empty > 0) {
						sb.append(empty);
					}
					if (rank > 0) {
						sb.append('/');
					}
				}
				return sb.toString();
			}

			/**
			 * refreshMoves method.
			 */
			protected void refreshMoves() {
				refreshHistory();
				if (!movesToggle.isSelected()) {
					movesCard.setVisible(false);
					if (underboardCard != null) {
						underboardCard.setVisible(false);
					}
					return;
				}
				movesCard.setVisible(true);
				if (underboardCard != null) {
					underboardCard.setVisible(true);
				}
			}

			/**
			 * refreshHistory method.
			 */
			protected void refreshHistory() {
				if (historyListModel == null) {
					return;
				}
				if (pgnNavigator == null && !moveHistory.isEmpty()) {
					ensureGameTree();
				}
				HistoryEntry selectedEntry = historyList != null ? historyList.getSelectedValue() : null;
				PgnNode selectedNode = selectedEntry != null ? selectedEntry.node() : null;
				int selectedMoveIndex = indexInCurrentLine(selectedNode);
				int lastVisibleIndex = historyList != null ? historyList.getLastVisibleIndex() : -1;
				int oldSize = moveHistory.size();
				boolean stickToEnd = selectedMoveIndex >= 0 && selectedMoveIndex == oldSize - 1;
				int scrollValue = -1;
				JScrollBar historyBar = null;
				if (historyScroll != null) {
					historyBar = historyScroll.getVerticalScrollBar();
					if (historyBar != null) {
						scrollValue = historyBar.getValue();
						int max = historyBar.getMaximum();
						int extent = historyBar.getModel().getExtent();
						if (max > 0) {
							stickToEnd = stickToEnd || (scrollValue + extent >= max - 1);
						}
					}
				}

				historyListModel.clear();
				List<HistoryEntry> entries = new ArrayList<>();
				if (pgnNavigator != null && pgnNavigator.root != null && pgnNavigator.root.mainNext != null) {
					buildHistoryEntries(pgnNavigator.root.mainNext, 0, entries);
				} else if (!moveHistory.isEmpty()) {
					buildLinearHistoryEntries(entries);
				}
				for (HistoryEntry entry : entries) {
					if (entry == null) {
						continue;
					}
					historyListModel.addElement(entry);
				}
				if (underboardMoveModel != null) {
					underboardMoveModel.clear();
					int idx = 0;
					int moveNo = historyStartFullmove;
					boolean whiteMove = historyStartWhite;
					while (idx < moveHistory.size()) {
						String whiteSan = "";
						String blackSan = "";
						int whiteIndex = -1;
						int blackIndex = -1;
						if (whiteMove) {
							whiteSan = moveHistory.get(idx);
							whiteIndex = idx;
							idx++;
							if (idx < moveHistory.size()) {
								blackSan = moveHistory.get(idx);
								blackIndex = idx;
								idx++;
							}
						} else {
							blackSan = moveHistory.get(idx);
							blackIndex = idx;
							idx++;
						}
						underboardMoveModel.addElement(new MovePair(moveNo, whiteSan, blackSan, whiteIndex, blackIndex));
						moveNo++;
						whiteMove = true;
					}
				}
				if (historyList != null && historyListModel.getSize() > 0) {
					if (selectedNode != null) {
						selectHistoryNode(selectedNode);
					} else if (pgnNavigator != null && pgnNavigator.current != null) {
						selectHistoryNode(pgnNavigator.current);
					}
					if (stickToEnd && pgnNavigator != null && pgnNavigator.current != null) {
						selectHistoryNode(pgnNavigator.current);
						historyList.ensureIndexIsVisible(historyList.getSelectedIndex());
					} else if (historyBar != null && scrollValue >= 0) {
						int finalScrollValue = scrollValue;
						JScrollBar finalBar = historyBar;
						javax.swing.SwingUtilities.invokeLater(
								() -> finalBar.setValue(Math.min(finalScrollValue, finalBar.getMaximum())));
					} else if (lastVisibleIndex >= 0) {
						historyList.ensureIndexIsVisible(Math.min(lastVisibleIndex, historyListModel.getSize() - 1));
					}
				}
				if (underboardMoveList != null && underboardMoveModel != null && !moveHistory.isEmpty()) {
					if (stickToEnd) {
						underboardMoveList.ensureIndexIsVisible(Math.max(0, underboardMoveModel.getSize() - 1));
					}
				}
				updateVariationButtons();
			}

			/**
			 * refreshVariations method.
			 */
			protected void refreshVariations() {
				if (variationModel == null) {
					return;
				}
				variationModel.setRowCount(0);
				variationNodes.clear();
				if (pgnNavigator == null || pgnNavigator.root == null) {
					return;
				}
				List<PgnNode> nodes = new ArrayList<>();
				collectVariationStarts(pgnNavigator.root, nodes);
				for (PgnNode node : nodes) {
					variationModel.addRow(new Object[] {
						formatMovePrefix(node),
						buildVariationLine(node)
					});
					variationNodes.add(node);
				}
			}

			/**
			 * collectVariationStarts method.
			 * @param node parameter.
			 * @param collector parameter.
			 */
			private void collectVariationStarts(PgnNode node, List<PgnNode> collector) {
				PgnHistoryTreeHelper.collectVariationStarts(node, collector);
			}

			/**
			 * buildVariationLine method.
			 * @param start parameter.
			 * @return return value.
			 */
			private String buildVariationLine(PgnNode start) {
				return PgnHistoryTreeHelper.buildVariationLine(start, this::displaySan);
			}

			/**
			 * buildHistoryEntries method.
			 * @param node parameter.
			 * @param depth parameter.
			 * @param entries parameter.
			 */
			protected void buildHistoryEntries(PgnNode node, int depth, List<HistoryEntry> entries) {
				if (node == null) {
					return;
				}
				entries.add(new HistoryEntry(node, node.san, formatMovePrefix(node), depth, node == pgnNavigator.current));
				for (PgnNode variation : node.variations) {
					buildHistoryEntries(variation, depth + 1, entries);
				}
				if (node.mainNext != null) {
					buildHistoryEntries(node.mainNext, depth, entries);
				}
			}

			/**
			 * buildLinearHistoryEntries method.
			 * @param entries parameter.
			 */
			protected void buildLinearHistoryEntries(List<HistoryEntry> entries) {
				if (moveHistory.isEmpty()) {
					return;
				}
				int moveNo = historyStartFullmove;
				boolean whiteMove = historyStartWhite;
				for (int i = 0; i < moveHistory.size(); i++) {
					String san = moveHistory.get(i);
					String prefix = whiteMove ? (moveNo + ".") : (moveNo + "...");
					boolean current = i == moveHistory.size() - 1;
					entries.add(new HistoryEntry(null, san, prefix, 0, current));
					if (!whiteMove) {
						moveNo++;
					}
					whiteMove = !whiteMove;
				}
			}

			/**
			 * indexInCurrentLine method.
			 * @param node parameter.
			 * @return return value.
			 */
			protected int indexInCurrentLine(PgnNode node) {
				if (node == null || currentLineNodes.isEmpty()) {
					return -1;
				}
				for (int i = 0; i < currentLineNodes.size(); i++) {
					if (currentLineNodes.get(i) == node) {
						return i;
					}
				}
				return -1;
			}

			/**
			 * formatMovePrefix method.
			 * @param node parameter.
			 * @return return value.
			 */
			protected String formatMovePrefix(PgnNode node) {
				if (node == null || node.ply <= 0) {
					return "";
				}
				boolean whiteMove = historyStartWhite ? (node.ply % 2 == 1) : (node.ply % 2 == 0);
				int fullmove = historyStartFullmove + (node.ply - 1) / 2;
				return whiteMove ? (fullmove + ".") : (fullmove + "...");
			}

			/**
			 * refreshTags method.
			 */
			protected void refreshTags() {
				tagListModel.clear();
				if (!tagsToggle.isSelected()) {
					tagsCard.setVisible(false);
					return;
				}
				tagsCard.setVisible(true);
				List<String> tags;
				if (latestAnalysis != null && latestAnalysisVersion == positionVersion) {
					tags = Tagging.tags(position, latestAnalysis);
				} else {
					tags = Tagging.tags(position);
				}
				for (String tag : tags) {
					tagListModel.addElement(tag);
				}
			}

			/**
			 * updateEvalGraph method.
			 */
			protected void updateEvalGraph() {
				if (evalGraphPanel == null) {
					return;
				}
				List<PgnNode> mainline = collectMainlineNodes();
				if (mainline.isEmpty()) {
					evalGraphPanel.setSeries(List.of(), -1, null);
					return;
				}
				Map<PgnNode, Integer> indexMap = new HashMap<>();
				List<Double> values = new ArrayList<>(mainline.size());
				for (int i = 0; i < mainline.size(); i++) {
					PgnNode node = mainline.get(i);
					indexMap.put(node, i);
					Evaluation eval = cachedEvalFor(node.positionAfter);
					values.add(evalToCp(node.positionAfter, eval));
				}
				PgnNode current = pgnNavigator != null ? pgnNavigator.current : null;
				int currentIndex = -1;
				String currentLabel = null;
				PgnNode cursor = current;
				while (cursor != null && cursor.parent != null) {
					Integer idx = indexMap.get(cursor);
					if (idx != null) {
						currentIndex = idx;
						Evaluation eval = cachedEvalFor(cursor.positionAfter);
						if (eval != null && eval.isValid()) {
							int value = eval.getValue();
							if (!cursor.positionAfter.isWhiteTurn()) {
								value = -value;
							}
							currentLabel = formatEvalLabel(value, eval.isMate());
						}
						break;
					}
					cursor = cursor.parent;
				}
				evalGraphPanel.setSeries(values, currentIndex, currentLabel);
			}

			/**
			 * cachedEvalFor method.
			 * @param pos parameter.
			 * @return return value.
			 */
			protected Evaluation cachedEvalFor(Position pos) {
				if (pos == null) {
					return null;
				}
				if (engineCacheConfigKey != null && !engineCacheConfigKey.isBlank()) {
					EngineCacheEntry cached = engineCache.get(engineCacheConfigKey + "|" + pos.toString());
					if (cached != null && cached.eval() != null && cached.eval().isValid()) {
						return cached.eval();
					}
				}
				if (latestAnalysis != null && latestAnalysisVersion == positionVersion && position != null
						&& pos.toString().equals(position.toString())) {
					Output out = latestAnalysis.getBestOutput(1);
					if (out != null && out.getEvaluation() != null && out.getEvaluation().isValid()) {
						return out.getEvaluation();
					}
				}
				return null;
			}

			/**
			 * evalToCp method.
			 * @param pos parameter.
			 * @param eval parameter.
			 * @return return value.
			 */
			protected Double evalToCp(Position pos, Evaluation eval) {
				if (pos == null || eval == null || !eval.isValid()) {
					return null;
				}
				int value = eval.getValue();
				if (!pos.isWhiteTurn()) {
					value = -value;
				}
				if (eval.isMate()) {
					return value >= 0 ? 1000.0 : -1000.0;
				}
				return (double) value;
			}

			/**
			 * Class declaration.
			  *
			  * @since 2026
			  * @author Lennart A. Conrad
			 */
			private static final class AblationResult {
				/**
				 * rows field.
				 */
				private final List<String[]> rows;
				/**
				 * matrix field.
				 */
				private final int[][] matrix;
				/**
				 * labels field.
				 */
				private final String[] labels;

				/**
				 * Constructor.
				 * @param rows parameter.
				 * @param matrix parameter.
				 * @param labels parameter.
				 */
				private AblationResult(List<String[]> rows, int[][] matrix, String[] labels) {
					this.rows = rows;
					this.matrix = matrix;
					this.labels = labels;
				}
			}

			/**
			 * refreshAblation method.
			 */
			protected void refreshAblation() {
				if (ablationModel == null || ablationToggle == null) {
					return;
				}
				if (!ablationToggle.isSelected() || position == null) {
					clearAblation();
					return;
				}
				Position snapshot = position.copyOf();
				long requestVersion = ++ablationVersion;
				SwingWorker<AblationResult, Void> worker = new SwingWorker<>() {
										/**
					 * Handles do in background.
					 * @return computed value
					 */
@Override
					protected AblationResult doInBackground() {
						int[][] matrix = ablationEvaluator.ablation(snapshot);
						AblationSupport.normalizeForWhite(snapshot, matrix);
						byte[] board = snapshot.getBoard();
						List<String[]> rows = new ArrayList<>();
						for (int i = 0; i < board.length; i++) {
							byte piece = board[i];
							if (Piece.isEmpty(piece)) {
								continue;
							}
							int file = Field.getX((byte) i);
							int rank = Field.getY((byte) i);
							int score = matrix[rank][file];
							rows.add(new String[] { AblationSupport.pieceName(piece), Field.toString((byte) i),
									AblationSupport.formatScore(score) });
						}
						String[] labels = AblationSupport.buildLabels(matrix, board);
						return new AblationResult(rows, matrix, labels);
					}

										/**
					 * Handles done.
					 */
@Override
					protected void done() {
						if (requestVersion != ablationVersion) {
							return;
						}
						try {
							AblationResult result = get();
							SwingUtilities.invokeLater(() -> applyAblationResult(result));
						} catch (Exception ex) {
							SwingUtilities.invokeLater(() -> clearAblation());
						}
					}
				};
				worker.execute();
			}

			/**
			 * applyAblationResult method.
			 * @param result parameter.
			 */
			private void applyAblationResult(AblationResult result) {
				if (ablationModel == null) {
					return;
				}
				ablationMatrix = result.matrix;
				ablationLabels = result.labels;
				ablationModel.setRowCount(0);
				for (String[] row : result.rows) {
					ablationModel.addRow(row);
				}
				if (boardPanel != null) {
					boardPanel.invalidateRenderCache();
					boardPanel.repaint();
				}
			}

			/**
			 * clearAblation method.
			 */
			private void clearAblation() {
				if (ablationModel != null) {
					ablationModel.setRowCount(0);
				}
				ablationMatrix = null;
				ablationLabels = null;
				if (boardPanel != null) {
					boardPanel.invalidateRenderCache();
					boardPanel.repaint();
				}
			}

			/**
			 * buildAblationLabels method.
			 * @param matrix parameter.
			 * @param board parameter.
			 * @return return value.
			 */
			protected String[] buildAblationLabels(int[][] matrix, byte[] board) {
				return AblationSupport.buildLabels(matrix, board);
			}

			/**
			 * normalizeAblationForWhite method.
			 * @param position parameter.
			 * @param matrix parameter.
			 */
			protected void normalizeAblationForWhite(Position position, int[][] matrix) {
				AblationSupport.normalizeForWhite(position, matrix);
			}

			/**
			 * ablationMaterialScales method.
			 * @param matrix parameter.
			 * @param board parameter.
			 * @return return value.
			 */
			protected double[] ablationMaterialScales(int[][] matrix, byte[] board) {
				return AblationSupport.materialScales(matrix, board);
			}

			/**
			 * formatAblationScore method.
			 * @param score parameter.
			 * @return return value.
			 */
			protected String formatAblationScore(int score) {
				return AblationSupport.formatScore(score);
			}

			/**
			 * ablationPieceName method.
			 * @param piece parameter.
			 * @return return value.
			 */
			protected String ablationPieceName(byte piece) {
				return AblationSupport.pieceName(piece);
			}

			/**
			 * refreshInfo method.
			 */
			protected void refreshInfo() {
				String fen = position.toString();
				String[] parts = fen.split(" ");
				String side = parts.length > 1 && "b".equals(parts[1]) ? "Black" : "White";
				StringBuilder sb = new StringBuilder();
				sb.append("Side to move: ").append(side).append('\n');
				sb.append("Move count: ").append(moveHistory.size()).append('\n');
				if (!moveHistory.isEmpty()) {
					sb.append("Last move: ").append(displaySan(moveHistory.get(moveHistory.size() - 1))).append('\n');
				}
				infoArea.setText(sb.toString());
			}

			/**
			 * updateAnnotatePanel method.
			 */
			protected void updateAnnotatePanel() {
				if (annotateCommentArea == null || annotateMoveLabel == null) {
					return;
				}
				PgnNode node = pgnNavigator != null ? pgnNavigator.current : null;
				boolean hasMove = node != null && node.parent != null;
				annotateCommentArea.setEnabled(hasMove);
				if (annotateSaveButton != null) {
					annotateSaveButton.setEnabled(hasMove);
				}
				if (annotateClearCommentButton != null) {
					annotateClearCommentButton.setEnabled(hasMove);
				}
				if (annotateClearNagButton != null) {
					annotateClearNagButton.setEnabled(hasMove);
				}
				for (JCheckBox box : annotateNagButtons.values()) {
					box.setEnabled(hasMove);
				}
				if (!hasMove) {
					annotateMoveLabel.setText("No move selected");
					annotateCommentArea.setText("");
					if (annotateNagGroup != null) {
						annotateNagGroup.clearSelection();
					}
					return;
				}
				String prefix = formatMovePrefix(node);
				String moveText = (node.san != null && !node.san.isBlank()) ? displaySan(node.san) : "—";
				annotateMoveLabel.setText(prefix.isEmpty() ? moveText : (prefix + " " + moveText));
				annotateCommentArea.setText(node.comment == null ? "" : node.comment);
				if (annotateNagGroup != null) {
					annotateNagGroup.clearSelection();
				}
				JCheckBox selected = annotateNagButtons.get(node.nag);
				if (selected != null) {
					selected.setSelected(true);
				}
			}

			/**
			 * refreshReport method.
			 */
			protected void refreshReport() {
				if (reportListModel == null) {
					return;
				}
				rebuildReportList();
			}

			/**
			 * saveAnnotateComment method.
			 */
			protected void saveAnnotateComment() {
				if (annotateCommentArea == null) {
					return;
				}
				PgnNode node = pgnNavigator != null ? pgnNavigator.current : null;
				if (node == null || node.parent == null) {
					return;
				}
				String text = annotateCommentArea.getText();
				String trimmed = text == null ? "" : text.trim();
				node.comment = trimmed.isEmpty() ? null : trimmed;
				refreshHistory();
				updateAnnotatePanel();
			}

			/**
			 * clearAnnotateComment method.
			 */
			protected void clearAnnotateComment() {
				PgnNode node = pgnNavigator != null ? pgnNavigator.current : null;
				if (node == null || node.parent == null) {
					return;
				}
				node.comment = null;
				if (annotateCommentArea != null) {
					annotateCommentArea.setText("");
				}
				refreshHistory();
				updateAnnotatePanel();
			}

			/**
			 * clearAnnotateNag method.
			 */
			protected void clearAnnotateNag() {
				PgnNode node = pgnNavigator != null ? pgnNavigator.current : null;
				if (node == null || node.parent == null) {
					return;
				}
				node.nag = 0;
				if (annotateNagGroup != null) {
					annotateNagGroup.clearSelection();
				}
				refreshHistory();
				updateAnnotatePanel();
			}

			/**
			 * setAnnotateNag method.
			 * @param nag parameter.
			 */
			protected void setAnnotateNag(int nag) {
				PgnNode node = pgnNavigator != null ? pgnNavigator.current : null;
				if (node == null || node.parent == null) {
					return;
				}
				node.nag = nag;
				refreshHistory();
				updateAnnotatePanel();
			}

			/**
			 * collectMainlineNodes method.
			 * @return return value.
			 */
			protected List<PgnNode> collectMainlineNodes() {
				ensureGameTree();
				if (pgnNavigator == null || pgnNavigator.root == null) {
					return List.of();
				}
				List<PgnNode> nodes = new ArrayList<>();
				PgnNode cursor = pgnNavigator.root;
				while (cursor != null && cursor.mainNext != null) {
					cursor = cursor.mainNext;
					nodes.add(cursor);
				}
				return nodes;
			}

			/**
			 * rebuildReportList method.
			 */
			protected void rebuildReportList() {
				if (reportListModel == null) {
					return;
				}
				reportListModel.clear();
				ensureGameTree();
				if (pgnNavigator == null || pgnNavigator.root == null) {
					if (reportStatusLabel != null && reportWorker == null) {
						reportStatusLabel.setText("No game loaded");
					}
					return;
				}
				List<PgnNode> nodes = collectMainlineNodes();
				if (nodes.isEmpty()) {
					if (reportStatusLabel != null && reportWorker == null) {
						reportStatusLabel.setText("No moves");
					}
					return;
				}
				PgnNode parent = pgnNavigator.root;
				int whiteLoss = 0;
				int blackLoss = 0;
				int whiteCount = 0;
				int blackCount = 0;
				int selectedIndex = -1;
				for (int i = 0; i < nodes.size(); i++) {
					PgnNode node = nodes.get(i);
					Integer loss = centipawnLoss(parent, node);
					int nag = nagFromLoss(loss);
					String nagLabel = nagGlyphFromCode(nag);
					String evalLabel = formatEvalLabelForWhite(node.analysisEval,
							node.positionAfter != null && node.positionAfter.isWhiteTurn());
					String lossLabel = loss == null ? "—" : loss + " cp";
					String prefix = formatMovePrefix(node);
					reportListModel.addElement(new ReportEntry(node, prefix, node.san, evalLabel, lossLabel, nagLabel, loss));
					if (node == pgnNavigator.current) {
						selectedIndex = i;
					}
					if (loss != null && parent != null && parent.positionAfter != null) {
						boolean whiteMove = parent.positionAfter.isWhiteTurn();
						if (whiteMove) {
							whiteLoss += loss;
							whiteCount++;
						} else {
							blackLoss += loss;
							blackCount++;
						}
					}
					parent = node;
				}
				if (reportList != null && selectedIndex >= 0) {
					reportList.setSelectedIndex(selectedIndex);
					reportList.ensureIndexIsVisible(selectedIndex);
				}
				if (reportStatusLabel != null && reportWorker == null) {
					if (whiteCount + blackCount == 0) {
						reportStatusLabel.setText("Not analyzed");
					} else {
						String whiteAvg = whiteCount == 0 ? "—" : (whiteLoss / whiteCount) + " cp";
						String blackAvg = blackCount == 0 ? "—" : (blackLoss / blackCount) + " cp";
						reportStatusLabel.setText("Avg CPL — White " + whiteAvg + "  Black " + blackAvg);
					}
				}
				if (reportApplyNagButton != null) {
					reportApplyNagButton.setEnabled(whiteCount + blackCount > 0);
				}
			}

			/**
			 * centipawnLoss method.
			 * @param parent parameter.
			 * @param node parameter.
			 * @return return value.
			 */
			protected Integer centipawnLoss(PgnNode parent, PgnNode node) {
				if (parent == null || node == null) {
					return null;
				}
				Evaluation bestEval = parent.analysisEval;
				Evaluation actualEval = node.analysisEval;
				if (bestEval == null || actualEval == null) {
					return null;
				}
				if (bestEval.isMate() || actualEval.isMate()) {
					return null;
				}
				int bestValue = bestEval.getValue();
				int actualValue = actualEval.getValue();
				int actualForMover = -actualValue;
				int loss = bestValue - actualForMover;
				return Math.max(0, loss);
			}

			/**
			 * nagFromLoss method.
			 * @param loss parameter.
			 * @return return value.
			 */
			protected int nagFromLoss(Integer loss) {
				if (loss == null) {
					return 0;
				}
				if (loss >= 250) {
					return 4; // ??
				}
				if (loss >= 120) {
					return 2; // ?
				}
				if (loss >= 60) {
					return 6; // ?!
				}
				return 0;
			}

			/**
			 * applyReportNags method.
			 */
			protected void applyReportNags() {
				ensureGameTree();
				if (pgnNavigator == null || pgnNavigator.root == null) {
					return;
				}
				List<PgnNode> nodes = collectMainlineNodes();
				PgnNode parent = pgnNavigator.root;
				for (PgnNode node : nodes) {
					Integer loss = centipawnLoss(parent, node);
					int nag = nagFromLoss(loss);
					node.nag = nag;
					parent = node;
				}
				refreshHistory();
				updateAnnotatePanel();
			}


}
