package application.gui.workbench.board;

import chess.core.Position;
import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * Immutable board state used by render-based board exports.
 *
 * @param pieces board pieces in CRTK square order
 * @param position source position for stateful overlays, or null in setup mode
 * @param boardLightColor light-square color
 * @param boardDarkColor dark-square color
 * @param whiteDown true when white is rendered at the bottom
 * @param lastMove previous move to highlight
 * @param suggestedMove engine move to draw as an arrow
 * @param selectedSquare selected source square
 * @param legalTargets legal target squares for the selected piece
 * @param captureTargets subset of legal targets that are captures
 * @param checkedKingSquare checked king square, or {@link Field#NO_SQUARE}
 * @param squareHighlights custom square highlights
 * @param boardMarkups user board annotations
 * @param showNotation true to render board coordinates
 * @param showLegalMovePreview true to render legal target markers
 * @param showLastMoveHighlight true to render previous-move highlights
 * @param showSuggestedMoveArrow true to render the suggested-move arrow
 * @param showSpecialMoveHints true to render castling/en-passant hints
 *
 * @author Lennart A. Conrad
 */
record BoardExportSnapshot(
        byte[] pieces,
        Position position,
        Color boardLightColor,
        Color boardDarkColor,
        boolean whiteDown,
        short lastMove,
        short suggestedMove,
        byte selectedSquare,
        byte[] legalTargets,
        byte[] captureTargets,
        byte checkedKingSquare,
        Map<Byte, Color> squareHighlights,
        List<BoardMarkup> boardMarkups,
        boolean showNotation,
        boolean showLegalMovePreview,
        boolean showLastMoveHighlight,
        boolean showSuggestedMoveArrow,
        boolean showSpecialMoveHints) {

    /**
     * Normalizes nullable collections and mutable arrays.
     *
     * @param pieces piece image set
     * @param position chess position
     * @param boardLightColor light-square board color
     * @param boardDarkColor dark-square board color
     * @param whiteDown whether White is rendered at the bottom
     * @param lastMove last played move
     * @param suggestedMove engine suggested move
     * @param selectedSquare selected square, or -1 when absent
     * @param legalTargets legal destination squares
     * @param captureTargets legal capture target squares
     * @param checkedKingSquare checked king square, or -1 when absent
     * @param squareHighlights per-square highlight colors
     * @param boardMarkups drawn board annotations
     * @param showNotation whether coordinates are drawn
     * @param showLegalMovePreview whether to show legal move previews
     * @param showLastMoveHighlight whether to highlight the last move
     * @param showSuggestedMoveArrow whether to draw the suggested move arrow
     * @param showSpecialMoveHints whether special move hints are drawn
     */
    BoardExportSnapshot {
        pieces = pieces == null ? new byte[64] : pieces.clone();
        position = position == null ? null : position.copy();
        boardLightColor = boardLightColor == null ? application.gui.workbench.ui.Theme.BOARD_LIGHT : boardLightColor;
        boardDarkColor = boardDarkColor == null ? application.gui.workbench.ui.Theme.BOARD_DARK : boardDarkColor;
        legalTargets = legalTargets == null ? new byte[0] : legalTargets.clone();
        captureTargets = captureTargets == null ? new byte[0] : captureTargets.clone();
        squareHighlights = squareHighlights == null ? Map.of() : Map.copyOf(squareHighlights);
        boardMarkups = boardMarkups == null ? List.of() : List.copyOf(boardMarkups);
    }
}
