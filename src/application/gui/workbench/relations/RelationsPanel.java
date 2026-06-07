package application.gui.workbench.relations;

import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import chess.core.Position;
import chess.images.render.RelationPalette;
import chess.nn.otis.Model;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

/**
 * Control rail for the workbench "Relations" tab: drives a {@link BoardPanel}
 * with the OTIS tactical-incidence relation channels (typed colour-coded arrows),
 * the same {@code A(x)} the network ingests and the CLI {@code fen relations}
 * draws. Channels are selected with per-colour toggles; the board is fed the
 * current analysis position (or a pasted FEN), and the edges come straight from
 * {@link Model#incidenceEdges(Position)} (deterministic, no model file needed).
 */
public final class RelationsPanel extends JPanel {

	/**
	 * Serialization identifier for Swing component compatibility.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Arrow line width for the relation overlay (thinner than the default markup
	 * so a dense graph stays legible).
	 */
	private static final int ARROW_WIDTH = 6;

	/**
	 * Channels selected on first open: the most tactically telling and least
	 * cluttered (us/them attacks, knight attacks, pin candidates).
	 */
	private static final int[] DEFAULT_CHANNELS = { 0, 1, 9, 11 };

	/**
	 * Fixed width for compact Relations rail control labels.
	 */
	private static final int LABEL_WIDTH = 78;

	/**
	 * Board this panel overlays.
	 */
	private final transient BoardPanel board;

	/**
	 * Supplier of the current analysis position, for "Sync to board".
	 */
	private final transient Supplier<Position> currentPosition;

	/**
	 * Per-channel selection toggles.
	 */
	private final ToggleBox[] channelToggles = new ToggleBox[Model.RELATION_COUNT];

	/**
	 * FEN entry for visualizing an arbitrary position.
	 */
	private final JTextField fenField = new JTextField();

	/**
	 * Arrow opacity slider (percent).
	 */
	private final JSlider opacitySlider = new JSlider(10, 100, 75);

	/**
	 * Edge-count / status readout.
	 */
	private final JLabel statusLabel = new JLabel();

	/**
	 * Position currently visualized.
	 */
	private transient Position position;

	/**
	 * Creates the relations panel.
	 *
	 * @param board board to overlay
	 * @param currentPosition supplier of the current analysis position
	 */
	public RelationsPanel(BoardPanel board, Supplier<Position> currentPosition) {
		super(new GridBagLayout());
		this.board = board;
		this.currentPosition = currentPosition;
		setOpaque(true);
		setBackground(Theme.BG);
		setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
		buildUi();
		syncToBoard();
	}

	/**
	 * Lays out the control rail.
	 */
	private void buildUi() {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.insets = new Insets(4, 0, 4, 0);

		JLabel title = Theme.sectionTitle("Tactical Incidence");
		title.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_XS, 0));
		add(title, c);

		c.gridy++;
		JLabel caption = new JLabel("The OTIS relation channels A(x): typed edges the network reads.");
		caption.setForeground(Theme.MUTED);
		caption.setFont(Theme.font(12, Font.PLAIN));
		add(caption, c);

		c.gridy++;
		add(positionRow(), c);

		c.gridy++;
		Ui.styleFields(fenField);
		fenField.setToolTipText("Paste a FEN to visualize, then press Load");
		add(fenField, c);

		c.gridy++;
		// Fold the All/None selectors and the ~12 channel toggles into one
		// collapsible "Channels" section so the idle rail can be tucked away.
		JPanel channels = Ui.transparentPanel(new java.awt.BorderLayout(0, Theme.SPACE_SM));
		channels.add(channelHeader(), java.awt.BorderLayout.NORTH);
		channels.add(channelList(), java.awt.BorderLayout.CENTER);
		add(Ui.collapsible("Channels", channels, true), c);

		c.gridy++;
		add(opacityRow(), c);

		c.gridy++;
		Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
		statusLabel.setFont(Theme.font(12, Font.PLAIN));
		add(statusLabel, c);

		// Push everything to the top.
		c.gridy++;
		c.weighty = 1.0;
		c.fill = GridBagConstraints.BOTH;
		add(Box.createGlue(), c);
	}

	/**
	 * Builds the position-source row.
	 *
	 * @return position row
	 */
	private JComponent positionRow() {
		JPanel row = Ui.transparentPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, Theme.SPACE_SM, 0));
		row.add(Ui.button("Sync to board", true, event -> syncToBoard()));
		row.add(Ui.button("Load FEN", false, event -> loadFen(fenField.getText())));
		return row;
	}

	/**
	 * Builds the channel-section header with All/None quick selectors.
	 *
	 * @return channel header
	 */
	private JComponent channelHeader() {
		JPanel row = Ui.transparentPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, Theme.SPACE_SM, 0));
		row.add(Ui.button("All", false, event -> selectAll(true)));
		row.add(Ui.button("None", false, event -> selectAll(false)));
		return row;
	}

	/**
	 * Builds the per-channel toggle list (colour swatch + name).
	 *
	 * @return channel list
	 */
	private JComponent channelList() {
		JPanel list = Ui.transparentPanel(null);
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		boolean[] defaultOn = new boolean[Model.RELATION_COUNT];
		for (int channel : DEFAULT_CHANNELS) {
			defaultOn[channel] = true;
		}
		for (int channel = 0; channel < Model.RELATION_COUNT; channel++) {
			list.add(channelRow(channel, defaultOn[channel]));
			list.add(Box.createVerticalStrut(2));
		}
		return list;
	}

	/**
	 * Builds one channel row: a colour swatch and a labelled toggle.
	 *
	 * @param channel relation channel
	 * @param selected initial selection
	 * @return channel row
	 */
	private JComponent channelRow(int channel, boolean selected) {
		JPanel row = Ui.transparentPanel(null);
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel swatch = new JPanel();
		swatch.setBackground(RelationPalette.color(channel));
		swatch.setBorder(BorderFactory.createLineBorder(Theme.LINE));
		Dimension dot = new Dimension(14, 14);
		swatch.setPreferredSize(dot);
		swatch.setMinimumSize(dot);
		swatch.setMaximumSize(dot);
		swatch.setAlignmentY(Component.CENTER_ALIGNMENT);

		ToggleBox toggle = new ToggleBox(Model.RELATION_NAMES[channel]);
		toggle.setSelected(selected);
		toggle.setAlignmentY(Component.CENTER_ALIGNMENT);
		toggle.addActionListener(event -> refresh());
		channelToggles[channel] = toggle;

		row.add(swatch);
		row.add(Box.createHorizontalStrut(Theme.SPACE_SM));
		row.add(toggle);
		row.add(Box.createHorizontalGlue());
		return row;
	}

	/**
	 * Builds the opacity slider row.
	 *
	 * @return opacity row
	 */
	private JComponent opacityRow() {
		Ui.styleSlider(opacitySlider);
		opacitySlider.setOpaque(false);
		opacitySlider.addChangeListener(event -> refresh());
		return Ui.labelControlRow("Opacity", opacitySlider, LABEL_WIDTH);
	}

	/**
	 * Loads the current analysis position onto the board.
	 */
	public void syncToBoard() {
		Position current = currentPosition == null ? null : currentPosition.get();
		if (current == null) {
			statusLabel.setText("No current position");
			return;
		}
		show(current.copy());
	}

	/**
	 * Loads a pasted FEN onto the board.
	 *
	 * @param fen FEN text
	 */
	private void loadFen(String fen) {
		if (fen == null || fen.isBlank()) {
			return;
		}
		try {
			show(new Position(fen.trim()));
		} catch (RuntimeException ex) {
			Theme.foreground(statusLabel, Theme.ForegroundRole.WARNING);
			statusLabel.setText("Invalid FEN");
		}
	}

	/**
	 * Shows a position and redraws the selected channels.
	 *
	 * @param value position to visualize
	 */
	private void show(Position value) {
		this.position = value;
		board.setPositionInstant(value, Move.NO_MOVE);
		refresh();
	}

	/**
	 * Selects or clears every channel.
	 *
	 * @param on whether to select all
	 */
	private void selectAll(boolean on) {
		for (ToggleBox toggle : channelToggles) {
			if (toggle != null) {
				toggle.setSelected(on);
			}
		}
		refresh();
	}

	/**
	 * Redraws the relation arrows for the selected channels onto the board.
	 */
	private void refresh() {
		board.clearMarkup();
		if (position == null) {
			statusLabel.setText("");
			return;
		}
		int alpha = Math.max(0, Math.min(255, opacitySlider.getValue() * 255 / 100));
		Color[] colors = new Color[Model.RELATION_COUNT];
		for (int channel = 0; channel < colors.length; channel++) {
			Color base = RelationPalette.color(channel);
			colors[channel] = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
		}
		List<Model.IncidenceEdge> edges = Model.incidenceEdges(position);
		int drawn = 0;
		int activeChannels = 0;
		for (ToggleBox toggle : channelToggles) {
			if (toggle != null && toggle.isSelected()) {
				activeChannels++;
			}
		}
		for (Model.IncidenceEdge edge : edges) {
			if (edge.from() == edge.to()) {
				continue;
			}
			ToggleBox toggle = channelToggles[edge.channel()];
			if (toggle != null && toggle.isSelected()) {
				board.addArrow((byte) edge.from(), (byte) edge.to(), colors[edge.channel()], ARROW_WIDTH);
				drawn++;
			}
		}
		Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
		statusLabel.setText(drawn + " edges across " + activeChannels + " channels");
	}
}
