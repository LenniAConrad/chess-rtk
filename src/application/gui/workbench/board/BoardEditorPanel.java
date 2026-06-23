package application.gui.workbench.board;

import application.gui.workbench.ui.RenderAcceleration;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.Theme;
import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.core.Setup;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.controlRow;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.flow;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.styleCheckBox;
import static application.gui.workbench.ui.Ui.styleCombos;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleIntegerSpinner;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Compact FEN setup editor for the workbench board tab.
 */
public final class BoardEditorPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Number of squares on a chess board.
     */
    private static final int BOARD_SQUARE_COUNT = 64;

    /**
     * Number of squares along one board edge.
     */
    private static final int BOARD_EDGE = 8;

    /**
     * Piece icon size used in the palette.
     */
    private static final int TOOL_ICON_SIZE = 30;

    /**
     * Transparent canvas size used for palette piece icons.
     */
    private static final int TOOL_TILE_SIZE = 36;

    /**
     * Fixed size for each palette button.
     */
    private static final Dimension TOOL_BUTTON_SIZE = new Dimension(52, 46);

    /**
     * Fixed width for the live FEN preview. Its content changes length as the
     * board is edited, so it must not dictate the panel width.
     */
    private static final int FEN_PREVIEW_WIDTH = 260;

    /**
     * FEN marker for an unavailable optional field.
     */
    private static final String FEN_NONE = "-";

    /**
     * White-to-move combo label.
     */
    private static final String WHITE_TO_MOVE = "White";

    /**
     * Black-to-move combo label.
     */
    private static final String BLACK_TO_MOVE = "Black";

    /**
     * Valid piece tools shown in the editor palette.
     */
    private static final byte[] PIECE_TOOLS = {
            Piece.WHITE_KING, Piece.WHITE_QUEEN, Piece.WHITE_ROOK, Piece.WHITE_BISHOP,
            Piece.WHITE_KNIGHT, Piece.WHITE_PAWN, Piece.BLACK_KING, Piece.BLACK_QUEEN,
            Piece.BLACK_ROOK, Piece.BLACK_BISHOP, Piece.BLACK_KNIGHT, Piece.BLACK_PAWN
    };

    /**
     * Current-FEN supplier from the hosting workbench window.
     */
    private final Supplier<String> currentFenSupplier;

    /**
     * Callback that applies a validated FEN to the workbench.
     */
    private final Consumer<String> applyFenConsumer;

    /**
     * Callback used to copy FEN text with the workbench clipboard helper.
     */
    private final Consumer<String> copyTextConsumer;

    /**
     * Cached board and piece renderer shared by the canvas and palette.
     */
    private final BoardImageCache imageCache = new BoardImageCache();

    /**
     * Mutable board edited by the user, using repository piece codes.
     */
    private final byte[] editedBoard = new byte[BOARD_SQUARE_COUNT];

    /**
     * Side-to-move selector.
     */
    private final JComboBox<String> sideToMoveBox =
            new JComboBox<>(new String[] { WHITE_TO_MOVE, BLACK_TO_MOVE });

    /**
     * White king-side castling toggle.
     */
    private final JCheckBox whiteKingSideBox = new JCheckBox("K");

    /**
     * White queen-side castling toggle.
     */
    private final JCheckBox whiteQueenSideBox = new JCheckBox("Q");

    /**
     * Black king-side castling toggle.
     */
    private final JCheckBox blackKingSideBox = new JCheckBox("k");

    /**
     * Black queen-side castling toggle.
     */
    private final JCheckBox blackQueenSideBox = new JCheckBox("q");

    /**
     * En-passant target square selector.
     */
    private final JComboBox<String> enPassantBox = new JComboBox<>();

    /**
     * Halfmove-clock spinner.
     */
    private final JSpinner halfmoveSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1));

    /**
     * Fullmove-number spinner.
     */
    private final JSpinner fullmoveSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));

    /**
     * Read-only live FEN preview.
     */
    private final JTextField fenPreviewField = new JTextField();

    /**
     * Validation and action feedback label.
     */
    private final JLabel statusLabel = new JLabel("Ready");

    /**
     * Currently selected piece tool; {@link Piece#EMPTY} erases squares.
     */
    private byte selectedPiece = Piece.WHITE_KING;

    /**
     * Board currently driven by setup edits. This is the shared main board,
     * never a small duplicate board inside the editor rail.
     */
    private transient BoardPanel hostBoard;

    /**
     * Whether the host board should currently intercept setup edits.
     */
    private boolean editingBoardActive;

    /**
     * Non-standard Chess960 castling text preserved from a loaded FEN until the
     * user edits castling controls.
     */
    private String customCastlingRights;

    /**
     * True while controls are being populated from a FEN.
     */
    private boolean suppressPreviewUpdates;

    /**
     * Creates an editable board setup panel.
     *
     * @param currentFenSupplier current-FEN supplier
     * @param applyFenConsumer validated-FEN apply callback
     * @param copyTextConsumer clipboard callback
     */
    public BoardEditorPanel(
            Supplier<String> currentFenSupplier,
            Consumer<String> applyFenConsumer,
            Consumer<String> copyTextConsumer) {
        super(new GridBagLayout());
        this.currentFenSupplier = Objects.requireNonNull(currentFenSupplier, "currentFenSupplier");
        this.applyFenConsumer = Objects.requireNonNull(applyFenConsumer, "applyFenConsumer");
        this.copyTextConsumer = Objects.requireNonNull(copyTextConsumer, "copyTextConsumer");
        configurePanel();
        installControlListeners();
        if (!loadFen(this.currentFenSupplier.get())) {
            loadFen(Setup.getStandardStartFEN());
        }
    }

    /**
     * Loads a FEN into the editor controls.
     *
     * @param fen FEN to load
     * @return true when loaded
     */
    public boolean loadFen(String fen) {
        String normalized = fen == null ? "" : fen.trim();
        try {
            Position position = new Position(normalized);
            populateFromFen(position.toString(), position.getBoard());
            setStatus("Loaded", Theme.ForegroundRole.MUTED);
            return true;
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), Theme.ForegroundRole.ERROR);
            return false;
        }
    }

    /**
     * Returns the current editor FEN, whether or not it is legal.
     *
     * @return edited FEN text
     */
    public String fen() {
        return placementText() + " "
                + sideToMoveText() + " "
                + castlingText() + " "
                + enPassantText() + " "
                + spinnerInt(halfmoveSpinner) + " "
                + spinnerInt(fullmoveSpinner);
    }

    /**
     * Applies the edited FEN to the host when it validates.
     *
     * @return true when applied
     */
    public boolean applyEditedFen() {
        String candidate = fen();
        try {
            Position position = new Position(candidate);
            String normalized = position.toString();
            applyFenConsumer.accept(normalized);
            loadFen(normalized);
            setStatus("Applied", Theme.ForegroundRole.SUCCESS);
            return true;
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), Theme.ForegroundRole.ERROR);
            return false;
        }
    }

    /**
     * Sets one board square to a piece code.
     *
     * @param square square index
     * @param piece piece code
     */
    public void setPieceAt(byte square, byte piece) {
        validateSquare(square);
        validatePiece(piece);
        editedBoard[square] = piece;
        pruneMetadataAfterBoardChange();
        updateFenPreview();
        syncHostBoard();
    }

    /**
     * Attaches this editor to the shared board surface.
     *
     * @param board main workbench board, or null to detach
     */
    public void attachBoard(BoardPanel board) {
        if (hostBoard == board) {
            syncHostBoard();
            return;
        }
        if (hostBoard != null) {
            hostBoard.setSetupEditMode(false);
            hostBoard.setSetupEditObserver(null);
        }
        hostBoard = board;
        if (hostBoard != null) {
            hostBoard.setSetupEditObserver(this::handleHostBoardEdit);
        }
        syncHostBoard();
    }

    /**
     * Enables or disables direct editing on the shared board.
     *
     * @param active true while the Editor tab is selected and editable
     */
    public void setEditingBoardActive(boolean active) {
        editingBoardActive = active;
        syncHostBoard();
        setStatus(active ? "Editing board" : "Ready", Theme.ForegroundRole.MUTED);
    }

    /**
     * Keeps the shared board edit mode in sync with this panel's enabled state.
     *
     * @param enabled true when editor controls are enabled
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        syncHostBoard();
    }

    /**
     * Configures the root panel.
     */
    private void configurePanel() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(Theme.SPACE_MD));

        GridBagConstraints c = constraints();
        int row = 0;
        c.insets = new Insets(0, 0, Theme.SPACE_SM, 0);
        grid(this, createHeader(), c, 0, row++, 1, 1);
        c.insets = new Insets(0, 0, Theme.SPACE_MD, 0);
        grid(this, createPalettePanel(), c, 0, row++, 1, 1);
        c.insets = new Insets(0, 0, Theme.SPACE_MD, 0);
        grid(this, titledSection("State", createStateControls()), c, 0, row++, 1, 1);
        c.insets = new Insets(0, 0, Theme.SPACE_MD, 0);
        grid(this, titledSection("FEN", createFenPreview()), c, 0, row++, 1, 1);
        c.insets = new Insets(0, 0, 0, 0);
        grid(this, createActionRows(), c, 0, row++, 1, 1);
        c.gridy = row;
        c.weighty = 1.0;
        add(transparentPanel(new BorderLayout()), c);
    }

    /**
     * Creates the editor title row with a short how-to hint.
     *
     * @return header component
     */
    private JComponent createHeader() {
        JPanel header = transparentPanel(new BorderLayout(0, 2));
        header.add(Theme.section("Position editor"), BorderLayout.NORTH);
        return header;
    }

    /**
     * Wraps a body under a flat section header.
     *
     * @param title section title
     * @param body section body
     * @return titled section
     */
    private static JComponent titledSection(String title, JComponent body) {
        JPanel section = transparentPanel(new BorderLayout(0, 4));
        section.add(Theme.section(title), BorderLayout.NORTH);
        section.add(body, BorderLayout.CENTER);
        return section;
    }

    /**
     * Flips the editing board orientation.
     */
    private void flipBoard() {
        if (hostBoard != null) {
            hostBoard.setWhiteDown(!hostBoard.isWhiteDown());
        }
    }

    /**
     * Creates the piece-tool panel.
     *
     * @return tool panel
     */
    private JComponent createPalettePanel() {
        JPanel panel = transparentPanel(new BorderLayout(0, 6));
        panel.add(Theme.section("Pieces"), BorderLayout.NORTH);
        panel.add(createPalette(), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the piece palette.
     *
     * @return palette component
     */
    private JComponent createPalette() {
        JPanel palette = transparentPanel(new GridLayout(2, 7, 4, 4));
        ButtonGroup group = new ButtonGroup();
        JToggleButton erase = createToolButton(Piece.EMPTY, "Erase square");
        group.add(erase);
        palette.add(erase);
        for (byte piece : PIECE_TOOLS) {
            JToggleButton button = createToolButton(piece, pieceLabel(piece));
            if (piece == selectedPiece) {
                button.setSelected(true);
            }
            group.add(button);
            palette.add(button);
        }
        return palette;
    }

    /**
     * Creates one palette tool button.
     *
     * @param piece selected piece code
     * @param tooltip button tooltip
     * @return palette button
     */
    private JToggleButton createToolButton(byte piece, String tooltip) {
        JToggleButton button = new JToggleButton();
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.setPreferredSize(TOOL_BUTTON_SIZE);
        button.setMinimumSize(TOOL_BUTTON_SIZE);
        button.setMaximumSize(TOOL_BUTTON_SIZE);
        if (piece == Piece.EMPTY) {
            button.setText("X");
            button.setFont(Theme.font(13, Font.BOLD));
        } else {
            button.setIcon(new ImageIcon(paletteIcon(piece)));
        }
        Theme.commandTab(button);
        button.addActionListener(event -> {
            selectedPiece = piece;
            syncHostBoardSelection();
        });
        return button;
    }

    /**
     * Renders a palette piece icon on a transparent canvas.
     *
     * @param piece piece code
     * @return palette icon image
     */
    private BufferedImage paletteIcon(byte piece) {
        BufferedImage icon = RenderAcceleration.translucentImage(TOOL_TILE_SIZE, TOOL_TILE_SIZE);
        Graphics2D graphics = icon.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Image image = imageCache.pieceImage(piece, TOOL_ICON_SIZE);
            if (image != null) {
                int inset = (TOOL_TILE_SIZE - TOOL_ICON_SIZE) / 2;
                graphics.drawImage(image, inset, inset, null);
            }
        } finally {
            graphics.dispose();
        }
        return icon;
    }

    /**
     * Creates the side-to-move, castling, and clock controls.
     *
     * @return state controls
     */
    private JComponent createStateControls() {
        JPanel state = transparentPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(3, 0, 3, 0);

        styleCombos(sideToMoveBox, enPassantBox);
        styleFields(fenPreviewField);
        styleIntegerSpinner(halfmoveSpinner);
        styleIntegerSpinner(fullmoveSpinner);

        grid(state, label("turn"), c, 0, 0, 1, 1);
        grid(state, sideToMoveBox, c, 1, 0, 2, 1);

        grid(state, label("castling"), c, 0, 1, 1, 1);
        grid(state, createCastlingRow(), c, 1, 1, 2, 1);

        grid(state, label("ep"), c, 0, 2, 1, 1);
        grid(state, enPassantBox, c, 1, 2, 1, 1);
        grid(state, label("half"), c, 0, 3, 1, 1);
        grid(state, halfmoveSpinner, c, 1, 3, 1, 1);
        grid(state, label("full"), c, 0, 4, 1, 1);
        grid(state, fullmoveSpinner, c, 1, 4, 1, 1);
        return state;
    }

    /**
     * Creates the standard castling-right checkboxes.
     *
     * @return castling row
     */
    private JComponent createCastlingRow() {
        JPanel row = flow(FlowLayout.LEFT);
        for (JCheckBox box : new JCheckBox[] {
                whiteKingSideBox, whiteQueenSideBox, blackKingSideBox, blackQueenSideBox }) {
            styleCheckBox(box);
            row.add(box);
        }
        return row;
    }

    /**
     * Creates the FEN preview field and status row.
     *
     * @return preview component
     */
    private JComponent createFenPreview() {
        JPanel preview = transparentPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(3, 0, 3, 0);
        fenPreviewField.setEditable(false);
        fenPreviewField.setFont(Theme.mono(12));
        applyFixedSize(fenPreviewField, new Dimension(FEN_PREVIEW_WIDTH, Theme.CONTROL_HEIGHT));
        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        statusLabel.setFont(Theme.font(11, Font.PLAIN));
        applyFixedSize(statusLabel, new Dimension(FEN_PREVIEW_WIDTH, Theme.CONTROL_HEIGHT));
        grid(preview, label("fen"), c, 0, 0, 1, 1);
        grid(preview, fenPreviewField, c, 1, 0, 3, 1);
        grid(preview, statusLabel, c, 1, 1, 3, 1);
        return preview;
    }

    /**
     * Creates editor action buttons.
     *
     * @return action rows
     */
    private JComponent createActionRows() {
        JPanel actions = transparentPanel(new BorderLayout(0, 6));
        actions.add(controlRow(FlowLayout.LEFT,
                button("Current", false, event -> loadFen(currentFenSupplier.get())),
                button("Start", false, event -> loadFen(Setup.getStandardStartFEN())),
                new HoldButton("Clear", this::clearBoard, true),
                button("Flip", false, event -> flipBoard())), BorderLayout.NORTH);
        actions.add(buttonRow(FlowLayout.LEFT,
                button("Apply", true, event -> applyEditedFen()),
                button("Copy FEN", false, event -> copyTextConsumer.accept(fen()))), BorderLayout.SOUTH);
        return actions;
    }

    /**
     * Accepts a square edit from the shared board.
     *
     * @param square boxed board square
     * @param piece boxed piece code
     */
    private void handleHostBoardEdit(Byte square, Byte piece) {
        if (square == null || piece == null) {
            return;
        }
        byte squareValue = square.byteValue();
        byte pieceValue = piece.byteValue();
        validateSquare(squareValue);
        validatePiece(pieceValue);
        editedBoard[squareValue] = pieceValue;
        pruneMetadataAfterBoardChange();
        updateFenPreview();
        setStatus(pieceValue == Piece.EMPTY ? "Square cleared" : pieceLabel(pieceValue), Theme.ForegroundRole.MUTED);
    }

    /**
     * Pushes the edited position, selected piece, and active state into the
     * shared board.
     */
    private void syncHostBoard() {
        if (hostBoard == null) {
            return;
        }
        hostBoard.setSetupEditBoard(editedBoard);
        syncHostBoardSelection();
        hostBoard.setSetupEditMode(editingBoardActive && isEnabled());
    }

    /**
     * Pushes the current piece tool into the shared board.
     */
    private void syncHostBoardSelection() {
        if (hostBoard != null) {
            hostBoard.setSetupEditSelectedPiece(selectedPiece);
        }
    }

    /**
     * Wires live FEN preview updates.
     */
    private void installControlListeners() {
        sideToMoveBox.addActionListener(event -> {
            if (!suppressPreviewUpdates) {
                refreshEnPassantOptions(enPassantText());
            }
            updateFenPreview();
        });
        enPassantBox.addActionListener(event -> updateFenPreview());
        halfmoveSpinner.addChangeListener(event -> updateFenPreview());
        fullmoveSpinner.addChangeListener(event -> updateFenPreview());
        for (JCheckBox box : new JCheckBox[] {
                whiteKingSideBox, whiteQueenSideBox, blackKingSideBox, blackQueenSideBox }) {
            box.addActionListener(event -> {
                customCastlingRights = null;
                updateFenPreview();
            });
        }
    }

    /**
     * Populates controls from a normalized FEN.
     *
     * @param fen normalized FEN
     * @param board board snapshot
     */
    private void populateFromFen(String fen, byte[] board) {
        String[] parts = fen.split("\\s+");
        suppressPreviewUpdates = true;
        try {
            System.arraycopy(board, 0, editedBoard, 0, editedBoard.length);
            sideToMoveBox.setSelectedItem("w".equals(parts[1]) ? WHITE_TO_MOVE : BLACK_TO_MOVE);
            populateCastling(parts[2]);
            refreshEnPassantOptions(parts[3]);
            halfmoveSpinner.setValue(Integer.valueOf(parts[4]));
            fullmoveSpinner.setValue(Integer.valueOf(parts[5]));
        } finally {
            suppressPreviewUpdates = false;
        }
        updateFenPreview();
        syncHostBoard();
    }

    /**
     * Removes FEN metadata that no longer matches the edited board.
     */
    private void pruneMetadataAfterBoardChange() {
        pruneCastlingRights();
        pruneEnPassantTarget();
    }

    /**
     * Removes castling rights whose king or rook is no longer present.
     */
    private void pruneCastlingRights() {
        if (customCastlingRights != null) {
            customCastlingRights = pruneChess960Castling(customCastlingRights);
            return;
        }
        whiteKingSideBox.setSelected(whiteKingSideBox.isSelected()
                && hasPiece(Field.E1, Piece.WHITE_KING)
                && hasPiece(Field.H1, Piece.WHITE_ROOK));
        whiteQueenSideBox.setSelected(whiteQueenSideBox.isSelected()
                && hasPiece(Field.E1, Piece.WHITE_KING)
                && hasPiece(Field.A1, Piece.WHITE_ROOK));
        blackKingSideBox.setSelected(blackKingSideBox.isSelected()
                && hasPiece(Field.E8, Piece.BLACK_KING)
                && hasPiece(Field.H8, Piece.BLACK_ROOK));
        blackQueenSideBox.setSelected(blackQueenSideBox.isSelected()
                && hasPiece(Field.E8, Piece.BLACK_KING)
                && hasPiece(Field.A8, Piece.BLACK_ROOK));
    }

    /**
     * Keeps only Chess960 castling letters whose current king and rook still
     * form a valid back-rank castling pair.
     *
     * @param castling current castling field
     * @return pruned castling field
     */
    private String pruneChess960Castling(String castling) {
        if (castling == null || castling.isBlank() || FEN_NONE.equals(castling)) {
            return FEN_NONE;
        }
        StringBuilder kept = new StringBuilder(castling.length());
        for (int index = 0; index < castling.length(); index++) {
            char ch = castling.charAt(index);
            if (validChess960CastlingLetter(ch)) {
                kept.append(ch);
            }
        }
        return kept.length() == 0 ? FEN_NONE : kept.toString();
    }

    /**
     * Returns whether one Chess960 castling letter is still physically valid.
     *
     * @param ch castling letter
     * @return true when the right can remain advertised
     */
    private boolean validChess960CastlingLetter(char ch) {
        if (ch >= 'A' && ch <= 'H') {
            return validChess960CastlingPair(Piece.WHITE_KING, Piece.WHITE_ROOK,
                    Field.A1 + (ch - 'A'), Field.A1, Field.H1);
        }
        if (ch >= 'a' && ch <= 'h') {
            return validChess960CastlingPair(Piece.BLACK_KING, Piece.BLACK_ROOK,
                    Field.A8 + (ch - 'a'), Field.A8, Field.H8);
        }
        return false;
    }

    /**
     * Returns whether one Chess960 king/rook pair is still present and separated
     * on the home rank.
     *
     * @param king king piece code
     * @param rook rook piece code
     * @param rookSquare advertised rook square
     * @param rankStart first square on the home rank
     * @param rankEnd last square on the home rank
     * @return true when the pair is physically valid
     */
    private boolean validChess960CastlingPair(byte king, byte rook, int rookSquare, int rankStart, int rankEnd) {
        if (!hasPiece(rookSquare, rook)) {
            return false;
        }
        int kingSquare = findPieceOnRank(king, rankStart, rankEnd);
        return kingSquare >= rankStart && kingSquare <= rankEnd && kingSquare != rookSquare;
    }

    /**
     * Keeps the en-passant selector aligned with targets that are still possible
     * from the edited board.
     */
    private void pruneEnPassantTarget() {
        refreshEnPassantOptions(enPassantText());
    }

    /**
     * Rebuilds the en-passant dropdown from the currently possible targets.
     *
     * @param preferred preferred FEN target, if still possible
     */
    private void refreshEnPassantOptions(String preferred) {
        String normalized = preferred != null && Field.isField(preferred) ? preferred : FEN_NONE;
        List<String> targets = possibleEnPassantTargets();
        boolean oldSuppress = suppressPreviewUpdates;
        suppressPreviewUpdates = true;
        try {
            enPassantBox.removeAllItems();
            if (!targets.isEmpty()) {
                enPassantBox.addItem(FEN_NONE);
                for (String target : targets) {
                    enPassantBox.addItem(target);
                }
                enPassantBox.setSelectedItem(targets.contains(normalized) ? normalized : FEN_NONE);
            }
            enPassantBox.setEnabled(!targets.isEmpty());
        } finally {
            suppressPreviewUpdates = oldSuppress;
        }
    }

    /**
     * Returns all en-passant target squares available to the side to move.
     *
     * @return target-square labels
     */
    private List<String> possibleEnPassantTargets() {
        List<String> targets = new ArrayList<>(2);
        if (WHITE_TO_MOVE.equals(sideToMoveBox.getSelectedItem())) {
            for (int square = Field.A5; square <= Field.H5; square++) {
                if (hasPiece(square, Piece.BLACK_PAWN)) {
                    maybeAddEnPassantTarget(targets, square, square - BOARD_EDGE, Piece.WHITE_PAWN);
                }
            }
        } else {
            for (int square = Field.A4; square <= Field.H4; square++) {
                if (hasPiece(square, Piece.WHITE_PAWN)) {
                    maybeAddEnPassantTarget(targets, square, square + BOARD_EDGE, Piece.BLACK_PAWN);
                }
            }
        }
        return targets;
    }

    /**
     * Adds one en-passant target when the passed pawn and at least one capturing
     * pawn are present.
     *
     * @param targets target list
     * @param passedPawnSquare square containing the capturable pawn
     * @param targetSquare empty target square behind the passed pawn
     * @param capturingPawn side-to-move pawn that can capture
     */
    private void maybeAddEnPassantTarget(List<String> targets, int passedPawnSquare,
            int targetSquare, byte capturingPawn) {
        if (!hasPiece(targetSquare, Piece.EMPTY) || !hasAdjacentPawn(passedPawnSquare, capturingPawn)) {
            return;
        }
        String target = Field.toString((byte) targetSquare);
        if (!targets.contains(target)) {
            targets.add(target);
        }
    }

    /**
     * Returns whether a pawn of the side to move is horizontally adjacent to the
     * passed pawn.
     *
     * @param square passed-pawn square
     * @param pawn capturing pawn code
     * @return true when an adjacent capturer is present
     */
    private boolean hasAdjacentPawn(int square, byte pawn) {
        int file = square % BOARD_EDGE;
        return (file > 0 && hasPiece(square - 1, pawn))
                || (file < BOARD_EDGE - 1 && hasPiece(square + 1, pawn));
    }

    /**
     * Returns whether a square currently contains one piece.
     *
     * @param square board square
     * @param piece expected piece
     * @return true when the piece is present
     */
    private boolean hasPiece(int square, byte piece) {
        return square >= 0 && square < editedBoard.length && editedBoard[square] == piece;
    }

    /**
     * Finds one piece on a rank.
     *
     * @param piece piece code
     * @param rankStart first square on the rank
     * @param rankEnd last square on the rank
     * @return square or -1
     */
    private int findPieceOnRank(byte piece, int rankStart, int rankEnd) {
        for (int square = rankStart; square <= rankEnd; square++) {
            if (hasPiece(square, piece)) {
                return square;
            }
        }
        return -1;
    }

    /**
     * Populates castling controls.
     *
     * @param castling castling field
     */
    private void populateCastling(String castling) {
        if (!isStandardCastling(castling)) {
            customCastlingRights = castling;
            whiteKingSideBox.setSelected(false);
            whiteQueenSideBox.setSelected(false);
            blackKingSideBox.setSelected(false);
            blackQueenSideBox.setSelected(false);
            return;
        }
        customCastlingRights = null;
        whiteKingSideBox.setSelected(castling.indexOf('K') >= 0);
        whiteQueenSideBox.setSelected(castling.indexOf('Q') >= 0);
        blackKingSideBox.setSelected(castling.indexOf('k') >= 0);
        blackQueenSideBox.setSelected(castling.indexOf('q') >= 0);
    }

    /**
     * Clears the board and resets metadata to minimal defaults.
     */
    private void clearBoard() {
        Arrays.fill(editedBoard, Piece.EMPTY);
        suppressPreviewUpdates = true;
        try {
            sideToMoveBox.setSelectedItem(WHITE_TO_MOVE);
            customCastlingRights = null;
            whiteKingSideBox.setSelected(false);
            whiteQueenSideBox.setSelected(false);
            blackKingSideBox.setSelected(false);
            blackQueenSideBox.setSelected(false);
            refreshEnPassantOptions(FEN_NONE);
            halfmoveSpinner.setValue(Integer.valueOf(0));
            fullmoveSpinner.setValue(Integer.valueOf(1));
        } finally {
            suppressPreviewUpdates = false;
        }
        updateFenPreview();
        setStatus("Board cleared", Theme.ForegroundRole.WARNING);
        syncHostBoard();
    }

    /**
     * Updates the read-only FEN preview field.
     */
    private void updateFenPreview() {
        if (suppressPreviewUpdates) {
            return;
        }
        fenPreviewField.setText(fen());
        fenPreviewField.setCaretPosition(0);
    }

    /**
     * Updates status copy and semantic color.
     *
     * @param text status text
     * @param role foreground role
     */
    private void setStatus(String text, Theme.ForegroundRole role) {
        statusLabel.setText(text == null || text.isBlank() ? "Ready" : text);
        Theme.foreground(statusLabel, role);
    }

    /**
     * Builds the board-placement FEN field.
     *
     * @return placement text
     */
    private String placementText() {
        StringBuilder placement = new StringBuilder();
        for (int rankRow = 0; rankRow < BOARD_EDGE; rankRow++) {
            if (rankRow > 0) {
                placement.append('/');
            }
            appendRankPlacement(placement, rankRow);
        }
        return placement.toString();
    }

    /**
     * Appends one FEN rank.
     *
     * @param placement target builder
     * @param rankRow internal rank row
     */
    private void appendRankPlacement(StringBuilder placement, int rankRow) {
        int emptyRun = 0;
        for (int file = 0; file < BOARD_EDGE; file++) {
            byte piece = editedBoard[rankRow * BOARD_EDGE + file];
            if (piece == Piece.EMPTY) {
                emptyRun++;
                continue;
            }
            if (emptyRun > 0) {
                placement.append(emptyRun);
                emptyRun = 0;
            }
            placement.append(Piece.toLowerCaseChar(piece));
        }
        if (emptyRun > 0) {
            placement.append(emptyRun);
        }
    }

    /**
     * Returns the active color field.
     *
     * @return side-to-move FEN token
     */
    private String sideToMoveText() {
        return WHITE_TO_MOVE.equals(sideToMoveBox.getSelectedItem()) ? "w" : "b";
    }

    /**
     * Returns the castling field.
     *
     * @return castling FEN token
     */
    private String castlingText() {
        if (customCastlingRights != null && !customCastlingRights.isBlank()) {
            return customCastlingRights;
        }
        StringBuilder rights = new StringBuilder();
        if (whiteKingSideBox.isSelected()) {
            rights.append('K');
        }
        if (whiteQueenSideBox.isSelected()) {
            rights.append('Q');
        }
        if (blackKingSideBox.isSelected()) {
            rights.append('k');
        }
        if (blackQueenSideBox.isSelected()) {
            rights.append('q');
        }
        return rights.length() == 0 ? FEN_NONE : rights.toString();
    }

    /**
     * Returns the en-passant field.
     *
     * @return en-passant FEN token
     */
    private String enPassantText() {
        Object selected = enPassantBox.getSelectedItem();
        String text = selected == null ? "" : selected.toString().trim();
        return Field.isField(text) ? text : FEN_NONE;
    }

    /**
     * Reads an integer spinner value.
     *
     * @param spinner spinner
     * @return integer value
     */
    private static int spinnerInt(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (java.text.ParseException ex) {
            spinner.getToolkit().beep();
        }
        return ((Number) spinner.getValue()).intValue();
    }

    /**
     * Applies a fixed footprint to a component.
     *
     * @param component component to size
     * @param size fixed size
     */
    private static void applyFixedSize(JComponent component, Dimension size) {
        component.setPreferredSize(new Dimension(size));
        component.setMinimumSize(new Dimension(size));
        component.setMaximumSize(new Dimension(size));
    }

    /**
     * Returns whether a castling field maps to the standard KQkq controls.
     *
     * @param castling castling field
     * @return true when standard
     */
    private static boolean isStandardCastling(String castling) {
        if (FEN_NONE.equals(castling)) {
            return true;
        }
        int previous = -1;
        for (int index = 0; index < castling.length(); index++) {
            int order = switch (castling.charAt(index)) {
                case 'K' -> 0;
                case 'Q' -> 1;
                case 'k' -> 2;
                case 'q' -> 3;
                default -> -1;
            };
            if (order < 0 || order <= previous) {
                return false;
            }
            previous = order;
        }
        return !castling.isBlank();
    }

    /**
     * Returns a human-readable piece label.
     *
     * @param piece piece code
     * @return piece label
     */
    private static String pieceLabel(byte piece) {
        String color = piece > 0 ? "White " : "Black ";
        return color + switch (Math.abs(piece)) {
            case Piece.KING -> "king";
            case Piece.QUEEN -> "queen";
            case Piece.ROOK -> "rook";
            case Piece.BISHOP -> "bishop";
            case Piece.KNIGHT -> "knight";
            case Piece.PAWN -> "pawn";
            default -> "piece";
        };
    }

    /**
     * Validates a board square.
     *
     * @param square square index
     */
    private static void validateSquare(byte square) {
        if (square < 0 || square >= BOARD_SQUARE_COUNT) {
            throw new IllegalArgumentException("Invalid square " + square);
        }
    }

    /**
     * Validates a piece code.
     *
     * @param piece piece code
     */
    private static void validatePiece(byte piece) {
        if (piece < Piece.BLACK_KING || piece > Piece.WHITE_KING) {
            throw new IllegalArgumentException("Invalid piece " + piece);
        }
    }

}
