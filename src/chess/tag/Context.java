package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.Objects;

import chess.classical.Wdl;
import chess.core.MoveList;
import chess.core.Position;

/**
 * Shared per-position state for tag detectors.
 * <p>
 * The context owns cached values that are expensive or noisy to recompute
 * across detectors. Existing detectors still receive the position directly in
 * many places, but new detector modules should take their shared inputs from
 * this object.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
final class Context {

    /**
     * The position being tagged.
     */
    final Position position;

    /**
     * Cached board array for the root position.
     */
    private byte[] board;

    /**
     * Cached legal moves for the root side to move.
     */
    private MoveList legalMoves;

    /**
     * The centipawn evaluation from White's perspective.
     */
    Integer evalCpWhite;

    /**
     * The WDL snapshot used for difficulty tagging.
     */
    Wdl wdl;

    /**
     * The center-control label extracted from center analysis.
     */
    String centerControl;

    /**
     * The space-advantage label extracted from center analysis.
     */
    String spaceAdvantage;

    /**
     * Number of forcing legal moves for the side to move (checks, captures,
     * or promotions). Populated by {@link MoveFacts}.
     */
    int forcingMovesCount;

    /**
     * Whether White has any promotion threats.
     */
    boolean hasThreatWhite;

    /**
     * Whether Black has any promotion threats.
     */
    boolean hasThreatBlack;

    /**
     * Creates a tagging context for the given position.
     *
     * @param position the position being tagged
     */
    Context(Position position) {
        this.position = Objects.requireNonNull(position, POSITION);
    }

    /**
     * Returns the root board snapshot.
     *
     * @return the board array for the root position
     */
    byte[] board() {
        if (board == null) {
            board = position.getBoard();
        }
        return board;
    }

    /**
     * Returns legal moves for the root side to move.
     *
     * @return cached legal moves
     */
    MoveList legalMoves() {
        if (legalMoves == null) {
            legalMoves = position.legalMoves();
        }
        return legalMoves;
    }
}
