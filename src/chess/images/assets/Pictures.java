package chess.images.assets;

import java.awt.image.BufferedImage;

/**
 * Compatibility facade for image assets.
 *
 * @since 2024
 * @author Lennart A. Conrad
 */
public final class Pictures {

	/**
	 * Prevents instantiation of this utility class.
	 */
	private Pictures() {
		// utility holder
	}

	/**
	 * A {@code BufferedImage} of the ChessRTK logo (squircle, knight, and flask).
	 */
	public static final BufferedImage Logo = Shapes.Logo;
}
