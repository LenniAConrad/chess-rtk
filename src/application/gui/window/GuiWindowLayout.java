package application.gui.window;
import application.Config;
import application.cli.Format;
import application.cli.command.CommandSupport;
import application.gui.GuiTheme;
import application.gui.model.CommandFieldBinding;
import application.gui.model.CommandFieldSpec;
import application.gui.model.CommandFieldType;
import application.gui.model.CommandSpec;
import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;
import application.gui.ui.ThemedScrollBarUI;
import application.gui.ui.ThemedSliderUI;
import application.gui.ui.ThemedTabbedPaneUI;
import application.gui.ui.ThemedToggleIcon;
import application.gui.util.TransferableImage;
import application.gui.history.layout.HistoryMovesCardBuilder;
import application.gui.history.status.HistoryStatusBar;
import application.gui.history.ui.HistoryUiFactory;
import application.gui.layout.command.CommandCenterContext;
import application.gui.layout.command.CommandCenterPanel;
import application.gui.layout.engine.EngineCardBuilder;
import application.gui.layout.explorer.EcoExplorerCardBuilder;
import application.gui.layout.tab.AnnotateTabContext;
import application.gui.layout.tab.AnnotateTabPanel;
import application.gui.layout.tab.ReportTabContext;
import application.gui.layout.tab.ReportTabPanel;
import application.gui.layout.tab.VariationTabContext;
import application.gui.layout.tab.VariationTabPanel;
import application.gui.render.MovePairCellRenderer;
import application.gui.render.PvCellRenderer;
import application.gui.render.ReportCellRenderer;
import application.gui.render.TreeCellRenderer;
import application.gui.model.HistoryEntry;
import application.gui.window.PgnNode;
import application.gui.model.PvEntry;
import application.gui.model.ReportEntry;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
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
import java.awt.event.ActionListener;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.TransferHandler;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
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
 * GuiWindowLayout class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
class GuiWindowLayout extends GuiWindowEngine {
	/**
	 * layoutBuilderSupport field.
	 */
	private final LayoutBuilderSupport layoutBuilderSupport = new LayoutBuilderSupport();
	/**
	 * commandCenterContext field.
	 */
	private final CommandCenterContext commandCenterContext = layoutBuilderSupport;
	/**
	 * annotateTabContext field.
	 */
	private final AnnotateTabContext annotateTabContext = layoutBuilderSupport;
	/**
	 * LayoutBuilderSupport class.
	 *
	 * Provides class behavior for the GUI module.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	private final class LayoutBuilderSupport implements CommandCenterContext, AnnotateTabContext, ReportTabContext, VariationTabContext {
		/**
		 * track method.
		 *
		 * @param list parameter.
		 * @param value parameter.
		 */
		private <T> void track(List<T> list, T value) {
			if (list != null && value != null) {
				list.add(value);
			}
		}

				/**
		 * Handles create muted label.
		 * @param text text value
		 * @return computed value
		 */
@Override
		/**
		 * createMutedLabel method.
		 *
		 * @param text parameter.
		 * @return return value.
		 */
		public JLabel createMutedLabel(String text) {
			return GuiWindowLayout.this.mutedLabel(text);
		}

				/**
		 * Handles create themed button.
		 * @param text text value
		 * @param action action value
		 * @return computed value
		 */
@Override
		/**
		 * createThemedButton method.
		 *
		 * @param text parameter.
		 * @param action parameter.
		 * @return return value.
		 */
		public JButton createThemedButton(String text, ActionListener action) {
			return GuiWindowLayout.this.themedButton(text, action);
		}

				/**
		 * Handles muted label.
		 * @param text text value
		 * @return computed value
		 */
@Override
		/**
		 * mutedLabel method.
		 *
		 * @param text parameter.
		 * @return return value.
		 */
		public JLabel mutedLabel(String text) {
			return GuiWindowLayout.this.mutedLabel(text);
		}

				/**
		 * Handles themed button.
		 * @param text text value
		 * @param action action value
		 * @return computed value
		 */
@Override
		/**
		 * themedButton method.
		 *
		 * @param text parameter.
		 * @param action parameter.
		 * @return return value.
		 */
		public JButton themedButton(String text, ActionListener action) {
			return GuiWindowLayout.this.themedButton(text, action);
		}

				/**
		 * Handles create themed checkbox.
		 * @param text text value
		 * @param selected selected value
		 * @param action action value
		 * @return computed value
		 */
@Override
		/**
		 * createThemedCheckbox method.
		 *
		 * @param text parameter.
		 * @param selected parameter.
		 * @param action parameter.
		 * @return return value.
		 */
		public JCheckBox createThemedCheckbox(String text, boolean selected, ActionListener action) {
			return GuiWindowLayout.this.themedCheckbox(text, selected, action);
		}

				/**
		 * Handles build flat card.
		 * @param title title value
		 * @return computed value
		 */
@Override
		/**
		 * buildFlatCard method.
		 *
		 * @param title parameter.
		 * @return return value.
		 */
		public RoundedPanel buildFlatCard(String title) {
			return GuiWindowLayout.this.createFlatCard(title);
		}

				/**
		 * Handles scaled dimension.
		 * @param base base value
		 * @return computed value
		 */
@Override
		/**
		 * scaledDimension method.
		 *
		 * @param base parameter.
		 * @return return value.
		 */
		public Dimension scaledDimension(Dimension base) {
			return scaleDimension(base);
		}

				/**
		 * Handles scaled row height.
		 * @param base base value
		 * @return computed value
		 */
@Override
		/**
		 * scaledRowHeight method.
		 *
		 * @param base parameter.
		 * @return return value.
		 */
		public int scaledRowHeight(int base) {
			return Math.round(base * uiScale);
		}

				/**
		 * Handles register flat card.
		 * @param card card value
		 */
@Override
		/**
		 * registerFlatCard method.
		 *
		 * @param card parameter.
		 */
		public void registerFlatCard(RoundedPanel card) {
			if (card != null && !flatCards.contains(card)) {
				flatCards.add(card);
			}
		}

				/**
		 * Handles register combo box.
		 * @param combo combo value
		 */
@Override
		/**
		 * registerComboBox method.
		 *
		 * @param combo parameter.
		 */
		public void registerComboBox(JComboBox<?> combo) {
			track(combos, combo);
		}

				/**
		 * Handles register text field.
		 * @param field field value
		 */
@Override
		/**
		 * registerTextField method.
		 *
		 * @param field parameter.
		 */
		public void registerTextField(JTextField field) {
			track(textFields, field);
		}

				/**
		 * Handles register text area.
		 * @param area area value
		 */
@Override
		/**
		 * registerTextArea method.
		 *
		 * @param area parameter.
		 */
		public void registerTextArea(JTextArea area) {
			track(textAreas, area);
		}

				/**
		 * Handles register list.
		 * @param list list value
		 */
@Override
		/**
		 * registerList method.
		 *
		 * @param list parameter.
		 */
		public void registerList(JList<?> list) {
			track(lists, list);
		}

				/**
		 * Handles register scroll pane.
		 * @param scroll scroll value
		 */
@Override
		/**
		 * registerScrollPane method.
		 *
		 * @param scroll parameter.
		 */
		public void registerScrollPane(JScrollPane scroll) {
			track(scrolls, scroll);
		}

				/**
		 * Handles register button.
		 * @param button button value
		 */
@Override
		/**
		 * registerButton method.
		 *
		 * @param button parameter.
		 */
		public void registerButton(JButton button) {
			track(buttons, button);
		}
		
				/**
		 * Handles create flat card.
		 * @param title title value
		 * @param showTitle show title value
		 * @return computed value
		 */
@Override
		/**
		 * createFlatCard method.
		 *
		 * @param title parameter.
		 * @param showTitle parameter.
		 * @return return value.
		 */
		public RoundedPanel createFlatCard(String title, boolean showTitle) {
			return GuiWindowLayout.this.createFlatCard(title, showTitle);
		}

				/**
		 * Handles register table.
		 * @param table table value
		 */
@Override
		/**
		 * registerTable method.
		 *
		 * @param table parameter.
		 */
		public void registerTable(JTable table) {
			track(tables, table);
		}
		
				/**
		 * Handles preview node.
		 * @param node node value
		 * @param screenPoint screen point value
		 */
@Override
		/**
		 * previewNode method.
		 *
		 * @param node parameter.
		 * @param screenPoint parameter.
		 */
		public void previewNode(PgnNode node, Point screenPoint) {
			GuiWindowLayout.this.previewNodeHover(node, screenPoint);
		}

				/**
		 * Handles clear hover previews.
		 */
@Override
		/**
		 * clearHoverPreviews method.
		 */
		public void clearHoverPreviews() {
			GuiWindowLayout.this.clearHoverPreviews();
		}

				/**
		 * Handles apply pgn node.
		 * @param node node value
		 */
@Override
		/**
		 * applyPgnNode method.
		 *
		 * @param node parameter.
		 */
		public void applyPgnNode(PgnNode node) {
			if (node != null) {
				GuiWindowLayout.this.applyPgnNode(node);
			}
		}

				/**
		 * Handles request fen toggle.
		 */
@Override
		/**
		 * requestFenToggle method.
		 */
		public void requestFenToggle() {
			toggleFenMode();
		}

				/**
		 * Handles request command run.
		 */
@Override
		/**
		 * requestCommandRun method.
		 */
		public void requestCommandRun() {
			runCommandFromForm();
		}

				/**
		 * Handles request command stop.
		 */
@Override
		/**
		 * requestCommandStop method.
		 */
		public void requestCommandStop() {
			stopCommand();
		}

				/**
		 * Handles request command help.
		 */
@Override
		/**
		 * requestCommandHelp method.
		 */
		public void requestCommandHelp() {
			runHelp();
		}

				/**
		 * Handles request recent command.
		 * @param index index value
		 */
@Override
		/**
		 * requestRecentCommand method.
		 *
		 * @param index parameter.
		 */
		public void requestRecentCommand(int index) {
			runRecentCommand(index);
		}

				/**
		 * Handles request command form update.
		 */
@Override
		/**
		 * requestCommandFormUpdate method.
		 */
		public void requestCommandFormUpdate() {
			updateCommandForm();
		}

				/**
		 * Handles add nag button.
		 * @param container container value
		 * @param label label value
		 * @param nag nag value
		 */
@Override
		/**
		 * addNagButton method.
		 *
		 * @param container parameter.
		 * @param label parameter.
		 * @param nag parameter.
		 */
		public void addNagButton(JPanel container, String label, int nag) {
			GuiWindowLayout.this.addNagButton(container, label, nag);
		}
	}

		/**
		 * GuiWindowLayout method.
		 *
		 * @param fen parameter.
		 * @param whiteDown parameter.
		 * @param lightMode parameter.
		 */
		public GuiWindowLayout(String fen, boolean whiteDown, boolean lightMode) {
			this.position = new Position(fen.trim());
			this.guiState = loadGuiState(lightMode, whiteDown);
			this.whiteDown = guiState.whiteDown;
			this.lightMode = guiState.lightMode;
			this.boardHueDegrees = guiState.boardHueDegrees;
			this.boardBrightness = guiState.boardBrightness;
			this.boardSaturation = guiState.boardSaturation;
			this.animationMillis = guiState.animationMillis;
			this.showCoords = guiState.showCoords;
			this.hoverLegal = guiState.hoverLegal;
			this.hoverHighlight = guiState.hoverHighlight;
			this.hoverOnlyLegal = guiState.hoverOnlyLegal;
			this.highContrast = guiState.highContrast;
			this.compactMode = guiState.compactMode;
			this.figurineSan = guiState.figurineSan;
			this.uiScale = 1.0f;
			this.guiState.uiScale = 1.0f;
			this.sidebarVisible = guiState.sidebarVisible;
			this.theme = this.lightMode ? GuiTheme.light() : GuiTheme.dark();
			updateHistoryStart(position);
			this.frame = new JFrame("ChessRTK GUI");
			this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			this.baseFrameMin = new Dimension(1040, 760);
			this.frame.setMinimumSize(scaleDimension(baseFrameMin));
			this.frame.setLocationByPlatform(true);
			this.frame.setIconImage(Pictures.BlackRook);
			this.frame.addWindowListener(new WindowAdapter() {
								/**
				 * Handles window closing.
				 * @param e e value
				 */
@Override
				public void windowClosing(WindowEvent e) {
					captureGuiState();
					saveGuiState();
					uninstallGlobalNavigationDispatcher();
					stopPvHoverAnimation();
					stopCommand();
					stopEngine();
					stopReportAnalysis();
					shutdownEngineInstance(); }
								/**
				 * Handles window deactivated.
				 * @param e e value
				 */
@Override
				public void windowDeactivated(WindowEvent e) {
					stopPvHoverAnimation();
					if (boardPanel != null) {
						boardPanel.clearPreview(); }
					hideEnginePvMiniBoardPreview(); }
			});
			this.root = new GradientPanel(theme.backgroundTop(), theme.backgroundBottom());
			this.root.setLayout(new BorderLayout(12, 12));
			this.root.setBorder(new EmptyBorder(12, 12, 12, 12));
			this.frame.setContentPane(root);
			JPanel left = new JPanel(new BorderLayout(16, 16));
			left.setOpaque(false);
			JPanel focusCornerRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
			focusCornerRow.setOpaque(false);
			focusExitCornerButton = themedButton("Exit Focus", e -> exitFocusModes());
			focusExitCornerButton.setToolTipText("Exit focus mode (Esc)");
			focusCornerRow.add(focusExitCornerButton);
			focusCornerRow.setVisible(false);
			focusCornerHost = focusCornerRow;
			left.add(focusCornerRow, BorderLayout.NORTH);
			this.boardPanel = new BoardPanel(this);
			this.baseBoardMin = new Dimension(520, 520);
			this.boardPanel.setMinimumSize(scaleDimension(baseBoardMin));
			this.evalBar = new EvalBar(this);
			JPanel barStack = new JPanel();
			barStack.setOpaque(false);
			barStack.setLayout(new BoxLayout(barStack, BoxLayout.X_AXIS));
			barStack.add(evalBar);
			boardBars = barStack;
			JPanel boardWrap = new JPanel(new BorderLayout(8, 8));
			this.boardWrap = boardWrap;
			boardWrap.setOpaque(false);
			this.baseBoardWrapMin = new Dimension(700, 520);
			boardWrap.setMinimumSize(scaleDimension(baseBoardWrapMin));
			boardWrap.add(barStack, BorderLayout.WEST);
			boardWrap.add(boardPanel, BorderLayout.CENTER);
			boardWrap.add(buildBoardActionStrip(), BorderLayout.EAST);
			RoundedPanel boardCard = new RoundedPanel(0);
			this.boardCard = boardCard;
			flatCards.add(boardCard);
			boardCard.setLayout(new BorderLayout(8, 8));
			boardCard.setBorder(new EmptyBorder(12, 12, 12, 12));
			boardCard.setContent(boardWrap);
			left.add(boardCard, BorderLayout.CENTER);
			this.leftPane = left;
			this.baseLeftMin = new Dimension(720, 520);
			left.setMinimumSize(scaleDimension(baseLeftMin));
			movesCard = buildMovesCard();
			tagsCard = buildTagsCard();
			RoundedPanel engineCard = buildEngineCard();
			analysisView = buildAnalysisTab(movesCard, engineCard, tagsCard);
			JComponent pgnView = buildPgnTab();
			JComponent ecoView = buildEcoEditorTab();
			boardEditorPane = new BoardEditorPane(this);
			editorTabs = new JTabbedPane();
			editorTabs.putClientProperty("editorTabs", Boolean.TRUE);
			tabs.add(editorTabs);
			editorTabs.addTab("board.pgn", left);
			editorTabs.addTab("board.editor", wrapTabComponent(boardEditorPane));
			editorTabs.addTab("eco.tsv", wrapTabComponent(ecoView));
			editorTabs.addTab("game.pgn", wrapTabComponent(pgnView));
			editorTabs.addChangeListener(e -> {
				stopPvHoverAnimation();
				if (boardPanel != null) {
					boardPanel.clearPreview(); }
				hideEnginePvMiniBoardPreview();
				int selected = editorTabs.getSelectedIndex();
				if (selected >= 0 && "board.pgn".equalsIgnoreCase(editorTabs.getTitleAt(selected)) && boardPanel != null) {
					boardPanel.requestFocusInWindow(); }
			});
			JScrollPane right = buildSidebar();
			this.baseSidebarMin = new Dimension(360, 520);
			right.setMinimumSize(scaleDimension(baseSidebarMin));
			underboardCard = null;
			root.add(buildTopBar(), BorderLayout.NORTH);
			mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, right, editorTabs);
			mainSplit.setResizeWeight(0.25);
			mainSplit.setDividerSize(1);
			mainSplit.setContinuousLayout(true);
			mainSplit.setBorder(null);
			mainSplit.setOpaque(false);
			mainSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
				captureGuiState();
				scheduleGuiStateSave();
			});
			JPanel center = new JPanel(new BorderLayout());
			center.setOpaque(false);
			center.add(buildActivityBar(), BorderLayout.WEST);
			center.add(mainSplit, BorderLayout.CENTER);
			root.add(center, BorderLayout.CENTER);
			JPanel south = new JPanel(new BorderLayout());
			south.setOpaque(false);
			south.add(buildBottomPanel(), BorderLayout.CENTER);
			south.add(buildStatusBar(), BorderLayout.SOUTH);
			root.add(south, BorderLayout.SOUTH);
			applyTheme();
			refreshAll();
			installNavigationBindings();
			installDragAndDrop();
			installStateListeners();
			frame.pack();
			if (guiState.windowWidth > 0 && guiState.windowHeight > 0) {
				frame.setSize(guiState.windowWidth, guiState.windowHeight); }
			if (guiState.splitDivider > 0) {
				mainSplit.setDividerLocation(guiState.splitDivider); }
			if (analysisSplit != null && guiState.analysisDivider > 0) {
				analysisSplit.setDividerLocation(guiState.analysisDivider); }
			if (rightTabs != null && guiState.rightTabIndex >= 0 && guiState.rightTabIndex < rightTabs.getTabCount()) {
				rightTabs.setSelectedIndex(guiState.rightTabIndex); }
			updateSidebarToggleLabel();
			updateFocusToggleLabel();
			setSidebarVisible(sidebarVisible, false);
			frame.setVisible(true);
			boardPanel.requestFocusInWindow(); }
		/**
		 * buildControls method.
		 *
		 * @return return value.
		 */
		private JPanel buildControls() {
			JPanel stack = new JPanel();
			stack.setOpaque(false);
			stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
			stack.add(buildExplorerCard());
			stack.add(Box.createVerticalStrut(12));
			stack.add(buildViewCard());
			return stack; }
		/**
		 * buildTopBar method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildTopBar() {
			RoundedPanel bar = new RoundedPanel(0);
			topBar = bar;
			flatCards.add(bar);
			bar.setLayout(new BorderLayout(12, 8));
			bar.setBorder(new EmptyBorder(10, 12, 10, 12));
			JPanel utilityRow = new JPanel(new BorderLayout(8, 8));
			utilityRow.setOpaque(false);
			JPanel navActions = new JPanel(new java.awt.GridLayout(1, 0, 6, 6));
			navActions.setOpaque(false);
			JButton navBack = iconButton("<", e -> navigatePrev());
			navBack.setToolTipText("Previous move (Left)");
			JButton navForward = iconButton(">", e -> navigateNext());
			navForward.setToolTipText("Next move (Right)");
			navActions.add(navBack);
			navActions.add(navForward);
			utilityRow.add(navActions, BorderLayout.WEST);
			utilityField = new JTextField();
			utilityField.setToolTipText(
					"Utilities: e2e4, Nf3, best, vs, vs new, vs stop, flip, editor, board, pgn, eco, analysis, commands");
			utilityField.addActionListener(e -> {
				runUtilityInput(utilityField.getText());
				utilityField.selectAll();
			});
			textFields.add(utilityField);
			utilityRow.add(utilityField, BorderLayout.CENTER);
			JPanel utilityActions = new JPanel(new java.awt.GridLayout(1, 0, 6, 6));
			utilityActions.setOpaque(false);
			JButton bestButton = iconButton("Best", e -> playEngineBest());
			bestButton.setToolTipText("Play best move (Space)");
			JButton editButton = iconButton("Edit", e -> openBoardEditor());
			editButton.setToolTipText("Open board editor (E)");
			JButton copyFenButton = iconButton("FEN", e -> copyFen());
			copyFenButton.setToolTipText("Copy FEN (Ctrl+Shift+C)");
			JButton cmdButton = iconButton("Cmd", e -> openCommandPalette());
			cmdButton.setToolTipText("Command palette (Ctrl+P)");
			utilityActions.add(bestButton);
			utilityActions.add(editButton);
			utilityActions.add(copyFenButton);
			utilityActions.add(cmdButton);
			utilityRow.add(utilityActions, BorderLayout.EAST);
			JPanel fenRow = new JPanel(new BorderLayout(8, 8));
			fenRow.setOpaque(false);
			fenRow.add(mutedLabel("FEN"), BorderLayout.WEST);
			fenField = new JTextField(position.toString());
			textFields.add(fenField);
			fenRow.add(fenField, BorderLayout.CENTER);
			JButton load = themedButton("Load", e -> loadFenFromField());
			fenRow.add(load, BorderLayout.EAST);
			JPanel centerStack = new JPanel();
			centerStack.setOpaque(false);
			centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
			centerStack.add(utilityRow);
			centerStack.add(Box.createVerticalStrut(6));
			centerStack.add(fenRow);
			bar.add(centerStack, BorderLayout.CENTER);
			JPanel quickActions = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
			quickActions.setOpaque(false);
			JButton copyFen = themedButton("Copy FEN", e -> copyFen());
			copyFen.setToolTipText("Copy FEN (Ctrl+Shift+C)");
			JButton undoMove = themedButton("Undo", e -> undoMove());
			undoMove.setToolTipText("Undo (Ctrl+Z)");
			JButton resetPos = themedButton("Reset", e -> resetPosition());
			resetPos.setToolTipText("Reset position");
			JButton paletteButton = themedButton("Palette", e -> openCommandPalette());
			paletteButton.setToolTipText("Command palette (Ctrl+P)");
			quickActions.add(copyFen);
			quickActions.add(undoMove);
			quickActions.add(resetPos);
			quickActions.add(paletteButton);
			JPanel viewActions = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
			viewActions.setOpaque(false);
			sidebarToggleButton = themedButton("Hide Sidebar", e -> toggleSidebar());
			sidebarToggleButton.setToolTipText("Toggle sidebar (Ctrl+Shift+B)");
			focusToggleButton = themedButton("Focus Mode", e -> toggleFocusMode());
			focusToggleButton.setToolTipText("Focus mode (F11)");
			viewActions.add(sidebarToggleButton);
			viewActions.add(focusToggleButton);
			JPanel rightStack = new JPanel();
			rightStack.setOpaque(false);
			rightStack.setLayout(new BoxLayout(rightStack, BoxLayout.Y_AXIS));
			rightStack.add(quickActions);
			rightStack.add(Box.createVerticalStrut(6));
			rightStack.add(viewActions);
			bar.add(rightStack, BorderLayout.EAST);
			return bar; }
		/**
		 * buildActivityBar method.
		 *
		 * @return return value.
		 */
		private JComponent buildActivityBar() {
			JPanel bar = new JPanel();
			activityBar = bar;
			bar.setOpaque(true);
			bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
			bar.setBorder(new MatteBorder(0, 0, 0, 1, theme.border()));
			Dimension rail = scaleDimension(new Dimension(38, 10));
			bar.setPreferredSize(rail);
			bar.setMinimumSize(rail);
			bar.add(Box.createVerticalStrut(8));
			addActivityTabButton(bar, "◉", "Analysis", "Analysis");
			addActivityTabButton(bar, "♟", "Explorer", "Explorer");
			addActivityTabButton(bar, "✎", "Annotate", "Annotate");
			addActivityTabButton(bar, "≡", "Report", "Report");
			addActivityTabButton(bar, "⋯", "Variations", "Variations");
			addActivityTabButton(bar, "Δ", "Ablation", "Ablation");
			addActivityTabButton(bar, "⌘", "Commands", "Commands");
			addActivityTabButton(bar, "ℹ", "Info", "Info");
			bar.add(Box.createVerticalGlue());
			addActivityActionButton(bar, "⌕", "Command Palette (Ctrl+P)", this::openCommandPalette);
			addActivityActionButton(bar, "⛶", "Toggle Zen Mode", this::toggleZenMode);
			addActivityActionButton(bar, "⚙", "Settings", () -> {
				if (!sidebarVisible && !(focusMode || zenMode)) {
					setSidebarVisible(true, true); }
				selectRightTab("Controls");
			});
			bar.add(Box.createVerticalStrut(8));
			return bar; }
		/**
		 * addActivityTabButton method.
		 *
		 * @param bar parameter.
		 * @param label parameter.
		 * @param tooltip parameter.
		 * @param tabTitle parameter.
		 */
		private void addActivityTabButton(JPanel bar, String label, String tooltip, String tabTitle) {
			int index = tabIndexByTitle(tabTitle);
			if (index < 0) {
				return; }
			JButton button = createActivityButton(label, tooltip, () -> {
				if (!sidebarVisible && !(focusMode || zenMode)) {
					setSidebarVisible(true, true); }
				selectRightTab(index);
			});
			button.putClientProperty("activityTabIndex", index);
			bar.add(button);
			bar.add(Box.createVerticalStrut(6)); }
		/**
		 * addActivityActionButton method.
		 *
		 * @param bar parameter.
		 * @param label parameter.
		 * @param tooltip parameter.
		 * @param action parameter.
		 */
		private void addActivityActionButton(JPanel bar, String label, String tooltip, Runnable action) {
			JButton button = createActivityButton(label, tooltip, action);
			bar.add(button);
			bar.add(Box.createVerticalStrut(6)); }
		/**
		 * createActivityButton method.
		 *
		 * @param label parameter.
		 * @param tooltip parameter.
		 * @param action parameter.
		 * @return return value.
		 */
		private JButton createActivityButton(String label, String tooltip, Runnable action) {
			JButton button = new JButton(label);
			activityButtons.add(button);
			button.setOpaque(true);
			button.setFocusable(false);
			button.setToolTipText(tooltip);
			button.putClientProperty("activityBaseLabel", label);
			button.addMouseListener(new MouseAdapter() {
								/**
				 * Handles mouse entered.
				 * @param e e value
				 */
@Override
				public void mouseEntered(MouseEvent e) {
					button.putClientProperty("activityHover", Boolean.TRUE);
					updateActivitySelection(); }
								/**
				 * Handles mouse exited.
				 * @param e e value
				 */
@Override
				public void mouseExited(MouseEvent e) {
					button.putClientProperty("activityHover", Boolean.FALSE);
					updateActivitySelection(); }
			});
			if (action != null) {
				button.addActionListener(e -> action.run()); }
			return button; }
		/**
		 * buildStatusBar method.
		 *
		 * @return return value.
		 */
		private JComponent buildStatusBar() {
			HistoryStatusBar.Result statusBarResult = HistoryStatusBar.build(tabDependencies, uiFactory);
			statusBar = statusBarResult.component;
			statusLeftLabel = statusBarResult.statusLeft;
			statusMiddleLabel = statusBarResult.statusMiddle;
			statusRightLabel = statusBarResult.statusRight;
			statusEngineLabel = statusBarResult.statusEngine;
			statusProblemLabel = statusBarResult.statusProblem;
			statusPanelLabel = statusBarResult.statusPanel;
			statusModeLabel = statusBarResult.statusMode;
			statusEolLabel = statusBarResult.statusEol;
			statusEncodingLabel = statusBarResult.statusEncoding;
			statusBranchLabel = statusBarResult.statusBranch;
			statusThemeLabel = statusBarResult.statusTheme;
			statusTabPicker = statusBarResult.statusTabPicker;
			refreshStatusTabPicker();
			return statusBar;
		}
		/**
		 * buildBottomPanel method.
		 *
		 * @return return value.
		 */
		private JComponent buildBottomPanel() {
			JTabbedPane panel = new JTabbedPane();
			panelTabs = panel;
			tabs.add(panel);
			panel.setOpaque(true);
			panel.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
			panelOutputArea = new JTextArea(6, 60);
			panelOutputArea.setEditable(false);
			panelOutputArea.setLineWrap(true);
			panelOutputArea.setWrapStyleWord(true);
			textAreas.add(panelOutputArea);
			JScrollPane outputScroll = new JScrollPane(panelOutputArea);
			outputScroll.setBorder(BorderFactory.createEmptyBorder());
			scrolls.add(outputScroll);
			problemListModel = new DefaultListModel<>();
			problemList = new JList<>(problemListModel);
			lists.add(problemList);
			JScrollPane problemScroll = new JScrollPane(problemList);
			problemScroll.setBorder(BorderFactory.createEmptyBorder());
			scrolls.add(problemScroll);
			panelTerminalArea = new JTextArea(6, 60);
			panelTerminalArea.setEditable(false);
			panelTerminalArea.setLineWrap(true);
			panelTerminalArea.setWrapStyleWord(true);
			textAreas.add(panelTerminalArea);
			JScrollPane terminalScroll = new JScrollPane(panelTerminalArea);
			terminalScroll.setBorder(BorderFactory.createEmptyBorder());
			scrolls.add(terminalScroll);
			panelDebugArea = new JTextArea(6, 60);
			panelDebugArea.setEditable(false);
			panelDebugArea.setLineWrap(true);
			panelDebugArea.setWrapStyleWord(true);
			textAreas.add(panelDebugArea);
			JScrollPane debugScroll = new JScrollPane(panelDebugArea);
			debugScroll.setBorder(BorderFactory.createEmptyBorder());
			scrolls.add(debugScroll);
			panel.addTab("Output", outputScroll);
			panel.addTab("Problems", problemScroll);
			panel.addTab("Terminal", terminalScroll);
			panel.addTab("Debug", debugScroll);
			panel.setPreferredSize(scaleDimension(new Dimension(10, 160)));
			panel.setVisible(panelVisible);
			return panel; }
		/**
		 * buildBoardControlsRow method.
		 *
		 * @return return value.
		 */
		private JPanel buildBoardControlsRow() {
			JPanel row = new JPanel(new BorderLayout(8, 8));
			boardControlsRow = row;
			row.setOpaque(false);
			JPanel left = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
			left.setOpaque(false);
			JButton prev = iconButton("Prev", e -> navigatePrev());
			JButton next = iconButton("Next", e -> navigateNext());
			JButton undo = iconButton("Undo", e -> undoMove());
			JButton flip = iconButton("Flip", e -> flipBoard());
			prev.setToolTipText("Previous (Left)");
			next.setToolTipText("Next (Right)");
			undo.setToolTipText("Undo (Ctrl+Z)");
			flip.setToolTipText("Flip board (F)");
			left.add(prev);
			left.add(next);
			left.add(undo);
			left.add(flip);
			row.add(left, BorderLayout.WEST);
			JPanel right = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
			right.setOpaque(false);
			boardBestButton = iconButton("Best", e -> playEngineBest());
			boardBestButton.setToolTipText("Play best move (Space)");
			boardBestButton.setEnabled(false);
			right.add(boardBestButton);
			row.add(right, BorderLayout.EAST);
			return row; }
		/**
		 * buildBoardActionStrip method.
		 *
		 * @return return value.
		 */
		private JPanel buildBoardActionStrip() {
			JPanel strip = new JPanel();
			boardActionStrip = strip;
			strip.setOpaque(false);
			strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
			JButton snap = iconButton("Snap", e -> saveBoardImage());
			snap.setToolTipText("Save board image");
			JButton copy = iconButton("Copy", e -> copyBoardImage());
			copy.setToolTipText("Copy board image");
			JButton edit = iconButton("Edit", e -> openBoardEditor());
			edit.setToolTipText("Edit board (E)");
			JButton clear = iconButton("Clear", e -> boardPanel.clearShapes());
			clear.setToolTipText("Clear arrows/markers");
			JButton flip = iconButton("Flip", e -> flipBoard());
			flip.setToolTipText("Flip board (F)");
			strip.add(snap);
			strip.add(Box.createVerticalStrut(6));
			strip.add(copy);
			strip.add(Box.createVerticalStrut(6));
			strip.add(edit);
			strip.add(Box.createVerticalStrut(6));
			strip.add(clear);
			strip.add(Box.createVerticalStrut(6));
			strip.add(flip);
			return strip; }
		/**
		 * buildBoardMenu method.
		 */
		private void buildBoardMenu() {
			boardMenu = new JPopupMenu();
			JMenuItem copyFen = new JMenuItem("Copy FEN");
			copyFen.addActionListener(e -> copyFen());
			JMenuItem edit = new JMenuItem("Edit Board");
			edit.addActionListener(e -> openBoardEditor());
			JMenuItem flip = new JMenuItem("Flip Board");
			flip.addActionListener(e -> flipBoard());
			JMenuItem clear = new JMenuItem("Clear Arrows");
			clear.addActionListener(e -> boardPanel.clearShapes());
			boardMenu.add(copyFen);
			boardMenu.add(edit);
			boardMenu.add(flip);
			boardMenu.addSeparator();
			boardMenu.add(clear); }
		/**
		 * buildPositionCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildPositionCard() {
			RoundedPanel card = createFlatCard("Position");
			JPanel body = new JPanel();
			body.setOpaque(false);
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			JPanel row1 = new JPanel(new BorderLayout(8, 8));
			row1.setOpaque(false);
			JPanel row1Buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
			row1Buttons.setOpaque(false);
			JButton resetButton = iconButton("Rst", e -> resetPosition());
			resetButton.setToolTipText("Reset position");
			JButton undoButton = iconButton("Undo", e -> undoMove());
			undoButton.setToolTipText("Undo (Ctrl+Z)");
			JButton flipButton = iconButton("Flip", e -> flipBoard());
			flipButton.setToolTipText("Flip board (F)");
			JButton editButton = iconButton("Edit", e -> openBoardEditor());
			editButton.setToolTipText("Board editor (E)");
			row1Buttons.add(resetButton);
			row1Buttons.add(undoButton);
			row1Buttons.add(flipButton);
			row1Buttons.add(editButton);
			row1.add(row1Buttons, BorderLayout.CENTER);
			JPanel row2 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
			row2.setOpaque(false);
			JButton copyImage = iconButton("Copy", e -> copyBoardImage());
			copyImage.setToolTipText("Copy board image (Ctrl+Shift+I)");
			JButton saveImage = iconButton("Save", e -> saveBoardImage());
			saveImage.setToolTipText("Save board image (Ctrl+Shift+S)");
			JButton copyFenButton = iconButton("FEN", e -> copyFen());
			copyFenButton.setToolTipText("Copy FEN (Ctrl+Shift+C)");
			row2.add(copyImage);
			row2.add(saveImage);
			row2.add(copyFenButton);
			body.add(row1);
			body.add(Box.createVerticalStrut(8));
			body.add(row2);
			card.setContent(body);
			return card; }
		/**
		 * buildExplorerCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildExplorerCard() {
			RoundedPanel card = createFlatCard("Explorer");
			JPanel body = new JPanel();
			body.setOpaque(false);
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			JPanel loadRow = new JPanel(new BorderLayout(8, 8));
			loadRow.setOpaque(false);
			JPanel loadButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
			loadButtons.setOpaque(false);
			JButton loadFensButton = iconButton("FEN", e -> loadFenList());
			loadFensButton.setToolTipText("Load FEN list");
			JButton pastePgnButton = iconButton("PGN", e -> openPgnPasteDialog());
			pastePgnButton.setToolTipText("Paste PGN");
			loadButtons.add(loadFensButton);
			loadButtons.add(pastePgnButton);
			fenOpenButton = iconButton("Open", e -> openFenSourceFile());
			fenOpenButton.setEnabled(false);
			fenOpenButton.setToolTipText("Open the loaded FEN/PGN file");
			loadButtons.add(fenOpenButton);
			loadRow.add(loadButtons, BorderLayout.WEST);
			fenSourceLabel = mutedLabel("No list loaded");
			loadRow.add(fenSourceLabel, BorderLayout.CENTER);
			JPanel navRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
			navRow.setOpaque(false);
			JButton prevButton = iconButton("Prev", e -> prevFen());
			prevButton.setToolTipText("Previous FEN");
			JButton nextButton = iconButton("Next", e -> nextFen());
			nextButton.setToolTipText("Next FEN");
			JButton randomButton = iconButton("Rand", e -> randomFen());
			randomButton.setToolTipText("Random FEN");
			navRow.add(prevButton);
			navRow.add(nextButton);
			navRow.add(randomButton);
			JPanel jumpRow = new JPanel(new BorderLayout(8, 8));
			jumpRow.setOpaque(false);
			fenIndexField = new JTextField("1");
			textFields.add(fenIndexField);
			jumpRow.add(fenIndexField, BorderLayout.CENTER);
			jumpRow.add(themedButton("Go", e -> jumpFen()), BorderLayout.EAST);
			fenListStatus = mutedLabel("—");
			jumpRow.add(fenListStatus, BorderLayout.WEST);
			body.add(loadRow);
			body.add(Box.createVerticalStrut(8));
			body.add(navRow);
			body.add(Box.createVerticalStrut(8));
			body.add(jumpRow);
			card.setContent(body);
			return card; }
		/**
		 * buildViewCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildViewCard() {
			RoundedPanel card = createFlatCard("View");
			JPanel body = new JPanel();
			body.setOpaque(false);
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			JPanel togglesRow1 = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
			togglesRow1.setOpaque(false);
			legalToggle = themedCheckbox("Legal hints", guiState.showLegal, e -> {
				guiState.showLegal = legalToggle.isSelected();
				refreshBoard();
			});
			legalToggle.setToolTipText("Toggle legal hints (L)");
			hoverLegalToggle = themedCheckbox("Hover hints", guiState.hoverLegal, e -> {
				hoverLegal = hoverLegalToggle.isSelected();
				guiState.hoverLegal = hoverLegal;
				if (!hoverLegal) {
					hoverOnlyLegal = false;
					if (hoverOnlyToggle != null) {
						hoverOnlyToggle.setSelected(false);
						hoverOnlyToggle.setEnabled(false); }
					guiState.hoverOnlyLegal = false;
				} else if (hoverOnlyToggle != null) {
					hoverOnlyToggle.setEnabled(true); }
				refreshBoard();
			});
			hoverHighlightToggle = themedCheckbox("Hover glow", guiState.hoverHighlight, e -> {
				hoverHighlight = hoverHighlightToggle.isSelected();
				guiState.hoverHighlight = hoverHighlight;
				refreshBoard();
			});
			hoverOnlyToggle = themedCheckbox("Hover only", guiState.hoverOnlyLegal, e -> {
				hoverOnlyLegal = hoverOnlyToggle.isSelected();
				guiState.hoverOnlyLegal = hoverOnlyLegal;
				refreshBoard();
			});
			hoverOnlyToggle.setEnabled(guiState.hoverLegal);
			togglesRow1.add(legalToggle);
			togglesRow1.add(hoverLegalToggle);
			togglesRow1.add(hoverHighlightToggle);
			togglesRow1.add(hoverOnlyToggle);
			JPanel togglesRow2 = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
			togglesRow2.setOpaque(false);
			coordsToggle = themedCheckbox("Coordinates", guiState.showCoords, e -> {
				showCoords = coordsToggle.isSelected();
				guiState.showCoords = showCoords;
				refreshBoard();
			});
			movesToggle = themedCheckbox("Moves", guiState.showMoves, e -> {
				guiState.showMoves = movesToggle.isSelected();
				toggleMoves();
			});
			tagsToggle = themedCheckbox("Tags", guiState.showTags, e -> {
				guiState.showTags = tagsToggle.isSelected();
				toggleTags();
			});
			bestMoveToggle = themedCheckbox("Best move", guiState.showBestMove, e -> {
				guiState.showBestMove = bestMoveToggle.isSelected();
				refreshBoard();
			});
			togglesRow2.add(coordsToggle);
			togglesRow2.add(movesToggle);
			togglesRow2.add(tagsToggle);
			togglesRow2.add(bestMoveToggle);
			JPanel togglesRow3 = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
			togglesRow3.setOpaque(false);
			darkToggle = themedCheckbox("Dark mode", !lightMode, e -> toggleTheme());
			contrastToggle = themedCheckbox("High contrast", highContrast, e -> toggleContrast());
			compactToggle = themedCheckbox("Compact", compactMode, e -> {
				compactMode = compactToggle.isSelected();
				guiState.compactMode = compactMode;
				applyTheme();
				frame.revalidate();
			});
			figurineSanToggle = themedCheckbox("Figurine SAN", figurineSan, e ->
				applyFigurineSanSetting(figurineSanToggle.isSelected()));
			togglesRow3.add(darkToggle);
			togglesRow3.add(contrastToggle);
			togglesRow3.add(compactToggle);
			togglesRow3.add(figurineSanToggle);
			JPanel hueRow = new JPanel(new BorderLayout(8, 8));
			hueRow.setOpaque(false);
			hueRow.add(mutedLabel("Board hue"), BorderLayout.WEST);
			hueSlider = new JSlider(0, 360, boardHueDegrees);
			sliders.add(hueSlider);
			hueValueLabel = mutedLabel(boardHueDegrees + "°");
			hueSlider.addChangeListener(new ChangeListener() {
								/**
				 * Handles state changed.
				 * @param e e value
				 */
@Override
				public void stateChanged(ChangeEvent e) {
					boardHueDegrees = hueSlider.getValue();
					guiState.boardHueDegrees = boardHueDegrees;
					hueValueLabel.setText(boardHueDegrees + "°");
					refreshBoard();
					if (editorDialog != null) {
						editorDialog.repaintBoard(); }
					if (boardEditorPane != null) {
						boardEditorPane.repaintBoard(); } }
			});
			hueRow.add(hueSlider, BorderLayout.CENTER);
			hueRow.add(hueValueLabel, BorderLayout.EAST);
			JPanel brightnessRow = new JPanel(new BorderLayout(8, 8));
			brightnessRow.setOpaque(false);
			brightnessRow.add(mutedLabel("Board brightness"), BorderLayout.WEST);
			brightnessSlider = new JSlider(40, 200, boardBrightness);
			sliders.add(brightnessSlider);
			brightnessValueLabel = mutedLabel(boardBrightness + "%");
			brightnessSlider.addChangeListener(new ChangeListener() {
								/**
				 * Handles state changed.
				 * @param e e value
				 */
@Override
				public void stateChanged(ChangeEvent e) {
					boardBrightness = brightnessSlider.getValue();
					guiState.boardBrightness = boardBrightness;
					brightnessValueLabel.setText(boardBrightness + "%");
					refreshBoard();
					if (editorDialog != null) {
						editorDialog.repaintBoard(); }
					if (boardEditorPane != null) {
						boardEditorPane.repaintBoard(); } }
			});
			brightnessRow.add(brightnessSlider, BorderLayout.CENTER);
			brightnessRow.add(brightnessValueLabel, BorderLayout.EAST);
			JPanel saturationRow = new JPanel(new BorderLayout(8, 8));
			saturationRow.setOpaque(false);
			saturationRow.add(mutedLabel("Board saturation"), BorderLayout.WEST);
			saturationSlider = new JSlider(40, 200, boardSaturation);
			sliders.add(saturationSlider);
			saturationValueLabel = mutedLabel(boardSaturation + "%");
			saturationSlider.addChangeListener(new ChangeListener() {
								/**
				 * Handles state changed.
				 * @param e e value
				 */
@Override
				public void stateChanged(ChangeEvent e) {
					boardSaturation = saturationSlider.getValue();
					guiState.boardSaturation = boardSaturation;
					saturationValueLabel.setText(boardSaturation + "%");
					refreshBoard();
					if (editorDialog != null) {
						editorDialog.repaintBoard(); }
					if (boardEditorPane != null) {
						boardEditorPane.repaintBoard(); } }
			});
			saturationRow.add(saturationSlider, BorderLayout.CENTER);
			saturationRow.add(saturationValueLabel, BorderLayout.EAST);
			JPanel animationRow = new JPanel(new BorderLayout(8, 8));
			animationRow.setOpaque(false);
			animationRow.add(mutedLabel("Animation"), BorderLayout.WEST);
			animationSlider = new JSlider(0, 1000, Math.max(0, Math.min(1000, animationMillis)));
			animationMillis = animationSlider.getValue();
			guiState.animationMillis = animationMillis;
			sliders.add(animationSlider);
			animationValueLabel = mutedLabel(animationSlider.getValue() == 0 ? "Off" : (animationSlider.getValue() + " ms"));
			animationSlider.addChangeListener(new ChangeListener() {
								/**
				 * Handles state changed.
				 * @param e e value
				 */
@Override
				public void stateChanged(ChangeEvent e) {
					animationMillis = animationSlider.getValue();
					guiState.animationMillis = animationMillis;
					animationValueLabel.setText(animationMillis == 0 ? "Off" : (animationMillis + " ms")); }
			});
			animationRow.add(animationSlider, BorderLayout.CENTER);
			animationRow.add(animationValueLabel, BorderLayout.EAST);
			body.add(togglesRow1);
			body.add(Box.createVerticalStrut(8));
			body.add(togglesRow2);
			body.add(Box.createVerticalStrut(8));
			body.add(togglesRow3);
			body.add(Box.createVerticalStrut(8));
			body.add(hueRow);
			body.add(Box.createVerticalStrut(8));
			body.add(brightnessRow);
			body.add(Box.createVerticalStrut(8));
			body.add(saturationRow);
			body.add(Box.createVerticalStrut(8));
			body.add(animationRow);
			card.setContent(body);
			return card; }
		/**
		 * buildSidebar method.
		 *
		 * @return return value.
		 */
		private JScrollPane buildSidebar() {
			rightTabs = new JTabbedPane();
			rightTabs.putClientProperty("hideTabs", Boolean.TRUE);
			tabs.add(rightTabs);
			rightTabs.addChangeListener(e -> {
				stopPvHoverAnimation();
				if (boardPanel != null) {
					boardPanel.clearPreview(); }
				hideEnginePvMiniBoardPreview();
				if (guiState != null) {
					guiState.rightTabIndex = rightTabs.getSelectedIndex();
					scheduleGuiStateSave(); }
				updateTabLabelStyles();
			});
			rightTabs.addTab("Analysis", wrapTabComponent(analysisView));
			rightTabs.addTab("Explorer", wrapTab(buildEcoCard()));
			rightTabs.addTab("Annotate", wrapTabComponent(buildAnnotateTab()));
			rightTabs.addTab("Report", wrapTabComponent(buildReportTab()));
			rightTabs.addTab("Variations", wrapTabComponent(buildVariationsTab()));
			rightTabs.addTab("Ablation", wrapTabComponent(buildAblationTab()));
			rightTabs.addTab("Controls", wrapTabComponent(buildControls()));
			rightTabs.addTab("Commands", wrapTab(buildCommandCard()));
			rightTabs.addTab("Info", wrapTab(buildInfoCard()));
			installTabLabels();
			JScrollPane scroll = new JScrollPane(rightTabs);
			rightSidebarScroll = scroll;
			JPanel header = new JPanel(new BorderLayout());
			sidebarHeader = header;
			header.setOpaque(true);
			sidebarHeaderLabel = titleLabel("Analysis");
			header.add(sidebarHeaderLabel, BorderLayout.WEST);
			header.setBorder(new EmptyBorder(6, 12, 6, 12));
			scroll.setColumnHeaderView(header);
			scroll.setBorder(null);
			scroll.setOpaque(false);
			scroll.getViewport().setOpaque(false);
			scrolls.add(scroll);
			this.baseSidebarPref = new Dimension(520, 720);
			scroll.setPreferredSize(scaleDimension(baseSidebarPref));
			return scroll; }
		/**
		 * wrapTab method.
		 *
		 * @param card parameter.
		 * @return return value.
		 */
		private JPanel wrapTab(RoundedPanel card) {
			JPanel panel = new JPanel(new BorderLayout());
			panel.setOpaque(false);
			panel.add(card, BorderLayout.CENTER);
			panel.setBorder(new EmptyBorder(8, 8, 8, 8));
			return panel; }
		/**
		 * wrapTabComponent method.
		 *
		 * @param content parameter.
		 * @return return value.
		 */
		private JPanel wrapTabComponent(JComponent content) {
			JPanel panel = new JPanel(new BorderLayout());
			panel.setOpaque(false);
			panel.add(content, BorderLayout.CENTER);
			panel.setBorder(new EmptyBorder(8, 8, 8, 8));
			return panel; }
		/**
		 * buildAnalysisTab method.
		 *
		 * @param moves parameter.
		 * @param engine parameter.
		 * @param tags parameter.
		 * @return return value.
		 */
		private JPanel buildAnalysisTab(RoundedPanel moves, RoundedPanel engine, RoundedPanel tags) {
			RoundedPanel shell = new RoundedPanel(0);
			flatCards.add(shell);
			shell.setLayout(new BorderLayout(12, 12));
			shell.setBorder(new EmptyBorder(12, 12, 12, 12));
			JPanel left = new JPanel();
			left.setOpaque(false);
			left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
			left.add(sectionHeader("Moves"));
			left.add(flattenCard(moves));
			RoundedPanel evalGraphCard = buildEvalGraphCard();
			JTabbedPane detailTabs = new JTabbedPane();
			tabs.add(detailTabs);
			JPanel engineTab = new JPanel(new BorderLayout());
			engineTab.setOpaque(false);
			engineTab.add(flattenCard(engine), BorderLayout.CENTER);
			JPanel evalTab = new JPanel(new BorderLayout());
			evalTab.setOpaque(false);
			evalTab.add(flattenCard(evalGraphCard), BorderLayout.CENTER);
			JPanel tagsTab = new JPanel(new BorderLayout());
			tagsTab.setOpaque(false);
			tagsTab.add(flattenCard(tags), BorderLayout.CENTER);
			detailTabs.addTab("Engine", engineTab);
			detailTabs.addTab("Eval", evalTab);
			detailTabs.addTab("Tags", tagsTab);
			JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, detailTabs);
			analysisSplit = split;
			split.setResizeWeight(0.45);
			split.setDividerSize(1);
			split.setContinuousLayout(true);
			split.setBorder(null);
			split.setOpaque(false);
			split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
				captureGuiState();
				scheduleGuiStateSave();
			});
			shell.add(split, BorderLayout.CENTER);
			JPanel panel = new JPanel(new BorderLayout());
			panel.setOpaque(false);
			panel.add(shell, BorderLayout.CENTER);
			return panel; }
		/**
		 * buildAnnotateTab method.
		 *
		 * @return return value.
		 */
		private JPanel buildAnnotateTab() {
			annotateNagGroup = new ButtonGroup();
			AnnotateTabPanel.Result result = AnnotateTabPanel.build(
				annotateTabContext,
				this::saveAnnotateComment,
				e -> saveAnnotateComment(),
				e -> clearAnnotateComment(),
				e -> clearAnnotateNag());
			annotateMoveLabel = result.moveLabel();
			annotateCommentArea = result.commentArea();
			annotateSaveButton = result.saveButton();
			annotateClearCommentButton = result.clearCommentButton();
			annotateClearNagButton = result.clearNagButton();
			JPanel panel = new JPanel(new BorderLayout());
			panel.setOpaque(false);
			panel.add(result.panel(), BorderLayout.CENTER);
			return panel;
		}

		/**
		 * buildReportTab method.
		 *
		 * @return return value.
		 */
		private JPanel buildReportTab() {
			ReportTabPanel.Result result = ReportTabPanel.build(
				layoutBuilderSupport,
				this,
				this::startReportAnalysis,
				this::stopReportAnalysis,
				this::applyReportNags);
			reportStatusLabel = result.statusLabel();
			reportAnalyzeButton = result.analyzeButton();
			reportStopButton = result.stopButton();
			reportApplyNagButton = result.applyNagButton();
			reportListModel = result.listModel();
			reportList = result.list();
			return result.panel();
		}
		/**
		 * buildVariationsTab method.
		 *
		 * @return return value.
		 */
		private JPanel buildVariationsTab() {
			VariationTabPanel.Result result = VariationTabPanel.build(
				layoutBuilderSupport,
				() -> variationNodes,
				layoutBuilderSupport.scaledRowHeight(22));
			variationModel = result.model();
			variationTable = result.table();
			return result.panel();
		}
		/**
		 * buildAblationTab method.
		 *
		 * @return return value.
		 */
		private JPanel buildAblationTab() {
			RoundedPanel card = createFlatCard("Ablation", false);
			JPanel body = new JPanel(new BorderLayout(8, 8));
			body.setOpaque(false);
			JPanel header = new JPanel(new BorderLayout(8, 8));
			header.setOpaque(false);
			ablationToggle = themedCheckbox("Enable", false, e -> refreshAblation());
			ablationToggle.setToolTipText("Compute evaluator ablation for each piece");
			header.add(ablationToggle, BorderLayout.EAST);
			body.add(header, BorderLayout.NORTH);
			ablationModel = new DefaultTableModel(new Object[] { "Piece", "Square", "Score" }, 0) {
								/**
				 * Returns whether cell editable.
				 * @param row row value
				 * @param column column value
				 * @return computed value
				 */
@Override
				public boolean isCellEditable(int row, int column) {
					return false; }
			};
			ablationTable = new JTable(ablationModel);
			ablationTable.setFillsViewportHeight(true);
			ablationTable.setRowHeight(Math.round(22 * uiScale));
			ablationTable.setShowGrid(false);
			ablationTable.setIntercellSpacing(new Dimension(0, 0));
			tables.add(ablationTable);
			JScrollPane scroll = new JScrollPane(ablationTable);
			scroll.setBorder(BorderFactory.createEmptyBorder());
			scrolls.add(scroll);
			body.add(scroll, BorderLayout.CENTER);
			card.setContent(body);
			JPanel panel = new JPanel(new BorderLayout());
			panel.setOpaque(false);
			panel.add(card, BorderLayout.CENTER);
			return panel; }
		/**
		 * addNagButton method.
		 *
		 * @param container parameter.
		 * @param label parameter.
		 * @param nag parameter.
		 */
		private void addNagButton(JPanel container, String label, int nag) {
			JCheckBox box = themedCheckbox(label, false, e -> setAnnotateNag(nag));
			box.setHorizontalAlignment(SwingConstants.CENTER);
			annotateNagButtons.put(nag, box);
			if (annotateNagGroup != null) {
				annotateNagGroup.add(box); }
			container.add(box); }
		/**
		 * flattenCard method.
		 *
		 * @param card parameter.
		 * @return return value.
		 */
		private RoundedPanel flattenCard(RoundedPanel card) {
			if (!flatCards.contains(card)) {
				flatCards.add(card); }
			card.setBorder(new EmptyBorder(6, 0, 6, 0));
			return card; }
		/**
		 * buildInfoCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildInfoCard() {
			RoundedPanel card = createFlatCard("Info");
			infoArea = new JTextArea(6, 26);
			infoArea.setEditable(false);
			infoArea.setLineWrap(true);
			infoArea.setWrapStyleWord(true);
			textAreas.add(infoArea);
			JScrollPane scroll = new JScrollPane(infoArea);
			scroll.setBorder(BorderFactory.createEmptyBorder());
			scrolls.add(scroll);
			card.setContent(scroll);
			return card; }
    /**
     * buildMovesCard method.
     *
     * @return return value.
     */
    private RoundedPanel buildMovesCard() {
        HistoryMovesCardBuilder.Context ctx = new HistoryMovesCardBuilder.Context() {
                        /**
             * Handles ui factory.
             * @return computed value
             */
@Override
            public HistoryUiFactory uiFactory() {
                return uiFactory;
            }

                        /**
             * Handles ui scale.
             * @return computed value
             */
@Override
            public float uiScale() {
                return uiScale;
            }

                        /**
             * Handles scale dimension.
             * @param base base value
             * @return computed value
             */
@Override
            public Dimension scaleDimension(Dimension base) {
                return GuiWindowLayout.this.scaleDimension(base);
            }

                        /**
             * Handles owner.
             * @return computed value
             */
@Override
            public GuiWindowHistory owner() {
                return GuiWindowLayout.this;
            }

                        /**
             * Handles register list.
             * @param list list value
             */
@Override
            public void registerList(JList<?> list) {
                layoutBuilderSupport.registerList(list);
            }

                        /**
             * Handles register scroll pane.
             * @param scroll scroll value
             */
@Override
            public void registerScrollPane(JScrollPane scroll) {
                layoutBuilderSupport.registerScrollPane(scroll);
            }
        };
        HistoryMovesCardBuilder.Actions actions = new HistoryMovesCardBuilder.Actions() {
                        /**
             * Handles copy history san.
             */
@Override
            public void copyHistorySan() {
                GuiWindowLayout.this.copyHistorySan();
            }

                        /**
             * Handles copy history uci.
             */
@Override
            public void copyHistoryUci() {
                GuiWindowLayout.this.copyHistoryUci();
            }

                        /**
             * Handles jump to selected history.
             */
@Override
            public void jumpToSelectedHistory() {
                GuiWindowLayout.this.jumpToSelectedHistory();
            }

                        /**
             * Handles edit selected comment.
             */
@Override
            public void editSelectedComment() {
                GuiWindowLayout.this.editSelectedComment();
            }

                        /**
             * Handles clear selected comment.
             */
@Override
            public void clearSelectedComment() {
                GuiWindowLayout.this.clearSelectedComment();
            }

                        /**
             * Handles clear selected nag.
             */
@Override
            public void clearSelectedNag() {
                GuiWindowLayout.this.clearSelectedNag();
            }

                        /**
             * Handles update variation buttons.
             */
@Override
            public void updateVariationButtons() {
                GuiWindowLayout.this.updateVariationButtons();
            }

                        /**
             * Handles preview history hover.
             * @param entry entry value
             * @param index index value
             * @param screenLocation screen location value
             */
@Override
            public void previewHistoryHover(HistoryEntry entry, int index, Point screenLocation) {
                if (entry != null && entry.node() != null) {
                    GuiWindowLayout.this.previewNodeHover(entry.node(), screenLocation);
                    return;
                }
                GuiWindowLayout.this.previewHistoryHover(index, screenLocation);
            }

                        /**
             * Handles clear board preview.
             */
@Override
            public void clearBoardPreview() {
                GuiWindowLayout.this.boardPanel.clearPreview();
            }

                        /**
             * Handles hide engine preview.
             */
@Override
            public void hideEnginePreview() {
                GuiWindowLayout.this.hideEnginePvMiniBoardPreview();
            }

                        /**
             * Handles apply pgn node.
             * @param node node value
             */
@Override
            public void applyPgnNode(PgnNode node) {
                GuiWindowLayout.this.applyPgnNode(node);
            }

                        /**
             * Handles maybe show history menu.
             * @param list list value
             * @param event event value
             */
@Override
            public void maybeShowHistoryMenu(JList<HistoryEntry> list, MouseEvent event) {
                GuiWindowLayout.this.maybeShowHistoryMenu(list, event);
            }

                        /**
             * Handles delete selected variation.
             */
@Override
            public void deleteSelectedVariation() {
                GuiWindowLayout.this.deleteSelectedVariation();
            }

                        /**
             * Handles promote selected variation.
             */
@Override
            public void promoteSelectedVariation() {
                GuiWindowLayout.this.promoteSelectedVariation();
            }

                        /**
             * Handles move selected variation.
             * @param direction direction value
             */
@Override
            public void moveSelectedVariation(int direction) {
                GuiWindowLayout.this.moveSelectedVariation(direction);
            }

                        /**
             * Handles create nag item.
             * @param label label value
             * @param nag nag value
             * @return computed value
             */
@Override
            public JMenuItem createNagItem(String label, int nag) {
                return GuiWindowLayout.this.createNagItem(label, nag);
            }
        };
        HistoryMovesCardBuilder.Result result = HistoryMovesCardBuilder.build(ctx, actions);
        historyListModel = result.historyListModel();
        historyList = result.historyList();
        historyScroll = result.historyScroll();
        baseHistoryScrollPref = result.historyScrollPref();
        historyMenu = result.historyMenu();
        historyCopySanItem = result.historyCopySanItem();
        historyCopyUciItem = result.historyCopyUciItem();
        historyJumpItem = result.historyJumpItem();
        historyCommentItem = result.historyCommentItem();
        historyClearCommentItem = result.historyClearCommentItem();
        historyClearNagItem = result.historyClearNagItem();
        historyDeleteButton = result.historyDeleteButton();
        historyPromoteButton = result.historyPromoteButton();
        historyVarUpButton = result.historyVarUpButton();
        historyVarDownButton = result.historyVarDownButton();
        return result.panel();
    }
		/**
		 * buildUnderboardCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildUnderboardCard() {
			RoundedPanel card = createFlatCard("Moves", true);
			underboardMoveModel = new DefaultListModel<>();
			underboardMoveList = new JList<>(underboardMoveModel);
			underboardMoveList.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
			underboardMoveList.setCellRenderer(new MovePairCellRenderer(this));
			underboardMoveList.setFixedCellHeight(Math.round(24 * uiScale));
			underboardMoveList.addMouseMotionListener(new MouseAdapter() {
								/**
				 * Handles mouse moved.
				 * @param e e value
				 */
@Override
				public void mouseMoved(MouseEvent e) {
					int idx = historyIndexAtPoint(underboardMoveList, e.getPoint());
					previewHistoryHover(idx, e.getLocationOnScreen()); }
			});
			underboardMoveList.addMouseListener(new MouseAdapter() {
								/**
				 * Handles mouse clicked.
				 * @param e e value
				 */
@Override
				public void mouseClicked(MouseEvent e) {
					if (e.getClickCount() == 2) {
						int idx = historyIndexAtPoint(underboardMoveList, e.getPoint());
						if (idx >= 0) {
							jumpToHistoryIndex(idx); } } }
								/**
				 * Handles mouse pressed.
				 * @param e e value
				 */
@Override
				public void mousePressed(MouseEvent e) {
					if (!e.isPopupTrigger() && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
						int idx = historyIndexAtPoint(underboardMoveList, e.getPoint());
						if (idx >= 0) {
							selectHistoryNode(nodeAtHistoryIndex(idx)); } }
					maybeShowUnderboardHistoryMenu(e); }
								/**
				 * Handles mouse released.
				 * @param e e value
				 */
@Override
				public void mouseReleased(MouseEvent e) {
					maybeShowUnderboardHistoryMenu(e); }
								/**
				 * Handles mouse exited.
				 * @param e e value
				 */
@Override
				public void mouseExited(MouseEvent e) {
					boardPanel.clearPreview();
					hideEnginePvMiniBoardPreview(); }
			});
			lists.add(underboardMoveList);
			underboardMoveScroll = new JScrollPane(underboardMoveList);
			underboardMoveScroll.setBorder(BorderFactory.createEmptyBorder());
			this.baseUnderboardScrollPref = new Dimension(260, 160);
			underboardMoveScroll.setPreferredSize(scaleDimension(baseUnderboardScrollPref));
			scrolls.add(underboardMoveScroll);
			card.setContent(underboardMoveScroll);
			return card; }
		/**
		 * buildTagsCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildTagsCard() {
			RoundedPanel card = createFlatCard("Tags", false);
			tagListModel = new DefaultListModel<>();
			tagList = new JList<>(tagListModel);
			tagList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			lists.add(tagList);
			JScrollPane scroll = new JScrollPane(tagList);
			scroll.setBorder(BorderFactory.createEmptyBorder());
			scrolls.add(scroll);
			card.setContent(scroll);
			return card; }
		/**
		 * buildEngineCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildEngineCard() {
			EngineCardBuilder.Context ctx = new EngineCardBuilder.Context() {
								/**
				 * Handles ui factory.
				 * @return computed value
				 */
@Override
				public HistoryUiFactory uiFactory() {
					return uiFactory;
				}

								/**
				 * Handles ui scale.
				 * @return computed value
				 */
@Override
				public float uiScale() {
					return GuiWindowLayout.this.uiScale;
				}

								/**
				 * Handles scale dimension.
				 * @param base base value
				 * @return computed value
				 */
@Override
				public Dimension scaleDimension(Dimension base) {
					return GuiWindowLayout.this.scaleDimension(base);
				}

								/**
				 * Handles owner.
				 * @return computed value
				 */
@Override
				public GuiWindowHistory owner() {
					return GuiWindowLayout.this;
				}

								/**
				 * Handles muted label.
				 * @param text text value
				 * @return computed value
				 */
@Override
				public JLabel mutedLabel(String text) {
					return GuiWindowLayout.this.mutedLabel(text);
				}

								/**
				 * Handles labeled value.
				 * @param label label value
				 * @param value value value
				 * @return computed value
				 */
@Override
				public JPanel labeledValue(String label, JLabel value) {
					return GuiWindowLayout.this.labeledValue(label, value);
				}

								/**
				 * Handles labeled field.
				 * @param label label value
				 * @param field field value
				 * @return computed value
				 */
@Override
				public JPanel labeledField(String label, JTextField field) {
					return GuiWindowLayout.this.labeledField(label, field);
				}

								/**
				 * Sets the tings row.
				 * @param label label value
				 * @param control control value
				 * @return computed value
				 */
@Override
				public JPanel settingsRow(String label, JComponent control) {
					return GuiWindowLayout.this.settingsRow(label, control);
				}

								/**
				 * Handles themed checkbox.
				 * @param text text value
				 * @param selected selected value
				 * @param action action value
				 * @return computed value
				 */
@Override
				public JCheckBox themedCheckbox(String text, boolean selected, java.awt.event.ActionListener action) {
					return GuiWindowLayout.this.themedCheckbox(text, selected, action);
				}

								/**
				 * Handles register strong label.
				 * @param label label value
				 */
@Override
				public void registerStrongLabel(JLabel label) {
					if (label != null) {
						strongLabels.add(label);
					}
				}

								/**
				 * Handles register text field.
				 * @param field field value
				 */
@Override
				public void registerTextField(JTextField field) {
					layoutBuilderSupport.registerTextField(field);
				}

								/**
				 * Handles register text area.
				 * @param area area value
				 */
@Override
				public void registerTextArea(JTextArea area) {
					layoutBuilderSupport.registerTextArea(area);
				}

								/**
				 * Handles register list.
				 * @param list list value
				 */
@Override
				public void registerList(JList<?> list) {
					layoutBuilderSupport.registerList(list);
				}

								/**
				 * Handles register scroll pane.
				 * @param scroll scroll value
				 */
@Override
				public void registerScrollPane(JScrollPane scroll) {
					layoutBuilderSupport.registerScrollPane(scroll);
				}

								/**
				 * Handles register combo box.
				 * @param combo combo value
				 */
@Override
				public void registerComboBox(JComboBox<?> combo) {
					layoutBuilderSupport.registerComboBox(combo);
				}

								/**
				 * Handles default protocol path.
				 * @return computed value
				 */
@Override
				public String defaultProtocolPath() {
					return Config.getProtocolPath();
				}

								/**
				 * Handles default engine nodes.
				 * @return computed value
				 */
@Override
				public long defaultEngineNodes() {
					return GuiWindowLayout.this.defaultEngineNodes();
				}

								/**
				 * Handles default engine duration.
				 * @return computed value
				 */
@Override
				public long defaultEngineDuration() {
					return GuiWindowLayout.this.defaultEngineDuration();
				}

								/**
				 * Handles vs engine human white.
				 * @return computed value
				 */
@Override
				public boolean vsEngineHumanWhite() {
					return GuiWindowLayout.this.vsEngineHumanWhite;
				}
			};

			EngineCardBuilder.Actions actions = new EngineCardBuilder.Actions() {
								/**
				 * Handles start engine.
				 */
@Override
				public void startEngine() {
					GuiWindowLayout.this.startEngine();
				}

								/**
				 * Handles stop engine.
				 */
@Override
				public void stopEngine() {
					GuiWindowLayout.this.stopEngine();
				}

								/**
				 * Handles play engine best.
				 */
@Override
				public void playEngineBest() {
					GuiWindowLayout.this.playEngineBest();
				}

								/**
				 * Handles start vs engine new game.
				 */
@Override
				public void startVsEngineNewGame() {
					GuiWindowLayout.this.startVsEngineNewGame();
				}

								/**
				 * Handles continue vs engine from current.
				 */
@Override
				public void continueVsEngineFromCurrent() {
					GuiWindowLayout.this.continueVsEngineFromCurrent();
				}

								/**
				 * Handles stop vs engine game.
				 */
@Override
				public void stopVsEngineGame() {
					GuiWindowLayout.this.stopVsEngineGame();
				}

								/**
				 * Handles refresh board.
				 */
@Override
				public void refreshBoard() {
					GuiWindowLayout.this.refreshBoard();
				}

								/**
				 * Handles request vs engine reply if needed.
				 */
@Override
				public void requestVsEngineReplyIfNeeded() {
					GuiWindowLayout.this.requestVsEngineReplyIfNeeded();
				}

								/**
				 * Handles refresh status bar.
				 */
@Override
				public void refreshStatusBar() {
					GuiWindowLayout.this.refreshStatusBar();
				}

								/**
				 * Handles choose protocol file.
				 */
@Override
				public void chooseProtocolFile() {
					GuiWindowLayout.this.chooseProtocolFile();
				}

								/**
				 * Handles copy engine pv.
				 */
@Override
				public void copyEnginePv() {
					GuiWindowLayout.this.copyEnginePv();
				}

								/**
				 * Handles toggle engine lock pv.
				 */
@Override
				public void toggleEngineLockPv() {
					GuiWindowLayout.this.toggleEngineLockPv();
				}

								/**
				 * Handles open engine manager.
				 */
@Override
				public void openEngineManager() {
					GuiWindowLayout.this.openEngineManager();
				}

								/**
				 * Handles refresh pv list cell height.
				 * @param entries entries value
				 */
@Override
				public void refreshPvListCellHeight(java.util.List<PvEntry> entries) {
					GuiWindowLayout.this.refreshPvListCellHeight(entries);
				}

								/**
				 * Handles start pv hover animation.
				 */
@Override
				public void startPvHoverAnimation() {
					GuiWindowLayout.this.startPvHoverAnimation();
				}

								/**
				 * Handles stop pv hover animation.
				 */
@Override
				public void stopPvHoverAnimation() {
					GuiWindowLayout.this.stopPvHoverAnimation();
				}

								/**
				 * Handles preview engine pv.
				 * @param pv pv value
				 * @param ply ply value
				 */
@Override
				public void previewEnginePv(int pv, int ply) {
					GuiWindowLayout.this.previewEnginePv(pv, ply);
				}

								/**
				 * Handles show engine pv mini board preview.
				 * @param pv pv value
				 * @param ply ply value
				 * @param location location value
				 */
@Override
				public void showEnginePvMiniBoardPreview(int pv, int ply, Point location) {
					GuiWindowLayout.this.showEnginePvMiniBoardPreview(pv, ply, location);
				}

								/**
				 * Handles hide engine pv mini board preview.
				 */
@Override
				public void hideEnginePvMiniBoardPreview() {
					GuiWindowLayout.this.hideEnginePvMiniBoardPreview();
				}

								/**
				 * Handles play engine pv.
				 * @param pv pv value
				 * @param ply ply value
				 */
@Override
				public void playEnginePv(int pv, int ply) {
					GuiWindowLayout.this.playEnginePv(pv, ply);
				}

								/**
				 * Handles toggle pv expanded.
				 * @param entry entry value
				 */
@Override
				public void togglePvExpanded(PvEntry entry) {
					GuiWindowLayout.this.togglePvExpanded(entry);
				}

								/**
				 * Returns whether pv expand toggle hit.
				 * @param list list value
				 * @param entry entry value
				 * @param bounds bounds value
				 * @param point point value
				 * @return computed value
				 */
@Override
				public boolean isPvExpandToggleHit(JList<? extends PvEntry> list, PvEntry entry,
						Rectangle bounds, Point point) {
					return GuiWindowLayout.this.isPvExpandToggleHit(list, entry, bounds, point);
				}

								/**
				 * Handles pv ply at point.
				 * @param entry entry value
				 * @param listPoint list point value
				 * @param cellBounds cell bounds value
				 * @return computed value
				 */
@Override
				public int pvPlyAtPoint(PvEntry entry, Point listPoint, Rectangle cellBounds) {
					return GuiWindowLayout.this.pvPlyAtPoint(entry, listPoint, cellBounds);
				}

								/**
				 * Handles pv total plies.
				 * @param entry entry value
				 * @return computed value
				 */
@Override
				public int pvTotalPlies(PvEntry entry) {
					return GuiWindowLayout.this.pvTotalPlies(entry);
				}

								/**
				 * Handles on vs engine side change.
				 * @param playWhite play white value
				 */
@Override
				public void onVsEngineSideChange(boolean playWhite) {
					GuiWindowLayout.this.vsEngineHumanWhite = playWhite;
					GuiWindowLayout.this.refreshBoard();
					if (GuiWindowLayout.this.vsEngineMode) {
						GuiWindowLayout.this.requestVsEngineReplyIfNeeded();
						GuiWindowLayout.this.refreshStatusBar();
					}
				}
			};

			EngineCardBuilder.Result result = EngineCardBuilder.build(ctx, actions);
			engineStatusLabel = result.engineStatusLabel();
			engineDepthValue = result.engineDepthValue();
			engineNodesValue = result.engineNodesValue();
			engineTimeValue = result.engineTimeValue();
			engineNpsValue = result.engineNpsValue();
			engineStartButton = result.engineStartButton();
			engineStopButton = result.engineStopButton();
			engineBestButton = result.engineBestButton();
			engineVsNewButton = result.engineVsNewButton();
			engineVsContinueButton = result.engineVsContinueButton();
			engineVsStopButton = result.engineVsStopButton();
			vsEnginePlayWhiteToggle = result.vsEnginePlayWhiteToggle();
			engineSearchField = result.engineSearchField();
			engineProtocolField = result.engineProtocolField();
			engineNodesField = result.engineNodesField();
			engineTimeField = result.engineTimeField();
			engineMultiPvBox = result.engineMultiPvBox();
			pvWrapModeBox = result.pvWrapModeBox();
			engineEndlessToggle = result.engineEndlessToggle();
			engineManageButton = result.engineManageButton();
			engineCopyPvButton = result.engineCopyPvButton();
			engineLockPvToggle = result.engineLockPvToggle();
			engineBestArea = result.engineBestArea();
			pvListModel = result.pvListModel();
			pvList = result.pvList();
			pvListScroll = result.pvListScroll();
			engineOutputArea = result.engineOutputArea();
			return result.panel();
		}
		/**
		 * buildEvalGraphCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildEvalGraphCard() {
			RoundedPanel card = createFlatCard("Eval Graph", false);
			JPanel body = new JPanel(new BorderLayout(8, 8));
			body.setOpaque(false);
			evalGraphPanel = new EvalGraphPanel(this);
			body.add(evalGraphPanel, BorderLayout.CENTER);
			card.setContent(body);
			return card; }
		/**
		 * ensureEcoTableModel method.
		 */
		private void ensureEcoTableModel() {
			if (ecoTableModel != null) {
				return; }
			ecoTableModel = new DefaultTableModel(new Object[] { "ECO", "Name", "Moves" }, 0) {
								/**
				 * Returns whether cell editable.
				 * @param row row value
				 * @param column column value
				 * @return computed value
				 */
@Override
				public boolean isCellEditable(int row, int column) {
					return false; }
			}; }
		/**
		 * buildEcoEditorTab method.
		 *
		 * @return return value.
		 */
		private JComponent buildEcoEditorTab() {
			ensureEcoTableModel();
			if (ecoTable == null) {
				ecoTable = new JTable(ecoTableModel);
				ecoTable.setFillsViewportHeight(true);
				ecoTable.setRowHeight(Math.round(22 * uiScale));
				ecoTable.setShowGrid(false);
				ecoTable.setIntercellSpacing(new Dimension(0, 0));
				ecoTable.setAutoCreateRowSorter(true);
				ecoTable.getSelectionModel().addListSelectionListener(e -> {
					if (e.getValueIsAdjusting()) {
						return; }
					int viewRow = ecoTable.getSelectedRow();
					if (viewRow < 0) {
						ecoSelected = null;
					} else {
						int modelRow = ecoTable.convertRowIndexToModel(viewRow);
						if (modelRow >= 0 && modelRow < ecoFilteredEntries.size()) {
							ecoSelected = ecoFilteredEntries.get(modelRow);
						} else {
							ecoSelected = null; } }
					updateEcoDetails();
				});
				ecoTable.addMouseListener(new MouseAdapter() {
										/**
					 * Handles mouse clicked.
					 * @param e e value
					 */
@Override
					public void mouseClicked(MouseEvent e) {
						if (e.getClickCount() == 2) {
							int viewRow = ecoTable.getSelectedRow();
							if (viewRow >= 0) {
								int modelRow = ecoTable.convertRowIndexToModel(viewRow);
								if (modelRow >= 0 && modelRow < ecoFilteredEntries.size()) {
									applyEcoEntry(ecoFilteredEntries.get(modelRow)); } } } }
				});
				tables.add(ecoTable); }
			JScrollPane scroll = new JScrollPane(ecoTable);
			scroll.setBorder(BorderFactory.createEmptyBorder());
			scrolls.add(scroll);
			JPanel panel = new JPanel(new BorderLayout());
			panel.setOpaque(false);
			panel.add(scroll, BorderLayout.CENTER);
			return panel; }
		/**
		 * buildEcoCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildEcoCard() {
			EcoExplorerCardBuilder.Context ctx = new EcoExplorerCardBuilder.Context() {
								/**
				 * Handles ui factory.
				 * @return computed value
				 */
@Override
				public HistoryUiFactory uiFactory() {
					return uiFactory;
				}

								/**
				 * Handles ui scale.
				 * @return computed value
				 */
@Override
				public float uiScale() {
					return GuiWindowLayout.this.uiScale;
				}

								/**
				 * Handles format title.
				 * @param text text value
				 * @return computed value
				 */
@Override
				public String formatTitle(String text) {
					return GuiWindowLayout.this.formatTitle(text);
				}

								/**
				 * Handles muted label.
				 * @param text text value
				 * @return computed value
				 */
@Override
				public JLabel mutedLabel(String text) {
					return GuiWindowLayout.this.mutedLabel(text);
				}

								/**
				 * Handles owner.
				 * @return computed value
				 */
@Override
				public GuiWindowHistory owner() {
					return GuiWindowLayout.this;
				}

								/**
				 * Handles register text field.
				 * @param field field value
				 */
@Override
				public void registerTextField(JTextField field) {
					layoutBuilderSupport.registerTextField(field);
				}

								/**
				 * Handles register text area.
				 * @param area area value
				 */
@Override
				public void registerTextArea(JTextArea area) {
					layoutBuilderSupport.registerTextArea(area);
				}

								/**
				 * Handles register list.
				 * @param list list value
				 */
@Override
				public void registerList(JList<?> list) {
					layoutBuilderSupport.registerList(list);
				}

								/**
				 * Handles register scroll pane.
				 * @param scroll scroll value
				 */
@Override
				public void registerScrollPane(JScrollPane scroll) {
					layoutBuilderSupport.registerScrollPane(scroll);
				}
			};

			EcoExplorerCardBuilder.Actions actions = new EcoExplorerCardBuilder.Actions() {
								/**
				 * Handles refresh eco list.
				 */
@Override
				public void refreshEcoList() {
					if (ensureEcoBook(true)) {
						filterEcoList();
					}
				}

								/**
				 * Handles collapse explorer selection.
				 */
@Override
				public void collapseExplorerSelection() {
					if (ecoList != null) {
						ecoList.clearSelection();
					}
				}

								/**
				 * Handles more explorer options.
				 */
@Override
				public void moreExplorerOptions() {
					// placeholder for future actions
				}

								/**
				 * Handles filter eco list.
				 */
@Override
				public void filterEcoList() {
					GuiWindowLayout.this.filterEcoList();
				}

								/**
				 * Handles update eco details.
				 */
@Override
				public void updateEcoDetails() {
					GuiWindowLayout.this.updateEcoDetails();
				}

								/**
				 * Handles apply eco entry.
				 * @param entry entry value
				 */
@Override
				public void applyEcoEntry(Entry entry) {
					GuiWindowLayout.this.applyEcoEntry(entry);
				}

								/**
				 * Handles load current eco line.
				 */
@Override
				public void loadCurrentEcoLine() {
					GuiWindowLayout.this.applyEcoEntry(ecoSelected);
				}

								/**
				 * Handles copy eco movetext.
				 */
@Override
				public void copyEcoMovetext() {
					GuiWindowLayout.this.copyEcoMovetext();
				}
			};

			EcoExplorerCardBuilder.Result result = EcoExplorerCardBuilder.build(ctx, actions);
			ecoCurrentLabel = result.currentLabel();
			ecoStatusLabel = result.statusLabel();
			ecoSearchField = result.searchField();
			ecoListModel = result.listModel();
			ecoList = result.list();
			ecoDetailArea = result.detailArea();
			return result.panel();
		}
		/**
		 * buildPgnTab method.
		 *
		 * @return return value.
		 */
		private JPanel buildPgnTab() {
		RoundedPanel card = createFlatCard("PGN", false);
		JPanel body = new JPanel(new BorderLayout(8, 8));
		body.setOpaque(false);
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		pgnSummaryLabel = mutedLabel("No PGN loaded");
		header.add(pgnSummaryLabel, BorderLayout.WEST);
		header.add(mutedLabel("Standard SAN PGN (no figurines)"), BorderLayout.EAST);
		body.add(header, BorderLayout.NORTH);
		JPanel center = new JPanel(new java.awt.GridLayout(2, 1, 0, 8));
		center.setOpaque(false);
		JPanel viewerPanel = new JPanel(new BorderLayout(4, 4));
		viewerPanel.setOpaque(false);
		viewerPanel.add(mutedLabel("Current Game PGN"), BorderLayout.NORTH);
		pgnViewerArea = new JTextArea(8, 26);
		pgnViewerArea.setEditable(false);
		pgnViewerArea.setLineWrap(true);
		pgnViewerArea.setWrapStyleWord(true);
		pgnViewerArea.setText(buildCurrentPgnText());
		pgnViewerArea.setCaretPosition(0);
		textAreas.add(pgnViewerArea);
		JScrollPane viewerScroll = new JScrollPane(pgnViewerArea);
		viewerScroll.setBorder(BorderFactory.createEmptyBorder());
		scrolls.add(viewerScroll);
		viewerPanel.add(viewerScroll, BorderLayout.CENTER);
		center.add(viewerPanel);
		JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
		inputPanel.setOpaque(false);
		inputPanel.add(mutedLabel("Load Game From PGN Text"), BorderLayout.NORTH);
		pgnEditorArea = new JTextArea(8, 26);
		pgnEditorArea.setLineWrap(true);
		pgnEditorArea.setWrapStyleWord(true);
		pgnEditorArea.setText(buildCurrentPgnText());
		pgnEditorArea.setCaretPosition(0);
		textAreas.add(pgnEditorArea);
		JScrollPane inputScroll = new JScrollPane(pgnEditorArea);
		inputScroll.setBorder(BorderFactory.createEmptyBorder());
		scrolls.add(inputScroll);
		inputPanel.add(inputScroll, BorderLayout.CENTER);
		center.add(inputPanel);
		body.add(center, BorderLayout.CENTER);
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
		actions.setOpaque(false);
		JButton refresh = themedButton("Refresh Current", e -> refreshPgnEditor());
		JButton copy = themedButton("Copy Current", e -> copyPgnText());
		JButton useCurrent = themedButton("Use Current", e -> useCurrentGamePgnAsInput());
		JButton apply = themedButton("Load Input", e -> applyPgnEditor());
		JButton paste = themedButton("Paste", e -> openPgnPasteDialog());
		JButton importBtn = themedButton("Import", e -> importPgnFromFile());
		JButton exportBtn = themedButton("Export", e -> exportPgnToFile());
		actions.add(refresh);
		actions.add(copy);
		actions.add(useCurrent);
		actions.add(apply);
		actions.add(paste);
		actions.add(importBtn);
		actions.add(exportBtn);
		body.add(actions, BorderLayout.SOUTH);
		card.setContent(body);
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.add(card, BorderLayout.CENTER);
		return panel; }
		/**
		 * buildCommandCard method.
		 *
		 * @return return value.
		 */
		private RoundedPanel buildCommandCard() {
			CommandCenterPanel.Result result = CommandCenterPanel.build(commandCenterContext, commandSpecs);
			commandSelect = result.commandSelect();
			commandFieldsPanel = result.commandFieldsPanel();
			commandFenPanel = result.commandFenPanel();
			commandFenField = result.commandFenField();
			commandFenHint = result.commandFenHint();
			commandExtraArgsField = result.commandExtraArgsField();
			useFenToggle = result.useFenToggle();
			runButton = result.runButton();
			stopButton = result.stopButton();
			helpButton = result.helpButton();
			recentCommandModel = result.recentCommandModel();
			recentCommandList = result.recentCommandList();
			recentCommandScroll = result.recentCommandScroll();
			baseRecentScrollPref = result.recentScrollPref();
			commandOutput = result.commandOutput();
			updateCommandForm();
			return result.panel();
		}
	}
