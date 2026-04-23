package application.gui.window;

import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;
import chess.core.Field;
import chess.core.Piece;
import chess.images.assets.Pictures;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Shared board editor components and helpers used by pane and dialog variants.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
final class BoardEditorShared {

    /**
     * ICON_SIZE constant.
     */
    static final int ICON_SIZE = 32;

    /**
     * BoardEditorShared method.
     */
    private BoardEditorShared() {
    }

    /**
     * buildLayout method.
     *
     * @param owner parameter.
     * @param boardPanel parameter.
     * @param palettePanel parameter.
     * @param selectedPiece parameter.
     * @param sideControl parameter.
     * @param sideConstraint parameter.
     * @param whiteKingCastle parameter.
     * @param whiteQueenCastle parameter.
     * @param blackKingCastle parameter.
     * @param blackQueenCastle parameter.
     * @param actionSpecs parameter.
     * @return return value.
     */
    static Layout buildLayout(
            GuiWindowHistory owner,
            JPanel boardPanel,
            JPanel palettePanel,
            byte selectedPiece,
            JComponent sideControl,
            Object sideConstraint,
            boolean whiteKingCastle,
            boolean whiteQueenCastle,
            boolean blackKingCastle,
            boolean blackQueenCastle,
            List<ActionSpec> actionSpecs) {
        GradientPanel root = new GradientPanel(owner.theme.backgroundTop(), owner.theme.backgroundBottom());
        root.setLayout(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        RoundedPanel boardCard = owner.createCard("Board");
        boardCard.setContent(boardPanel);
        root.add(boardCard, BorderLayout.CENTER);

        RoundedPanel paletteCard = owner.createCard("Pieces");
        paletteCard.setContent(palettePanel);

        RoundedPanel optionsCard = owner.createCard("Options");
        JPanel optionsBody = new JPanel();
        optionsBody.setOpaque(false);
        optionsBody.setLayout(new BoxLayout(optionsBody, BoxLayout.Y_AXIS));

        JLabel selectedLabel = owner.mutedLabel("Selected: " + pieceName(selectedPiece));
        optionsBody.add(selectedLabel);
        optionsBody.add(Box.createVerticalStrut(8));

        JPanel sideRow = new JPanel(new BorderLayout(8, 8));
        sideRow.setOpaque(false);
        sideRow.add(owner.mutedLabel("Side to move"), BorderLayout.WEST);
        sideRow.add(sideControl, sideConstraint == null ? BorderLayout.CENTER : sideConstraint);
        optionsBody.add(sideRow);
        optionsBody.add(Box.createVerticalStrut(8));

        JPanel castleRow = new JPanel(new GridLayout(2, 2, 8, 8));
        castleRow.setOpaque(false);
        JCheckBox castleK = owner.themedCheckbox("White K", whiteKingCastle, null);
        JCheckBox castleQ = owner.themedCheckbox("White Q", whiteQueenCastle, null);
        JCheckBox castlek = owner.themedCheckbox("Black K", blackKingCastle, null);
        JCheckBox castleq = owner.themedCheckbox("Black Q", blackQueenCastle, null);
        castleRow.add(castleK);
        castleRow.add(castleQ);
        castleRow.add(castlek);
        castleRow.add(castleq);
        optionsBody.add(owner.mutedLabel("Castling rights"));
        optionsBody.add(Box.createVerticalStrut(4));
        optionsBody.add(castleRow);
        optionsBody.add(Box.createVerticalStrut(8));
        optionsBody.add(owner.mutedLabel("Tip: Right-click a square to clear it."));
        optionsBody.add(Box.createVerticalStrut(8));

        JLabel legalityLabel = new JLabel("Position: -");
        legalityLabel.setFont(owner.theme.bodyFont());
        legalityLabel.setVerticalAlignment(SwingConstants.TOP);
        optionsBody.add(legalityLabel);

        optionsCard.setContent(optionsBody);
        int optionsWidth = optionsCard.getPreferredSize().width;
        int paletteWidth = paletteCard.getPreferredSize().width;
        int rightWidth = Math.max(optionsWidth, paletteWidth);
        int legalityWrapWidth = Math.max(200, rightWidth - 24);
        /**
         * configureLegalityLabelSize method.
         *
         * @param owner parameter.
         * @param legalityLabel parameter.
         * @param legalityWrapWidth parameter.
         */
        configureLegalityLabelSize(owner, legalityLabel, legalityWrapWidth);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(paletteCard);
        right.add(Box.createVerticalStrut(8));
        right.add(optionsCard);
        Dimension rightSize = new Dimension(rightWidth, right.getPreferredSize().height);
        right.setPreferredSize(rightSize);
        right.setMinimumSize(new Dimension(rightWidth, 10));
        right.setMaximumSize(new Dimension(rightWidth, Integer.MAX_VALUE));
        paletteCard.setPreferredSize(new Dimension(rightWidth, paletteCard.getPreferredSize().height));
        optionsCard.setPreferredSize(new Dimension(rightWidth, optionsCard.getPreferredSize().height));
        root.add(right, BorderLayout.EAST);

        JPanel actionRow = new JPanel(new GridLayout(1, 0, 8, 8));
        actionRow.setOpaque(false);
        List<JButton> actionButtons = new ArrayList<>();
        for (ActionSpec actionSpec : actionSpecs) {
            JButton button = owner.themedButton(actionSpec.label(), actionSpec.listener());
            actionButtons.add(button);
            actionRow.add(button);
        }
        /**
         * applyActionButtonStyles method.
         *
         * @param owner parameter.
         * @param actionButtons parameter.
         */
        applyActionButtonStyles(owner, actionButtons);
        root.add(actionRow, BorderLayout.SOUTH);
        return new Layout(root, selectedLabel, legalityLabel, castleK, castleQ, castlek, castleq, legalityWrapWidth,
                actionButtons);
    }

    /**
     * buildPalettePanel method.
     *
     * @param owner parameter.
     * @param paletteButtons parameter.
     * @param onSelection parameter.
     * @return return value.
     */
    static JPanel buildPalettePanel(
            GuiWindowHistory owner,
            List<PaletteButton> paletteButtons,
            Consumer<Byte> onSelection) {
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
        grid.setOpaque(false);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Empty parameter.
         * @param null parameter.
         * @param PieceEMPTY parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "Empty", null, Piece.EMPTY);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param King parameter.
         * @param PicturesWhiteKing parameter.
         * @param PieceWHITE_KING parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "White King", Pictures.WhiteKing, Piece.WHITE_KING);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Queen parameter.
         * @param PicturesWhiteQueen parameter.
         * @param PieceWHITE_QUEEN parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "White Queen", Pictures.WhiteQueen, Piece.WHITE_QUEEN);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Rook parameter.
         * @param PicturesWhiteRook parameter.
         * @param PieceWHITE_ROOK parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "White Rook", Pictures.WhiteRook, Piece.WHITE_ROOK);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Bishop parameter.
         * @param PicturesWhiteBishop parameter.
         * @param PieceWHITE_BISHOP parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "White Bishop", Pictures.WhiteBishop, Piece.WHITE_BISHOP);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Knight parameter.
         * @param PicturesWhiteKnight parameter.
         * @param PieceWHITE_KNIGHT parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "White Knight", Pictures.WhiteKnight, Piece.WHITE_KNIGHT);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Pawn parameter.
         * @param PicturesWhitePawn parameter.
         * @param PieceWHITE_PAWN parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "White Pawn", Pictures.WhitePawn, Piece.WHITE_PAWN);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param King parameter.
         * @param PicturesBlackKing parameter.
         * @param PieceBLACK_KING parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "Black King", Pictures.BlackKing, Piece.BLACK_KING);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Queen parameter.
         * @param PicturesBlackQueen parameter.
         * @param PieceBLACK_QUEEN parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "Black Queen", Pictures.BlackQueen, Piece.BLACK_QUEEN);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Rook parameter.
         * @param PicturesBlackRook parameter.
         * @param PieceBLACK_ROOK parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "Black Rook", Pictures.BlackRook, Piece.BLACK_ROOK);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Bishop parameter.
         * @param PicturesBlackBishop parameter.
         * @param PieceBLACK_BISHOP parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "Black Bishop", Pictures.BlackBishop, Piece.BLACK_BISHOP);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Knight parameter.
         * @param PicturesBlackKnight parameter.
         * @param PieceBLACK_KNIGHT parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "Black Knight", Pictures.BlackKnight, Piece.BLACK_KNIGHT);
        /**
         * addPaletteButton method.
         *
         * @param owner parameter.
         * @param grid parameter.
         * @param paletteButtons parameter.
         * @param onSelection parameter.
         * @param Pawn parameter.
         * @param PicturesBlackPawn parameter.
         * @param PieceBLACK_PAWN parameter.
         */
        addPaletteButton(grid, paletteButtons, onSelection, "Black Pawn", Pictures.BlackPawn, Piece.BLACK_PAWN);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(grid, BorderLayout.NORTH);
        return panel;
    }

    /**
     * addPaletteButton method.
     *
     * @param grid parameter.
     * @param paletteButtons parameter.
     * @param onSelection parameter.
     * @param label parameter.
     * @param icon parameter.
     * @param piece parameter.
     */
    private static void addPaletteButton(
            JPanel grid,
            List<PaletteButton> paletteButtons,
            Consumer<Byte> onSelection,
            String label,
            BufferedImage icon,
            byte piece) {
        PaletteButton button = new PaletteButton(label, icon, piece);
        button.addActionListener(e -> onSelection.accept(button.piece()));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusable(false);
        grid.add(button);
        paletteButtons.add(button);
    }

    /**
     * applyPaletteStyles method.
     *
     * @param owner parameter.
     * @param paletteButtons parameter.
     * @param selectedPiece parameter.
     */
    static void applyPaletteStyles(GuiWindowHistory owner, List<PaletteButton> paletteButtons, byte selectedPiece) {
        for (PaletteButton button : paletteButtons) {
            boolean active = button.piece() == selectedPiece;
            Color bg = active ? owner.theme.accent() : owner.theme.surfaceAlt();
            Color fg = active ? owner.theme.accentText() : owner.theme.text();
            Color border = active ? owner.theme.accent() : owner.theme.border();
            button.setBackground(bg);
            button.setForeground(fg);
            button.setBorder(BorderFactory.createLineBorder(border, active ? 2 : 1));
            button.setFont(owner.theme.bodyFont());
        }
    }

    /**
     * applyActionButtonStyles method.
     *
     * @param owner parameter.
     * @param actionButtons parameter.
     */
    static void applyActionButtonStyles(GuiWindowHistory owner, List<JButton> actionButtons) {
        if (actionButtons == null || actionButtons.isEmpty()) {
            return;
        }
        float fontSize = Math.max(13f, owner.theme.bodyFont().getSize2D() * 1.05f);
        for (JButton button : actionButtons) {
            if (button == null) {
                continue;
            }
            String label = button.getText() == null ? "" : button.getText();
            boolean primary = "Apply".equalsIgnoreCase(label);
            Color bg = primary ? owner.theme.accent() : owner.theme.surfaceAlt();
            Color fg = primary ? owner.theme.accentText() : owner.theme.textStrong();
            Color border = primary ? owner.theme.accent() : owner.theme.border();
            button.setFont(owner.theme.bodyFont().deriveFont(java.awt.Font.BOLD, fontSize));
            button.setBackground(bg);
            button.setForeground(fg);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setBorder(BorderFactory.createLineBorder(border, primary ? 2 : 1));
            button.setMargin(new Insets(8, 12, 8, 12));
            int minHeight = Math.max(34, Math.round(34f * owner.uiScale));
            Dimension pref = button.getPreferredSize();
            button.setPreferredSize(new Dimension(pref.width, Math.max(pref.height, minHeight)));
        }
    }

    /**
     * buildCastling method.
     *
     * @param castleK parameter.
     * @param castleQ parameter.
     * @param castlek parameter.
     * @param castleq parameter.
     * @return return value.
     */
    static String buildCastling(JCheckBox castleK, JCheckBox castleQ, JCheckBox castlek, JCheckBox castleq) {
        StringBuilder sb = new StringBuilder();
        if (castleK != null && castleK.isSelected()) {
            sb.append('K');
        }
        if (castleQ != null && castleQ.isSelected()) {
            sb.append('Q');
        }
        if (castlek != null && castlek.isSelected()) {
            sb.append('k');
        }
        if (castleq != null && castleq.isSelected()) {
            sb.append('q');
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    /**
     * pieceName method.
     *
     * @param piece parameter.
     * @return return value.
     */
    static String pieceName(byte piece) {
        switch (piece) {
            case Piece.WHITE_KING:
                return "White King";
            case Piece.WHITE_QUEEN:
                return "White Queen";
            case Piece.WHITE_ROOK:
                return "White Rook";
            case Piece.WHITE_BISHOP:
                return "White Bishop";
            case Piece.WHITE_KNIGHT:
                return "White Knight";
            case Piece.WHITE_PAWN:
                return "White Pawn";
            case Piece.BLACK_KING:
                return "Black King";
            case Piece.BLACK_QUEEN:
                return "Black Queen";
            case Piece.BLACK_ROOK:
                return "Black Rook";
            case Piece.BLACK_BISHOP:
                return "Black Bishop";
            case Piece.BLACK_KNIGHT:
                return "Black Knight";
            case Piece.BLACK_PAWN:
                return "Black Pawn";
            default:
                return "Empty";
        }
    }

    /**
     * pieceIcon method.
     *
     * @param image parameter.
     * @return return value.
     */
    static ImageIcon pieceIcon(BufferedImage image) {
        if (image == null) {
            return null;
        }
        Image scaled = image.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    /**
     * configureLegalityLabelSize method.
     *
     * @param owner parameter.
     * @param legalityLabel parameter.
     * @param legalityWrapWidth parameter.
     */
    static void configureLegalityLabelSize(GuiWindowHistory owner, JLabel legalityLabel, int legalityWrapWidth) {
        if (legalityLabel == null) {
            return;
        }
        java.awt.Font font = owner.theme.bodyFont();
        legalityLabel.setFont(font);
        java.awt.FontMetrics fm = legalityLabel.getFontMetrics(font);
        int lineHeight = fm.getHeight();
        int height = Math.max(lineHeight * 3, lineHeight + 8);
        Dimension size = new Dimension(legalityWrapWidth, height);
        legalityLabel.setMinimumSize(size);
        legalityLabel.setPreferredSize(size);
        legalityLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        legalityLabel.revalidate();
    }

    /**
     * setLegalityStatus method.
     *
     * @param owner parameter.
     * @param legalityLabel parameter.
     * @param legalityWrapWidth parameter.
     * @param ok parameter.
     * @param text parameter.
     */
    static void setLegalityStatus(
            GuiWindowHistory owner,
            JLabel legalityLabel,
            int legalityWrapWidth,
            boolean ok,
            String text) {
        if (legalityLabel == null) {
            return;
        }
        Color color = ok ? owner.theme.accent()
                : (owner.lightMode ? new Color(180, 70, 70) : new Color(200, 90, 90));
        legalityLabel.setForeground(color);
        legalityLabel.setText(wrapHtml(shorten(text, 220), legalityWrapWidth));
    }

    /**
     * updateCastlingAvailability method.
     *
     * @param board parameter.
     * @param castleK parameter.
     * @param castleQ parameter.
     * @param castlek parameter.
     * @param castleq parameter.
     */
    static void updateCastlingAvailability(
            byte[] board,
            JCheckBox castleK,
            JCheckBox castleQ,
            JCheckBox castlek,
            JCheckBox castleq) {
        if (board == null) {
            return;
        }
        /**
         * whiteKing field.
         */
        boolean whiteKing = board[Field.E1] == Piece.WHITE_KING;
        /**
         * whiteRookH field.
         */
        boolean whiteRookH = board[Field.H1] == Piece.WHITE_ROOK;
        /**
         * whiteRookA field.
         */
        boolean whiteRookA = board[Field.A1] == Piece.WHITE_ROOK;
        /**
         * blackKing field.
         */
        boolean blackKing = board[Field.E8] == Piece.BLACK_KING;
        /**
         * blackRookH field.
         */
        boolean blackRookH = board[Field.H8] == Piece.BLACK_ROOK;
        /**
         * blackRookA field.
         */
        boolean blackRookA = board[Field.A8] == Piece.BLACK_ROOK;
        /**
         * updateCastlingToggle method.
         *
         * @param castleK parameter.
         * @param whiteRookH parameter.
         */
        updateCastlingToggle(castleK, whiteKing && whiteRookH);
        /**
         * updateCastlingToggle method.
         *
         * @param castleQ parameter.
         * @param whiteRookA parameter.
         */
        updateCastlingToggle(castleQ, whiteKing && whiteRookA);
        /**
         * updateCastlingToggle method.
         *
         * @param castlek parameter.
         * @param blackRookH parameter.
         */
        updateCastlingToggle(castlek, blackKing && blackRookH);
        /**
         * updateCastlingToggle method.
         *
         * @param castleq parameter.
         * @param blackRookA parameter.
         */
        updateCastlingToggle(castleq, blackKing && blackRookA);
    }

    /**
     * updateCastlingToggle method.
     *
     * @param toggle parameter.
     * @param available parameter.
     */
    private static void updateCastlingToggle(JCheckBox toggle, boolean available) {
        if (toggle == null) {
            return;
        }
        toggle.setEnabled(available);
        if (!available && toggle.isSelected()) {
            toggle.setSelected(false);
        }
    }

    /**
     * shorten method.
     *
     * @param text parameter.
     * @param max parameter.
     * @return return value.
     */
    private static String shorten(String text, int max) {
        if (text == null || text.length() <= max) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    /**
     * wrapHtml method.
     *
     * @param text parameter.
     * @param width parameter.
     * @return return value.
     */
    private static String wrapHtml(String text, int width) {
        return "<html><div style='width:" + width + "px;'>" + escapeHtml(text) + "</div></html>";
    }

    /**
     * escapeHtml method.
     *
     * @param text parameter.
     * @return return value.
     */
    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * ActionSpec record.
     *
     * Provides record behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    record ActionSpec(
        /**
         * Stores the label.
         */
        String label,
        /**
         * Stores the listener.
         */
        ActionListener listener
    ) {
    }

    /**
     * Layout record.
     *
     * Provides record behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    record Layout(
        /**
         * Stores the root.
         */
        GradientPanel root,
        /**
         * Stores the selected label.
         */
        JLabel selectedLabel,
        /**
         * Stores the legality label.
         */
        JLabel legalityLabel,
        /**
         * Stores the castle k.
         */
        JCheckBox castleK,
        /**
         * Stores the castle q.
         */
        JCheckBox castleQ,
        /**
         * Stores the castlek.
         */
        JCheckBox castlek,
        /**
         * Stores the castleq.
         */
        JCheckBox castleq,
        /**
         * Stores the legality wrap width.
         */
        int legalityWrapWidth,
        /**
         * Stores the action buttons.
         */
        List<JButton> actionButtons
    ) {
    }

    /**
     * PaletteButton class.
     *
     * Provides class behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    static final class PaletteButton extends JButton {

				/**
		 * Serialization version identifier.
		 */
@java.io.Serial
		private static final long serialVersionUID = 1L;
        /**
         * piece field.
         */
        private final byte piece;

        /**
         * PaletteButton method.
         *
         * @param label parameter.
         * @param icon parameter.
         * @param piece parameter.
         */
        PaletteButton(String label, BufferedImage icon, byte piece) {
            super(label, icon == null ? null : pieceIcon(icon));
            this.piece = piece;
            setToolTipText(label);
            if (icon != null) {
                setText("");
            }
            setFocusPainted(false);
            setOpaque(true);
            setContentAreaFilled(true);
            setBorderPainted(true);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setPreferredSize(new Dimension(48, 48));
        }

        /**
         * piece method.
         *
         * @return return value.
         */
        byte piece() {
            return piece;
        }
    }

        /**
     * Defines byte supplier behavior.
     */
@FunctionalInterface
    /**
     * ByteSupplier interface.
     *
     * Provides interface behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    interface ByteSupplier {
        /**
         * getAsByte method.
         *
         * @return return value.
         */
        byte getAsByte();
    }

    /**
     * EditorBoardPanel class.
     *
     * Provides class behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    static final class EditorBoardPanel extends JPanel {

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
         * boardSupplier field.
         */
        private final transient Supplier<byte[]> boardSupplier;
        /**
         * selectedPieceSupplier field.
         */
        private final transient ByteSupplier selectedPieceSupplier;
        /**
         * onBoardChanged field.
         */
        private final transient Runnable onBoardChanged;
        /**
         * boardX field.
         */
        private int boardX;
        /**
         * boardY field.
         */
        private int boardY;
        /**
         * boardSize field.
         */
        private int boardSize;
        /**
         * tileSize field.
         */
        private int tileSize;

        /**
         * EditorBoardPanel method.
         *
         * @param owner parameter.
         * @param boardSupplier parameter.
         * @param selectedPieceSupplier parameter.
         * @param onBoardChanged parameter.
         */
        EditorBoardPanel(
                GuiWindowHistory owner,
                Supplier<byte[]> boardSupplier,
                ByteSupplier selectedPieceSupplier,
                Runnable onBoardChanged) {
            this.owner = owner;
            this.boardSupplier = boardSupplier;
            this.selectedPieceSupplier = selectedPieceSupplier;
            this.onBoardChanged = onBoardChanged;
            /**
             * setOpaque method.
             *
             * @param false parameter.
             */
            setOpaque(false);
            /**
             * setPreferredSize method.
             *
             * @param 520 parameter.
             */
            setPreferredSize(new Dimension(520, 520));
            MouseAdapter mouseHandler = new MouseAdapter() {
                                /**
                 * Handles mouse pressed.
                 * @param e e value
                 */
@Override
                /**
                 * mousePressed method.
                 *
                 * @param e parameter.
                 */
                public void mousePressed(MouseEvent e) {
                    handlePaint(e);
                }

                                /**
                 * Handles mouse dragged.
                 * @param e e value
                 */
@Override
                /**
                 * mouseDragged method.
                 *
                 * @param e parameter.
                 */
                public void mouseDragged(MouseEvent e) {
                    handleDrag(e);
                }
            };
            /**
             * addMouseListener method.
             *
             * @param mouseHandler parameter.
             */
            addMouseListener(mouseHandler);
            /**
             * addMouseMotionListener method.
             *
             * @param mouseHandler parameter.
             */
            addMouseMotionListener(mouseHandler);
        }

        /**
         * handlePaint method.
         *
         * @param e parameter.
         */
        private void handlePaint(MouseEvent e) {
            byte[] board = boardSupplier.get();
            if (board == null) {
                return;
            }
            byte square = squareFromPoint(e.getX(), e.getY());
            if (square == Field.NO_SQUARE) {
                return;
            }
            byte piece = javax.swing.SwingUtilities.isRightMouseButton(e)
                    ? Piece.EMPTY
                    : selectedPieceSupplier.getAsByte();
            board[square] = piece;
            repaint();
            onBoardChanged.run();
        }

        /**
         * handleDrag method.
         *
         * @param e parameter.
         */
        private void handleDrag(MouseEvent e) {
            if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            byte[] board = boardSupplier.get();
            if (board == null) {
                return;
            }
            byte square = squareFromPoint(e.getX(), e.getY());
            if (square == Field.NO_SQUARE) {
                return;
            }
            board[square] = selectedPieceSupplier.getAsByte();
            repaint();
            onBoardChanged.run();
        }

        /**
         * squareFromPoint method.
         *
         * @param x parameter.
         * @param y parameter.
         * @return return value.
         */
        private byte squareFromPoint(int x, int y) {
            if (boardSize <= 0 || tileSize <= 0) {
                return Field.NO_SQUARE;
            }
            int relX = x - boardX;
            int relY = y - boardY;
            if (relX < 0 || relY < 0 || relX >= boardSize || relY >= boardSize) {
                return Field.NO_SQUARE;
            }
            int file = Math.min(7, relX / tileSize);
            int rank = Math.min(7, relY / tileSize);
            int boardFile = owner.whiteDown ? file : 7 - file;
            int boardRank = owner.whiteDown ? 7 - rank : rank;
            return (byte) Field.toIndex(boardFile, boardRank);
        }

                /**
         * Handles paint component.
         * @param g g value
         */
@Override
        /**
         * paintComponent method.
         *
         * @param g parameter.
         */
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            byte[] board = boardSupplier.get();
            if (board == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            owner.applyRenderHints(g2);
            int size = Math.min(getWidth(), getHeight());
            if (size <= 0) {
                g2.dispose();
                return;
            }
            int tile = size / 8;
            boardSize = tile * 8;
            tileSize = tile;
            boardX = (getWidth() - boardSize) / 2;
            boardY = (getHeight() - boardSize) / 2;

            Color light = owner.boardColor(GuiWindowBase.LICHESS_LIGHT);
            Color dark = owner.boardColor(GuiWindowBase.LICHESS_DARK);
            for (int screenRank = 0; screenRank < 8; screenRank++) {
                for (int screenFile = 0; screenFile < 8; screenFile++) {
                    int boardFile = owner.whiteDown ? screenFile : 7 - screenFile;
                    int boardRank = owner.whiteDown ? 7 - screenRank : screenRank;
                    boolean isLight = ((boardFile + boardRank) & 1) == 1;
                    g2.setColor(isLight ? light : dark);
                    g2.fillRect(boardX + screenFile * tile, boardY + screenRank * tile, tile, tile);
                }
            }

            int pieceSize = Math.round(tile * GuiWindowBase.PIECE_SCALE);
            int pad = (tile - pieceSize) / 2;
            for (int square = 0; square < board.length; square++) {
                byte piece = board[square];
                if (Piece.isEmpty(piece)) {
                    continue;
                }
                BufferedImage image = owner.pieceImage(piece);
                if (image == null) {
                    continue;
                }
                int file = Field.getX((byte) square);
                int rank = Field.getY((byte) square);
                int screenFile = owner.whiteDown ? file : 7 - file;
                int screenRank = owner.whiteDown ? 7 - rank : rank;
                int x = boardX + screenFile * tile + pad;
                int y = boardY + screenRank * tile + pad;
                g2.drawImage(image, x, y, pieceSize, pieceSize, null);
            }
            g2.dispose();
        }
    }
}
