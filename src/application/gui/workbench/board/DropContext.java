package application.gui.workbench.board;


/**
 * Context handed to a drop resolver.
 *
 * @param fromSquare origin square
 * @param toSquare drop square ({@link Field#NO_SQUARE} when off-board)
 * @param piece piece being dropped
 * @param fen FEN before the drop
 * @param defaultMove first matching legal move, or no move
 */
public record DropContext(byte fromSquare, byte toSquare, byte piece, String fen, short defaultMove) { }
