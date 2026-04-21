package application.gui.window;

import application.gui.ui.GradientPanel;
import chess.core.Piece;
import chess.core.Position;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Modal board editor for building custom positions.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
final class BoardEditorDialog extends JDialog {

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
    private final BoardEditorShared.EditorBoardPanel editorBoard;
    private final List<BoardEditorShared.PaletteButton> paletteButtons = new ArrayList<>();
    /**
     * originalBoard field.
     */
    private final byte[] originalBoard;
    /**
     * board field.
     */
    private byte[] board;
    /**
     * selectedPiece field.
     */
    private byte selectedPiece = Piece.WHITE_PAWN;
    /**
     * sideBox field.
     */
    private final JComboBox<String> sideBox;
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
    private final String enPassant;
    /**
     * halfmove field.
     */
    private final String halfmove;
    /**
     * fullmove field.
     */
    private final String fullmove;
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
    private final List<javax.swing.JButton> actionButtons;
    /**
     * adjustingCastling field.
     */
    private boolean adjustingCastling = false;

    /**
     * BoardEditorDialog method.
     *
     * @param owner parameter.
     */
    BoardEditorDialog(GuiWindowHistory owner) {
        super(owner.frame, "Board Editor", true);
        this.owner = owner;
        String[] parts = owner.position.toString().split(" ");
        String side = parts.length > 1 ? parts[1] : "w";
        String castling = parts.length > 2 ? parts[2] : "-";
        enPassant = parts.length > 3 ? parts[3] : "-";
        halfmove = parts.length > 4 ? parts[4] : "0";
        fullmove = parts.length > 5 ? parts[5] : "1";

        board = owner.position.getBoard();
        originalBoard = Arrays.copyOf(board, board.length);
        sideBox = new JComboBox<>(new String[]{"White", "Black"});
        owner.combos.add(sideBox);
        sideBox.setSelectedIndex("b".equals(side) ? 1 : 0);
        sideBox.addActionListener(e -> updateLegality());
        editorBoard = new BoardEditorShared.EditorBoardPanel(owner, () -> board, () -> selectedPiece, this::updateLegality);
        JPanel palettePanel = buildPalettePanel();
        List<BoardEditorShared.ActionSpec> actions = Arrays.asList(
                new BoardEditorShared.ActionSpec("Apply", e -> applyEditor()),
                new BoardEditorShared.ActionSpec("Reset", e -> resetBoard()),
                new BoardEditorShared.ActionSpec("Clear", e -> clearBoard()),
                new BoardEditorShared.ActionSpec("Cancel", e -> dispose()));
        BoardEditorShared.Layout layout = BoardEditorShared.buildLayout(
                owner,
                editorBoard,
                palettePanel,
                selectedPiece,
                sideBox,
                BorderLayout.CENTER,
                castling.contains("K"),
                castling.contains("Q"),
                castling.contains("k"),
                castling.contains("q"),
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
        setContentPane(editorRoot);

        castleK.addActionListener(e -> updateLegality());
        castleQ.addActionListener(e -> updateLegality());
        castlek.addActionListener(e -> updateLegality());
        castleq.addActionListener(e -> updateLegality());

        owner.applyTheme();
        updatePaletteStyles();
        updateLegality();

        pack();
        setLocationRelativeTo(owner.frame);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                owner.editorDialog = null;
            }
        });
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
            javax.swing.JOptionPane.showMessageDialog(this, msg, "Board Editor",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        owner.applyFen(fen);
        dispose();
    }

    /**
     * buildFen method.
     *
     * @return return value.
     */
    private String buildFen() {
        String placement = owner.buildPiecePlacement(board);
        String side = sideBox.getSelectedIndex() == 1 ? "b" : "w";
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
     * applyDialogTheme method.
     */
    void applyDialogTheme() {
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
