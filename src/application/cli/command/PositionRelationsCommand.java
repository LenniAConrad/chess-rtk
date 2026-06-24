package application.cli.command;

import static application.cli.Constants.OPT_ACCENT;
import static application.cli.Constants.OPT_CHANNEL;
import static application.cli.Constants.OPT_CHANNELS;
import static application.cli.Constants.OPT_COLOR;
import static application.cli.Constants.OPT_COORDINATES;
import static application.cli.Constants.OPT_FLIP;
import static application.cli.Constants.OPT_OPACITY;
import static application.cli.Constants.OPT_STROKE;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_HEIGHT;
import static application.cli.Constants.OPT_LEGEND;
import static application.cli.Constants.OPT_MONTAGE;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PIECES;
import static application.cli.Constants.OPT_SCRAMBLE;
import static application.cli.Constants.OPT_SEED;
import static application.cli.Constants.OPT_SIZE;
import static application.cli.Constants.OPT_WIDTH;

import application.cli.Constants;
import chess.core.Move;
import chess.core.Position;
import chess.images.render.RelationPalette;
import chess.images.render.Render;
import chess.nn.otis.Model;
import chess.nn.otis.Model.IncidenceEdge;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;
import utility.Argv;

/**
 * Renders the OTIS "tactical incidence" relation channels of a position as typed,
 * colour-coded arrows over the board ({@code fen relations}).
 *
 * <p>
 * Each of the twelve {@link Model#RELATION_NAMES relation channels} of the
 * incidence tensor A(x) -- the deterministic, weightless input the oriented
 * tactical-sheaf (OTIS) network ingests -- is drawn as directed edges between
 * squares: attacks, defenses, king-zone pressure, occlusion-aware slider rays,
 * knight and pawn attacks, and absolute-pin candidates. The edges come straight
 * from {@link Model#incidenceEdges(Position)}, so the picture matches the network.
 * </p>
 *
 * <p>
 * Channels can be drawn together on one board or as a small-multiples montage
 * (one labelled board per channel). A {@code --scramble} toggle degree-preserving
 * randomizes the edges as a falsifier visual. Output is the usual png/jpg/bmp/svg
 * (montage and the legend strip are raster only).
 * </p>
 */
public final class PositionRelationsCommand {

	/**
	 * Default relation-arrow fill opacity (0..1); the border is drawn more opaque.
	 */
	private static final double DEFAULT_FILL_OPACITY = 0.42;

	/**
	 * Default relation-arrow line width.
	 */
	private static final double DEFAULT_STROKE = 3.0;

	/**
	 * Extra opacity added to the border over the fill, so arrows stay legible even
	 * at a low fill opacity.
	 */
	private static final double BORDER_OPACITY_BONUS = 0.45;

	/**
	 * Resolved arrow styling: the per-channel palette, fill opacity, and stroke.
	 *
	 * @param colors per-channel base colours (index = channel)
	 * @param opacity fill opacity in [0, 1]
	 * @param stroke arrow line width
	 */
	private record Style(Color[] colors, double opacity, float stroke) {

		/**
		 * Returns the opaque-ish border colour for a channel.
		 *
		 * @param channel relation channel
		 * @return border colour
		 */
		Color border(int channel) {
			return withAlpha(colors[channel], clampAlpha(opacity + BORDER_OPACITY_BONUS));
		}

		/**
		 * Returns the translucent fill colour for a channel.
		 *
		 * @param channel relation channel
		 * @return fill colour
		 */
		Color fill(int channel) {
			return withAlpha(colors[channel], clampAlpha(opacity));
		}
	}

	/**
	 * Board presentation options shared with {@code fen render}.
	 *
	 * @param whiteDown white at the bottom
	 * @param noPieces hide the pieces (graph-only)
	 * @param showBorder draw the outer frame
	 * @param coordinatesInside draw coordinate labels inside the board
	 * @param coordinatesOutside draw coordinate labels in an outer margin
	 * @param accent board accent hex, or null
	 */
	private record BoardStyle(boolean whiteDown, boolean noPieces, boolean showBorder,
			boolean coordinatesInside, boolean coordinatesOutside, String accent) {
	}

	/**
	 * Manual board overlays, as in {@code fen render}, drawn over the relation graph.
	 *
	 * @param arrows manual arrow moves (e.g. {@code e2e4})
	 * @param circles square circles (e.g. {@code e4})
	 * @param legal squares whose legal-move dots to draw
	 * @param special draw castling/en-passant hint arrows
	 */
	private record Overlays(List<String> arrows, List<String> circles, List<String> legal, boolean special) {

		/**
		 * Returns whether any overlay is requested.
		 *
		 * @return true when at least one overlay is set
		 */
		boolean any() {
			return special || !arrows.isEmpty() || !circles.isEmpty() || !legal.isEmpty();
		}
	}

	/**
	 * Supported raster output formats (svg is handled separately).
	 */
	private static final List<String> RASTER_FORMATS = List.of("png", "jpg", "bmp");

	/**
	 * Board pixel size for one montage cell.
	 */
	private static final int MONTAGE_CELL = 360;

	/**
	 * Columns in the montage grid.
	 */
	private static final int MONTAGE_COLUMNS = 4;

	/**
	 * Prevents instantiation.
	 */
	private PositionRelationsCommand() {
	}

	/**
	 * Runs {@code fen relations}, surfacing I/O failures as a command error.
	 *
	 * @param a parsed arguments
	 */
	public static void runRelations(Argv a) {
		try {
			runRelationsChecked(a);
		} catch (IOException ex) {
			throw new CommandFailure("fen relations: " + ex.getMessage(), ex, 1, false);
		}
	}

	/**
	 * Runs {@code fen relations}.
	 *
	 * @param a parsed arguments
	 * @throws IOException if the image cannot be written
	 */
	private static void runRelationsChecked(Argv a) throws IOException {
		// Consume every known flag/option BEFORE resolving the FEN: the FEN
		// resolver reads the leftover positionals and asserts the tail is empty.
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		String format = normalizeFormat(a.string(OPT_FORMAT), output);
		boolean whiteDown = !a.flag(OPT_FLIP, Constants.OPT_BLACK_DOWN);
		int size = a.integerOr(0, OPT_SIZE);
		int width = a.integerOr(0, OPT_WIDTH);
		int height = a.integerOr(0, OPT_HEIGHT);
		boolean montage = a.flag(OPT_MONTAGE);
		boolean legend = a.flag(OPT_LEGEND);
		// --pieces (default true) / --no-pieces; absent leaves pieces drawn.
		boolean noPieces = Boolean.FALSE.equals(a.flagValue(OPT_PIECES));
		boolean scramble = a.flag(OPT_SCRAMBLE);
		int seed = a.integerOr(0, OPT_SEED);
		String accent = a.string(OPT_ACCENT);
		boolean coordinates = a.flag(OPT_COORDINATES);
		boolean coordinatesOutside = a.flag(Constants.OPT_COORDINATES_OUTSIDE);
		boolean showBorder = !a.flag(Constants.OPT_NO_BORDER);
		boolean shadow = a.flag(Constants.OPT_SHADOW, Constants.OPT_DROP_SHADOW);
		boolean specialArrows = a.flag(Constants.OPT_SPECIAL_ARROWS);
		double opacity = parseFraction(a.string(OPT_OPACITY), DEFAULT_FILL_OPACITY, OPT_OPACITY, 0.0, 1.0);
		float stroke = (float) parseFraction(a.string(OPT_STROKE), DEFAULT_STROKE, OPT_STROKE, 0.1, 64.0);
		List<String> colorOverrides = a.strings(OPT_COLOR);
		List<String> arrows = a.strings(Constants.OPT_ARROW, Constants.OPT_ARROWS);
		List<String> circles = a.strings(Constants.OPT_CIRCLE, Constants.OPT_CIRCLES);
		List<String> legalSquares = a.strings(Constants.OPT_LEGAL);
		List<String> channelSpec = a.strings(OPT_CHANNEL, OPT_CHANNELS);
		// Accepted for parity with `fen render`; the board theme is set via --accent.
		a.flag(Constants.OPT_DARK, Constants.OPT_DARK_MODE);
		String fen = CommandSupport.resolveFenArgument(a, "fen relations", false);

		if (output == null) {
			throw new CommandFailure("fen relations: " + OPT_OUTPUT + " <path> is required", 2);
		}
		if (fen == null) {
			throw new CommandFailure("fen relations: provide a FEN, --fen, --startpos, or --randompos", 2);
		}
		Position position = parsePosition(fen);
		int[] channels = resolveChannels(channelSpec);
		Style style = new Style(resolvePalette(colorOverrides), opacity, stroke);
		BoardStyle boardStyle = new BoardStyle(whiteDown, noPieces, showBorder, coordinates, coordinatesOutside, accent);
		Overlays overlays = new Overlays(arrows, circles, legalSquares, specialArrows);
		List<IncidenceEdge> edges = Model.incidenceEdges(position);
		if (scramble) {
			edges = scramble(edges, new Random(seed));
		}

		if ("svg".equals(format)) {
			if (montage) {
				throw new CommandFailure("fen relations: --montage supports png/jpg/bmp only, not svg", 2);
			}
			if (shadow) {
				System.out.println("fen relations: --drop-shadow is raster-only; ignored for svg");
			}
			if (legend) {
				System.out.println("fen relations: --legend is raster-only; omitted for svg");
			}
			Render render = buildRender(position, boardStyle);
			int drawn = drawChannels(render, edges, channels, style);
			applyOverlays(render, position, overlays);
			int w = dimension(width, size, render.renderedWidth());
			int h = dimension(height, size, render.renderedHeight());
			Files.writeString(output, render.renderSvg(w, h), StandardCharsets.UTF_8);
			System.out.println("Saved relation graph (" + drawn + " edges, "
					+ channels.length + " channels): " + output.toAbsolutePath());
			return;
		}

		BufferedImage image;
		String summary;
		if (montage) {
			image = renderMontage(position, edges, channels, boardStyle, style, overlays);
			summary = "Saved relation montage (" + channels.length + " channels): ";
		} else {
			Render render = buildRender(position, boardStyle);
			int drawn = drawChannels(render, edges, channels, style);
			applyOverlays(render, position, overlays);
			int w = dimension(width, size, render.renderedWidth());
			int h = dimension(height, size, render.renderedHeight());
			BufferedImage rendered = render.render(w, h);
			image = legend ? withLegend(rendered, channels, style) : rendered;
			summary = "Saved relation graph (" + drawn + " edges, " + channels.length + " channels): ";
		}
		if (shadow) {
			image = PositionViewCommand.applyDropShadow(image);
		}
		writeRaster(image, format, output);
		System.out.println(summary + output.toAbsolutePath());
	}

	/**
	 * Draws every selected channel's arrows onto a renderer.
	 *
	 * @param render renderer
	 * @param edges incidence edges
	 * @param channels channels to draw
	 * @param style arrow styling
	 * @return total arrows drawn
	 */
	private static int drawChannels(Render render, List<IncidenceEdge> edges, int[] channels, Style style) {
		int drawn = 0;
		for (int channel : channels) {
			drawn += addChannelArrows(render, edges, channel, style);
		}
		return drawn;
	}

	/**
	 * Applies the manual board overlays (arrows, circles, legal-move dots, special
	 * hint arrows) shared with {@code fen render}.
	 *
	 * @param render renderer
	 * @param position chess position
	 * @param overlays overlays to draw
	 */
	private static void applyOverlays(Render render, Position position, Overlays overlays) {
		if (overlays.any()) {
			PositionViewCommand.applyDisplayOverlays(render, position, overlays.arrows(), overlays.circles(),
					overlays.legal(), overlays.special());
		}
	}

	/**
	 * Builds a board renderer for a relation overlay.
	 *
	 * @param position chess position
	 * @param boardStyle board presentation options
	 * @return configured renderer
	 */
	private static Render buildRender(Position position, BoardStyle boardStyle) {
		Render render = new Render()
				.setPosition(position)
				.setWhiteSideDown(boardStyle.whiteDown())
				.setShowBorder(boardStyle.showBorder())
				.setShowPieces(!boardStyle.noPieces())
				.setShowCoordinates(boardStyle.coordinatesInside())
				.setShowCoordinatesOutside(boardStyle.coordinatesOutside())
				// Suppress the AUTOMATIC castling/en-passant hint arrows: they are
				// not incidence channels. --special-arrows re-adds them explicitly
				// via the shared overlay path.
				.setShowSpecialMoveHints(false);
		if (boardStyle.accent() != null && !boardStyle.accent().isBlank()) {
			render.setBoardAccent(boardStyle.accent());
		}
		return render;
	}

	/**
	 * Adds one channel's edges to a renderer as styled arrows.
	 *
	 * @param render renderer
	 * @param edges all incidence edges
	 * @param channel relation channel to draw
	 * @param style arrow palette, opacity, and stroke
	 * @return number of arrows added
	 */
	private static int addChannelArrows(Render render, List<IncidenceEdge> edges, int channel, Style style) {
		Color border = style.border(channel);
		Color fill = style.fill(channel);
		Stroke stroke = new BasicStroke(style.stroke());
		int count = 0;
		for (IncidenceEdge edge : edges) {
			if (edge.channel() != channel || edge.from() == edge.to()) {
				continue;
			}
			render.addArrow(Move.of((byte) edge.from(), (byte) edge.to()),
					border, fill, stroke, 0.30, 0.30, 0.30);
			count++;
		}
		return count;
	}

	/**
	 * Renders a small-multiples montage: one labelled board per channel.
	 *
	 * @param position chess position
	 * @param edges incidence edges
	 * @param channels channels to render
	 * @param boardStyle board presentation options
	 * @param style arrow palette, opacity, and stroke
	 * @param overlays manual overlays drawn on each cell
	 * @return composed montage image
	 */
	private static BufferedImage renderMontage(Position position, List<IncidenceEdge> edges, int[] channels,
			BoardStyle boardStyle, Style style, Overlays overlays) {
		int columns = Math.min(MONTAGE_COLUMNS, Math.max(1, channels.length));
		int rows = (channels.length + columns - 1) / columns;
		int caption = Math.max(18, MONTAGE_CELL / 14);
		int pad = MONTAGE_CELL / 24;
		int cellW = MONTAGE_CELL + pad * 2;
		int cellH = MONTAGE_CELL + caption + pad * 2;
		BufferedImage montage = new BufferedImage(columns * cellW, rows * cellH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = montage.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x1A1A1A));
		g.fillRect(0, 0, montage.getWidth(), montage.getHeight());
		Font font = new Font("SansSerif", Font.BOLD, caption - 4);
		g.setFont(font);
		for (int i = 0; i < channels.length; i++) {
			int channel = channels[i];
			Render render = buildRender(position, boardStyle);
			addChannelArrows(render, edges, channel, style);
			applyOverlays(render, position, overlays);
			BufferedImage board = render.render(MONTAGE_CELL, MONTAGE_CELL);
			int cx = (i % columns) * cellW + pad;
			int cy = (i / columns) * cellH + pad;
			g.setColor(style.colors()[channel]);
			g.fillRect(cx, cy, caption - 6, caption - 6);
			g.setColor(Color.WHITE);
			g.drawString(channel + "  " + Model.RELATION_NAMES[channel], cx + caption, cy + caption - 8);
			g.drawImage(board, cx, cy + caption, null);
		}
		g.dispose();
		return montage;
	}

	/**
	 * Composes a colour legend strip beneath a rendered board.
	 *
	 * @param board rendered board image
	 * @param channels channels included in the legend
	 * @param style arrow palette (for the swatch colours)
	 * @return board with a legend strip below it
	 */
	private static BufferedImage withLegend(BufferedImage board, int[] channels, Style style) {
		int row = Math.max(20, board.getWidth() / 36);
		int pad = row / 2;
		int legendHeight = pad * 2 + row * channels.length;
		BufferedImage image = new BufferedImage(board.getWidth(), board.getHeight() + legendHeight,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x1A1A1A));
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.drawImage(board, 0, 0, null);
		g.setFont(new Font("SansSerif", Font.PLAIN, row - 8));
		for (int i = 0; i < channels.length; i++) {
			int channel = channels[i];
			int y = board.getHeight() + pad + i * row;
			g.setColor(style.colors()[channel]);
			g.fillRect(pad, y, row - 8, row - 8);
			g.setColor(Color.WHITE);
			g.drawString(channel + "  " + Model.RELATION_NAMES[channel], pad + row, y + row - 12);
		}
		g.dispose();
		return image;
	}

	/**
	 * Builds the effective per-channel palette by applying {@code --color}
	 * overrides ({@code channel=#hex}) to the defaults.
	 *
	 * @param overrides override tokens
	 * @return palette indexed by channel
	 */
	private static Color[] resolvePalette(List<String> overrides) {
		Color[] palette = new Color[RelationPalette.count()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = RelationPalette.color(i);
		}
		for (String token : overrides) {
			for (String part : token.split(";")) {
				if (part.isBlank()) {
					continue;
				}
				int eq = part.indexOf('=');
				if (eq <= 0 || eq == part.length() - 1) {
					throw new CommandFailure("fen relations: --color expects channel=#hex, got '" + part + "'", 2);
				}
				palette[channelIndex(part.substring(0, eq).trim())] = parseHexColor(part.substring(eq + 1).trim());
			}
		}
		return palette;
	}

	/**
	 * Parses a {@code #rrggbb} (or {@code rrggbb}) colour.
	 *
	 * @param hex colour text
	 * @return parsed colour
	 */
	private static Color parseHexColor(String hex) {
		String value = hex.startsWith("#") ? hex.substring(1) : hex;
		if (value.length() != 6) {
			throw new CommandFailure("fen relations: invalid colour '" + hex + "', expected #rrggbb", 2);
		}
		try {
			return new Color(Integer.parseInt(value, 16));
		} catch (NumberFormatException ex) {
			throw new CommandFailure("fen relations: invalid colour '" + hex + "', expected #rrggbb", 2);
		}
	}

	/**
	 * Parses an optional fractional/decimal option within bounds.
	 *
	 * @param text option text, or null for the default
	 * @param fallback default value
	 * @param name option name, for diagnostics
	 * @param min inclusive lower bound
	 * @param max inclusive upper bound
	 * @return parsed value
	 */
	private static double parseFraction(String text, double fallback, String name, double min, double max) {
		if (text == null) {
			return fallback;
		}
		double value;
		try {
			value = Double.parseDouble(text.trim());
		} catch (NumberFormatException ex) {
			throw new CommandFailure("fen relations: " + name + " must be a number, got '" + text + "'", 2);
		}
		if (value < min || value > max) {
			throw new CommandFailure("fen relations: " + name + " must be in [" + min + ", " + max + "]", 2);
		}
		return value;
	}

	/**
	 * Applies an alpha to a colour.
	 *
	 * @param color base colour
	 * @param alpha alpha 0..255
	 * @return colour with the given alpha
	 */
	private static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	/**
	 * Clamps an opacity fraction to a 0..255 alpha.
	 *
	 * @param opacity fraction
	 * @return alpha in [0, 255]
	 */
	private static int clampAlpha(double opacity) {
		return Math.max(0, Math.min(255, (int) Math.round(opacity * 255.0)));
	}

	/**
	 * Degree-preserving randomization of the edges per channel: each source keeps
	 * its out-degree, but its targets are reshuffled. A falsifier visual -- if the
	 * downstream model reacts the same way to scrambled edges, the typed structure
	 * was not being used.
	 *
	 * @param edges source edges
	 * @param random seeded source of randomness
	 * @return scrambled edges
	 */
	private static List<IncidenceEdge> scramble(List<IncidenceEdge> edges, Random random) {
		List<IncidenceEdge> out = new ArrayList<>(edges.size());
		for (int channel = 0; channel < Model.RELATION_COUNT; channel++) {
			List<Integer> froms = new ArrayList<>();
			List<Integer> tos = new ArrayList<>();
			for (IncidenceEdge edge : edges) {
				if (edge.channel() == channel) {
					froms.add(edge.from());
					tos.add(edge.to());
				}
			}
			Collections.shuffle(tos, random);
			for (int i = 0; i < froms.size(); i++) {
				out.add(new IncidenceEdge(channel, froms.get(i), tos.get(i)));
			}
		}
		return out;
	}

	/**
	 * Resolves the channel selection from a list of names, indices, or "all".
	 *
	 * @param spec channel tokens (empty means all)
	 * @return distinct channel indices in ascending order
	 */
	private static int[] resolveChannels(List<String> spec) {
		if (spec.isEmpty() || spec.stream().anyMatch(s -> s.equalsIgnoreCase("all"))) {
			int[] all = new int[Model.RELATION_COUNT];
			for (int i = 0; i < all.length; i++) {
				all[i] = i;
			}
			return all;
		}
		List<Integer> resolved = new ArrayList<>();
		for (String token : spec) {
			for (String part : token.split("[,\\s]+")) {
				if (part.isBlank()) {
					continue;
				}
				int channel = channelIndex(part.trim());
				if (!resolved.contains(channel)) {
					resolved.add(channel);
				}
			}
		}
		if (resolved.isEmpty()) {
			throw new CommandFailure("fen relations: no valid channels in selection", 2);
		}
		Collections.sort(resolved);
		return resolved.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Maps a channel token (index 0..11, exact name, or unique name prefix) to a
	 * channel index.
	 *
	 * @param token channel token
	 * @return channel index
	 */
	private static int channelIndex(String token) {
		try {
			int index = Integer.parseInt(token);
			if (index < 0 || index >= Model.RELATION_COUNT) {
				throw new CommandFailure("fen relations: channel index out of range (0.."
						+ (Model.RELATION_COUNT - 1) + "): " + token, 2);
			}
			return index;
		} catch (NumberFormatException ignored) {
			// fall through to name matching
		}
		String key = token.toLowerCase(Locale.ROOT);
		int match = -1;
		for (int i = 0; i < Model.RELATION_NAMES.length; i++) {
			String name = Model.RELATION_NAMES[i];
			if (name.equals(key)) {
				return i;
			}
			if (name.startsWith(key) || name.contains(key)) {
				if (match >= 0) {
					throw new CommandFailure("fen relations: ambiguous channel '" + token
							+ "'; use the index or full name", 2);
				}
				match = i;
			}
		}
		if (match < 0) {
			throw new CommandFailure("fen relations: unknown channel '" + token + "'. Known: "
					+ String.join(", ", Model.RELATION_NAMES), 2);
		}
		return match;
	}

	/**
	 * Resolves the output format, defaulting from the output extension then to png.
	 *
	 * @param requested explicit --format value, or null
	 * @param output output path (for extension inference)
	 * @return lowercase format (png|jpg|bmp|svg)
	 */
	private static String normalizeFormat(String requested, Path output) {
		String format = requested;
		if (format == null && output != null) {
			String name = output.getFileName().toString();
			int dot = name.lastIndexOf('.');
			if (dot >= 0 && dot < name.length() - 1) {
				format = name.substring(dot + 1);
			}
		}
		if (format == null) {
			return "png";
		}
		format = format.toLowerCase(Locale.ROOT);
		if (format.equals("jpeg")) {
			format = "jpg";
		}
		if (!RASTER_FORMATS.contains(format) && !format.equals("svg")) {
			throw new CommandFailure("fen relations: unsupported format '" + format
					+ "'. Use one of png, jpg, bmp, svg", 2);
		}
		return format;
	}

	/**
	 * Parses a FEN into a position, surfacing a usage error on failure.
	 *
	 * @param fen FEN string
	 * @return parsed position
	 */
	private static Position parsePosition(String fen) {
		try {
			return new Position(fen.trim());
		} catch (RuntimeException ex) {
			throw new CommandFailure("fen relations: invalid FEN: " + ex.getMessage(), 2);
		}
	}

	/**
	 * Writes a raster image, flattening alpha for jpg.
	 *
	 * @param image image to write
	 * @param format raster format
	 * @param output destination path
	 * @throws IOException on write failure
	 */
	private static void writeRaster(BufferedImage image, String format, Path output) throws IOException {
		BufferedImage toWrite = image;
		if (format.equals("jpg") && image.getColorModel().hasAlpha()) {
			BufferedImage opaque = new BufferedImage(image.getWidth(), image.getHeight(),
					BufferedImage.TYPE_INT_RGB);
			Graphics2D g = opaque.createGraphics();
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, opaque.getWidth(), opaque.getHeight());
			g.drawImage(image, 0, 0, null);
			g.dispose();
			toWrite = opaque;
		}
		if (output.getParent() != null) {
			Files.createDirectories(output.getParent());
		}
		if (!ImageIO.write(toWrite, format, output.toFile())) {
			throw new IOException("No ImageIO writer for format: " + format);
		}
	}

	/**
	 * Resolves an output dimension: explicit wins, then a shared size, then a
	 * natural fallback.
	 *
	 * @param explicit explicit width/height, or 0
	 * @param size shared --size, or 0
	 * @param fallback natural dimension
	 * @return resolved dimension
	 */
	private static int dimension(int explicit, int size, int fallback) {
		if (explicit > 0) {
			return explicit;
		}
		if (size > 0) {
			return size;
		}
		return fallback;
	}

	/**
	 * Returns the relation channel names, for help and tests.
	 *
	 * @return channel names in order
	 */
	public static String[] channelNames() {
		return Arrays.copyOf(Model.RELATION_NAMES, Model.RELATION_NAMES.length);
	}
}
