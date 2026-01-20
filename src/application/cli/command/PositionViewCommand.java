package application.cli.command;

import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.MSG_FEN_REQUIRED_HINT;
import static application.cli.Constants.OPT_ABLATION;
import static application.cli.Constants.OPT_ARROW;
import static application.cli.Constants.OPT_ARROWS;
import static application.cli.Constants.OPT_BACKEND;
import static application.cli.Constants.OPT_BLACK_DOWN;
import static application.cli.Constants.OPT_CIRCLE;
import static application.cli.Constants.OPT_CIRCLES;
import static application.cli.Constants.OPT_DARK;
import static application.cli.Constants.OPT_DARK_MODE;
import static application.cli.Constants.OPT_DETAILS_INSIDE;
import static application.cli.Constants.OPT_DETAILS_OUTSIDE;
import static application.cli.Constants.OPT_DROP_SHADOW;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_FLIP;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_HEIGHT;
import static application.cli.Constants.OPT_LEGAL;
import static application.cli.Constants.OPT_NO_BORDER;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_SHADOW;
import static application.cli.Constants.OPT_SHOW_BACKEND;
import static application.cli.Constants.OPT_SIZE;
import static application.cli.Constants.OPT_SPECIAL_ARROWS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WIDTH;
import static application.cli.Constants.OPT_ZOOM;
import static application.cli.Format.formatBackendLabel;
import static application.cli.Format.formatSigned;
import static application.cli.PathOps.ensureParentDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.imageio.ImageIO;

import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.debug.LogService;
import chess.debug.Printer;
import chess.eval.Evaluator;
import chess.eval.Result;
import chess.images.render.Render;
import chess.tag.Tagging;
import utility.Argv;
import utility.Display;
import utility.Images;

/**
 * Implements {@code print}, {@code display}, and {@code render}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PositionViewCommand {

	/**
	 * Used for attaching FEN context to log entries.
	 */
	private static final String LOG_CTX_FEN_PREFIX = "FEN: ";

	/**
	 * Used for the default display window size when no overrides are supplied.
	 */
	private static final int DEFAULT_DISPLAY_WINDOW_SIZE = 640;

	/**
	 * Utility class; prevent instantiation.
	 */
	private PositionViewCommand() {
		// utility
	}

	/**
	 * Handles {@code print}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPrint(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		String fen = CommandSupport.resolveFenArgument(a);

		if (fen == null || fen.isEmpty()) {
			System.err.println("print requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(fen.trim());
			Printer.board(pos);
			List<String> tags = Tagging.tags(pos);
			if (!tags.isEmpty()) {
				System.out.println();
				System.out.println("Tags:");
				for (String tag : tags) {
					System.out.println("  " + tag);
				}
			}
		} catch (IllegalArgumentException ex) {
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "print: invalid FEN", LOG_CTX_FEN_PREFIX + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception t) {
			System.err.println("Error: failed to print position. " + (t.getMessage() == null ? "" : t.getMessage()));
			LogService.error(t, "print: unexpected failure while printing position", LOG_CTX_FEN_PREFIX + fen);
			if (verbose) {
				t.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Handles {@code display}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runDisplay(Argv a) {
		DisplayOptions opts = parseDisplayOptions(a);

		if (opts.fen() == null || opts.fen().isEmpty()) {
			System.err.println("display requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(opts.fen().trim());
			Render render = createRender(pos, opts.whiteDown(), opts.showBorder(), opts.details(),
					opts.detailsOutside());
			applyDisplayOverlays(render, pos, opts.arrows(), opts.circles(), opts.legal(), opts.specialArrows());
			String backendLabel = applyDisplayEvaluatorOverlays(render, pos, opts.showBackend(), opts.ablation());

			int windowWidth = resolveWindowDimension(opts.width(), opts.size(), DEFAULT_DISPLAY_WINDOW_SIZE);
			int windowHeight = resolveWindowDimension(opts.height(), opts.size(), DEFAULT_DISPLAY_WINDOW_SIZE);
			BufferedImage image = render.render();
			if (opts.shadow()) {
				image = applyDropShadow(image);
			}
			Display display = new Display(image, windowWidth, windowHeight, opts.light());
			display.setZoom(opts.zoom());
			if (backendLabel != null) {
				display.setTitle("Backend: " + backendLabel);
			}
		} catch (IllegalArgumentException ex) {
			System.err.println("Error: invalid display input. " + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "display: invalid input", LOG_CTX_FEN_PREFIX + opts.fen());
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception t) {
			System.err.println("Error: failed to display position. " + (t.getMessage() == null ? "" : t.getMessage()));
			LogService.error(t, "display: unexpected failure while rendering position",
					LOG_CTX_FEN_PREFIX + opts.fen());
			if (opts.verbose()) {
				t.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Handles {@code render}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRenderImage(Argv a) {
		renderImageOrExit(parseRenderImageOptions(a));
	}

	/**
	 * Parsed display options for {@code display}.
	 *
	 * @param verbose        whether to print stack traces on failure
	 * @param fen            FEN string to render
	 * @param showBorder     whether to draw the outer border
	 * @param whiteDown      whether White is rendered at the bottom
	 * @param light          whether to use the light theme
	 * @param showBackend    whether to show the evaluator backend label
	 * @param ablation       whether to render the ablation heatmap
	 * @param size           square size override
	 * @param width          window width override
	 * @param height         window height override
	 * @param zoom           zoom factor
	 * @param arrows         arrow overlays
	 * @param specialArrows  whether to include special arrows
	 * @param details        whether to show details inside the board
	 * @param detailsOutside whether to show details outside the board
	 * @param shadow         whether to apply a drop shadow
	 * @param circles        circle overlays
	 * @param legal          legal move overlays
	 */
	private record DisplayOptions(
			boolean verbose,
			String fen,
			boolean showBorder,
			boolean whiteDown,
			boolean light,
			boolean showBackend,
			boolean ablation,
			int size,
			int width,
			int height,
			double zoom,
			List<String> arrows,
			boolean specialArrows,
			boolean details,
			boolean detailsOutside,
			boolean shadow,
			List<String> circles,
			List<String> legal) {
	}

	/**
	 * Parsed render options for {@code render}.
	 *
	 * @param verbose        whether to print stack traces on failure
	 * @param fen            FEN string to render
	 * @param showBorder     whether to draw the outer border
	 * @param whiteDown      whether White is rendered at the bottom
	 * @param showBackend    whether to show the evaluator backend label
	 * @param ablation       whether to render the ablation heatmap
	 * @param size           square size override
	 * @param width          image width override
	 * @param height         image height override
	 * @param arrows         arrow overlays
	 * @param specialArrows  whether to include special arrows
	 * @param details        whether to show details inside the board
	 * @param detailsOutside whether to show details outside the board
	 * @param shadow         whether to apply a drop shadow
	 * @param circles        circle overlays
	 * @param legal          legal move overlays
	 * @param output         output image path
	 * @param format         image format override
	 */
	private record RenderImageOptions(
			boolean verbose,
			String fen,
			boolean showBorder,
			boolean whiteDown,
			boolean showBackend,
			boolean ablation,
			int size,
			int width,
			int height,
			List<String> arrows,
			boolean specialArrows,
			boolean details,
			boolean detailsOutside,
			boolean shadow,
			List<String> circles,
			List<String> legal,
			Path output,
			String format) {
	}

	/**
	 * Parses CLI arguments for {@code display}.
	 *
	 * @param a argument parser for the subcommand
	 * @return parsed display options
	 */
	private static DisplayOptions parseDisplayOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		String fen = a.string(OPT_FEN);
		boolean showBorder = !a.flag(OPT_NO_BORDER);
		boolean whiteDown = !a.flag(OPT_FLIP, OPT_BLACK_DOWN);
		boolean light = !a.flag(OPT_DARK, OPT_DARK_MODE);

		boolean showBackend = a.flag(OPT_SHOW_BACKEND, OPT_BACKEND);
		boolean ablation = a.flag(OPT_ABLATION);
		int size = a.integerOr(0, OPT_SIZE);
		int width = a.integerOr(0, OPT_WIDTH);
		int height = a.integerOr(0, OPT_HEIGHT);
		double zoom = parseZoomFactor(a.string(OPT_ZOOM));
		List<String> arrows = a.strings(OPT_ARROW, OPT_ARROWS);
		boolean specialArrows = a.flag(OPT_SPECIAL_ARROWS);
		boolean details = a.flag(OPT_DETAILS_INSIDE);
		boolean detailsOutside = a.flag(OPT_DETAILS_OUTSIDE);
		boolean shadow = a.flag(OPT_SHADOW, OPT_DROP_SHADOW);
		List<String> circles = a.strings(OPT_CIRCLE, OPT_CIRCLES);
		List<String> legal = a.strings(OPT_LEGAL);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}

		a.ensureConsumed();
		return new DisplayOptions(
				verbose,
				fen,
				showBorder,
				whiteDown,
				light,
				showBackend,
				ablation,
				size,
				width,
				height,
				zoom,
				arrows,
				specialArrows,
				details,
				detailsOutside,
				shadow,
				circles,
				legal);
	}

	/**
	 * Parses CLI arguments for {@code render}.
	 *
	 * @param a argument parser for the subcommand
	 * @return parsed render options
	 */
	private static RenderImageOptions parseRenderImageOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		String fen = a.string(OPT_FEN);
		boolean showBorder = !a.flag(OPT_NO_BORDER);
		boolean whiteDown = !a.flag(OPT_FLIP, OPT_BLACK_DOWN);

		boolean showBackend = a.flag(OPT_SHOW_BACKEND, OPT_BACKEND);
		boolean ablation = a.flag(OPT_ABLATION);
		int size = a.integerOr(0, OPT_SIZE);
		int width = a.integerOr(0, OPT_WIDTH);
		int height = a.integerOr(0, OPT_HEIGHT);
		List<String> arrows = a.strings(OPT_ARROW, OPT_ARROWS);
		boolean specialArrows = a.flag(OPT_SPECIAL_ARROWS);
		boolean details = a.flag(OPT_DETAILS_INSIDE);
		boolean detailsOutside = a.flag(OPT_DETAILS_OUTSIDE);
		boolean shadow = a.flag(OPT_SHADOW, OPT_DROP_SHADOW);
		List<String> circles = a.strings(OPT_CIRCLE, OPT_CIRCLES);
		List<String> legal = a.strings(OPT_LEGAL);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		String format = a.string(OPT_FORMAT);
		a.flag(OPT_DARK, OPT_DARK_MODE);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}

		a.ensureConsumed();
		return new RenderImageOptions(
				verbose,
				fen,
				showBorder,
				whiteDown,
				showBackend,
				ablation,
				size,
				width,
				height,
				arrows,
				specialArrows,
				details,
				detailsOutside,
				shadow,
				circles,
				legal,
				output,
				format);
	}

	/**
	 * Renders an image to disk or exits with an error.
	 *
	 * @param opts parsed render options
	 */
	private static void renderImageOrExit(RenderImageOptions opts) {
		String validationError = validateRenderImageOptions(opts);
		if (validationError != null) {
			System.err.println(validationError);
			System.exit(2);
			return;
		}

		try {
			renderImageToDisk(opts);
		} catch (IllegalArgumentException ex) {
			handleRenderFailure(opts, "Error: invalid render input. ", "render: invalid input", ex);
		} catch (IOException ex) {
			handleRenderFailure(opts, "Error: failed to write image. ", "render: failed to write image", ex);
		} catch (Exception ex) {
			handleRenderFailure(opts, "Error: failed to render image. ",
					"render: unexpected failure while rendering image", ex);
		}
	}

	/**
	 * Validates required render options.
	 *
	 * @param opts parsed render options
	 * @return error message when invalid, otherwise {@code null}
	 */
	private static String validateRenderImageOptions(RenderImageOptions opts) {
		if (opts.fen() == null || opts.fen().isEmpty()) {
			return "render requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")";
		}
		if (opts.output() == null) {
			return "render requires --output|-o <path>";
		}
		return null;
	}

	/**
	 * Renders a position image and writes it to disk.
	 *
	 * @param opts parsed render options
	 * @throws IOException when the image cannot be written
	 */
	private static void renderImageToDisk(RenderImageOptions opts) throws IOException {
		Position pos = new Position(opts.fen().trim());
		Render render = createRender(pos, opts.whiteDown(), opts.showBorder(), opts.details(), opts.detailsOutside());
		applyDisplayOverlays(render, pos, opts.arrows(), opts.circles(), opts.legal(), opts.specialArrows());
		applyDisplayEvaluatorOverlays(render, pos, opts.showBackend(), opts.ablation());

		BufferedImage image = render.render();
		int outWidth = resolveWindowDimension(opts.width(), opts.size(), image.getWidth());
		int outHeight = resolveWindowDimension(opts.height(), opts.size(), image.getHeight());
		if (outWidth <= 0 || outHeight <= 0) {
			throw new IllegalArgumentException("render size must be positive");
		}
		if (outWidth != image.getWidth() || outHeight != image.getHeight()) {
			image = scaleImage(image, outWidth, outHeight);
		}
		if (opts.shadow()) {
			image = applyDropShadow(image);
		}

		Path output = Objects.requireNonNull(opts.output(), "output");
		String format = resolveImageFormat(output, opts.format());
		Path out = Objects.requireNonNull(ensureImageExtension(output, format), "output");
		ensureParentDir(out);

		BufferedImage toWrite = "jpg".equals(format) ? toOpaqueImage(image, Color.WHITE) : image;
		if (!ImageIO.write(toWrite, format, out.toFile())) {
			throw new IOException("No ImageIO writer for format: " + format);
		}
		System.out.println("Saved board image: " + out.toAbsolutePath());
	}

	/**
	 * Logs and prints a render failure, then exits.
	 *
	 * @param opts        parsed render options
	 * @param userPrefix  prefix to display for user-facing errors
	 * @param logMessage  message to include in logs
	 * @param ex          exception that triggered the failure
	 */
	private static void handleRenderFailure(RenderImageOptions opts, String userPrefix, String logMessage,
			Exception ex) {
		System.err.println(userPrefix + (ex.getMessage() == null ? "" : ex.getMessage()));
		LogService.error(ex, logMessage, LOG_CTX_FEN_PREFIX + opts.fen());
		if (opts.verbose()) {
			ex.printStackTrace(System.err);
		}
		System.exit(3);
	}

	/**
	 * Creates a renderer configured for board orientation and overlays.
	 *
	 * @param pos                   position to render
	 * @param whiteDown             whether White is rendered at the bottom
	 * @param showBorder            whether to show the outer border
	 * @param showCoordinates       whether to show file/rank coordinates
	 * @param showCoordinatesOutside whether to place coordinates outside the board
	 * @return configured renderer instance
	 */
	private static Render createRender(Position pos, boolean whiteDown, boolean showBorder, boolean showCoordinates,
			boolean showCoordinatesOutside) {
		return new Render()
				.setPosition(pos)
				.setWhiteSideDown(whiteDown)
				.setShowBorder(showBorder)
				.setShowCoordinates(showCoordinates)
				.setShowCoordinatesOutside(showCoordinatesOutside);
	}

	/**
	 * Applies arrow, circle, and legal-move overlays to the renderer.
	 *
	 * @param render        renderer to mutate
	 * @param pos           position for legal move overlays
	 * @param arrows        arrow overlays
	 * @param circles       circle overlays
	 * @param legalSquares  legal move overlay squares
	 * @param specialArrows whether to include special arrow overlays
	 */
	private static void applyDisplayOverlays(
			Render render,
			Position pos,
			List<String> arrows,
			List<String> circles,
			List<String> legalSquares,
			boolean specialArrows) {
		if (specialArrows) {
			render.addCastlingRights(pos).addEnPassant(pos);
		}
		for (String arrow : arrows) {
			short move = Move.parse(arrow);
			render.addArrow(move);
		}
		for (String circle : circles) {
			byte index = parseSquare(circle);
			render.addCircle(index);
		}
		for (String sq : legalSquares) {
			byte index = parseSquare(sq);
			render.addLegalMoves(pos, index);
		}
	}

	/**
	 * Applies evaluator overlays and returns the backend label if requested.
	 *
	 * @param render      renderer to mutate
	 * @param pos         position to evaluate
	 * @param showBackend whether to print backend info
	 * @param ablation    whether to render ablation overlays
	 * @return backend label string, or {@code null} if not shown
	 */
	private static String applyDisplayEvaluatorOverlays(
			Render render,
			Position pos,
			boolean showBackend,
			boolean ablation) {
		if (!showBackend && !ablation) {
			return null;
		}

		String backendLabel = null;
		try (Evaluator evaluator = new Evaluator()) {
			if (showBackend) {
				Result result = evaluator.evaluate(pos);
				backendLabel = formatBackendLabel(result.backend());
				System.out.println("Display backend: " + backendLabel);
			}
			if (ablation) {
				applyAblationOverlay(render, pos, evaluator);
			}
		}
		return backendLabel;
	}

	/**
	 * Resolves a window dimension from explicit, size, or fallback values.
	 *
	 * @param explicit explicit width/height
	 * @param size     shared size override
	 * @param fallback fallback dimension
	 * @return resolved dimension
	 */
	private static int resolveWindowDimension(int explicit, int size, int fallback) {
		if (explicit > 0) {
			return explicit;
		}
		if (size > 0) {
			return size;
		}
		return fallback;
	}

	/**
	 * Parses a zoom factor string with validation.
	 *
	 * @param raw raw zoom string
	 * @return parsed zoom factor
	 */
	private static double parseZoomFactor(String raw) {
		if (raw == null || raw.isEmpty()) {
			return 1.0;
		}
		try {
			double zoom = Double.parseDouble(raw);
			if (zoom <= 0.0) {
				throw new IllegalArgumentException("display: " + OPT_ZOOM + " must be > 0");
			}
			return zoom;
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("display: invalid " + OPT_ZOOM + " value: " + raw, ex);
		}
	}

	/**
	 * Resolves the image format based on explicit or file extension values.
	 *
	 * @param output         output path used for extension inference
	 * @param formatOverride explicit format override
	 * @return normalized format string
	 */
	private static String resolveImageFormat(Path output, String formatOverride) {
		String format = normalizeImageFormat(formatOverride);
		if (format != null) {
			return format;
		}
		String ext = extensionOf(output);
		format = normalizeImageFormat(ext);
		if (format == null) {
			if (ext == null || ext.isBlank()) {
				throw new IllegalArgumentException(
						"Missing image format (use --format png|jpg|bmp or add an extension)");
			}
			throw new IllegalArgumentException("Unsupported image format: " + ext);
		}
		return format;
	}

	/**
	 * Normalizes image format labels into supported values.
	 *
	 * @param format raw format string
	 * @return normalized format string or {@code null} if unsupported
	 */
	private static String normalizeImageFormat(String format) {
		if (format == null || format.isBlank()) {
			return null;
		}
		String normalized = format.toLowerCase(Locale.ROOT);
		if ("jpeg".equals(normalized)) {
			normalized = "jpg";
		}
		return switch (normalized) {
			case "png", "jpg", "bmp" -> normalized;
			default -> null;
		};
	}

	/**
	 * Extracts the file extension from a path.
	 *
	 * @param output path to inspect
	 * @return extension without dot, or {@code null} if none exists
	 */
	private static String extensionOf(Path output) {
		if (output == null) {
			return null;
		}
		String name = output.getFileName().toString();
		int dot = name.lastIndexOf('.');
		if (dot <= 0 || dot == name.length() - 1) {
			return null;
		}
		return name.substring(dot + 1);
	}

	/**
	 * Ensures the output path has a filename extension.
	 *
	 * @param output output image path
	 * @param format format to append when missing
	 * @return output path with an extension applied when needed
	 */
	private static Path ensureImageExtension(Path output, String format) {
		if (output == null || format == null || format.isBlank()) {
			return output;
		}
		String ext = extensionOf(output);
		if (ext == null || ext.isBlank()) {
			return output.resolveSibling(output.getFileName().toString() + "." + format);
		}
		return output;
	}

	/**
	 * Scales an image to the requested size with high-quality filtering.
	 *
	 * @param source source image
	 * @param width  target width
	 * @param height target height
	 * @return scaled image
	 */
	private static BufferedImage scaleImage(BufferedImage source, int width, int height) {
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return scaled;
	}

	/**
	 * Converts an image to opaque RGB using the given background color.
	 *
	 * @param source     input image
	 * @param background background color for alpha blending
	 * @return opaque RGB image
	 */
	private static BufferedImage toOpaqueImage(BufferedImage source, Color background) {
		if (source.getType() == BufferedImage.TYPE_INT_RGB) {
			return source;
		}
		BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setPaint(background);
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		g.drawImage(source, 0, 0, null);
		g.dispose();
		return out;
	}

	/**
	 * Applies a drop shadow to a rendered image.
	 *
	 * @param image input image
	 * @return image with drop shadow applied
	 */
	private static BufferedImage applyDropShadow(BufferedImage image) {
		int width = image.getWidth();
		int blur = (int) (width * 0.05);
		int offset = (int) (width * 0.05);
		return Images.addDropShadow(image, blur, offset, offset, new Color(0, 0, 0, 255));
	}

	/**
	 * Parses a square coordinate into a board index.
	 *
	 * @param square algebraic square coordinate
	 * @return square index
	 */
	private static byte parseSquare(String square) {
		if (square == null || "-".equals(square) || !Field.isField(square)) {
			throw new IllegalArgumentException("Invalid square: " + square);
		}
		return Field.toIndex(square);
	}

	/**
	 * Applies evaluator ablation overlays to the renderer.
	 *
	 * @param render    renderer to mutate
	 * @param pos       position to evaluate
	 * @param evaluator evaluator used for ablation scores
	 */
	private static void applyAblationOverlay(Render render, Position pos, Evaluator evaluator) {
		render.setPieceScaleAndOffset(0.70, -0.15);
		int[][] matrix = evaluator.ablation(pos);
		byte[] board = pos.getBoard();
		double[] scales = ablationMaterialScales(matrix, board);

		for (int index = 0; index < 64; index++) {
			byte piece = board[index];
			if (piece == Piece.EMPTY) {
				continue;
			}
			int file = Field.getX((byte) index);
			int rankFromBottom = Field.getY((byte) index);
			int delta = matrix[rankFromBottom][file];
			int type = Math.abs(piece);
			double scaled = delta * scales[type];
			int signed = (int) Math.round(Piece.isWhite(piece) ? scaled : -scaled);
			render.setSquareTextBottom((byte) index, formatSigned(signed));
		}
	}

	/**
	 * Computes per-piece scaling factors for ablation overlays.
	 *
	 * @param matrix ablation matrix values
	 * @param board  board representation array
	 * @return scaling factors by piece type
	 */
	private static double[] ablationMaterialScales(int[][] matrix, byte[] board) {
		int[] counts = new int[7];
		long[] sumAbs = new long[7];
		for (int index = 0; index < 64; index++) {
			byte piece = board[index];
			if (piece == Piece.EMPTY) {
				continue;
			}
			int file = Field.getX((byte) index);
			int rankFromBottom = Field.getY((byte) index);
			int raw = matrix[rankFromBottom][file];
			int type = Math.abs(piece);
			sumAbs[type] += Math.abs(raw);
			counts[type]++;
		}

		double[] scales = new double[7];
		for (int type = 1; type <= 6; type++) {
			if (counts[type] == 0) {
				scales[type] = 1.0;
				continue;
			}
			double avg = sumAbs[type] / (double) counts[type];
			int material = Math.abs(Piece.getValue((byte) type));
			if (material <= 0 || avg <= 0.0) {
				scales[type] = 1.0;
			} else {
				scales[type] = material / avg;
			}
		}
		return scales;
	}
}
