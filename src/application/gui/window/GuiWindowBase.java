package application.gui.window;


import java.awt.Color;


import chess.struct.Game;

/**
 * Shared GUI constants for the window stack.
 *
 * Centralizes fen defaults, promotion encoding helpers, and board-color/piece scaling constants so each derived window implementation stays consistent with the shared theme.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
class GuiWindowBase {

	/**
	 * Default starting FEN used when none is provided.
	 */
	protected static final String DEFAULT_FEN = Game.STANDARD_START_FEN;
	/**
	 * Promotion encodings matching PawnMove handlers.
	 */
	protected static final byte PROMOTION_QUEEN = 4;
	/**
	 * PROMOTION_ROOK field.
	 */
	protected static final byte PROMOTION_ROOK = 3;
	/**
	 * PROMOTION_BISHOP field.
	 */
	protected static final byte PROMOTION_BISHOP = 2;
	/**
	 * PROMOTION_KNIGHT field.
	 */
	protected static final byte PROMOTION_KNIGHT = 1;
	/**
	 * PROMOTION_NONE field.
	 */
	protected static final byte PROMOTION_NONE = 0;
	/**
	 * Base board colors that mimic lichess’s palette.
	 */
	protected static final Color LICHESS_LIGHT = new Color(238, 223, 199);
	/**
	 * Constructor.
	 */
	protected static final Color LICHESS_DARK = new Color(181, 140, 94);
	/**
	 * Uniform piece scaling used when drawing piece images.
	 */
	protected static final float PIECE_SCALE = 0.90f;

}
