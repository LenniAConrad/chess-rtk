package testing;



import chess.uci.Analysis;

/**
 * Compact indexed puzzle record.
 */
record PuzzleNode(
        String fen,
        String engine,
        long created,
        long parentSignature,
        long positionSignature,
        long afterBestSignature,
        short solutionMove,
        Analysis analysis) {
}
