package chess.images.render;

import java.awt.Color;

/**
 * Per-channel colours for the OTIS tactical-incidence relation channels, shared
 * by the CLI {@code fen relations} renderer and the workbench Relations panel so
 * both read the same. The index is the relation channel; the order matches
 * {@code chess.nn.otis.Model.RELATION_NAMES}: attacks warm, defenses teal, king
 * zone pink/purple, the three slider rays a blue family, then knight, pawn, pin.
 */
public final class RelationPalette {

	/**
	 * Base colour per channel (index = relation channel).
	 */
	private static final Color[] COLORS = {
			new Color(0xE53935), // 0  us_attacks_them_piece
			new Color(0xF4511E), // 1  them_attacks_us_piece
			new Color(0x26A69A), // 2  us_defends_us_piece
			new Color(0x00897B), // 3  them_defends_them_piece
			new Color(0xD81B60), // 4  us_attacks_empty_near_king
			new Color(0x8E24AA), // 5  them_attacks_empty_near_king
			new Color(0x42A5F5), // 6  bishop_ray_visible
			new Color(0x1E88E5), // 7  rook_ray_visible
			new Color(0x0D47A1), // 8  queen_ray_visible
			new Color(0xFDD835), // 9  knight_attack
			new Color(0x7CB342), // 10 pawn_attack_forward_oriented
			new Color(0xFF7043), // 11 king_ray_pin_candidate
	};

	/**
	 * Prevents instantiation.
	 */
	private RelationPalette() {
	}

	/**
	 * Returns the number of relation channels.
	 *
	 * @return channel count
	 */
	public static int count() {
		return COLORS.length;
	}

	/**
	 * Returns the base colour for a channel.
	 *
	 * @param channel relation channel index
	 * @return base colour
	 */
	public static Color color(int channel) {
		return COLORS[channel];
	}
}
