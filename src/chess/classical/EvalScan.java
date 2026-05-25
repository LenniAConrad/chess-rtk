package chess.classical;

import static chess.classical.Wdl.*;

import chess.core.Bits;
import chess.core.MoveGenerator;
import chess.core.Piece;
import chess.core.Position;

/**
 * Accumulates material and PST-derived signals during a board scan.
 */
final class EvalScan {

    /**
     * White material total in centipawns (kings excluded).
     */
    int whiteMaterial;

    /**
     * Black material total in centipawns (kings excluded).
     */
    int blackMaterial;

    /**
     * PST-derived score from White's perspective.
     */
    int score;

    /**
     * Number of White bishops on the board.
     */
    int whiteBishops;

    /**
     * Number of Black bishops on the board.
     */
    int blackBishops;

    /**
     * Number of White pawns on the board.
     */
    int whitePawns;

    /**
     * Number of Black pawns on the board.
     */
    int blackPawns;
}
