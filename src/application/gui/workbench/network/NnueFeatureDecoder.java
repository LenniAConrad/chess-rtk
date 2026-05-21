package application.gui.workbench.network;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

import chess.nn.nnue.FeatureEncoder;

/**
 * Decodes NNUE HalfKP feature identifiers into board-facing labels.
 */
public final class NnueFeatureDecoder {

    /**
     * Utility class.
     */
    private NnueFeatureDecoder() {
    }

    /**
     * Decodes a half-KP feature index into a human-friendly board-square
     * summary. The feature encoder stores squares in perspective-oriented
     * coordinates, so black-perspective labels are mirrored back to the board
     * before display.
     *
     * @param featureIndex sparse feature index
     * @param whitePerspective true when the feature was encoded from White's
     *     perspective
     * @return summary like "Kc4 / Nf6"
     */
    public static String decodeHalfKP(int featureIndex, boolean whitePerspective) {
        HalfKpFeature feature = decodeHalfKpFeature(featureIndex, whitePerspective);
        if (!feature.valid) {
            return "invalid";
        }
        char[] codes = { 'P', 'N', 'B', 'R', 'Q', 'p', 'n', 'b', 'r', 'q' };
        char pieceChar = feature.pieceCode < codes.length ? codes[feature.pieceCode] : '?';
        return "K" + TensorViz.squareLabel(feature.kingSquare)
                + " / " + pieceChar + TensorViz.squareLabel(feature.pieceSquare);
    }

    /**
     * Decodes feature components and maps oriented squares back to board
     * coordinates for display.
     *
     * @param featureIndex sparse feature index
     * @param whitePerspective true for White perspective, false for Black
     * @return decoded feature parts
     */
    public static HalfKpFeature decodeHalfKpFeature(int featureIndex, boolean whitePerspective) {
        if (featureIndex < 0 || featureIndex >= FeatureEncoder.FEATURE_COUNT) {
    return new HalfKpFeature(0, 0, 0, false);
        }
        int pieceSquare = featureIndex % FeatureEncoder.SQUARES;
        int packed = featureIndex / FeatureEncoder.SQUARES;
        int pieceCode = packed % FeatureEncoder.PIECE_PLANES;
        int kingSquare = packed / FeatureEncoder.PIECE_PLANES;
    return new HalfKpFeature(
                displaySquare(kingSquare, whitePerspective),
                pieceCode,
                displaySquare(pieceSquare, whitePerspective),
                true);
    }

    /**
     * Converts a perspective-oriented square back to board coordinates.
     *
     * @param square oriented square
     * @param whitePerspective perspective flag
     * @return display square
     */
    public static int displaySquare(int square, boolean whitePerspective) {
        return whitePerspective ? square : (square ^ 56);
    }
}
