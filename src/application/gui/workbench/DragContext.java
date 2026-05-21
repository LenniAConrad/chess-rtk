package application.gui.workbench;

/**
 * Context handed to a drag-start filter.
 *
 * @param square origin square
 * @param piece piece on the origin square
 * @param fen current FEN
 */
record DragContext(byte square, byte piece, String fen) { }
