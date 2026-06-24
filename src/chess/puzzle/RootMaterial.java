package chess.puzzle;



/**
 * Root material summary used by tree-evidence heuristics.
 *
 * @param nonKingPieceCount number of non-king pieces on the board
 * @param nonPawnPieceCount number of non-pawn, non-king pieces on the board
 * @param nonKingMaterialCp material value of all non-king pieces
 */
record RootMaterial(int nonKingPieceCount, int nonPawnPieceCount, int nonKingMaterialCp) {
    /**
     * Normalizes material counters.
     *
     * @param nonKingPieceCount number of non-king pieces
     * @param nonPawnPieceCount number of non-pawn pieces
     * @param nonKingMaterialCp non-king material in centipawns
     */
    RootMaterial {
        nonKingPieceCount = Math.max(0, nonKingPieceCount);
        nonPawnPieceCount = Math.max(0, nonPawnPieceCount);
        nonKingMaterialCp = Math.max(0, nonKingMaterialCp);
    }
}
