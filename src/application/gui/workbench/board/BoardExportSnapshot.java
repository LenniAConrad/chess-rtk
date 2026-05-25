package application.gui.workbench.board;

import chess.core.Field;
import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * Immutable board state used by render-based board exports.
 *
 * @param pieces board pieces in CRTK square order
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
 *
 * @author Lennart A. Conrad
 */
record BoardExportSnapshot(
        byte[] pieces,
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
        boolean showSuggestedMoveArrow) {

    /**
     * Normalizes nullable collections and mutable arrays.
     */
    BoardExportSnapshot {
        pieces = pieces == null ? new byte[64] : pieces.clone();
        legalTargets = legalTargets == null ? new byte[0] : legalTargets.clone();
        captureTargets = captureTargets == null ? new byte[0] : captureTargets.clone();
        squareHighlights = squareHighlights == null ? Map.of() : Map.copyOf(squareHighlights);
        boardMarkups = boardMarkups == null ? List.of() : List.copyOf(boardMarkups);
    }
}
