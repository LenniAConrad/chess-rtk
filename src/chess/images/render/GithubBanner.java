package chess.images.render;

import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

import javax.imageio.ImageIO;

import chess.core.Position;
import chess.struct.Game;
import utility.Images;

/**
 * Generates a GitHub banner image for ucicli.
 *
 * <p>
 * The output is a centered render of a chess position (default: standard start),
 * with file/rank coordinates around the board, on a dark diagonal gradient
 * background ({@code #F0F0F0} top-left to {@code #E0E0E0} bottom-right).
 * </p>
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * java -cp out chess.images.render.GithubBanner \
 *   --out assets/ucicli-github-banner.png \
 *   --width 1280 --height 640
 * </pre>
 *
 * <h2>Arguments</h2>
 * <ul>
 *   <li>{@code --out PATH} output PNG path (default: {@code assets/ucicli-github-banner.png})</li>
 *   <li>{@code --width N} image width in pixels (default: {@code 1280})</li>
 *   <li>{@code --height N} image height in pixels (default: {@code 640})</li>
 *   <li>{@code --fen FEN} position to render (default: standard start FEN)</li>
 * </ul>
 */
public final class GithubBanner {

	private static final Color GRADIENT_TOP_LEFT = new Color(0xF0F0F0);
	private static final Color GRADIENT_BOTTOM_RIGHT = new Color(0xE0E0E0);

	/**
	 * Prevents instantiation of this utility class.
	 */
	private GithubBanner() {
		// static utility
	}

	/**
	 * CLI entry point for generating the banner image.
	 *
	 * @param args CLI arguments (see {@link GithubBanner} for supported flags)
	 * @throws IOException if writing the PNG fails
	 */
	public static void main(String[] args) throws IOException {
		Args a = Args.parse(args);

		int width = a.intOr("--width", 1280);
		int height = a.intOr("--height", 640);
		String outPath = a.strOr("--out", "assets/ucicli-github-banner.png");
		String fen = a.strOr("--fen", Game.STANDARD_START_FEN);

		BufferedImage banner = renderBanner(width, height, fen);

		File outFile = new File(outPath);
		File parent = outFile.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		ImageIO.write(banner, "png", outFile);
		System.out.println("Wrote banner: " + outFile.getPath());
	}

	/**
	 * Renders a banner image with the requested dimensions.
	 *
	 * <p>
	 * The chessboard is sized to an integer multiple of 8 pixels to preserve crisp
	 * tile boundaries, and is centered within the output image with a subtle drop
	 * shadow for contrast on the dark background.
	 * </p>
	 *
	 * @param width output width in pixels (must be {@code > 0})
	 * @param height output height in pixels (must be {@code > 0})
	 * @param fen FEN describing the position to render
	 * @return rendered banner image
	 */
	private static BufferedImage renderBanner(int width, int height, String fen) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("width/height must be > 0");
		}

		BufferedImage baseBoard = new Render()
				.setPosition(new Position(fen))
				.setWhiteSideDown(true)
				.setShowBorder(false)
				.render();

		int minDim = Math.min(width, height);
		int outerMargin = clamp((int) Math.round(minDim * 0.09), 36, 96);
		int coordsPad = clamp((int) Math.round(minDim * 0.055), 24, 72);

		int maxBoard = Math.min(width - 2 * outerMargin - 2 * coordsPad, height - 2 * outerMargin - 2 * coordsPad);
		int boardSize = (maxBoard / 8) * 8;
		boardSize = Math.max(8 * 24, boardSize);

		BufferedImage scaledBoard = scale(baseBoard, boardSize, boardSize);
		BufferedImage boardWithCoords = drawCoordinates(scaledBoard, coordsPad);

		int shadowBlur = clamp((int) Math.round(minDim * 0.02), 10, 28);
		BufferedImage boardWithShadow = Images.addDropShadow(boardWithCoords, shadowBlur, new Color(0, 0, 0, 140));

		BufferedImage banner = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = banner.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g.setPaint(new GradientPaint(0, 0, GRADIENT_TOP_LEFT, width, height, GRADIENT_BOTTOM_RIGHT));
		g.fillRect(0, 0, width, height);

		int x = (width - boardWithShadow.getWidth()) / 2;
		int y = (height - boardWithShadow.getHeight()) / 2;
		g.drawImage(boardWithShadow, x, y, null);

		g.dispose();
		return banner;
	}

	/**
	 * Draws coordinate labels around a square board image.
	 *
	 * <p>
	 * The method expects a square image whose side length is divisible by 8 (one
	 * tile per rank/file), and produces a new image padded on all sides.
	 * </p>
	 *
	 * @param board rendered board image (square, divisible by 8)
	 * @param pad padding in pixels used for coordinate gutters (must be {@code >= 0})
	 * @return new image containing the board and coordinate labels
	 */
	private static BufferedImage drawCoordinates(BufferedImage board, int pad) {
		int boardW = board.getWidth();
		int boardH = board.getHeight();
		if (boardW != boardH || boardW % 8 != 0) {
			throw new IllegalArgumentException("board must be square and divisible by 8");
		}

		int tile = boardW / 8;
		int outW = boardW + pad * 2;
		int outH = boardH + pad * 2;

		BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g.drawImage(board, pad, pad, null);

		// Board frame/border.
		float borderStroke = clamp((int) Math.round(tile * 0.08), 2, 8);
		g.setStroke(new BasicStroke(borderStroke, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
		g.setPaint(new Color(65, 65, 65, 210));
		float inset = borderStroke / 2.0f;
		g.draw(new java.awt.geom.Rectangle2D.Float(pad + inset, pad + inset, boardW - borderStroke, boardH - borderStroke));

		int fontSize = clamp((int) Math.round(pad * 0.55), 12, 36);
		Font font = new Font("Times New Roman", Font.BOLD, fontSize);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();

		Color text = new Color(30, 30, 30, 230);
		Color shadow = new Color(255, 255, 255, 160);

		// Files (a..h) at top/bottom.
		for (int file = 0; file < 8; file++) {
			char c = (char) ('a' + file);
			String s = String.valueOf(c);

			int cx = pad + file * tile + tile / 2;
			int sw = fm.stringWidth(s);
			int x = cx - sw / 2;

			int yTop = pad / 2 + fm.getAscent() / 2;
			int yBottom = pad + boardH + (pad / 2) + fm.getAscent() / 2;

			drawTextWithShadow(g, s, x, yTop, text, shadow);
			drawTextWithShadow(g, s, x, yBottom, text, shadow);
		}

		// Ranks (8..1) at left/right.
		for (int rank = 0; rank < 8; rank++) {
			int label = 8 - rank;
			String s = String.valueOf(label);

			int cy = pad + rank * tile + tile / 2;
			int sh = fm.getAscent();
			int y = cy + sh / 2 - 1;

			int xLeft = pad / 2 - fm.stringWidth(s) / 2;
			int xRight = pad + boardW + (pad / 2) - fm.stringWidth(s) / 2;

			drawTextWithShadow(g, s, xLeft, y, text, shadow);
			drawTextWithShadow(g, s, xRight, y, text, shadow);
		}

		g.dispose();
		return out;
	}

	/**
	 * Draws a small text label with a 1px shadow for legibility.
	 *
	 * @param g graphics context
	 * @param text label content
	 * @param x x-position for the baseline origin
	 * @param y y-position for the baseline origin
	 * @param color text color
	 * @param shadow shadow color
	 */
	private static void drawTextWithShadow(Graphics2D g, String text, int x, int y, Color color, Color shadow) {
		g.setPaint(shadow);
		g.drawString(text, x + 1, y + 1);
		g.setPaint(color);
		g.drawString(text, x, y);
	}

	/**
	 * Scales an image with high-quality interpolation.
	 *
	 * @param src source image
	 * @param w target width
	 * @param h target height
	 * @return new scaled image
	 */
	private static BufferedImage scale(BufferedImage src, int w, int h) {
		Objects.requireNonNull(src, "src");
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	/**
	 * Clamps an integer to the inclusive range {@code [lo, hi]}.
	 *
	 * @param v value to clamp
	 * @param lo lower bound (inclusive)
	 * @param hi upper bound (inclusive)
	 * @return clamped value
	 */
	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static final class Args {
		private final String[] arguments;

		/**
		 * Creates an argument parser wrapper for simple {@code --flag value} pairs.
		 *
		 * @param args raw argument list (may be null)
		 */
		private Args(String[] args) {
			this.arguments = args == null ? new String[0] : args;
		}

		/**
		 * Wraps a raw argument array.
		 *
		 * @param args raw args
		 * @return wrapper
		 */
		static Args parse(String[] args) {
			return new Args(args);
		}

		/**
		 * Reads the string value following {@code key}, or returns {@code fallback}.
		 *
		 * @param key flag name (e.g. {@code --out})
		 * @param fallback value to return if not present
		 * @return parsed value or fallback
		 */
		String strOr(String key, String fallback) {
			for (int i = 0; i < arguments.length - 1; i++) {
				if (key.equals(arguments[i])) {
					return arguments[i + 1];
				}
			}
			return fallback;
		}

		/**
		 * Reads the integer value following {@code key}, or returns {@code fallback}.
		 *
		 * @param key flag name (e.g. {@code --width})
		 * @param fallback value to return if not present or invalid
		 * @return parsed integer or fallback
		 */
		int intOr(String key, int fallback) {
			String v = strOr(key, null);
			if (v == null) {
				return fallback;
			}
			try {
				return Integer.parseInt(v);
			} catch (NumberFormatException e) {
				return fallback;
			}
		}
	}
}
