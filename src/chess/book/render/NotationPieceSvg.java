package chess.book.render;

import chess.images.assets.shape.SvgShapes;

/**
 * Resolves neutral, outline-only SVGs for figurine SAN placeholders.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class NotationPieceSvg {

	/**
	 * White king figurine placeholder emitted by {@link MoveText}.
	 */
	private static final char KING = '\u2654';

	/**
	 * White queen figurine placeholder emitted by {@link MoveText}.
	 */
	private static final char QUEEN = '\u2655';

	/**
	 * White rook figurine placeholder emitted by {@link MoveText}.
	 */
	private static final char ROOK = '\u2656';

	/**
	 * White bishop figurine placeholder emitted by {@link MoveText}.
	 */
	private static final char BISHOP = '\u2657';

	/**
	 * White knight figurine placeholder emitted by {@link MoveText}.
	 */
	private static final char KNIGHT = '\u2658';

	/**
	 * White pawn figurine placeholder emitted by {@link MoveText}.
	 */
	private static final char PAWN = '\u2659';

	/**
	 * Black fill used by the embedded white-piece SVG outlines.
	 */
	private static final String BLACK_FILL = "fill=\"#000000\"";

	/**
	 * White fill used by the embedded white-piece SVG interiors.
	 */
	private static final String WHITE_FILL = "fill=\"#ffffff\"";

	/**
	 * SVG path attribute prefix used by the embedded piece sources.
	 */
	private static final String PATH_D_PREFIX = "<path d=\"";

	/**
	 * Neutral king notation SVG.
	 */
	private static final String KING_SVG = outlineSvg("King", SvgShapes.whiteKing());

	/**
	 * Neutral queen notation SVG.
	 */
	private static final String QUEEN_SVG = outlineSvg("Queen", SvgShapes.whiteQueen());

	/**
	 * Neutral rook notation SVG.
	 */
	private static final String ROOK_SVG = outlineSvg("Rook", SvgShapes.whiteRook());

	/**
	 * Neutral bishop notation SVG.
	 */
	private static final String BISHOP_SVG = outlineSvg("Bishop", SvgShapes.whiteBishop());

	/**
	 * Neutral knight notation SVG.
	 */
	private static final String KNIGHT_SVG = outlineSvg("Knight", SvgShapes.whiteKnight());

	/**
	 * Neutral pawn notation SVG.
	 */
	private static final String PAWN_SVG = outlineSvg("Pawn", SvgShapes.whitePawn());

	/**
	 * Utility class; prevent instantiation.
	 */
	private NotationPieceSvg() {
		// utility
	}

	/**
	 * Returns whether the character is a figurine placeholder produced by
	 * {@link MoveText}.
	 *
	 * @param ch character to inspect
	 * @return true when the character should render as an inline notation SVG
	 */
	public static boolean isPlaceholder(char ch) {
		return svg(ch) != null;
	}

	/**
	 * Resolves a neutral SVG for one figurine placeholder.
	 *
	 * @param ch placeholder character
	 * @return outline-only SVG source, or {@code null} when the character is ordinary text
	 */
	public static String svg(char ch) {
		return switch (ch) {
			case KING -> KING_SVG;
			case QUEEN -> QUEEN_SVG;
			case ROOK -> ROOK_SVG;
			case BISHOP -> BISHOP_SVG;
			case KNIGHT -> KNIGHT_SVG;
			case PAWN -> PAWN_SVG;
			default -> null;
		};
	}

	/**
	 * Builds an outline-only SVG from the embedded white-piece source.
	 *
	 * @param title accessibility title
	 * @param source embedded side-specific SVG
	 * @return outline-only notation SVG
	 */
	private static String outlineSvg(String title, String source) {
		String outer = pathAfterFill(source, BLACK_FILL);
		String inner = pathAfterFill(source, WHITE_FILL);
		return new StringBuilder(outer.length() + inner.length() + 240)
				.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 200\" role=\"img\"")
				.append(" aria-labelledby=\"title\">\n")
				.append("  <title id=\"title\">").append(title).append(" Notation</title>\n")
				.append("  <g shape-rendering=\"geometricPrecision\">\n")
				.append("    <g transform=\"translate(0.000000,200.000000) scale(0.100000,-0.100000)\">\n")
				.append("    <path fill=\"#000000\" fill-rule=\"evenodd\" stroke=\"none\" d=\"")
				.append(outer).append(' ').append(inner).append("\"/>\n")
				.append("    </g>\n")
				.append("  </g>\n")
				.append("</svg>\n")
				.toString();
	}

	/**
	 * Extracts the path following a specific fill declaration.
	 *
	 * @param svg source SVG
	 * @param fill fill declaration to locate
	 * @return path data
	 */
	private static String pathAfterFill(String svg, String fill) {
		int fillIndex = svg.indexOf(fill);
		if (fillIndex < 0) {
			throw new IllegalStateException("Missing notation SVG fill: " + fill);
		}
		int pathIndex = svg.indexOf(PATH_D_PREFIX, fillIndex);
		if (pathIndex < 0) {
			throw new IllegalStateException("Missing notation SVG path after fill: " + fill);
		}
		int start = pathIndex + PATH_D_PREFIX.length();
		int end = svg.indexOf('"', start);
		if (end < 0) {
			throw new IllegalStateException("Unterminated notation SVG path after fill: " + fill);
		}
		return svg.substring(start, end);
	}
}
