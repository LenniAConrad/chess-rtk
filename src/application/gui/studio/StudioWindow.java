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

	private static final long serialVersionUID = 1L;

	private final StudioController controller;
	private final StudioBoardPanel boardPanel = new StudioBoardPanel();
	private final StudioTaskCatalog taskCatalog = StudioTaskCatalog.defaults();
	private final StudioTaskRunner taskRunner = new StudioTaskRunner();
	private final DefaultListModel<String> legalModel = new DefaultListModel<>();
	private final DefaultListModel<StudioGameNode> nodeModel = new DefaultListModel<>();
	private final DefaultListModel<StudioTask> taskModel = new DefaultListModel<>();
	private final JTextField fenField = new JTextField();
	private final JTextField moveField = new JTextField();
	private final JTextField enginePathField = new JTextField(StudioEngineProfile.defaultProfile().path().toString());
	private final JTextField nodesField = new JTextField("1000000");
	private final JTextField multipvField = new JTextField("3");
	private final JCheckBox wdlBox = new JCheckBox("WDL", true);
	private final JTextArea outputArea = new JTextArea(8, 40);
	private final JTextArea noteArea = new JTextArea(5, 24);
	private final JTextArea commentArea = new JTextArea(4, 24);
	private final JTextField nagsField = new JTextField();
	private final JLabel statusLabel = new JLabel("Ready");
	private final JLabel positionLabel = new JLabel();
	private final JLabel engineLabel = new JLabel("Engine idle");
	private final JLabel pvLabel = new JLabel("PV: -");
	private final JLabel fenListLabel = new JLabel("No FEN list");
	private final JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
	private final JTabbedPane rightTabs = new JTabbedPane();
	private final JPanel outputDrawer = new JPanel(new BorderLayout());
	private final EvalStrip evalStrip = new EvalStrip();
	private JList<StudioGameNode> nodeList;
	private JList<StudioTask> taskList;
	private JTextField commandPreview;
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

	private Component buildCenter() {
		JPanel boardWrap = new JPanel(new BorderLayout(8, 8));
		boardWrap.add(evalStrip, BorderLayout.WEST);
		boardWrap.add(boardPanel, BorderLayout.CENTER);
		boardPanel.setBoardListener(new StudioBoardPanel.BoardListener() {
			@Override
			public void squareSelected(byte square) {
				controller.selectSquare(square);
			}

			@Override
			public void moveRequested(byte from, byte to) {
				controller.playFromTo(from, to);
			}

			@Override
			public void markRequested(BoardMark mark) {
				controller.toggleMark(mark);
			}

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

	@Override
	public void status(String message) {
		SwingUtilities.invokeLater(() -> statusLabel.setText(message == null ? "" : message));
	}

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

	private void refreshLegalMoves() {
		legalModel.clear();
		Position position = controller.position();
		MoveList moves = position.legalMoves();
		for (int i = 0; i < moves.size(); i++) {
			short move = moves.get(i);
			legalModel.addElement(Move.toString(move) + "    " + SAN.toAlgebraic(position, move));
		}
	}

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

	private void startEngine() {
		long nodes = parseLong(nodesField.getText(), 1_000_000L);
		int multipv = (int) parseLong(multipvField.getText(), 3L);
		controller.startEngine(new StudioEngineProfile("Custom", java.nio.file.Path.of(enginePathField.getText())),
				nodes, multipv, wdlBox.isSelected());
	}

	private void playBestMove() {
		StudioEngineSnapshot snapshot = controller.engineSnapshot();
		if (snapshot != null && snapshot.bestMove() != Move.NO_MOVE) {
			controller.playEncodedMove(snapshot.bestMove());
		}
	}

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

	private void updateCommandPreview() {
		if (commandPreview == null || taskList == null) {
			return;
		}
		StudioTask task = taskList.getSelectedValue();
		commandPreview.setText(task == null ? "" : StudioTaskCatalog.preview(task,
				controller.position().toString(), StudioTaskCatalog.emptyOptions()));
	}

	private void openProject() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			controller.openProject(chooser.getSelectedFile().toPath());
		}
	}

	private void openFenList() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			controller.loadFenList(chooser.getSelectedFile().toPath());
		}
	}

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

	private void copyReport() {
		copyText(StudioReport.currentPosition(controller.position(), controller.tags(),
				controller.engineSnapshot(), noteArea.getText()));
	}

	private void copyBoardImage() {
		TransferableImage image = new TransferableImage(boardPanel.renderImage(900));
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(image, image);
		status("Board image copied.");
	}

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

	private void copyText(String text) {
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		status("Copied.");
	}

	private void toggleFocus() {
		controller.settings().setFocusMode(!controller.settings().isFocusMode());
		rightTabs.setVisible(!controller.settings().isFocusMode());
		outputDrawer.setVisible(false);
	}

	private void applyTheme() {
		StudioTheme theme = controller.theme();
		applyTheme(this.getContentPane(), theme);
		outputArea.setBackground(theme.panelAlt());
		outputArea.setForeground(theme.text());
		statusLabel.setForeground(theme.muted());
	}

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

	private JPanel row() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		panel.setOpaque(false);
		return panel;
	}

	private JPanel verticalPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		return panel;
	}

	private JLabel label(String text) {
		JLabel label = new JLabel(text);
		label.setBorder(new EmptyBorder(10, 0, 4, 0));
		return label;
	}

	private JButton button(String text, java.awt.event.ActionListener action) {
		JButton button = new JButton(text);
		button.addActionListener(action);
		return button;
	}

	private String nagsText(List<Integer> nags) {
		return String.join(",", nags.stream().map(String::valueOf).toList());
	}

	private long parseLong(String text, long fallback) {
		try {
			return Long.parseLong(text.trim());
		} catch (RuntimeException ex) {
			return fallback;
		}
	}

	private void installCloseHandler() {
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				controller.settings().setWidth(getWidth());
				controller.settings().setHeight(getHeight());
				controller.close();
				taskRunner.cancel();
			}
		});
	}

	private static final class NodeRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

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

	private static final class TaskRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

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

	private static final class EvalStrip extends JPanel {
		private static final long serialVersionUID = 1L;
		private StudioEngineSnapshot snapshot;
		private StudioTheme theme = StudioTheme.light();

		void setSnapshot(StudioEngineSnapshot snapshot, StudioTheme theme) {
			this.snapshot = snapshot;
			this.theme = theme;
			repaint();
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(22, 400);
		}

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
