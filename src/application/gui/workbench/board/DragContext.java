package application.gui.workbench.board;

/**
 * Context handed to a drag-start filter.
 *
 * @param square origin square
 * @param piece piece on the origin square
 * @param fen current FEN
 */
public record DragContext(byte square, byte piece, String fen) { }
