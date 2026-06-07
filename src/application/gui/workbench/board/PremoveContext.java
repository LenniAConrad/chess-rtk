package application.gui.workbench.board;

/**
 * Context handed to a premove handler.
 *
 * @param fromSquare origin square
 * @param toSquare target square
 * @param piece piece on the origin square
 * @param fen FEN before the premove was queued
 * @param tentativeMove from/to move, using queen promotion when the gesture
 *        reaches the back rank
 */
public record PremoveContext(byte fromSquare, byte toSquare, byte piece, String fen, short tentativeMove) { }
