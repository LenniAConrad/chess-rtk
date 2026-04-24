package application.gui.studio;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import application.gui.util.TransferableImage;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.images.assets.Pictures;

/**
 * ChessRTK Studio GUI v3 window.
 */
public final class StudioWindow extends JFrame implements StudioController.StudioListener {

	/**
	 * Serialization identifier for Swing frame compatibility.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Controller backing the window.
	 */
	private final StudioController controller;

	/**
	 * Interactive chess board surface.
	 */
	private final StudioBoardPanel boardPanel = new StudioBoardPanel();

	/**
	 * Available task definitions.
	 */
	private final StudioTaskCatalog taskCatalog = StudioTaskCatalog.defaults();

	/**
	 * Background task runner for CLI tasks.
	 */
	private final StudioTaskRunner taskRunner = new StudioTaskRunner();

	/**
	 * Legal move list model.
	 */
	private final DefaultListModel<String> legalModel = new DefaultListModel<>();

	/**
	 * Game-tree list model.
	 */
	private final DefaultListModel<StudioGameNode> nodeModel = new DefaultListModel<>();

	/**
	 * Task launcher list model.
	 */
	private final DefaultListModel<StudioTask> taskModel = new DefaultListModel<>();

	/**
	 * FEN input field.
	 */
	private final JTextField fenField = new JTextField();

	/**
	 * SAN or UCI move input field.
	 */
	private final JTextField moveField = new JTextField();

	/**
	 * Engine protocol path input field.
	 */
	private final JTextField enginePathField = new JTextField(StudioEngineProfile.defaultProfile().path().toString());

	/**
	 * Engine node limit input field.
	 */
	private final JTextField nodesField = new JTextField("1000000");

	/**
	 * MultiPV count input field.
	 */
	private final JTextField multipvField = new JTextField("3");

	/**
	 * Win/draw/loss analysis toggle.
	 */
	private final JCheckBox wdlBox = new JCheckBox("WDL", true);

	/**
	 * Output drawer text area.
	 */
	private final JTextArea outputArea = new JTextArea(8, 40);

	/**
	 * Position note text area.
	 */
	private final JTextArea noteArea = new JTextArea(5, 24);

	/**
	 * Move comment text area.
	 */
	private final JTextArea commentArea = new JTextArea(4, 24);

	/**
	 * NAG entry field.
	 */
	private final JTextField nagsField = new JTextField();

	/**
	 * Footer status label.
	 */
	private final JLabel statusLabel = new JLabel("Ready");

	/**
	 * Current position summary label.
	 */
	private final JLabel positionLabel = new JLabel();

	/**
	 * Engine status label.
	 */
	private final JLabel engineLabel = new JLabel("Engine idle");

	/**
	 * Principal variation label.
	 */
	private final JLabel pvLabel = new JLabel("PV: -");

	/**
	 * Loaded FEN-list status label.
	 */
	private final JLabel fenListLabel = new JLabel("No FEN list");

	/**
	 * Panel containing position tag chips.
	 */
	private final JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));

	/**
	 * Right-side tabbed panel.
	 */
	private final JTabbedPane rightTabs = new JTabbedPane();

	/**
	 * Collapsible output drawer.
	 */
	private final JPanel outputDrawer = new JPanel(new BorderLayout());

	/**
	 * Evaluation balance strip beside the board.
	 */
	private final EvalStrip evalStrip = new EvalStrip();

	/**
	 * Game-tree list, initialized during layout construction.
	 */
	private JList<StudioGameNode> nodeList;

	/**
	 * Task launcher list, initialized during layout construction.
	 */
	private JList<StudioTask> taskList;

	/**
	 * Generated command preview field.
	 */
	private JTextField commandPreview;

	/**
	 * Guard flag used while list selections are refreshed programmatically.
	 */
	private boolean refreshing;

	/**
	 * Constructor.
	 *
	 * @param fen initial FEN
	 * @param whiteDown initial orientation
	 * @param lightMode initial theme
	 */
	public StudioWindow(String fen, boolean whiteDown, boolean lightMode) {
		super("ChessRTK Studio");
		StudioSettings settings = StudioSettings.load(lightMode, whiteDown);
		settings.setLightMode(lightMode);
		settings.setWhiteDown(whiteDown);
		controller = new StudioController(fen, settings);
		controller.setListener(this);
		setIconImage(Pictures.BlackRook);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(980, 680));
		setSize(settings.getWidth(), settings.getHeight());
		build();
		installCloseHandler();
		stateChanged();
		setLocationByPlatform(true);
		setVisible(true);
		boardPanel.requestFocusInWindow();
	}

	/**
	 * Builds the root window layout.
	 */
	private void build() {
		JPanel root = new JPanel(new BorderLayout(12, 12));
		root.setBorder(new EmptyBorder(12, 12, 12, 12));
		setContentPane(root);
		root.add(buildHeader(), BorderLayout.NORTH);
		root.add(buildCenter(), BorderLayout.CENTER);
		outputDrawer.add(new JScrollPane(outputArea), BorderLayout.CENTER);
		outputDrawer.setVisible(false);
		root.add(outputDrawer, BorderLayout.SOUTH);
		applyTheme();
	}

	/**
	 * Builds the header with status and global actions.
	 *
	 * @return header panel
	 */
	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout(10, 0));
		JLabel title = new JLabel("ChessRTK Studio");
		title.setFont(title.getFont().deriveFont(20f));
		header.add(title, BorderLayout.WEST);
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		header.add(statusLabel, BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		actions.add(button("Report", e -> copyReport()));
		actions.add(button("Copy board", e -> copyBoardImage()));
		actions.add(button("Save board", e -> saveBoardImage()));
		actions.add(button("Flip", e -> {
			controller.settings().setWhiteDown(!controller.settings().isWhiteDown());
			stateChanged();
		}));
		actions.add(button("Theme", e -> {
			controller.settings().setLightMode(!controller.settings().isLightMode());
			applyTheme();
			stateChanged();
		}));
		actions.add(button("Focus", e -> toggleFocus()));
		header.add(actions, BorderLayout.EAST);
		return header;
	}

	/**
	 * Builds the central board and side-tab split pane.
	 *
	 * @return center component
	 */
	private Component buildCenter() {
		JPanel boardWrap = new JPanel(new BorderLayout(8, 8));
		boardWrap.add(evalStrip, BorderLayout.WEST);
		boardWrap.add(boardPanel, BorderLayout.CENTER);
		boardPanel.setBoardListener(new StudioBoardPanel.BoardListener() {
			/**
			 * Selects the requested square in the controller.
			 *
			 * @param square selected square
			 */
			@Override
			public void squareSelected(byte square) {
				controller.selectSquare(square);
			}

			/**
			 * Requests a controller move from the source square to the target square.
			 *
			 * @param from source square
			 * @param to target square
			 */
			@Override
			public void moveRequested(byte from, byte to) {
				controller.playFromTo(from, to);
			}

			/**
			 * Toggles the requested visual board mark.
			 *
			 * @param mark mark request
			 */
			@Override
			public void markRequested(BoardMark mark) {
				controller.toggleMark(mark);
			}

			/**
			 * Receives hover updates from the board surface.
			 *
			 * @param square hovered square
			 */
			@Override
			public void hoverSquare(byte square) {
				// Kept for future status hover text.
			}
		});
		rightTabs.addTab("Analyze", buildAnalyzeTab());
		rightTabs.addTab("Game", buildGameTab());
		rightTabs.addTab("Tasks", buildTasksTab());
		rightTabs.addTab("Project", buildProjectTab());
		rightTabs.setVisible(!controller.settings().isFocusMode());
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, boardWrap, rightTabs);
		split.setResizeWeight(0.68);
		split.setContinuousLayout(true);
		split.setDividerSize(4);
		return split;
	}

	/**
	 * Builds the analysis tab.
	 *
	 * @return analysis tab component
	 */
	private Component buildAnalyzeTab() {
		JPanel panel = verticalPanel();
		panel.add(positionLabel);
		panel.add(label("FEN"));
		panel.add(fenField);
		JPanel fenActions = row();
		fenActions.add(button("Load", e -> controller.loadFen(fenField.getText())));
		fenActions.add(button("Copy", e -> copyText(controller.position().toString())));
		panel.add(fenActions);
		panel.add(label("Move input"));
		moveField.addActionListener(e -> {
			controller.playTextMove(moveField.getText());
			moveField.setText("");
		});
		panel.add(moveField);
		panel.add(label("Engine profile"));
		panel.add(enginePathField);
		JPanel engineOptions = new JPanel(new GridLayout(1, 3, 6, 0));
		engineOptions.add(nodesField);
		engineOptions.add(multipvField);
		engineOptions.add(wdlBox);
		panel.add(engineOptions);
		JPanel engineActions = row();
		engineActions.add(button("Start", e -> startEngine()));
		engineActions.add(button("Stop", e -> controller.stopEngine()));
		engineActions.add(button("Play best", e -> playBestMove()));
		panel.add(engineActions);
		panel.add(engineLabel);
		panel.add(pvLabel);
		panel.add(label("Legal moves"));
		JList<String> legalList = new JList<>(legalModel);
		legalList.addMouseListener(new java.awt.event.MouseAdapter() {
			/**
			 * Plays the selected legal move after a double click.
			 *
			 * @param e mouse event
			 */
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2) {
					String value = legalList.getSelectedValue();
					if (value != null) {
						controller.playTextMove(value.split("\\s+")[0]);
					}
				}
			}
		});
		panel.add(new JScrollPane(legalList));
		panel.add(label("Tags"));
		panel.add(new JScrollPane(tagsPanel));
		return new JScrollPane(panel);
	}

	/**
	 * Builds the game-tree and annotation tab.
	 *
	 * @return game tab component
	 */
	private Component buildGameTab() {
		JPanel panel = verticalPanel();
		nodeList = new JList<>(nodeModel);
		nodeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		nodeList.addListSelectionListener(e -> {
			if (!refreshing && !e.getValueIsAdjusting()) {
				StudioGameNode node = nodeList.getSelectedValue();
				controller.navigateToNode(node);
			}
		});
		nodeList.setCellRenderer(new NodeRenderer());
		panel.add(label("Move tree"));
		panel.add(new JScrollPane(nodeList));
		JPanel nav = row();
		nav.add(button("Back", e -> controller.previousNode()));
		nav.add(button("Forward", e -> controller.nextNode()));
		nav.add(button("Promote", e -> controller.promoteNode(nodeList.getSelectedValue())));
		nav.add(button("Delete", e -> controller.deleteNode(nodeList.getSelectedValue())));
		panel.add(nav);
		panel.add(label("Comment"));
		panel.add(new JScrollPane(commentArea));
		panel.add(label("NAGs, comma separated"));
		panel.add(nagsField);
		panel.add(button("Save annotation", e -> saveNodeAnnotation()));
		JPanel pgn = row();
		pgn.add(button("Import PGN", e -> importPgn()));
		pgn.add(button("Export PGN", e -> exportPgn()));
		pgn.add(button("Copy PGN", e -> copyText(controller.gameTree().toPgn())));
		panel.add(pgn);
		return new JScrollPane(panel);
	}

	/**
	 * Builds the task launcher tab.
	 *
	 * @return tasks tab component
	 */
	private Component buildTasksTab() {
		JPanel panel = verticalPanel();
		for (StudioTask task : taskCatalog.tasks()) {
			taskModel.addElement(task);
		}
		taskList = new JList<>(taskModel);
		taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		taskList.setCellRenderer(new TaskRenderer());
		taskList.addListSelectionListener(e -> updateCommandPreview());
		panel.add(label("Task launcher"));
		panel.add(new JScrollPane(taskList));
		commandPreview = new JTextField();
		commandPreview.setEditable(false);
		panel.add(label("Generated command"));
		panel.add(commandPreview);
		JPanel actions = row();
		actions.add(button("Run", e -> runSelectedTask()));
		actions.add(button("Cancel", e -> taskRunner.cancel()));
		actions.add(button("Output", e -> outputDrawer.setVisible(!outputDrawer.isVisible())));
		panel.add(actions);
		if (!taskModel.isEmpty()) {
			taskList.setSelectedIndex(0);
		}
		return new JScrollPane(panel);
	}

	/**
	 * Builds the project and FEN-list tab.
	 *
	 * @return project tab component
	 */
	private Component buildProjectTab() {
		JPanel panel = verticalPanel();
		panel.add(fenListLabel);
		JPanel projectActions = row();
		projectActions.add(button("Open project", e -> openProject()));
		projectActions.add(button("Open FEN list", e -> openFenList()));
		panel.add(projectActions);
		JPanel fenNav = row();
		fenNav.add(button("Previous FEN", e -> controller.navigateFen(-1)));
		fenNav.add(button("Next FEN", e -> controller.navigateFen(1)));
		fenNav.add(button("Save project", e -> controller.saveProject()));
		panel.add(fenNav);
		panel.add(label("Position note"));
		noteArea.setLineWrap(true);
		noteArea.setWrapStyleWord(true);
		panel.add(new JScrollPane(noteArea));
		panel.add(button("Save note", e -> {
			controller.setPositionNote(noteArea.getText());
			controller.savePositionNote();
			controller.saveProject();
		}));
		return new JScrollPane(panel);
	}

	/**
	 * Refreshes visible widgets from the current controller state.
	 */
	@Override
	public void stateChanged() {
		SwingUtilities.invokeLater(() -> {
			refreshing = true;
			StudioTheme theme = controller.theme();
			boardPanel.setViewModel(controller.boardModel());
			fenField.setText(controller.position().toString());
			positionLabel.setText((controller.position().isWhiteToMove() ? "White" : "Black") + " to move | "
					+ controller.position().legalMoveCount() + " legal moves");
			refreshLegalMoves();
			refreshTags();
			refreshGameList();
			refreshEngine();
			noteArea.setText(controller.positionNote());
			fenListLabel.setText(controller.fenCount() == 0 ? "No FEN list"
					: "FEN " + (controller.fenIndex() + 1) + " / " + controller.fenCount());
			evalStrip.setSnapshot(controller.engineSnapshot(), theme);
			applyTheme();
			refreshing = false;
		});
	}

	/**
	 * Displays a status message in the window footer.
	 *
	 * @param message status text
	 */
	@Override
	public void status(String message) {
		SwingUtilities.invokeLater(() -> statusLabel.setText(message == null ? "" : message));
	}

	/**
	 * Prompts the user for a promotion piece.
	 *
	 * @param from source square
	 * @param to promotion square
	 * @return promotion piece character
	 */
	@Override
	public char choosePromotion(byte from, byte to) {
		Object[] options = { "Queen", "Rook", "Bishop", "Knight" };
		int picked = JOptionPane.showOptionDialog(this, "Choose promotion piece", "Promotion",
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		return switch (picked) {
			case 1 -> 'r';
			case 2 -> 'b';
			case 3 -> 'n';
			default -> 'q';
		};
	}

	/**
	 * Rebuilds the legal move list for the current position.
	 */
	private void refreshLegalMoves() {
		legalModel.clear();
		Position position = controller.position();
		MoveList moves = position.legalMoves();
		for (int i = 0; i < moves.size(); i++) {
			short move = moves.get(i);
			legalModel.addElement(Move.toString(move) + "    " + SAN.toAlgebraic(position, move));
		}
	}

	/**
	 * Refreshes the displayed position tags.
	 */
	private void refreshTags() {
		tagsPanel.removeAll();
		for (String tag : controller.tags()) {
			JLabel chip = new JLabel(tag);
			chip.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(controller.theme().border()),
					new EmptyBorder(3, 6, 3, 6)));
			tagsPanel.add(chip);
		}
		tagsPanel.revalidate();
	}

	/**
	 * Refreshes the move-tree list and annotation fields.
	 */
	private void refreshGameList() {
		StudioGameNode selected = controller.gameTree().current();
		nodeModel.clear();
		for (StudioGameNode node : controller.gameTree().nodes()) {
			nodeModel.addElement(node);
			if (node == selected) {
				nodeList.setSelectedValue(node, true);
				commentArea.setText(node.comment());
				nagsField.setText(nagsText(node.nags()));
			}
		}
		if (selected.parent() == null) {
			commentArea.setText("");
			nagsField.setText("");
		}
	}

	/**
	 * Refreshes engine status and principal variation labels.
	 */
	private void refreshEngine() {
		StudioEngineSnapshot snapshot = controller.engineSnapshot();
		if (snapshot == null) {
			engineLabel.setText("Engine idle");
			pvLabel.setText("PV: -");
			return;
		}
		engineLabel.setText(snapshot.status() + " | eval " + snapshot.eval() + " | depth " + snapshot.depth()
				+ " | nodes " + snapshot.nodes());
		pvLabel.setText("PV: " + snapshot.pv());
	}

	/**
	 * Starts engine analysis using the values in the analysis controls.
	 */
	private void startEngine() {
		long nodes = parseLong(nodesField.getText(), 1_000_000L);
		int multipv = (int) parseLong(multipvField.getText(), 3L);
		controller.startEngine(new StudioEngineProfile("Custom", java.nio.file.Path.of(enginePathField.getText())),
				nodes, multipv, wdlBox.isSelected());
	}

	/**
	 * Plays the best move from the current engine snapshot.
	 */
	private void playBestMove() {
		StudioEngineSnapshot snapshot = controller.engineSnapshot();
		if (snapshot != null && snapshot.bestMove() != Move.NO_MOVE) {
			controller.playEncodedMove(snapshot.bestMove());
		}
	}

	/**
	 * Saves the selected node's comment and NAG annotation.
	 */
	private void saveNodeAnnotation() {
		StudioGameNode node = nodeList.getSelectedValue();
		if (node == null) {
			return;
		}
		node.setComment(commentArea.getText());
		List<Integer> nags = new java.util.ArrayList<>();
		for (String token : nagsField.getText().split(",")) {
			try {
				if (!token.isBlank()) {
					nags.add(Integer.parseInt(token.trim()));
				}
			} catch (NumberFormatException ignored) {
				// skip invalid NAGs
			}
		}
		node.setNags(nags);
		controller.saveProject();
		stateChanged();
	}

	/**
	 * Runs the currently selected task.
	 */
	private void runSelectedTask() {
		StudioTask task = taskList.getSelectedValue();
		if (task == null) {
			return;
		}
		outputDrawer.setVisible(true);
		outputArea.setText("");
		List<String> args = StudioTaskCatalog.argsFor(task, controller.position().toString(),
				StudioTaskCatalog.emptyOptions());
		taskRunner.start(args, outputArea::append,
				code -> statusLabel.setText("Task exited with code " + code));
	}

	/**
	 * Updates the generated command preview for the selected task.
	 */
	private void updateCommandPreview() {
		if (commandPreview == null || taskList == null) {
			return;
		}
		StudioTask task = taskList.getSelectedValue();
		commandPreview.setText(task == null ? "" : StudioTaskCatalog.preview(task,
				controller.position().toString(), StudioTaskCatalog.emptyOptions()));
	}

	/**
	 * Opens a studio project directory chosen by the user.
	 */
	private void openProject() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			controller.openProject(chooser.getSelectedFile().toPath());
		}
	}

	/**
	 * Opens a FEN list chosen by the user.
	 */
	private void openFenList() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			controller.loadFenList(chooser.getSelectedFile().toPath());
		}
	}

	/**
	 * Imports PGN text from a selected file.
	 */
	private void importPgn() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				controller.loadPgn(Files.readString(chooser.getSelectedFile().toPath()));
			} catch (IOException ex) {
				status("PGN read failed: " + ex.getMessage());
			}
		}
	}

	/**
	 * Exports the current game tree as PGN.
	 */
	private void exportPgn() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				Files.writeString(chooser.getSelectedFile().toPath(), controller.gameTree().toPgn(),
						StandardCharsets.UTF_8);
			} catch (IOException ex) {
				status("PGN export failed: " + ex.getMessage());
			}
		}
	}

	/**
	 * Copies the current position report to the clipboard.
	 */
	private void copyReport() {
		copyText(StudioReport.currentPosition(controller.position(), controller.tags(),
				controller.engineSnapshot(), noteArea.getText()));
	}

	/**
	 * Copies a rendered board image to the clipboard.
	 */
	private void copyBoardImage() {
		TransferableImage image = new TransferableImage(boardPanel.renderImage(900));
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(image, image);
		status("Board image copied.");
	}

	/**
	 * Saves a rendered board image to a selected file.
	 */
	private void saveBoardImage() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				ImageIO.write(boardPanel.renderImage(1200), "png", chooser.getSelectedFile());
				status("Board image saved.");
			} catch (IOException ex) {
				status("Board image save failed: " + ex.getMessage());
			}
		}
	}

	/**
	 * Copies text to the system clipboard.
	 *
	 * @param text clipboard text
	 */
	private void copyText(String text) {
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		status("Copied.");
	}

	/**
	 * Toggles focus mode and hides transient panels.
	 */
	private void toggleFocus() {
		controller.settings().setFocusMode(!controller.settings().isFocusMode());
		rightTabs.setVisible(!controller.settings().isFocusMode());
		outputDrawer.setVisible(false);
	}

	/**
	 * Applies the current controller theme to the window.
	 */
	private void applyTheme() {
		StudioTheme theme = controller.theme();
		applyTheme(this.getContentPane(), theme);
		outputArea.setBackground(theme.panelAlt());
		outputArea.setForeground(theme.text());
		statusLabel.setForeground(theme.muted());
	}

	/**
	 * Recursively applies the theme to a component subtree.
	 *
	 * @param component root component
	 * @param theme theme values
	 */
	private void applyTheme(Component component, StudioTheme theme) {
		component.setBackground(theme.background());
		component.setForeground(theme.text());
		if (component instanceof JPanel panel) {
			panel.setOpaque(true);
		}
		if (component instanceof javax.swing.text.JTextComponent text) {
			text.setBackground(theme.panelAlt());
			text.setForeground(theme.text());
			text.setCaretColor(theme.text());
			text.setBorder(BorderFactory.createLineBorder(theme.border()));
		}
		if (component instanceof JButton button) {
			button.setBackground(theme.panelAlt());
			button.setForeground(theme.text());
			button.setBorder(BorderFactory.createLineBorder(theme.accent()));
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				applyTheme(child, theme);
			}
		}
	}

	/**
	 * Creates a left-aligned action row.
	 *
	 * @return row panel
	 */
	private JPanel row() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		panel.setOpaque(false);
		return panel;
	}

	/**
	 * Creates the standard vertical content panel.
	 *
	 * @return vertical panel
	 */
	private JPanel verticalPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		return panel;
	}

	/**
	 * Creates a section label.
	 *
	 * @param text label text
	 * @return label component
	 */
	private JLabel label(String text) {
		JLabel label = new JLabel(text);
		label.setBorder(new EmptyBorder(10, 0, 4, 0));
		return label;
	}

	/**
	 * Creates an action button.
	 *
	 * @param text button text
	 * @param action button action
	 * @return button component
	 */
	private JButton button(String text, java.awt.event.ActionListener action) {
		JButton button = new JButton(text);
		button.addActionListener(action);
		return button;
	}

	/**
	 * Formats NAG values for the editor field.
	 *
	 * @param nags NAG values
	 * @return comma-separated NAG text
	 */
	private String nagsText(List<Integer> nags) {
		return String.join(",", nags.stream().map(String::valueOf).toList());
	}

	/**
	 * Parses a long with a fallback.
	 *
	 * @param text input text
	 * @param fallback fallback value
	 * @return parsed value or fallback
	 */
	private long parseLong(String text, long fallback) {
		try {
			return Long.parseLong(text.trim());
		} catch (RuntimeException ex) {
			return fallback;
		}
	}

	/**
	 * Installs shutdown handling for the window.
	 */
	private void installCloseHandler() {
		addWindowListener(new WindowAdapter() {
			/**
			 * Persists window settings and stops background services before closing.
			 *
			 * @param e window event
			 */
			@Override
			public void windowClosing(WindowEvent e) {
				controller.settings().setWidth(getWidth());
				controller.settings().setHeight(getHeight());
				controller.close();
				taskRunner.cancel();
			}
		});
	}

	/**
	 * Renderer for game-tree nodes.
	 */
	private static final class NodeRenderer extends DefaultListCellRenderer {

		/**
		 * Serialization identifier for Swing renderer compatibility.
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * Builds the display component for a game-tree node.
		 *
		 * @param list owning list
		 * @param value node value
		 * @param index row index
		 * @param selected whether the row is selected
		 * @param focus whether the row has focus
		 * @return renderer component
		 */
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean selected, boolean focus) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
			if (value instanceof StudioGameNode node) {
				label.setText((index + 1) + ". " + node.san() + (node.comment().isBlank() ? "" : "  {...}"));
			}
			return label;
		}
	}

	/**
	 * Renderer for task launcher entries.
	 */
	private static final class TaskRenderer extends DefaultListCellRenderer {

		/**
		 * Serialization identifier for Swing renderer compatibility.
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * Builds the display component for a task entry.
		 *
		 * @param list owning list
		 * @param value task value
		 * @param index row index
		 * @param selected whether the row is selected
		 * @param focus whether the row has focus
		 * @return renderer component
		 */
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean selected, boolean focus) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
			if (value instanceof StudioTask task) {
				label.setText(task.label() + "    " + task.group());
			}
			return label;
		}
	}

	/**
	 * Narrow evaluation strip rendered beside the board.
	 */
	private static final class EvalStrip extends JPanel {

		/**
		 * Serialization identifier for Swing component compatibility.
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * Snapshot currently represented by the strip.
		 */
		private StudioEngineSnapshot snapshot;

		/**
		 * Theme used for border and background colors.
		 */
		private StudioTheme theme = StudioTheme.light();

		/**
		 * Updates the snapshot and theme shown by the strip.
		 *
		 * @param snapshot engine snapshot
		 * @param theme active theme
		 */
		void setSnapshot(StudioEngineSnapshot snapshot, StudioTheme theme) {
			this.snapshot = snapshot;
			this.theme = theme;
			repaint();
		}

		/**
		 * Returns the fixed strip size used beside the board.
		 *
		 * @return preferred strip size
		 */
		@Override
		public Dimension getPreferredSize() {
			return new Dimension(22, 400);
		}

		/**
		 * Paints the current evaluation balance.
		 *
		 * @param g graphics context
		 */
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.setColor(Color.BLACK);
			g.fillRect(5, 0, 12, getHeight());
			int fill = evalFillHeight();
			g.setColor(Color.WHITE);
			g.fillRect(5, getHeight() - fill, 12, fill);
			g.setColor(theme.border());
			g.drawRect(5, 0, 12, getHeight() - 1);
		}

		/**
		 * Computes the white-side fill height from the evaluation text.
		 *
		 * @return fill height in pixels
		 */
		private int evalFillHeight() {
			if (snapshot == null || snapshot.eval() == null || snapshot.eval().equals("-")) {
				return getHeight() / 2;
			}
			try {
				int cp = Integer.parseInt(snapshot.eval().replace("+", ""));
				double balance = 0.5 + Math.tanh(cp / 240.0) * 0.44;
				return (int) Math.round(getHeight() * balance);
			} catch (NumberFormatException ex) {
				return snapshot.eval().startsWith("#-") ? 0 : getHeight();
			}
		}
	}
}
