package application.gui.window;

import application.Config;
import application.cli.Format;
import application.cli.command.CommandSupport;
import application.gui.GuiTheme;
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
import application.gui.history.fen.FenListManager;
import application.gui.history.ui.HistoryUiFactory;
import application.gui.history.command.CommandFormBuilder;
import application.gui.history.window.HistoryTabController;
import application.gui.history.window.HistoryTabDependencies;
import application.gui.history.window.HistoryTabDependencies;
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
 * Core GUI logic and history handling.
 *
 * Hosts the board, history tables, evaluation chips, and command/engine controls while wiring configuration, annotations, and lifecycle callbacks that subclasses like {@link GuiWindowEngine} fulfill.
 *
 * @since 2026
  * @author Lennart A. Conrad
 */
abstract class GuiWindowHistoryCore extends GuiWindowBase {

		// Engine/command hooks implemented in GuiWindowEngine.
		/**
		 * runCliCommand method.
		 * @param command parameter.
		 * @param args parameter.
		 */
		protected abstract void runCliCommand(String command, List<String> args);

		protected abstract java.util.Map<String, CommandSpec> buildCommandSpecs();

		/**
		 * field method.
		 * @param flag parameter.
		 * @param label parameter.
		 * @param type parameter.
		 * @param placeholder parameter.
		 * @return return value.
		 */
		protected abstract CommandFieldSpec field(String flag, String label, CommandFieldType type, String placeholder);

		/**
		 * setEngineStatus method.
		 * @param status parameter.
		 */
		protected abstract void setEngineStatus(String status);

		/**
		 * updateEngineStats method.
		 * @param analysis parameter.
		 */
		protected abstract void updateEngineStats(Analysis analysis);

		/**
		 * startEngine method.
		 */
		protected abstract void startEngine();

		/**
		 * stopEngine method.
		 */
		protected abstract void stopEngine();

		/**
		 * restartEngine method.
		 */
		protected abstract void restartEngine();

		/**
		 * playEngineBest method.
		 */
		protected abstract void playEngineBest();

		/**
		 * splitArgs method.
		 * @param input parameter.
		 * @return return value.
		 */
		protected abstract List<String> splitArgs(String input);

		// Cross-part hooks declared in later split classes.
		/**
		 * scaleFont method.
		 *
		 * @param base parameter.
		 * @return return value.
		 */
		public abstract Font scaleFont(Font base);
		/**
		 * scaleInt method.
		 *
		 * @param value parameter.
		 * @return return value.
		 */
		protected abstract int scaleInt(int value);
		/**
		 * scaleDimension method.
		 *
		 * @param base parameter.
		 * @return return value.
		 */
		protected abstract Dimension scaleDimension(Dimension base);
		/**
		 * blend method.
		 *
		 * @param base parameter.
		 * @param overlay parameter.
		 * @param amount parameter.
		 * @return return value.
		 */
		public abstract Color blend(Color base, Color overlay, float amount);
		/**
		 * installListHover method.
		 *
		 * @param list parameter.
		 */
		protected abstract void installListHover(JList<?> list);
		/**
		 * installTableHover method.
		 *
		 * @param table parameter.
		 */
		protected abstract void installTableHover(JTable table);
		/**
		 * clearHoverPreviews method.
		 */
		protected abstract void clearHoverPreviews();
		/**
		 * refreshEcoCurrent method.
		 */
		protected abstract void refreshEcoCurrent();
		/**
		 * updateFenStatus method.
		 */
		protected abstract void updateFenStatus();
		/**
		 * updateFenSourceButton method.
		 */
		protected abstract void updateFenSourceButton();
		/**
		 * refreshPgnEditor method.
		 */
		protected abstract void refreshPgnEditor();
		/**
		 * updatePgnSummaryLabel method.
		 */
		protected abstract void updatePgnSummaryLabel();
		/**
		 * canUserPlayCurrentTurn method.
		 *
		 * @return return value.
		 */
		public abstract boolean canUserPlayCurrentTurn();
		/**
		 * ensureGameTree method.
		 */
		protected abstract void ensureGameTree();
		/**
		 * selectHistoryNode method.
		 *
		 * @param node parameter.
		 */
		protected abstract void selectHistoryNode(PgnNode node);
		/**
		 * updateVariationButtons method.
		 */
		protected abstract void updateVariationButtons();
		/**
		 * nagGlyphFromCode method.
		 *
		 * @param nag parameter.
		 * @return return value.
		 */
		protected abstract String nagGlyphFromCode(int nag);
		/**
		 * applyPgnNode method.
		 *
		 * @param node parameter.
		 */
		protected abstract void applyPgnNode(PgnNode node);
		/**
		 * requestVsEngineReplyIfNeeded method.
		 */
		protected abstract void requestVsEngineReplyIfNeeded();
		/**
		 * loadPgnText method.
		 *
		 * @param text parameter.
		 * @return return value.
		 */
		protected abstract boolean loadPgnText(String text);
		/**
		 * buildCurrentPgnText method.
		 *
		 * @return return value.
		 */
		protected abstract String buildCurrentPgnText();
		/**
		 * copyBoardImage method.
		 */
		protected abstract void copyBoardImage();
		/**
		 * saveBoardImage method.
		 */
		protected abstract void saveBoardImage();
		/**
		 * continueVsEngineFromCurrent method.
		 */
		protected abstract void continueVsEngineFromCurrent();
		/**
		 * startVsEngineNewGame method.
		 */
		protected abstract void startVsEngineNewGame();
		/**
		 * stopVsEngineGame method.
		 */
		protected abstract void stopVsEngineGame();
		/**
		 * loadFenList method.
		 */
		protected abstract void loadFenList();
		/**
		 * openFenSourceFile method.
		 */
		protected abstract void openFenSourceFile();
		/**
		 * navigatePrev method.
		 */
		protected abstract void navigatePrev();
		/**
		 * navigateNext method.
		 */
		protected abstract void navigateNext();
		/**
		 * navigateStart method.
		 */
		protected abstract void navigateStart();
		/**
		 * navigateEnd method.
		 */
		protected abstract void navigateEnd();
		/**
		 * scheduleGuiStateSave method.
		 */
		protected abstract void scheduleGuiStateSave();

		/**
		 * toggleTheme method.
		 */
		protected abstract void toggleTheme();

		/**
		 * togglePanel method.
		 */
		protected abstract void togglePanel();

		/**
		 * setPanelVisible method.
		 *
		 * @param visible parameter.
		 * @param persist parameter.
		 */
		protected abstract void setPanelVisible(boolean visible, boolean persist);

		/**
		 * openPanelTab method.
		 *
		 * @param title parameter.
		 */
		protected abstract void openPanelTab(String title);

		/**
		 * toggleEnginePower method.
		 */
		protected abstract void toggleEnginePower();

	// Main GUI window implementation.
			/**
			 * frame field.
			 */
			protected JFrame frame;
			/**
			 * root field.
			 */
			protected GradientPanel root;
			/**
			 * boardPanel field.
			 */
			protected BoardPanel boardPanel;
			/**
			 * evalBar field.
			 */
			protected EvalBar evalBar;
	
			/**
			 * theme field.
			 */
			protected GuiTheme theme;
			/**
			 * whiteDown field.
			 */
			protected boolean whiteDown;
			/**
			 * lightMode field.
			 */
			protected boolean lightMode;
			/**
			 * positionVersion field.
			 */
			protected volatile long positionVersion = 0;
			/**
			 * boardHueDegrees field.
			 */
			protected int boardHueDegrees = 0;
	
			/**
			 * fenField field.
			 */
			protected JTextField fenField;
			/**
			 * utilityField field.
			 */
			protected JTextField utilityField;
			/**
			 * infoArea field.
			 */
			protected JTextArea infoArea;
			/**
			 * commandOutput field.
			 */
			protected JTextArea commandOutput;
			/**
			 * recentCommandModel field.
			 */
			protected DefaultListModel<RecentCommand> recentCommandModel;
			/**
			 * recentCommandList field.
			 */
			protected JList<RecentCommand> recentCommandList;
			/**
			 * commandSelect field.
			 */
			protected JComboBox<String> commandSelect;
			/**
			 * commandFieldsPanel field.
			 */
			protected JPanel commandFieldsPanel;
			/**
			 * commandExtraArgsField field.
			 */
			protected JTextField commandExtraArgsField;
			/**
			 * useFenToggle field.
			 */
			protected JCheckBox useFenToggle;
			/**
			 * commandFenPanel field.
			 */
			protected JPanel commandFenPanel;
			/**
			 * commandFenField field.
			 */
			protected JTextField commandFenField;
			/**
			 * commandFenHint field.
			 */
			protected JLabel commandFenHint;
			/**
			 * commandFenCustom field.
			 */
			protected String commandFenCustom = "";
			/**
			 * runButton field.
			 */
			protected JButton runButton;
			/**
			 * stopButton field.
			 */
			protected JButton stopButton;
			/**
			 * helpButton field.
			 */
			protected JButton helpButton;
	
			/**
			 * historyListModel field.
			 */
			protected DefaultListModel<HistoryEntry> historyListModel;
			/**
			 * tagListModel field.
			 */
			protected DefaultListModel<String> tagListModel;
			/**
			 * historyList field.
			 */
			protected JList<HistoryEntry> historyList;
			/**
			 * tagList field.
			 */
			protected JList<String> tagList;
			/**
			 * historyScroll field.
			 */
			protected JScrollPane historyScroll;
			/**
			 * underboardMoveModel field.
			 */
			protected DefaultListModel<MovePair> underboardMoveModel;
			/**
			 * underboardMoveList field.
			 */
			protected JList<MovePair> underboardMoveList;
			/**
			 * underboardMoveScroll field.
			 */
			protected JScrollPane underboardMoveScroll;
			/**
			 * historyMenu field.
			 */
			protected JPopupMenu historyMenu;
			/**
			 * historyJumpItem field.
			 */
			protected JMenuItem historyJumpItem;
			/**
			 * historyCopySanItem field.
			 */
			protected JMenuItem historyCopySanItem;
			/**
			 * historyCopyUciItem field.
			 */
			protected JMenuItem historyCopyUciItem;
			/**
			 * historyPromoteItem field.
			 */
			protected JMenuItem historyPromoteItem;
			/**
			 * historyDeleteItem field.
			 */
			protected JMenuItem historyDeleteItem;
			/**
			 * historyVarUpItem field.
			 */
			protected JMenuItem historyVarUpItem;
			/**
			 * historyVarDownItem field.
			 */
			protected JMenuItem historyVarDownItem;
			/**
			 * historyCommentItem field.
			 */
			protected JMenuItem historyCommentItem;
			/**
			 * historyClearCommentItem field.
			 */
			protected JMenuItem historyClearCommentItem;
			/**
			 * historyClearNagItem field.
			 */
			protected JMenuItem historyClearNagItem;
			/**
			 * historyPromoteButton field.
			 */
			protected JButton historyPromoteButton;
			/**
			 * historyDeleteButton field.
			 */
			protected JButton historyDeleteButton;
			/**
			 * historyVarUpButton field.
			 */
			protected JButton historyVarUpButton;
			/**
			 * historyVarDownButton field.
			 */
			protected JButton historyVarDownButton;
			/**
			 * boardMenu field.
			 */
			protected JPopupMenu boardMenu;
			/**
			 * movesCard field.
			 */
			protected RoundedPanel movesCard;
			/**
			 * tagsCard field.
			 */
			protected RoundedPanel tagsCard;
			/**
			 * underboardCard field.
			 */
			protected RoundedPanel underboardCard;
			/**
			 * boardCard field.
			 */
			protected RoundedPanel boardCard;
			/**
			 * leftPane field.
			 */
			protected JPanel leftPane;
			/**
			 * boardWrap field.
			 */
			protected JPanel boardWrap;
			/**
			 * analysisView field.
			 */
			protected JComponent analysisView;
			/**
			 * legalToggle field.
			 */
			protected JCheckBox legalToggle;
			/**
			 * movesToggle field.
			 */
			protected JCheckBox movesToggle;
			/**
			 * tagsToggle field.
			 */
			protected JCheckBox tagsToggle;
			/**
			 * darkToggle field.
			 */
			protected JCheckBox darkToggle;
			/**
			 * bestMoveToggle field.
			 */
			protected JCheckBox bestMoveToggle;
			/**
			 * coordsToggle field.
			 */
			protected JCheckBox coordsToggle;
			/**
			 * hoverLegalToggle field.
			 */
			protected JCheckBox hoverLegalToggle;
			/**
			 * hoverHighlightToggle field.
			 */
			protected JCheckBox hoverHighlightToggle;
			/**
			 * hoverOnlyToggle field.
			 */
			protected JCheckBox hoverOnlyToggle;
			/**
			 * contrastToggle field.
			 */
			protected JCheckBox contrastToggle;
			/**
			 * compactToggle field.
			 */
			protected JCheckBox compactToggle;
			/**
			 * figurineSanToggle field.
			 */
			protected JCheckBox figurineSanToggle;
			/**
			 * hueSlider field.
			 */
			protected JSlider hueSlider;
			/**
			 * hueValueLabel field.
			 */
			protected JLabel hueValueLabel;
			/**
			 * brightnessSlider field.
			 */
			protected JSlider brightnessSlider;
			/**
			 * brightnessValueLabel field.
			 */
			protected JLabel brightnessValueLabel;
			/**
			 * saturationSlider field.
			 */
			protected JSlider saturationSlider;
			/**
			 * saturationValueLabel field.
			 */
			protected JLabel saturationValueLabel;
			/**
			 * animationSlider field.
			 */
			protected JSlider animationSlider;
			/**
			 * animationValueLabel field.
			 */
			protected JLabel animationValueLabel;
			/**
			 * uiScaleSlider field.
			 */
			protected JSlider uiScaleSlider;
			/**
			 * uiScaleValueLabel field.
			 */
			protected JLabel uiScaleValueLabel;
	
			/**
			 * engineStatusLabel field.
			 */
			protected JLabel engineStatusLabel;
			/**
			 * headerEngineStatus field.
			 */
			protected JLabel headerEngineStatus;
			/**
			 * headerEngineStats field.
			 */
			protected JLabel headerEngineStats;
			/**
			 * engineProtocolField field.
			 */
			protected JTextField engineProtocolField;
			/**
			 * engineNodesField field.
			 */
			protected JTextField engineNodesField;
			/**
			 * engineTimeField field.
			 */
			protected JTextField engineTimeField;
			/**
			 * engineMultiPvBox field.
			 */
			protected JComboBox<Integer> engineMultiPvBox;
			/**
			 * pvWrapModeBox field.
			 */
			protected JComboBox<String> pvWrapModeBox;
			/**
			 * engineEndlessToggle field.
			 */
			protected JCheckBox engineEndlessToggle;
			/**
			 * engineManageButton field.
			 */
			protected JButton engineManageButton;
			/**
			 * engineOutputArea field.
			 */
			protected JTextArea engineOutputArea;
			/**
			 * engineBestArea field.
			 */
			protected JTextArea engineBestArea;
			/**
			 * pvListModel field.
			 */
			protected DefaultListModel<PvEntry> pvListModel;
			/**
			 * pvList field.
			 */
			protected JList<PvEntry> pvList;
			/**
			 * pvListScroll field.
			 */
			protected JScrollPane pvListScroll;
			/**
			 * Constructor.
			 */
			protected final Set<Integer> expandedPvRows = new HashSet<>();
			/**
			 * engineStartButton field.
			 */
			protected JButton engineStartButton;
			/**
			 * engineStopButton field.
			 */
			protected JButton engineStopButton;
			/**
			 * engineBestButton field.
			 */
			protected JButton engineBestButton;
			/**
			 * boardBestButton field.
			 */
			protected JButton boardBestButton;
			/**
			 * boardBestIconButton field.
			 */
			protected JButton boardBestIconButton;
			/**
			 * engineVsNewButton field.
			 */
			protected JButton engineVsNewButton;
			/**
			 * engineVsContinueButton field.
			 */
			protected JButton engineVsContinueButton;
			/**
			 * engineVsStopButton field.
			 */
			protected JButton engineVsStopButton;
			/**
			 * engineCopyPvButton field.
			 */
			protected JButton engineCopyPvButton;
			/**
			 * engineLockPvToggle field.
			 */
			protected JCheckBox engineLockPvToggle;
			/**
			 * vsEnginePlayWhiteToggle field.
			 */
			protected JCheckBox vsEnginePlayWhiteToggle;
			/**
			 * engineDepthValue field.
			 */
			protected JLabel engineDepthValue;
			/**
			 * engineNodesValue field.
			 */
			protected JLabel engineNodesValue;
			/**
			 * engineTimeValue field.
			 */
			protected JLabel engineTimeValue;
			/**
			 * engineNpsValue field.
			 */
			protected JLabel engineNpsValue;
			/**
			 * pvHoverAnimationTimer field.
			 */
			protected Timer pvHoverAnimationTimer;
			/**
			 * pvHoverAnimationActive field.
			 */
			protected boolean pvHoverAnimationActive = false;
			/**
			 * pvHoverHighlightAmount field.
			 */
			protected float pvHoverHighlightAmount = 0f;
			/**
			 * engineLockPv field.
			 */
			protected boolean engineLockPv = false;
			/**
			 * lockedBestMoves field.
			 */
			protected String lockedBestMoves;
			/**
			 * lockedBestMove field.
			 */
			protected short lockedBestMove = Move.NO_MOVE;
			/**
			 * lockedAnalysis field.
			 */
			protected Analysis lockedAnalysis;
			/**
			 * engineBestMove field.
			 */
			protected short engineBestMove = Move.NO_MOVE;
			/**
			 * Void field.
			 */
			protected SwingWorker<Void, EngineUpdate> engineWorker;
			/**
			 * engineStopRequested field.
			 */
			protected volatile boolean engineStopRequested = false;
			/**
			 * engineRestartQueued field.
			 */
			protected boolean engineRestartQueued = false;
			/**
			 * engineInstance field.
			 */
			protected Engine engineInstance;
			/**
			 * engineProtocolPath field.
			 */
			protected String engineProtocolPath;
			/**
			 * latestAnalysis field.
			 */
			protected Analysis latestAnalysis;
			/**
			 * latestAnalysisVersion field.
			 */
			protected long latestAnalysisVersion = -1;
			/**
			 * vsEngineMode field.
			 */
			protected boolean vsEngineMode = false;
			/**
			 * vsEngineHumanWhite field.
			 */
			protected boolean vsEngineHumanWhite = true;
			/**
			 * vsEngineAwaitingReply field.
			 */
			protected boolean vsEngineAwaitingReply = false;
			/**
			 * Constructor.
			 */
			protected final Map<String, EngineCacheEntry> engineCache = new HashMap<>();
			/**
			 * engineCacheConfigKey field.
			 */
			protected String engineCacheConfigKey = "";
			/**
			 * Void field.
			 */
			protected SwingWorker<Void, ReportUpdate> reportWorker;
			/**
			 * reportStopRequested field.
			 */
			protected volatile boolean reportStopRequested = false;

			/**
			 * reportAnalyzeButton field.
			 */
			protected JButton reportAnalyzeButton;
			/**
			 * reportStopButton field.
			 */
			protected JButton reportStopButton;
			/**
			 * reportApplyNagButton field.
			 */
			protected JButton reportApplyNagButton;
			/**
			 * reportStatusLabel field.
			 */
			protected JLabel reportStatusLabel;
			/**
			 * reportListModel field.
			 */
			protected DefaultListModel<ReportEntry> reportListModel;
			/**
			 * reportList field.
			 */
			protected JList<ReportEntry> reportList;
			/**
			 * Constructor.
			 */
			protected final Evaluator ablationEvaluator = new Evaluator();
			/**
			 * ablationVersion field.
			 */
			protected long ablationVersion = 0;
			/**
			 * ablationMatrix field.
			 */
			protected int[][] ablationMatrix;
			/**
			 * ablationLabels field.
			 */
			protected String[] ablationLabels;

			/**
			 * annotateMoveLabel field.
			 */
			protected JLabel annotateMoveLabel;
			/**
			 * annotateCommentArea field.
			 */
			protected JTextArea annotateCommentArea;
			/**
			 * annotateSaveButton field.
			 */
			protected JButton annotateSaveButton;
			/**
			 * annotateClearCommentButton field.
			 */
			protected JButton annotateClearCommentButton;
			/**
			 * annotateClearNagButton field.
			 */
			protected JButton annotateClearNagButton;
			/**
			 * Constructor.
			 */
			protected final Map<Integer, JCheckBox> annotateNagButtons = new HashMap<>();
			/**
			 * annotateNagGroup field.
			 */
			protected ButtonGroup annotateNagGroup;
	
			/**
			 * fenListStatus field.
			 */
			protected JLabel fenListStatus;
			/**
			 * fenIndexField field.
			 */
			protected JTextField fenIndexField;
			/**
			 * fenSourceLabel field.
			 */
			protected JLabel fenSourceLabel;
			/**
			 * fenOpenButton field.
			 */
			protected JButton fenOpenButton;
			/**
			 * fenListManager field.
			 */
			protected final FenListManager fenListManager = new FenListManager();
			/**
			 * rightTabs field.
			 */
			protected JTabbedPane rightTabs;
			/**
			 * sidebarHeader field.
			 */
			protected JComponent sidebarHeader;
			/**
			 * sidebarHeaderLabel field.
			 */
			protected JLabel sidebarHeaderLabel;
			/**
			 * rightSidebarScroll field.
			 */
			protected JScrollPane rightSidebarScroll;
			/**
			 * recentCommandScroll field.
			 */
			protected JScrollPane recentCommandScroll;
			/**
			 * ablationModel field.
			 */
			protected DefaultTableModel ablationModel;
			/**
			 * ablationTable field.
			 */
			protected JTable ablationTable;
			/**
			 * ablationToggle field.
			 */
			protected JCheckBox ablationToggle;
			/**
			 * variationModel field.
			 */
			protected DefaultTableModel variationModel;
			/**
			 * variationTable field.
			 */
			protected JTable variationTable;
			/**
			 * Constructor.
			 */
			protected final List<PgnNode> variationNodes = new ArrayList<>();
			/**
			 * evalGraphPanel field.
			 */
			protected EvalGraphPanel evalGraphPanel;
			/**
			 * analysisSplit field.
			 */
			protected JSplitPane analysisSplit;
			/**
			 * topBar field.
			 */
			protected JComponent topBar;
			/**
			 * activityBar field.
			 */
			protected JComponent activityBar;
			/**
			 * statusBar field.
			 */
			protected JComponent statusBar;
			/**
			 * statusLeftLabel field.
			 */
			protected JLabel statusLeftLabel;
			/**
			 * statusMiddleLabel field.
			 */
			protected JLabel statusMiddleLabel;
			/**
			 * statusRightLabel field.
			 */
			protected JLabel statusRightLabel;
			/**
			 * statusEngineLabel field.
			 */
			protected JLabel statusEngineLabel;
			/**
			 * statusProblemLabel field.
			 */
			protected JLabel statusProblemLabel;
			/**
			 * statusThemeLabel field.
			 */
			protected JLabel statusThemeLabel;
			/**
			 * statusPanelLabel field.
			 */
			protected JLabel statusPanelLabel;
			/**
			 * statusModeLabel field.
			 */
			protected JLabel statusModeLabel;
			/**
			 * statusEolLabel field.
			 */
			protected JLabel statusEolLabel;
			/**
			 * statusEncodingLabel field.
			 */
			protected JLabel statusEncodingLabel;
			/**
			 * statusBranchLabel field.
			 */
			protected JLabel statusBranchLabel;
			/**
			 * statusTabPicker field.
			 */
			protected JComboBox<String> statusTabPicker;
			/**
			 * statusTabUpdating field.
			 */
			protected boolean statusTabUpdating = false;
			/**
			 * problemCount field.
			 */
			protected int problemCount = 0;
			/**
			 * lastProblemMessage field.
			 */
			protected String lastProblemMessage = "";
			/**
			 * panelTabs field.
			 */
			protected JTabbedPane panelTabs;
			/**
			 * editorTabs field.
			 */
			protected JTabbedPane editorTabs;
			/**
			 * panelOutputArea field.
			 */
			protected JTextArea panelOutputArea;
			/**
			 * panelTerminalArea field.
			 */
			protected JTextArea panelTerminalArea;
			/**
			 * panelDebugArea field.
			 */
			protected JTextArea panelDebugArea;
			/**
			 * problemListModel field.
			 */
			protected DefaultListModel<String> problemListModel;
			/**
			 * problemList field.
			 */
			protected JList<String> problemList;
			/**
			 * panelVisible field.
			 */
			protected boolean panelVisible = false;
			/**
			 * boardControlsRow field.
			 */
			protected JComponent boardControlsRow;
			/**
			 * boardBars field.
			 */
			protected JComponent boardBars;
			/**
			 * boardActionStrip field.
			 */
			protected JComponent boardActionStrip;
			/**
			 * focusCornerHost field.
			 */
			protected JComponent focusCornerHost;
			/**
			 * sidebarToggleButton field.
			 */
			protected JButton sidebarToggleButton;
			/**
			 * focusToggleButton field.
			 */
			protected JButton focusToggleButton;
			/**
			 * focusExitCornerButton field.
			 */
			protected JButton focusExitCornerButton;
			/**
			 * ecoSearchField field.
			 */
			protected JTextField ecoSearchField;
			/**
			 * ecoStatusLabel field.
			 */
			protected JLabel ecoStatusLabel;
			/**
			 * ecoCurrentLabel field.
			 */
			protected JLabel ecoCurrentLabel;
			/**
			 * ecoDetailArea field.
			 */
			protected JTextArea ecoDetailArea;
			/**
			 * ecoListModel field.
			 */
			protected DefaultListModel<Entry> ecoListModel;
			/**
			 * ecoList field.
			 */
			protected JList<Entry> ecoList;
			/**
			 * ecoTableModel field.
			 */
			protected DefaultTableModel ecoTableModel;
			/**
			 * ecoTable field.
			 */
			protected JTable ecoTable;
			/**
			 * Constructor.
			 */
			protected final List<Entry> ecoFilteredEntries = new ArrayList<>();
			/**
			 * pgnViewerArea field.
			 */
			protected JTextArea pgnViewerArea;
			/**
			 * pgnEditorArea field.
			 */
			protected JTextArea pgnEditorArea;
			/**
			 * pgnSummaryLabel field.
			 */
			protected JLabel pgnSummaryLabel;
			/**
			 * engineSearchField field.
			 */
			protected JTextField engineSearchField;
			protected final java.util.Map<String, String> pgnTags = new java.util.LinkedHashMap<>();
			/**
			 * pgnResult field.
			 */
			protected String pgnResult = "*";
			/**
			 * pgnStartPosition field.
			 */
			protected Position pgnStartPosition;
			/**
			 * ecoBook field.
			 */
			protected Encyclopedia ecoBook;
			/**
			 * of method.
			 */
			protected List<Entry> ecoEntries = List.of();
			/**
			 * ecoSelected field.
			 */
			protected Entry ecoSelected;
			/**
			 * editorDialog field.
			 */
			protected BoardEditorDialog editorDialog;
			/**
			 * boardEditorPane field.
			 */
			protected BoardEditorPane boardEditorPane;
			/**
			 * pgnDialog field.
			 */
			protected PgnPasteDialog pgnDialog;
			/**
			 * engineManagerDialog field.
			 */
			protected EngineManagerDialog engineManagerDialog;
			/**
			 * commandPaletteDialog field.
			 */
			protected CommandPaletteDialog commandPaletteDialog;
			/**
			 * pgnNavigator field.
			 */
			protected PgnNavigator pgnNavigator;
			/**
			 * mainSplit field.
			 */
			protected JSplitPane mainSplit;
			/**
			 * guiStateSaveTimer field.
			 */
			protected Timer guiStateSaveTimer;
			/**
			 * sidebarVisible field.
			 */
			protected boolean sidebarVisible = true;
			/**
			 * focusMode field.
			 */
			protected boolean focusMode = false;
			/**
			 * zenMode field.
			 */
			protected boolean zenMode = false;
			/**
			 * sidebarLastDivider field.
			 */
			protected int sidebarLastDivider = -1;
	
			/**
			 * Constructor.
			 */
			protected final List<RoundedPanel> cards = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<RoundedPanel> flatCards = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JButton> buttons = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JButton> iconButtons = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JButton> activityButtons = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JCheckBox> checks = new ArrayList<>();
			/**
			 * Constructor.
			 */
		protected final List<JLabel> strongLabels = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JLabel> mutedLabels = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JTextField> textFields = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JTextArea> textAreas = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JList<?>> lists = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JTable> tables = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JScrollPane> scrolls = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JComboBox<?>> combos = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JSlider> sliders = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JTabbedPane> tabs = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<JComponent> separators = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<TabLabel> tabLabels = new ArrayList<>();

		protected final HistoryUiFactory uiFactory = new HistoryUiFactory(
				() -> theme, cards, flatCards, buttons, iconButtons, strongLabels, mutedLabels, separators);
		/**
		 * commandFormBuilder field.
		 */
		protected final CommandFormBuilder commandFormBuilder = new CommandFormBuilder(uiFactory, textFields::add);
			/**
			 * tabDependencies field.
			 */
			protected final HistoryTabDependencies tabDependencies = new TabDependencies();
			/**
			 * tabController field.
			 */
			protected final HistoryTabController tabController = new HistoryTabController(tabDependencies);

			/**
			 * Constructor.
			 */
			protected final List<CommandFieldBinding> commandBindings = new ArrayList<>();
			protected final java.util.Map<String, CommandSpec> commandSpecs = buildCommandSpecs();
	
			/**
			 * position field.
			 */
			protected Position position;
			/**
			 * Constructor.
			 */
			protected final List<Position> history = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<String> moveHistory = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<PgnNode> currentLineNodes = new ArrayList<>();
			/**
			 * Constructor.
			 */
			protected final List<String> fenList = new ArrayList<>();
			/**
			 * fenIndex field.
			 */
			protected int fenIndex = -1;
			/**
			 * fenSource field.
			 */
			protected String fenSource = "";
			/**
			 * fenSourcePath field.
			 */
			protected Path fenSourcePath;
			/**
			 * currentProcess field.
			 */
			protected Process currentProcess;
			/**
			 * guiState field.
			 */
			protected GuiState guiState;
			/**
			 * showCoords field.
			 */
			protected boolean showCoords = true;
			/**
			 * hoverLegal field.
			 */
			protected boolean hoverLegal = true;
			/**
			 * hoverHighlight field.
			 */
			protected boolean hoverHighlight = true;
			/**
			 * hoverOnlyLegal field.
			 */
			protected boolean hoverOnlyLegal = false;
			/**
			 * highContrast field.
			 */
			protected boolean highContrast = false;
			/**
			 * compactMode field.
			 */
			protected boolean compactMode = false;
			/**
			 * figurineSan field.
			 */
			protected boolean figurineSan = false;
			/**
			 * uiScale field.
			 */
			protected float uiScale = 1.0f;
			/**
			 * uiScaleApplyPending field.
			 */
			protected boolean uiScaleApplyPending = false;
			/**
			 * boardBrightness field.
			 */
			protected int boardBrightness = 100;
			/**
			 * boardSaturation field.
			 */
			protected int boardSaturation = 100;
			/**
			 * animationMillis field.
			 */
			protected int animationMillis = 100;
			/**
			 * baseFrameMin field.
			 */
			protected Dimension baseFrameMin;
			/**
			 * baseBoardMin field.
			 */
			protected Dimension baseBoardMin;
			/**
			 * baseBoardWrapMin field.
			 */
			protected Dimension baseBoardWrapMin;
			/**
			 * baseLeftMin field.
			 */
			protected Dimension baseLeftMin;
			/**
			 * baseSidebarMin field.
			 */
			protected Dimension baseSidebarMin;
			/**
			 * baseSidebarPref field.
			 */
			protected Dimension baseSidebarPref;
			/**
			 * baseHistoryScrollPref field.
			 */
			protected Dimension baseHistoryScrollPref;
			/**
			 * baseUnderboardScrollPref field.
			 */
			protected Dimension baseUnderboardScrollPref;
			/**
			 * baseRecentScrollPref field.
			 */
			protected Dimension baseRecentScrollPref;
			/**
			 * lastMove field.
			 */
			protected short lastMove = Move.NO_MOVE;
			/**
			 * Pending transition move consumed by {@link BoardPanel} for one-shot animations.
			 */
			protected short pendingAnimationMove = Move.NO_MOVE;
			protected java.awt.KeyEventDispatcher navigationDispatcher;
			/**
			 * One-shot flag to skip board animation on the next refresh.
			 */
			protected boolean skipNextBoardAnimation = false;
			/**
			 * historyStartFullmove field.
			 */
			protected int historyStartFullmove = 1;
			/**
			 * historyStartWhite field.
			 */
			protected boolean historyStartWhite = true;

	/**
	 * installTabLabels method.
	 */
	protected void installTabLabels() {
		tabController.installTabLabels();
	}

			/**
			 * updateTabLabelStyles method.
			 */
			protected void updateTabLabelStyles() {
				tabController.updateTabLabelStyles();
			}

			/**
			 * tabIndexByTitle method.
			 * @param title parameter.
			 * @return return value.
			 */
			protected int tabIndexByTitle(String title) {
				if (rightTabs == null || title == null) {
					return -1;
				}
				for (int i = 0; i < rightTabs.getTabCount(); i++) {
					if (title.equalsIgnoreCase(rightTabs.getTitleAt(i))) {
						return i;
					}
				}
				return -1;
			}

			/**
			 * selectRightTab method.
			 * @param index parameter.
			 */
			protected void selectRightTab(int index) {
				if (rightTabs == null || index < 0 || index >= rightTabs.getTabCount()) {
					return;
				}
				clearHoverPreviews();
				rightTabs.setSelectedIndex(index);
				ensureComfortSidebarWidth();
			}

			/**
			 * sidebarMinWidthPx method.
			 * @return return value.
			 */
			protected int sidebarMinWidthPx() {
				if (baseSidebarMin != null) {
					return scaleDimension(baseSidebarMin).width;
				}
				return scaleInt(360);
			}

			/**
			 * sidebarComfortWidthPx method.
			 * @return return value.
			 */
			protected int sidebarComfortWidthPx() {
				int min = sidebarMinWidthPx();
				int byWindow = frame != null ? Math.round(frame.getWidth() * 0.32f) : min;
				return Math.max(min, byWindow);
			}

			/**
			 * clampSidebarWidthPx method.
			 * @param width parameter.
			 * @return return value.
			 */
			protected int clampSidebarWidthPx(int width) {
				int min = sidebarMinWidthPx();
				int total = 0;
				if (mainSplit != null && mainSplit.getWidth() > 0) {
					total = mainSplit.getWidth();
				} else if (frame != null) {
					total = frame.getWidth();
				}
				if (total <= 0) {
					return Math.max(min, width);
				}
				int max = Math.max(min, Math.round(total * 0.55f));
				return Math.max(min, Math.min(width, max));
			}

			/**
			 * ensureComfortSidebarWidth method.
			 */
			protected void ensureComfortSidebarWidth() {
				if (mainSplit == null || rightSidebarScroll == null) {
					return;
				}
				if (!rightSidebarScroll.isVisible() || focusMode || zenMode) {
					return;
				}
				int desired = clampSidebarWidthPx(sidebarComfortWidthPx());
				int current = mainSplit.getDividerLocation();
				if (current < desired) {
					mainSplit.setDividerLocation(desired);
					sidebarLastDivider = desired;
				}
			}

			/**
			 * selectEditorTab method.
			 * @param title parameter.
			 */
			protected void selectEditorTab(String title) {
				if (editorTabs == null || title == null) {
					return;
				}
				for (int i = 0; i < editorTabs.getTabCount(); i++) {
					if (title.equalsIgnoreCase(editorTabs.getTitleAt(i))) {
						clearHoverPreviews();
						editorTabs.setSelectedIndex(i);
						if ("board.pgn".equalsIgnoreCase(title) && boardPanel != null) {
							boardPanel.requestFocusInWindow();
						}
						return;
					}
				}
			}

			/**
			 * currentEditorTabTitle method.
			 * @return return value.
			 */
			protected String currentEditorTabTitle() {
				if (editorTabs == null) {
					return null;
				}
				int index = editorTabs.getSelectedIndex();
				if (index < 0 || index >= editorTabs.getTabCount()) {
					return null;
				}
				return editorTabs.getTitleAt(index);
			}

			/**
			 * isEditorTabSelected method.
			 * @param title parameter.
			 * @return return value.
			 */
			protected boolean isEditorTabSelected(String title) {
				if (editorTabs == null || title == null) {
					return false;
				}
				int index = editorTabs.getSelectedIndex();
				if (index < 0 || index >= editorTabs.getTabCount()) {
					return false;
				}
				return title.equalsIgnoreCase(editorTabs.getTitleAt(index));
			}

			/**
			 * updateActivitySelection method.
			 */
			protected void updateActivitySelection() {
				tabController.updateActivitySelection();
			}

			/**
			 * refreshStatusTabPicker method.
			 */
			protected void refreshStatusTabPicker() {
				tabController.refreshStatusTabPicker();
			}

			/**
			 * updateStatusTabPickerSelection method.
			 */
			protected void updateStatusTabPickerSelection() {
				tabController.updateStatusTabPickerSelection();
			}

			/**
			 * themeSplitPane method.
			 * @param split parameter.
			 */
			protected void themeSplitPane(JSplitPane split) {
				if (split == null) {
					return;
				}
				split.setOpaque(false);
				split.setBackground(theme.border());
				split.setUI(new ThemedSplitPaneUI(theme));
			}

			/**
			 * sectionHeader method.
			 * @param text parameter.
			 * @return return value.
			 */
			protected JLabel sectionHeader(String text) {
				return uiFactory.sectionHeader(text);
			}

			/**
			 * separatorLine method.
			 * @return return value.
			 */
			protected JComponent separatorLine() {
				return uiFactory.separatorLine();
			}

		

		

		

		

		

		

		

		

			/**
			 * labeledField method.
			 * @param labelText parameter.
			 * @param field parameter.
			 * @return return value.
			 */
			protected JPanel labeledField(String labelText, JTextField field) {
				return uiFactory.labeledField(labelText, field);
			}

			/**
			 * labeledValue method.
			 * @param labelText parameter.
			 * @param value parameter.
			 * @return return value.
			 */
			protected JPanel labeledValue(String labelText, JLabel value) {
				return uiFactory.labeledValue(labelText, value);
			}

			/**
			 * labeledCombo method.
			 * @param labelText parameter.
			 * @param combo parameter.
			 * @return return value.
			 */
			protected JPanel labeledCombo(String labelText, JComboBox<?> combo) {
				return uiFactory.labeledCombo(labelText, combo);
			}

			/**
			 * settingsRow method.
			 * @param labelText parameter.
			 * @param control parameter.
			 * @return return value.
			 */
			protected JPanel settingsRow(String labelText, JComponent control) {
				return uiFactory.settingsRow(labelText, control);
			}

			/**
			 * createCard method.
			 * @param title parameter.
			 * @return return value.
			 */
			protected RoundedPanel createCard(String title) {
				return uiFactory.createCard(title);
			}

			/**
			 * createCard method.
			 * @param title parameter.
			 * @param showTitle parameter.
			 * @return return value.
			 */
			protected RoundedPanel createCard(String title, boolean showTitle) {
				return uiFactory.createCard(title, showTitle);
			}

			/**
			 * createFlatCard method.
			 * @param title parameter.
			 * @param showTitle parameter.
			 * @return return value.
			 */
			protected RoundedPanel createFlatCard(String title, boolean showTitle) {
				return uiFactory.createFlatCard(title, showTitle);
			}

			/**
			 * createFlatCard method.
			 * @param title parameter.
			 * @return return value.
			 */
			protected RoundedPanel createFlatCard(String title) {
				return uiFactory.createFlatCard(title);
			}

			/**
			 * titleLabel method.
			 * @param text parameter.
			 * @return return value.
			 */
			protected JLabel titleLabel(String text) {
				return uiFactory.titleLabel(text);
			}

			/**
			 * formatTitle method.
			 * @param text parameter.
			 * @return return value.
			 */
			protected String formatTitle(String text) {
				return uiFactory.formatTitle(text);
			}

			/**
			 * mutedLabel method.
			 * @param text parameter.
			 * @return return value.
			 */
			protected JLabel mutedLabel(String text) {
				return uiFactory.mutedLabel(text);
			}

			/**
			 * themedButton method.
			 * @param text parameter.
			 * @param action parameter.
			 * @return return value.
			 */
			protected JButton themedButton(String text, java.awt.event.ActionListener action) {
				return uiFactory.themedButton(text, action);
			}

			/**
			 * iconButton method.
			 * @param text parameter.
			 * @param action parameter.
			 * @return return value.
			 */
			protected JButton iconButton(String text, java.awt.event.ActionListener action) {
				return uiFactory.iconButton(text, action);
			}

			/**
			 * TabDependencies class.
			 *
			 * Provides class behavior for the GUI module.
			 *
			 * @since 2026
			 * @author Lennart A. Conrad
			 */
			private final class TabDependencies implements HistoryTabDependencies {

				@Override
				/**
				 * rightTabs method.
				 *
				 * @return return value.
				 */
				public JTabbedPane rightTabs() {
					return GuiWindowHistoryCore.this.rightTabs;
				}

				@Override
				/**
				 * tabLabels method.
				 *
				 * @return return value.
				 */
				public List<TabLabel> tabLabels() {
					return GuiWindowHistoryCore.this.tabLabels;
				}

				@Override
				/**
				 * activityButtons method.
				 *
				 * @return return value.
				 */
				public List<JButton> activityButtons() {
					return GuiWindowHistoryCore.this.activityButtons;
				}

				@Override
				/**
				 * sidebarHeaderLabel method.
				 *
				 * @return return value.
				 */
				public JLabel sidebarHeaderLabel() {
					return GuiWindowHistoryCore.this.sidebarHeaderLabel;
				}

				@Override
				/**
				 * statusTabPicker method.
				 *
				 * @return return value.
				 */
				public JComboBox<String> statusTabPicker() {
					return GuiWindowHistoryCore.this.statusTabPicker;
				}

				@Override
				/**
				 * theme method.
				 *
				 * @return return value.
				 */
				public GuiTheme theme() {
					return GuiWindowHistoryCore.this.theme;
				}

				@Override
				/**
				 * scaleFont method.
				 *
				 * @param base parameter.
				 * @return return value.
				 */
				public Font scaleFont(Font base) {
					return GuiWindowHistoryCore.this.scaleFont(base);
				}

				@Override
				/**
				 * blend method.
				 *
				 * @param base parameter.
				 * @param overlay parameter.
				 * @param amount parameter.
				 * @return return value.
				 */
				public Color blend(Color base, Color overlay, float amount) {
					return GuiWindowHistoryCore.this.blend(base, overlay, amount);
				}

				@Override
				/**
				 * formatTitle method.
				 *
				 * @param text parameter.
				 * @return return value.
				 */
				public String formatTitle(String text) {
					return uiFactory.formatTitle(text);
				}

				@Override
				/**
				 * statusTabUpdating method.
				 *
				 * @return return value.
				 */
				public boolean statusTabUpdating() {
					return GuiWindowHistoryCore.this.statusTabUpdating;
				}

				@Override
				/**
				 * setStatusTabUpdating method.
				 *
				 * @param updating parameter.
				 */
				public void setStatusTabUpdating(boolean updating) {
					GuiWindowHistoryCore.this.statusTabUpdating = updating;
				}

				@Override
				/**
				 * scaleDimension method.
				 *
				 * @param base parameter.
				 * @return return value.
				 */
				public Dimension scaleDimension(Dimension base) {
					return GuiWindowHistoryCore.this.scaleDimension(base);
				}

				@Override
				/**
				 * isLightMode method.
				 *
				 * @return return value.
				 */
				public boolean isLightMode() {
					return GuiWindowHistoryCore.this.lightMode;
				}

				@Override
				/**
				 * toggleTheme method.
				 */
				public void toggleTheme() {
					GuiWindowHistoryCore.this.toggleTheme();
				}

				@Override
				/**
				 * panelVisible method.
				 *
				 * @return return value.
				 */
				public boolean panelVisible() {
					return GuiWindowHistoryCore.this.panelVisible;
				}

				@Override
				/**
				 * togglePanel method.
				 */
				public void togglePanel() {
					GuiWindowHistoryCore.this.togglePanel();
				}

				@Override
				/**
				 * setPanelVisible method.
				 *
				 * @param visible parameter.
				 * @param persist parameter.
				 */
				public void setPanelVisible(boolean visible, boolean persist) {
					GuiWindowHistoryCore.this.setPanelVisible(visible, persist);
				}

				@Override
				/**
				 * openPanelTab method.
				 *
				 * @param title parameter.
				 */
				public void openPanelTab(String title) {
					GuiWindowHistoryCore.this.openPanelTab(title);
				}

				@Override
				/**
				 * toggleEnginePower method.
				 */
				public void toggleEnginePower() {
					GuiWindowHistoryCore.this.toggleEnginePower();
				}

				@Override
				/**
				 * selectRightTab method.
				 *
				 * @param index parameter.
				 */
				public void selectRightTab(int index) {
					GuiWindowHistoryCore.this.selectRightTab(index);
				}

				@Override
				/**
				 * registerCombo method.
				 *
				 * @param combo parameter.
				 */
				public void registerCombo(JComboBox<?> combo) {
					if (combo != null) {
						GuiWindowHistoryCore.this.combos.add(combo);
					}
				}

			}


}
