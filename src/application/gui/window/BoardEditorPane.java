package application.gui.window;

import application.gui.ui.GradientPanel;
import chess.core.Piece;
import chess.core.Position;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Embedded board editor pane used by the single-window layout.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
final class BoardEditorPane extends JPanel {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;

    /**
     * owner field.
     */
    private final GuiWindowHistory owner;
    /**
     * editorRoot field.
     */
    private final GradientPanel editorRoot;
        /**
     * Stores the editor board.
     */
private final BoardEditorShared.EditorBoardPanel editorBoard;
        /**
     * Stores the palette buttons.
     */
private final List<BoardEditorShared.PaletteButton> paletteButtons = new ArrayList<>();

    /**
     * originalBoard field.
     */
    private byte[] originalBoard = new byte[64];
    /**
     * board field.
     */
    private byte[] board = new byte[64];
    /**
     * selectedPiece field.
     */
    private byte selectedPiece = Piece.WHITE_PAWN;

    /**
     * whiteToMoveToggle field.
     */
    private final JCheckBox whiteToMoveToggle;
    /**
     * castleK field.
     */
    private final JCheckBox castleK;
    /**
     * castleQ field.
     */
    private final JCheckBox castleQ;
    /**
     * castlek field.
     */
    private final JCheckBox castlek;
    /**
     * castleq field.
     */
    private final JCheckBox castleq;
    /**
     * enPassant field.
     */
    private String enPassant = "-";
    /**
     * halfmove field.
     */
    private String halfmove = "0";
    /**
     * fullmove field.
     */
    private String fullmove = "1";

    /**
     * selectedLabel field.
     */
    private final JLabel selectedLabel;
    /**
     * legalityLabel field.
     */
    private final JLabel legalityLabel;
    /**
     * legalityWrapWidth field.
     */
    private final int legalityWrapWidth;
        /**
     * Stores the action buttons.
     */
private final List<javax.swing.JButton> actionButtons;
    /**
     * adjustingCastling field.
     */
    private boolean adjustingCastling = false;

    /**
     * BoardEditorPane method.
     *
     * @param owner parameter.
     */
    BoardEditorPane(GuiWindowHistory owner) {
        this.owner = owner;
        setOpaque(false);
        setLayout(new BorderLayout());

        whiteToMoveToggle = owner.themedCheckbox("White to move", true, e -> updateLegality());
        editorBoard = new BoardEditorShared.EditorBoardPanel(owner, () -> board, () -> selectedPiece, this::updateLegality);
        JPanel palettePanel = buildPalettePanel();
        List<BoardEditorShared.ActionSpec> actions = Arrays.asList(
                new BoardEditorShared.ActionSpec("Apply", e -> applyEditor()),
                new BoardEditorShared.ActionSpec("Reset", e -> resetBoard()),
                new BoardEditorShared.ActionSpec("Clear", e -> clearBoard()),
                new BoardEditorShared.ActionSpec("Abort", e -> abortEditing()));
        BoardEditorShared.Layout layout = BoardEditorShared.buildLayout(
                owner,
                editorBoard,
                palettePanel,
                selectedPiece,
                whiteToMoveToggle,
                BorderLayout.EAST,
                false,
                false,
                false,
                false,
                actions);
        editorRoot = layout.root();
        selectedLabel = layout.selectedLabel();
        legalityLabel = layout.legalityLabel();
        castleK = layout.castleK();
        castleQ = layout.castleQ();
        castlek = layout.castlek();
        castleq = layout.castleq();
        legalityWrapWidth = layout.legalityWrapWidth();
        actionButtons = layout.actionButtons();
        add(editorRoot, BorderLayout.CENTER);

        castleK.addActionListener(e -> updateLegality());
        castleQ.addActionListener(e -> updateLegality());
        castlek.addActionListener(e -> updateLegality());
        castleq.addActionListener(e -> updateLegality());

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "board-editor-abort");
        getActionMap().put("board-editor-abort", new AbstractAction() {
                        /**
             * Handles action performed.
             * @param e e value
             */
@Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                abortEditing();
            }
        });

        updatePaletteStyles();
        loadFromOwnerPosition();
    }

    /**
     * loadFromOwnerPosition method.
     */
    void loadFromOwnerPosition() {
        if (owner.position == null) {
            return;
        }
        String[] parts = owner.position.toString().split(" ");
        String side = parts.length > 1 ? parts[1] : "w";
        String castling = parts.length > 2 ? parts[2] : "-";
        enPassant = parts.length > 3 ? parts[3] : "-";
        halfmove = parts.length > 4 ? parts[4] : "0";
        fullmove = parts.length > 5 ? parts[5] : "1";
        board = Arrays.copyOf(owner.position.getBoard(), owner.position.getBoard().length);
        originalBoard = Arrays.copyOf(board, board.length);
        whiteToMoveToggle.setSelected(!"b".equals(side));
        castleK.setSelected(castling.contains("K"));
        castleQ.setSelected(castling.contains("Q"));
        castlek.setSelected(castling.contains("k"));
        castleq.setSelected(castling.contains("q"));
        editorBoard.repaint();
        updateLegality();
    }

    /**
     * buildPalettePanel method.
     *
     * @return return value.
     */
    private JPanel buildPalettePanel() {
        return BoardEditorShared.buildPalettePanel(owner, paletteButtons, this::setSelectedPiece);
    }

    /**
     * setSelectedPiece method.
     *
     * @param piece parameter.
     */
    private void setSelectedPiece(byte piece) {
        selectedPiece = piece;
        selectedLabel.setText("Selected: " + BoardEditorShared.pieceName(piece));
        updatePaletteStyles();
    }

    /**
     * updatePaletteStyles method.
     */
    private void updatePaletteStyles() {
        BoardEditorShared.applyPaletteStyles(owner, paletteButtons, selectedPiece);
    }

    /**
     * clearBoard method.
     */
    private void clearBoard() {
        Arrays.fill(board, Piece.EMPTY);
        editorBoard.repaint();
        updateLegality();
    }

    /**
     * resetBoard method.
     */
    private void resetBoard() {
        board = Arrays.copyOf(originalBoard, originalBoard.length);
        editorBoard.repaint();
        updateLegality();
    }

    /**
     * applyEditor method.
     */
    private void applyEditor() {
        String fen = buildFen();
        try {
            new Position(fen);
        } catch (IllegalArgumentException ex) {
            String msg = "Invalid FEN: " + ex.getMessage();
            owner.recordProblem("Board Editor", msg);
            javax.swing.JOptionPane.showMessageDialog(owner.frame, msg, "Board Editor",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        owner.applyFen(fen);
        owner.selectEditorTab("board.pgn");
        if (owner.boardPanel != null) {
            owner.boardPanel.requestFocusInWindow();
        }
    }

    /**
     * abortEditing method.
     */
    private void abortEditing() {
        loadFromOwnerPosition();
        owner.selectEditorTab("board.pgn");
        if (owner.boardPanel != null) {
            owner.boardPanel.requestFocusInWindow();
        }
    }

    /**
     * buildFen method.
     *
     * @return return value.
     */
    private String buildFen() {
        String placement = owner.buildPiecePlacement(board);
        String side = whiteToMoveToggle.isSelected() ? "w" : "b";
        String castling = BoardEditorShared.buildCastling(castleK, castleQ, castlek, castleq);
        return placement + " " + side + " " + castling + " " + enPassant + " " + halfmove + " " + fullmove;
    }

    /**
     * repaintBoard method.
     */
    void repaintBoard() {
        editorBoard.repaint();
    }

    /**
     * applyPaneTheme method.
     */
    void applyPaneTheme() {
        editorRoot.setColors(owner.theme.backgroundTop(), owner.theme.backgroundBottom());
        updatePaletteStyles();
        BoardEditorShared.applyActionButtonStyles(owner, actionButtons);
        configureLegalityLabelSize();
        updateLegality();
        editorRoot.repaint();
    }

    /**
     * configureLegalityLabelSize method.
     */
    private void configureLegalityLabelSize() {
        BoardEditorShared.configureLegalityLabelSize(owner, legalityLabel, legalityWrapWidth);
    }

    /**
     * updateLegality method.
     */
    private void updateLegality() {
        updateCastlingAvailability();
        String fen = buildFen();
        try {
            new Position(fen);
            setLegalityStatus(true, "Position: legal");
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "Invalid position" : ex.getMessage();
            setLegalityStatus(false, "Position: illegal (" + msg + ")");
        }
    }

    /**
     * setLegalityStatus method.
     *
     * @param ok parameter.
     * @param text parameter.
     */
    private void setLegalityStatus(boolean ok, String text) {
        BoardEditorShared.setLegalityStatus(owner, legalityLabel, legalityWrapWidth, ok, text);
    }

    /**
     * updateCastlingAvailability method.
     */
    private void updateCastlingAvailability() {
        if (adjustingCastling || board == null) {
            return;
        }
        adjustingCastling = true;
        try {
            BoardEditorShared.updateCastlingAvailability(board, castleK, castleQ, castlek, castleq);
        } finally {
            adjustingCastling = false;
        }
    }
}
