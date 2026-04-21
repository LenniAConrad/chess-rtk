package application.gui.web;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import application.cli.EvalOps;
import application.gui.ui.RoundedPanel;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.eval.Result;
import chess.images.assets.Pictures;
import chess.tag.Tagging;

/**
 * Chess-web-inspired Swing desktop GUI.
 *
 * <p>
 * This keeps the existing GUI intact and offers a second, simpler layout that
 * focuses on board play, move history, legal-move browsing, and tags.
 * </p>
 *
 * @since 2026
 */
public final class WebGuiWindow extends JFrame {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Default startup FEN.
	 */
	public static final String DEFAULT_FEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * Maximum number of tags shown in the panel.
	 */
	private static final int MAX_VISIBLE_TAGS = 48;

	/**
	 * Timeline state.
	 */
	private final List<Position> positions = new ArrayList<>();
		/**
	 * Stores the moves.
	 */
private final List<Short> moves = new ArrayList<>();
		/**
	 * Stores the san moves.
	 */
private final List<String> sanMoves = new ArrayList<>();

	/**
	 * View state.
	 */
	private boolean whiteDown;
		/**
	 * Stores the light mode.
	 */
private boolean lightMode;
		/**
	 * Stores the glass enabled.
	 */
private boolean glassEnabled = true;
		/**
	 * Stores the show coordinates.
	 */
private boolean showCoordinates = true;
		/**
	 * Stores the current ply.
	 */
private int currentPly;
		/**
	 * Stores the selected square.
	 */
private byte selectedSquare = Field.NO_SQUARE;
		/**
	 * Stores the hover square.
	 */
private byte hoverSquare = Field.NO_SQUARE;
		/**
	 * Stores the legal targets.
	 */
private final boolean[] legalTargets = new boolean[64];
		/**
	 * Stores the capture targets.
	 */
private final boolean[] captureTargets = new boolean[64];
		/**
	 * Stores the tag request id.
	 */
private long tagRequestId;

	/**
	 * Theme and widgets.
	 */
	private WebGuiTheme theme;
		/**
	 * Stores the root panel.
	 */
private BackgroundPanel rootPanel;
		/**
	 * Stores the side surface.
	 */
private RoundedPanel sideSurface;
		/**
	 * Stores the brand label.
	 */
private JLabel brandLabel;
		/**
	 * Stores the brand meta label.
	 */
private JLabel brandMetaLabel;
		/**
	 * Stores the title label.
	 */
private JLabel titleLabel;
		/**
	 * Stores the subtitle label.
	 */
private JLabel subtitleLabel;
		/**
	 * Stores the eval label.
	 */
private JLabel evalLabel;
		/**
	 * Stores the status label.
	 */
private JLabel statusLabel;
		/**
	 * Stores the hover label.
	 */
private JLabel hoverLabel;
		/**
	 * Stores the fen field.
	 */
private JTextField fenField;
		/**
	 * Stores the load fen button.
	 */
private JButton loadFenButton;
		/**
	 * Stores the copy fen button.
	 */
private JButton copyFenButton;
		/**
	 * Stores the reset button.
	 */
private JButton resetButton;
		/**
	 * Stores the undo button.
	 */
private JButton undoButton;
		/**
	 * Stores the flip button.
	 */
private JButton flipButton;
		/**
	 * Stores the theme button.
	 */
private JButton themeButton;
		/**
	 * Stores the glass toggle.
	 */
private JToggleButton glassToggle;
		/**
	 * Stores the coord toggle.
	 */
private JToggleButton coordToggle;
		/**
	 * Stores the board panel.
	 */
private WebBoardPanel boardPanel;
		/**
	 * Stores the eval bar panel.
	 */
private EvalBarPanel evalBarPanel;
		/**
	 * Stores the history model.
	 */
private final DefaultListModel<String> historyModel = new DefaultListModel<>();
		/**
	 * Stores the move model.
	 */
private final DefaultListModel<MoveEntry> moveModel = new DefaultListModel<>();
		/**
	 * Stores the tag model.
	 */
private final DefaultListModel<String> tagModel = new DefaultListModel<>();
		/**
	 * Stores the history list.
	 */
private JList<String> historyList;
		/**
	 * Stores the move list.
	 */
private JList<MoveEntry> moveList;
		/**
	 * Stores the tag list.
	 */
private JList<String> tagList;
		/**
	 * Stores the syncing history selection.
	 */
private boolean syncingHistorySelection;

	/**
	 * Creates the alternate GUI window.
	 *
	 * @param fen initial position
	 * @param whiteDown whether White starts at the bottom
	 * @param lightMode whether to use the light palette
	 */
	public WebGuiWindow(String fen, boolean whiteDown, boolean lightMode) {
		super("ChessRTK Desktop");
		this.whiteDown = whiteDown;
		this.lightMode = lightMode;
		this.theme = lightMode ? WebGuiTheme.light() : WebGuiTheme.dark();
		resetTimeline(parseFenOrDefault(fen));
		buildUi();
		applyTheme();
		refreshUi();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(1180, 780));
		setSize(1540, 920);
		setLocationRelativeTo(null);
		setIconImages(List.of(Pictures.IconKnight, Pictures.IconKing, Pictures.IconQueen));
		setVisible(true);
	}

		/**
	 * Handles build ui.
	 */
private void buildUi() {
		rootPanel = new BackgroundPanel();
		rootPanel.setLayout(new BorderLayout(0, 24));
		rootPanel.setBorder(new EmptyBorder(18, 22, 22, 22));
		setContentPane(rootPanel);
		rootPanel.add(buildHeader(), BorderLayout.NORTH);
		rootPanel.add(buildMainRow(), BorderLayout.CENTER);
		registerShortcuts();
	}

		/**
	 * Handles build header.
	 * @return computed value
	 */
private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);

		JPanel brand = new JPanel();
		brand.setOpaque(false);
		brand.setLayout(new BorderLayout());
		brandLabel = new JLabel("chess-rtk");
		brandLabel.setBorder(new EmptyBorder(0, 0, 2, 0));
		brandMetaLabel = new JLabel("desktop board");
		brand.add(brandLabel, BorderLayout.NORTH);
		brand.add(brandMetaLabel, BorderLayout.CENTER);

		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
		actions.setOpaque(false);
		flipButton = buildButton("Flip board", event -> {
			whiteDown = !whiteDown;
			refreshUi();
		});
		themeButton = buildButton("", event -> {
			lightMode = !lightMode;
			theme = lightMode ? WebGuiTheme.light() : WebGuiTheme.dark();
			applyTheme();
			refreshUi();
		});
		actions.add(flipButton);
		actions.add(themeButton);

		header.add(brand, BorderLayout.WEST);
		header.add(actions, BorderLayout.EAST);
		return header;
	}

		/**
	 * Handles build main row.
	 * @return computed value
	 */
private JPanel buildMainRow() {
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.insets = new Insets(0, 0, 0, 18);
		gbc.fill = GridBagConstraints.VERTICAL;

		evalBarPanel = new EvalBarPanel();
		gbc.gridx = 0;
		gbc.weightx = 0;
		row.add(evalBarPanel, gbc);

		boardPanel = new WebBoardPanel(this::handleBoardSquare, square -> {
			hoverSquare = square;
			refreshHoverLabel();
		});
		boardPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.fill = GridBagConstraints.BOTH;
		row.add(boardPanel, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0;
		gbc.insets = new Insets(0, 18, 0, 0);
		gbc.fill = GridBagConstraints.BOTH;
		sideSurface = new RoundedPanel(16);
		sideSurface.setLayout(new BorderLayout());
		sideSurface.setPreferredSize(new Dimension(470, 740));
		sideSurface.add(buildSidePanel(), BorderLayout.CENTER);
		row.add(sideSurface, gbc);
		return row;
	}

		/**
	 * Handles build side panel.
	 * @return computed value
	 */
private JPanel buildSidePanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(18, 18, 18, 18));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 12, 0);

		titleLabel = new JLabel("Current position");
		subtitleLabel = new JLabel();
		evalLabel = new JLabel();
		statusLabel = new JLabel();
		hoverLabel = new JLabel();
		panel.add(buildStatusBlock(), gbc);

		gbc.gridy = 1;
		panel.add(buildFenBlock(), gbc);

		gbc.gridy = 2;
		panel.add(buildControlsBlock(), gbc);

		gbc.gridy = 3;
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weighty = 1;
		panel.add(buildListsBlock(), gbc);

		return panel;
	}

		/**
	 * Handles build status block.
	 * @return computed value
	 */
private JPanel buildStatusBlock() {
		JPanel block = new JPanel(new GridBagLayout());
		block.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 4, 0);

		block.add(titleLabel, gbc);
		gbc.gridy = 1;
		block.add(subtitleLabel, gbc);
		gbc.gridy = 2;
		block.add(evalLabel, gbc);
		gbc.gridy = 3;
		block.add(statusLabel, gbc);
		gbc.gridy = 4;
		gbc.insets = new Insets(8, 0, 0, 0);
		block.add(hoverLabel, gbc);
		return block;
	}

		/**
	 * Handles build fen block.
	 * @return computed value
	 */
private JPanel buildFenBlock() {
		JPanel block = new JPanel(new GridBagLayout());
		block.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 6, 0);

		JLabel label = new JLabel("FEN");
		block.add(label, gbc);

		gbc.gridy = 1;
		fenField = new JTextField();
		fenField.addActionListener(event -> loadFenFromField());
		fenField.addKeyListener(new KeyAdapter() {
						/**
			 * Handles key pressed.
			 * @param event event value
			 */
@Override
			public void keyPressed(KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
					fenField.setText(currentPosition().toString());
					fenField.selectAll();
				}
			}
		});
		block.add(fenField, gbc);

		gbc.gridy = 2;
		gbc.insets = new Insets(8, 0, 0, 0);
		JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
		buttons.setOpaque(false);
		loadFenButton = buildButton("Load FEN", event -> loadFenFromField());
		copyFenButton = buildButton("Copy FEN", event -> copyCurrentFen());
		buttons.add(loadFenButton);
		buttons.add(copyFenButton);
		block.add(buttons, gbc);
		return block;
	}

		/**
	 * Handles build controls block.
	 * @return computed value
	 */
private JPanel buildControlsBlock() {
		JPanel block = new JPanel(new GridBagLayout());
		block.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 8, 0);

		JPanel actions = new JPanel(new java.awt.GridLayout(2, 2, 10, 10));
		actions.setOpaque(false);
		resetButton = buildButton("Start position", event -> resetTimeline(parseFenOrDefault(DEFAULT_FEN)));
		undoButton = buildButton("Undo", event -> navigateToPly(Math.max(0, currentPly - 1)));
		JButton startFromCurrent = buildButton("Snapshot root", event -> resetTimeline(currentPosition().copyOf()));
		JButton copyMoveList = buildButton("Copy line", event -> copyCurrentLine());
		actions.add(resetButton);
		actions.add(undoButton);
		actions.add(startFromCurrent);
		actions.add(copyMoveList);
		block.add(actions, gbc);

		gbc.gridy = 1;
		gbc.insets = new Insets(2, 0, 0, 0);
		JPanel toggles = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
		toggles.setOpaque(false);
		glassToggle = buildToggle("Board glass", glassEnabled, event -> {
			glassEnabled = glassToggle.isSelected();
			styleToggle(glassToggle);
			refreshBoard();
		});
		coordToggle = buildToggle("Coordinates", showCoordinates, event -> {
			showCoordinates = coordToggle.isSelected();
			styleToggle(coordToggle);
			refreshBoard();
		});
		toggles.add(glassToggle);
		toggles.add(coordToggle);
		block.add(toggles, gbc);
		return block;
	}

		/**
	 * Handles build lists block.
	 * @return computed value
	 */
private JComponent buildListsBlock() {
		JPanel block = new JPanel(new GridBagLayout());
		block.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(0, 0, 8, 0);

		historyList = new JList<>(historyModel);
		historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		historyList.addListSelectionListener(event -> {
			if (event.getValueIsAdjusting() || syncingHistorySelection) {
				return;
			}
			int index = historyList.getSelectedIndex();
			if (index >= 0) {
				navigateToPly(index + 1);
			}
		});
		historyList.setCellRenderer(new StringListRenderer(false));
		moveList = new JList<>(moveModel);
		moveList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		moveList.addMouseListener(new java.awt.event.MouseAdapter() {
						/**
			 * Handles mouse clicked.
			 * @param event event value
			 */
@Override
			public void mouseClicked(java.awt.event.MouseEvent event) {
				if (event.getClickCount() == 2) {
					playSelectedMove();
				}
			}
		});
		moveList.addKeyListener(new KeyAdapter() {
						/**
			 * Handles key pressed.
			 * @param event event value
			 */
@Override
			public void keyPressed(KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_ENTER) {
					playSelectedMove();
				}
			}
		});
		moveList.setCellRenderer(new MoveListRenderer());
		tagList = new JList<>(tagModel);
		tagList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tagList.setCellRenderer(new StringListRenderer(true));

		gbc.gridy = 0;
		gbc.weighty = 0.44;
		block.add(buildListSection("Move history", wrapList(historyList, 178)), gbc);
		gbc.gridy = 1;
		gbc.weighty = 0.26;
		block.add(buildListSection("Legal moves", wrapList(moveList, 148)), gbc);
		gbc.gridy = 2;
		gbc.weighty = 0.30;
		gbc.insets = new Insets(0, 0, 0, 0);
		block.add(buildListSection("Tags", wrapList(tagList, 180)), gbc);
		return block;
	}

		/**
	 * Handles build list section.
	 * @param title title value
	 * @param scrollPane scroll pane value
	 * @return computed value
	 */
private JPanel buildListSection(String title, JScrollPane scrollPane) {
		JPanel block = new JPanel(new BorderLayout(0, 8));
		block.setOpaque(false);
		JLabel label = new JLabel(title);
		block.add(label, BorderLayout.NORTH);
		block.add(scrollPane, BorderLayout.CENTER);
		return block;
	}

		/**
	 * Handles wrap list.
	 * @param list list value
	 * @param preferredHeight preferred height value
	 * @return computed value
	 */
private JScrollPane wrapList(JList<?> list, int preferredHeight) {
		JScrollPane scrollPane = new JScrollPane(list);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getViewport().setOpaque(true);
		scrollPane.setPreferredSize(new Dimension(320, preferredHeight));
		list.setFixedCellHeight(24);
		return scrollPane;
	}

		/**
	 * Handles build button.
	 * @param text text value
	 * @param action action value
	 * @return computed value
	 */
private JButton buildButton(String text, java.awt.event.ActionListener action) {
		JButton button = new JButton(text);
		button.addActionListener(action);
		button.setFocusPainted(false);
		button.setOpaque(true);
		button.setBorderPainted(true);
		button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		return button;
	}

		/**
	 * Handles build toggle.
	 * @param text text value
	 * @param selected selected value
	 * @param action action value
	 * @return computed value
	 */
private JToggleButton buildToggle(String text, boolean selected, java.awt.event.ActionListener action) {
		JToggleButton button = new JToggleButton(text, selected);
		button.addActionListener(action);
		button.setFocusPainted(false);
		button.setOpaque(true);
		button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		return button;
	}

		/**
	 * Handles register shortcuts.
	 */
private void registerShortcuts() {
		rootPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "undo-ply");
		rootPanel.getActionMap().put("undo-ply", new javax.swing.AbstractAction() {
						/**
			 * Handles action performed.
			 * @param event event value
			 */
@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				navigateToPly(Math.max(0, currentPly - 1));
			}
		});
		rootPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "root-pos");
		rootPanel.getActionMap().put("root-pos", new javax.swing.AbstractAction() {
						/**
			 * Handles action performed.
			 * @param event event value
			 */
@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				navigateToPly(0);
			}
		});
		rootPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "flip-board");
		rootPanel.getActionMap().put("flip-board", new javax.swing.AbstractAction() {
						/**
			 * Handles action performed.
			 * @param event event value
			 */
@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				whiteDown = !whiteDown;
				refreshUi();
			}
		});
		rootPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_T, 0), "toggle-theme");
		rootPanel.getActionMap().put("toggle-theme", new javax.swing.AbstractAction() {
						/**
			 * Handles action performed.
			 * @param event event value
			 */
@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				lightMode = !lightMode;
				theme = lightMode ? WebGuiTheme.light() : WebGuiTheme.dark();
				applyTheme();
				refreshUi();
			}
		});
	}

		/**
	 * Handles load fen from field.
	 */
private void loadFenFromField() {
		String fen = fenField.getText().trim();
		try {
			resetTimeline(new Position(fen));
		} catch (IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid FEN", JOptionPane.ERROR_MESSAGE);
			fenField.requestFocusInWindow();
			fenField.selectAll();
		}
	}

		/**
	 * Handles copy current fen.
	 */
private void copyCurrentFen() {
		copyToClipboard(currentPosition().toString());
	}

		/**
	 * Handles copy current line.
	 */
private void copyCurrentLine() {
		if (sanMoves.isEmpty()) {
			copyToClipboard("(start position)");
			return;
		}
		copyToClipboard(String.join(" ", sanMoves.subList(0, currentPly)));
	}

		/**
	 * Handles copy to clipboard.
	 * @param text text value
	 */
private void copyToClipboard(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

		/**
	 * Handles parse fen or default.
	 * @param fen fen value
	 * @return computed value
	 */
private Position parseFenOrDefault(String fen) {
		try {
			return new Position(fen == null || fen.isBlank() ? DEFAULT_FEN : fen);
		} catch (IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(this,
					"Failed to load the requested start position.\n\n" + ex.getMessage()
							+ "\n\nFalling back to the standard start FEN.",
					"GUI startup", JOptionPane.WARNING_MESSAGE);
			return new Position(DEFAULT_FEN);
		}
	}

		/**
	 * Handles reset timeline.
	 * @param root root value
	 */
private void resetTimeline(Position root) {
		positions.clear();
		moves.clear();
		sanMoves.clear();
		positions.add(root.copyOf());
		currentPly = 0;
		selectedSquare = Field.NO_SQUARE;
		refreshUi();
	}

		/**
	 * Handles navigate to ply.
	 * @param targetPly target ply value
	 */
private void navigateToPly(int targetPly) {
		currentPly = Math.max(0, Math.min(targetPly, moves.size()));
		selectedSquare = Field.NO_SQUARE;
		refreshUi();
	}

		/**
	 * Handles handle board square.
	 * @param square square value
	 */
private void handleBoardSquare(byte square) {
		Position position = currentPosition();
		byte[] board = position.getBoard();
		List<Short> options = matchingMoves(square);
		boolean ownPiece = square != Field.NO_SQUARE && Piece.isPiece(board[square])
				&& (position.isWhiteTurn() ? Piece.isWhite(board[square]) : Piece.isBlack(board[square]));

		if (selectedSquare != Field.NO_SQUARE) {
			if (square == selectedSquare) {
				selectedSquare = Field.NO_SQUARE;
				refreshBoard();
				return;
			}
			List<Short> matches = matchingMoves(selectedSquare, square);
			if (!matches.isEmpty()) {
				playMove(resolveMove(matches));
				return;
			}
			if (ownPiece) {
				selectedSquare = square;
			} else {
				selectedSquare = Field.NO_SQUARE;
			}
			refreshBoard();
			return;
		}

		if (ownPiece && !options.isEmpty()) {
			selectedSquare = square;
			refreshBoard();
		}
	}

		/**
	 * Handles play selected move.
	 */
private void playSelectedMove() {
		MoveEntry entry = moveList.getSelectedValue();
		if (entry != null) {
			playMove(entry.move());
		}
	}

		/**
	 * Handles play move.
	 * @param move move value
	 */
private void playMove(short move) {
		Position before = currentPosition().copyOf();
		if (!before.isLegalMove(move)) {
			return;
		}
		truncateFuture();
		String san = SAN.toAlgebraic(before, move);
		Position after = before.copyOf().play(move);
		moves.add(move);
		sanMoves.add(san);
		positions.add(after);
		currentPly = moves.size();
		selectedSquare = Field.NO_SQUARE;
		refreshUi();
	}

		/**
	 * Handles truncate future.
	 */
private void truncateFuture() {
		while (moves.size() > currentPly) {
			moves.remove(moves.size() - 1);
			sanMoves.remove(sanMoves.size() - 1);
		}
		while (positions.size() > currentPly + 1) {
			positions.remove(positions.size() - 1);
		}
	}

		/**
	 * Handles resolve move.
	 * @param matches matches value
	 * @return computed value
	 */
private short resolveMove(List<Short> matches) {
		if (matches.size() == 1) {
			return matches.get(0);
		}
		String[] options = { "Queen", "Knight", "Rook", "Bishop" };
		int picked = JOptionPane.showOptionDialog(this, "Choose promotion piece", "Promotion",
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		char promotion = switch (picked) {
			case 1 -> 'n';
			case 2 -> 'r';
			case 3 -> 'b';
			case 0 -> 'q';
			default -> '\u0000';
		};
		for (short move : matches) {
			String uci = Move.toString(move);
			if (uci.length() == 5 && uci.charAt(4) == promotion) {
				return move;
			}
		}
		return Move.NO_MOVE;
	}

		/**
	 * Handles matching moves.
	 * @param from from value
	 * @return computed value
	 */
private List<Short> matchingMoves(byte from) {
		List<Short> result = new ArrayList<>();
		MoveList legal = currentPosition().getMoves();
		for (int i = 0; i < legal.size(); i++) {
			short move = legal.get(i);
			if (Move.getFromIndex(move) == from) {
				result.add(move);
			}
		}
		return result;
	}

		/**
	 * Handles matching moves.
	 * @param from from value
	 * @param to to value
	 * @return computed value
	 */
private List<Short> matchingMoves(byte from, byte to) {
		List<Short> result = new ArrayList<>();
		MoveList legal = currentPosition().getMoves();
		for (int i = 0; i < legal.size(); i++) {
			short move = legal.get(i);
			if (Move.getFromIndex(move) == from && Move.getToIndex(move) == to) {
				result.add(move);
			}
		}
		return result;
	}

		/**
	 * Handles current position.
	 * @return computed value
	 */
private Position currentPosition() {
		return positions.get(currentPly);
	}

		/**
	 * Handles current last move.
	 * @return computed value
	 */
private short currentLastMove() {
		return currentPly == 0 ? Move.NO_MOVE : moves.get(currentPly - 1);
	}

		/**
	 * Handles refresh ui.
	 */
private void refreshUi() {
		if (rootPanel == null) {
			return;
		}
		refreshStatus();
		refreshHistoryModel();
		refreshMoveModel();
		refreshBoard();
		refreshTagsAsync();
	}

		/**
	 * Handles refresh status.
	 */
private void refreshStatus() {
		Position position = currentPosition();
		Result eval = EvalOps.evaluateClassical(position, true);
		Integer cp = eval.centipawns();
		int whiteCp = cp == null ? 0 : (position.isWhiteTurn() ? cp : -cp);

		titleLabel.setText(position.isMate() ? "Checkmate" : "Current position");
		subtitleLabel.setText(position.isWhiteTurn() ? "White to move" : "Black to move");
		evalLabel.setText("Static eval " + formatEvalCp(whiteCp) + " | " + describePosition(eval, position));
		statusLabel.setText(buildStatusText(position));
		fenField.setText(position.toString());
		undoButton.setEnabled(currentPly > 0);
		flipButton.setText(whiteDown ? "Black bottom" : "White bottom");
		themeButton.setText(lightMode ? "Dark mode" : "Light mode");
		evalBarPanel.setTheme(theme);
		evalBarPanel.setWhiteAdvantageCp(whiteCp);
		refreshHoverLabel();
	}

		/**
	 * Handles refresh hover label.
	 */
private void refreshHoverLabel() {
		if (hoverLabel == null) {
			return;
		}
		if (hoverSquare == Field.NO_SQUARE) {
			hoverLabel.setText("Shortcuts: Left arrow undo, Home start, F flip, T theme");
			return;
		}
		byte piece = currentPosition().getBoard()[hoverSquare];
		String square = Field.toString(hoverSquare);
		if (Piece.isPiece(piece)) {
			hoverLabel.setText(square + " | " + pieceLabel(piece));
		} else {
			hoverLabel.setText(square);
		}
	}

		/**
	 * Handles build status text.
	 * @param position position value
	 * @return computed value
	 */
private String buildStatusText(Position position) {
		MoveList legal = position.getMoves();
		if (position.isMate()) {
			return position.isWhiteTurn() ? "Black delivered mate" : "White delivered mate";
		}
		if (legal.isEmpty()) {
			return "Stalemate";
		}
		if (position.inCheck()) {
			return "Check | " + legal.size() + " legal moves";
		}
		return legal.size() + " legal moves | halfmove " + position.getHalfMove()
				+ " | move " + position.getFullMove();
	}

		/**
	 * Handles describe position.
	 * @param eval eval value
	 * @param position position value
	 * @return computed value
	 */
private String describePosition(Result eval, Position position) {
		if (position.isMate()) {
			return "terminal";
		}
		int whiteCp = position.isWhiteTurn() ? eval.centipawns() : -eval.centipawns();
		if (whiteCp > 120) {
			return "White better";
		}
		if (whiteCp < -120) {
			return "Black better";
		}
		return "Balanced";
	}

		/**
	 * Handles format eval cp.
	 * @param whiteCp white cp value
	 * @return computed value
	 */
private String formatEvalCp(int whiteCp) {
		return String.format(Locale.ROOT, "%+.2f", whiteCp / 100.0);
	}

		/**
	 * Handles refresh history model.
	 */
private void refreshHistoryModel() {
		historyModel.clear();
		for (int i = 0; i < sanMoves.size(); i++) {
			String prefix = (i % 2 == 0) ? ((i / 2) + 1) + ". " : ((i / 2) + 1) + "... ";
			historyModel.addElement(prefix + sanMoves.get(i));
		}
		syncingHistorySelection = true;
		try {
			if (currentPly == 0) {
				historyList.clearSelection();
			} else {
				historyList.setSelectedIndex(currentPly - 1);
				historyList.ensureIndexIsVisible(currentPly - 1);
			}
		} finally {
			syncingHistorySelection = false;
		}
	}

		/**
	 * Handles refresh move model.
	 */
private void refreshMoveModel() {
		moveModel.clear();
		Arrays.fill(legalTargets, false);
		Arrays.fill(captureTargets, false);
		Position position = currentPosition();
		MoveList legal = position.getMoves();
		for (int i = 0; i < legal.size(); i++) {
			short move = legal.get(i);
			String san = SAN.toAlgebraic(position, move);
			moveModel.addElement(new MoveEntry(move, san, Move.toString(move)));
		}
		updateSelectionTargets();
	}

		/**
	 * Handles update selection targets.
	 */
private void updateSelectionTargets() {
		Arrays.fill(legalTargets, false);
		Arrays.fill(captureTargets, false);
		if (selectedSquare == Field.NO_SQUARE) {
			return;
		}
		Position position = currentPosition();
		byte[] board = position.getBoard();
		MoveList legal = position.getMoves();
		for (int i = 0; i < legal.size(); i++) {
			short move = legal.get(i);
			if (Move.getFromIndex(move) != selectedSquare) {
				continue;
			}
			byte to = Move.getToIndex(move);
			legalTargets[to] = true;
			captureTargets[to] = Piece.isPiece(board[to])
					|| (Piece.isPawn(board[selectedSquare]) && to == position.getEnPassant());
		}
	}

		/**
	 * Handles refresh board.
	 */
private void refreshBoard() {
		updateSelectionTargets();
		boardPanel.updateBoard(currentPosition(), currentLastMove(), selectedSquare, legalTargets, captureTargets,
				whiteDown, showCoordinates, glassEnabled, theme);
		boardPanel.repaint();
	}

		/**
	 * Handles refresh tags async.
	 */
private void refreshTagsAsync() {
		final long request = ++tagRequestId;
		final Position snapshot = currentPosition().copyOf();
		tagModel.clear();
		tagModel.addElement("Loading tags...");
		new SwingWorker<List<String>, Void>() {
						/**
			 * Handles do in background.
			 * @return computed value
			 */
@Override
			protected List<String> doInBackground() {
				List<String> tags = Tagging.tags(snapshot);
				if (tags.size() <= MAX_VISIBLE_TAGS) {
					return tags;
				}
				return new ArrayList<>(tags.subList(0, MAX_VISIBLE_TAGS));
			}

						/**
			 * Handles done.
			 */
@Override
			protected void done() {
				if (request != tagRequestId) {
					return;
				}
				tagModel.clear();
				try {
					for (String tag : get()) {
						tagModel.addElement(tag);
					}
					if (tagModel.isEmpty()) {
						tagModel.addElement("(no tags)");
					}
				} catch (Exception ex) {
					tagModel.addElement("Tagging unavailable: " + ex.getMessage());
				}
			}
		}.execute();
	}

		/**
	 * Handles apply theme.
	 */
private void applyTheme() {
		rootPanel.setTheme(theme);
		sideSurface.setTheme(theme.surface(), theme.surfaceBorder());
		sideSurface.setShadow(theme.surfaceShadow(), 10);
		brandLabel.setForeground(theme.headerText());
		brandLabel.setFont(theme.titleFont().deriveFont(28f));
		brandMetaLabel.setForeground(theme.headerMuted());
		brandMetaLabel.setFont(theme.bodyFont().deriveFont(12f));

		titleLabel.setFont(theme.titleFont());
		subtitleLabel.setFont(theme.bodyFont().deriveFont(Font.BOLD, 15f));
		evalLabel.setFont(theme.bodyFont());
		statusLabel.setFont(theme.smallFont());
		hoverLabel.setFont(theme.smallFont());
		titleLabel.setForeground(theme.textStrong());
		subtitleLabel.setForeground(theme.textStrong());
		evalLabel.setForeground(theme.textMuted());
		statusLabel.setForeground(theme.text());
		hoverLabel.setForeground(theme.textMuted());

		fenField.setFont(theme.monoFont());
		fenField.setForeground(theme.inputText());
		fenField.setBackground(theme.inputBackground());
		fenField.setCaretColor(theme.inputText());
		fenField.setBorder(new CompoundBorder(
				new LineBorder(theme.inputBorder(), 1, true),
				new EmptyBorder(8, 10, 8, 10)));

		styleButton(flipButton);
		styleButton(themeButton);
		styleButton(loadFenButton);
		styleButton(copyFenButton);
		styleButton(resetButton);
		styleButton(undoButton);
		styleToggle(glassToggle);
		styleToggle(coordToggle);

		styleList(historyList);
		styleList(moveList);
		styleList(tagList);
		styleScroll((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, historyList));
		styleScroll((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, moveList));
		styleScroll((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tagList));
		boardPanel.repaint();
		rootPanel.repaint();
	}

		/**
	 * Handles style button.
	 * @param button button value
	 */
private void styleButton(JButton button) {
		button.setBackground(theme.buttonBackground());
		button.setForeground(theme.buttonText());
		button.setFont(theme.bodyFont());
		button.setBorder(new CompoundBorder(
				new LineBorder(theme.buttonBorder(), 1, true),
				new EmptyBorder(9, 12, 9, 12)));
		button.setContentAreaFilled(true);
		button.setOpaque(true);
	}

		/**
	 * Handles style toggle.
	 * @param button button value
	 */
private void styleToggle(JToggleButton button) {
		Color background = button.isSelected() ? theme.toggleOnBackground() : theme.toggleOffBackground();
		Color border = button.isSelected() ? theme.toggleOnBorder() : theme.toggleOffBorder();
		button.setBackground(background);
		button.setForeground(theme.textStrong());
		button.setFont(theme.bodyFont());
		button.setBorder(new CompoundBorder(new LineBorder(border, 1, true), new EmptyBorder(9, 12, 9, 12)));
		button.setContentAreaFilled(true);
		button.setOpaque(true);
	}

		/**
	 * Handles style list.
	 * @param list list value
	 */
private void styleList(JList<?> list) {
		list.setBackground(theme.surfaceAlt());
		list.setForeground(theme.text());
		list.setSelectionBackground(theme.accentSoft());
		list.setSelectionForeground(theme.textStrong());
		list.setBorder(new EmptyBorder(6, 6, 6, 6));
	}

		/**
	 * Handles style scroll.
	 * @param scrollPane scroll pane value
	 */
private void styleScroll(JScrollPane scrollPane) {
		if (scrollPane == null) {
			return;
		}
		scrollPane.setBackground(theme.surfaceAlt());
		scrollPane.getViewport().setBackground(theme.surfaceAlt());
		scrollPane.setBorder(new CompoundBorder(
				new LineBorder(theme.surfaceBorder(), 1, true),
				new EmptyBorder(0, 0, 0, 0)));
	}

		/**
	 * Handles piece label.
	 * @param piece piece value
	 * @return computed value
	 */
private String pieceLabel(byte piece) {
		String color = Piece.isWhite(piece) ? "White " : "Black ";
		String name = switch (Math.abs(piece)) {
			case Piece.PAWN -> "pawn";
			case Piece.KNIGHT -> "knight";
			case Piece.BISHOP -> "bishop";
			case Piece.ROOK -> "rook";
			case Piece.QUEEN -> "queen";
			case Piece.KING -> "king";
			default -> "piece";
		};
		return color + name;
	}

	/**
	 * Background gradient for the page.
	 */
	private static final class BackgroundPanel extends JPanel {

				/**
		 * Serialization version identifier.
		 */
@java.io.Serial
		private static final long serialVersionUID = 1L;
				/**
		 * Stores the theme.
		 */
private WebGuiTheme theme;

				/**
		 * Sets the theme.
		 * @param theme theme value
		 */
void setTheme(WebGuiTheme theme) {
			this.theme = theme;
		}

				/**
		 * Handles paint component.
		 * @param graphics graphics value
		 */
@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			if (theme == null) {
				return;
			}
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setPaint(new GradientPaint(0, 0, theme.pageTop(), 0, getHeight(), theme.pageBottom()));
			g.fillRect(0, 0, getWidth(), getHeight());
			g.dispose();
		}
	}

	/**
	 * Vertical evaluation rail.
	 */
	private static final class EvalBarPanel extends JPanel {

				/**
		 * Serialization version identifier.
		 */
@java.io.Serial
		private static final long serialVersionUID = 1L;
				/**
		 * Stores the theme.
		 */
private WebGuiTheme theme = WebGuiTheme.light();
				/**
		 * Stores the white advantage cp.
		 */
private int whiteAdvantageCp;

				/**
		 * Creates a new instance.
		 */
EvalBarPanel() {
			setOpaque(false);
			setPreferredSize(new Dimension(30, 640));
			setMinimumSize(new Dimension(30, 220));
		}

				/**
		 * Sets the theme.
		 * @param theme theme value
		 */
void setTheme(WebGuiTheme theme) {
			this.theme = theme;
			repaint();
		}

				/**
		 * Sets the white advantage cp.
		 * @param whiteAdvantageCp white advantage cp value
		 */
void setWhiteAdvantageCp(int whiteAdvantageCp) {
			this.whiteAdvantageCp = whiteAdvantageCp;
			repaint();
		}

				/**
		 * Handles paint component.
		 * @param graphics graphics value
		 */
@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int x = 6;
			int y = 12;
			int width = Math.max(10, getWidth() - 12);
			int height = Math.max(24, getHeight() - 24);
			double balance = 0.5 + Math.tanh(whiteAdvantageCp / 240.0) * 0.44;
			balance = Math.max(0.04, Math.min(0.96, balance));
			int whiteHeight = (int) Math.round(height * balance);
			int blackHeight = height - whiteHeight;
			g.setColor(theme.evalBorder());
			g.setStroke(new BasicStroke(1.3f));
			g.drawRoundRect(x, y, width, height, 12, 12);
			g.setClip(x + 1, y + 1, width - 1, height - 1);
			g.setColor(theme.evalDark());
			g.fillRect(x + 1, y + 1, width - 1, blackHeight);
			g.setColor(theme.evalLight());
			g.fillRect(x + 1, y + blackHeight, width - 1, whiteHeight);
			g.setClip(null);
			g.dispose();
		}
	}

	/**
	 * List entry for legal moves.
	 *
	 * @param move encoded move
	 * @param san SAN text
	 * @param uci UCI text
	 */
	private record MoveEntry(
		/**
		 * Stores the move.
		 */
		short move,
		/**
		 * Stores the san.
		 */
		String san,
		/**
		 * Stores the uci.
		 */
		String uci
	) {
	}

	/**
	 * Renderer for plain string lists.
	 */
	private final class StringListRenderer extends DefaultListCellRenderer {

				/**
		 * Serialization version identifier.
		 */
@java.io.Serial
		private static final long serialVersionUID = 1L;
				/**
		 * Stores the mono.
		 */
private final boolean mono;

				/**
		 * Creates a new instance.
		 * @param mono mono value
		 */
private StringListRenderer(boolean mono) {
			this.mono = mono;
		}

				/**
		 * Returns the list cell renderer component.
		 * @param list list value
		 * @param value value value
		 * @param index index value
		 * @param isSelected is selected value
		 * @param cellHasFocus cell has focus value
		 * @return computed value
		 */
@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			label.setOpaque(true);
			label.setBorder(new EmptyBorder(5, 8, 5, 8));
			label.setFont(mono ? theme.monoFont() : theme.bodyFont());
			label.setForeground(isSelected ? theme.textStrong() : theme.text());
			label.setBackground(isSelected ? theme.accentSoft() : theme.surfaceAlt());
			return label;
		}
	}

	/**
	 * Renderer for legal move entries.
	 */
	private final class MoveListRenderer extends DefaultListCellRenderer {

				/**
		 * Serialization version identifier.
		 */
@java.io.Serial
		private static final long serialVersionUID = 1L;
				/**
		 * Returns the list cell renderer component.
		 * @param list list value
		 * @param value value value
		 * @param index index value
		 * @param isSelected is selected value
		 * @param cellHasFocus cell has focus value
		 * @return computed value
		 */
@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			MoveEntry entry = (MoveEntry) value;
			JLabel label = (JLabel) super.getListCellRendererComponent(list,
					entry.san() + "    " + entry.uci(), index, isSelected, cellHasFocus);
			label.setOpaque(true);
			label.setBorder(new EmptyBorder(5, 8, 5, 8));
			label.setFont(theme.monoFont());
			label.setForeground(isSelected ? theme.textStrong() : theme.text());
			label.setBackground(isSelected ? theme.accentSoft() : theme.surfaceAlt());
			return label;
		}
	}
}
