package application.cli.command;

import static application.cli.Constants.OPT_ASCII;
import static application.cli.Constants.OPT_BATCH;
import static application.cli.Constants.OPT_CHESS960;
import static application.cli.Constants.OPT_CHESS960_FILES;
import static application.cli.Constants.OPT_FENS_PER_FILE;
import static application.cli.Constants.OPT_FILES;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PER_FILE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Validation.requireBetweenInclusive;
import static application.cli.Validation.requirePositive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import application.console.Bar;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.Setup;
import utility.Argv;

/**
 * Implements the {@code fen generate} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GenFensCommand {

	/**
	 * Current command label used in diagnostics.
	 */
	private static final String COMMAND_LABEL = "fen generate";

	/**
	 * {@code --max-attempts} option flag.
	 */
	private static final String OPT_MAX_ATTEMPTS = "--max-attempts";

	/**
	 * {@code --stage} option flag.
	 */
	private static final String OPT_STAGE = "--stage";

	/**
	 * {@code --endgame} option flag.
	 */
	private static final String OPT_ENDGAME = "--endgame";

	/**
	 * {@code --late-endgame} option flag.
	 */
	private static final String OPT_LATE_ENDGAME = "--late-endgame";

	/**
	 * {@code --king-pawn-endgame} option flag.
	 */
	private static final String OPT_KING_PAWN_ENDGAME = "--king-pawn-endgame";

	/**
	 * {@code --minor-endgame} option flag.
	 */
	private static final String OPT_MINOR_ENDGAME = "--minor-endgame";

	/**
	 * {@code --rook-endgame} option flag.
	 */
	private static final String OPT_ROOK_ENDGAME = "--rook-endgame";

	/**
	 * {@code --queenless} option flag.
	 */
	private static final String OPT_QUEENLESS = "--queenless";

	/**
	 * {@code --opposite-bishops} option flag.
	 */
	private static final String OPT_OPPOSITE_BISHOPS = "--opposite-bishops";

	/**
	 * {@code --en-passant} option flag.
	 */
	private static final String OPT_EN_PASSANT = "--en-passant";

	/**
	 * {@code --ep} option alias.
	 */
	private static final String OPT_EP = "--ep";

	/**
	 * {@code --promotion} option flag.
	 */
	private static final String OPT_PROMOTION = "--promotion";

	/**
	 * {@code --promotions} option alias.
	 */
	private static final String OPT_PROMOTIONS = "--promotions";

	/**
	 * {@code --underpromotion} option flag.
	 */
	private static final String OPT_UNDERPROMOTION = "--underpromotion";

	/**
	 * {@code --underpromotions} option alias.
	 */
	private static final String OPT_UNDERPROMOTIONS = "--underpromotions";

	/**
	 * {@code --capture} option flag.
	 */
	private static final String OPT_CAPTURE = "--capture";

	/**
	 * {@code --captures} option alias.
	 */
	private static final String OPT_CAPTURES = "--captures";

	/**
	 * {@code --castle-rights} option flag.
	 */
	private static final String OPT_CASTLE_RIGHTS = "--castle-rights";

	/**
	 * {@code --castling-rights} option alias.
	 */
	private static final String OPT_CASTLING_RIGHTS = "--castling-rights";

	/**
	 * {@code --legal-castle} option flag.
	 */
	private static final String OPT_LEGAL_CASTLE = "--legal-castle";

	/**
	 * {@code --legal-castling} option alias.
	 */
	private static final String OPT_LEGAL_CASTLING = "--legal-castling";

	/**
	 * {@code --in-check} option flag.
	 */
	private static final String OPT_IN_CHECK = "--in-check";

	/**
	 * {@code --not-in-check} option flag.
	 */
	private static final String OPT_NOT_IN_CHECK = "--not-in-check";

	/**
	 * {@code --checkmate} option flag.
	 */
	private static final String OPT_CHECKMATE = "--checkmate";

	/**
	 * {@code --stalemate} option flag.
	 */
	private static final String OPT_STALEMATE = "--stalemate";

	/**
	 * {@code --side} option flag.
	 */
	private static final String OPT_SIDE = "--side";

	/**
	 * {@code --max-material-imbalance} option flag.
	 */
	private static final String OPT_MAX_MATERIAL_IMBALANCE = "--max-material-imbalance";

	/**
	 * Maximum piece count accepted by generic piece-count filters.
	 */
	private static final int MAX_BOARD_PIECES = 32;

	/**
	 * Conservative centipawn limit for material filters.
	 */
	private static final int MAX_MATERIAL_CP = 100_000;

	/**
	 * Piece count used by the coarse {@code --endgame} preset.
	 */
	private static final int ENDGAME_MAX_PIECES = 14;

	/**
	 * Piece count used by the coarse {@code --late-endgame} preset.
	 */
	private static final int LATE_ENDGAME_MAX_PIECES = 8;

	/**
	 * Piece-family index for knights.
	 */
	private static final int PIECE_INDEX_KNIGHTS = 1;

	/**
	 * Piece-family index for bishops.
	 */
	private static final int PIECE_INDEX_BISHOPS = 2;

	/**
	 * Piece-family index for rooks.
	 */
	private static final int PIECE_INDEX_ROOKS = 3;

	/**
	 * Piece-family index for queens.
	 */
	private static final int PIECE_INDEX_QUEENS = 4;

	/**
	 * Supported non-king material count filters.
	 */
	private static final PieceCountSpec[] PIECE_COUNT_SPECS = {
			new PieceCountSpec("pawns", Piece.WHITE_PAWN, Piece.BLACK_PAWN),
			new PieceCountSpec("knights", Piece.WHITE_KNIGHT, Piece.BLACK_KNIGHT),
			new PieceCountSpec("bishops", Piece.WHITE_BISHOP, Piece.BLACK_BISHOP),
			new PieceCountSpec("rooks", Piece.WHITE_ROOK, Piece.BLACK_ROOK),
			new PieceCountSpec("queens", Piece.WHITE_QUEEN, Piece.BLACK_QUEEN)
	};

	/**
	 * Utility class; prevent instantiation.
	 */
	private GenFensCommand() {
		// utility
	}

	/**
	 * Handles {@code fen generate}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runGenerateFens(Argv a) {
		final boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path outDir = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		final int files = a.integerOr(1_000, OPT_FILES);
		final int perFile = a.integerOr(100_000, OPT_PER_FILE, OPT_FENS_PER_FILE);
		final int chess960Files = a.integerOr(100, OPT_CHESS960_FILES, OPT_CHESS960);
		final int batch = a.integerOr(2_048, OPT_BATCH);
		final boolean ascii = a.flag(OPT_ASCII);
		final FenFilter filter = FenFilter.parse(a);
		final long maxAttempts = a.lngOr(defaultMaxAttempts(perFile, batch, filter), OPT_MAX_ATTEMPTS);

		a.ensureConsumed();

		validateGenFensArgs(files, perFile, batch, chess960Files, maxAttempts);

		if (outDir == null) {
			outDir = Paths.get("all_positions_shards");
		}

		ensureDirectoryOrExit(COMMAND_LABEL, outDir, verbose);

		final long total = (long) files * (long) perFile;
		final Bar bar = new Bar(total, "fens", ascii);
		final int width = Math.max(4, String.valueOf(Math.max(files - 1, 0)).length());
		final GenerationStats stats = new GenerationStats();
		final GenerationContext context = new GenerationContext(batch, maxAttempts, filter, bar, stats, verbose);

		for (int i = 0; i < files; i++) {
			boolean useChess960 = i < chess960Files;
			Path target = outDir.resolve(fenShardFileName(i, width, useChess960));
			writeFenShardOrExit(target, perFile, useChess960, context);
		}

		bar.finish();
		printSummary(files, chess960Files, outDir, filter, stats);
	}

	/**
	 * Validates arguments for the {@code fen generate} command and exits on violation.
	 *
	 * @param files         number of output files requested
	 * @param perFile       FENs per file
	 * @param batch         batch size for random generation
	 * @param chess960Files number of Chess960 shards to emit
	 * @param maxAttempts   maximum sampled candidate positions per shard
	 */
	private static void validateGenFensArgs(int files, int perFile, int batch, int chess960Files, long maxAttempts) {
		requirePositive(COMMAND_LABEL, OPT_FILES, files);
		requirePositive(COMMAND_LABEL, OPT_PER_FILE, perFile);
		requirePositive(COMMAND_LABEL, OPT_BATCH, batch);
		requirePositive(COMMAND_LABEL, OPT_MAX_ATTEMPTS, maxAttempts);
		requireBetweenInclusive(COMMAND_LABEL, OPT_CHESS960_FILES, chess960Files, 0, files);
		if (maxAttempts < perFile) {
			throw new IllegalArgumentException(COMMAND_LABEL + ": " + OPT_MAX_ATTEMPTS
					+ " must be at least " + OPT_PER_FILE);
		}
	}

	/**
	 * Ensures the target directory exists or exits with a diagnostic.
	 *
	 * @param cmd     command label used in diagnostics
	 * @param dir     output directory to create
	 * @param verbose whether to print stack traces on failure
	 */
	private static void ensureDirectoryOrExit(String cmd, Path dir, boolean verbose) {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			System.err.println(cmd + ": failed to create output directory: " + e.getMessage());
			if (verbose) {
				e.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Builds the output filename for a generated FEN shard.
	 *
	 * <p>
	 * Uses zero-padding for the shard index so filenames sort lexicographically
	 * (e.g. {@code fens-0001-std.txt}).
	 * </p>
	 *
	 * @param index    shard index (0-based)
	 * @param width    minimum number of digits for {@code index}
	 * @param chess960 whether the shard contains Chess960-start-derived positions
	 * @return filename (no directory component)
	 */
	private static String fenShardFileName(int index, int width, boolean chess960) {
		String suffix = chess960 ? "-960" : "-std";
		return "fens-" + zeroPad(index, width) + suffix + ".txt";
	}

	/**
	 * Pads a decimal integer with leading zeros to reach a minimum width.
	 *
	 * @param value non-negative integer value
	 * @param width minimum number of digits to return
	 * @return zero-padded decimal string (or the unmodified value when already wide enough)
	 */
	private static String zeroPad(int value, int width) {
		String raw = Integer.toString(value);
		if (raw.length() >= width) {
			return raw;
		}
		StringBuilder sb = new StringBuilder(width);
		for (int i = raw.length(); i < width; i++) {
			sb.append('0');
		}
		sb.append(raw);
		return sb.toString();
	}

	/**
	 * Writes a single FEN shard file and terminates the process on failure.
	 *
	 * @param target    output path
	 * @param fenCount  number of FENs to write
	 * @param chess960  whether to seed from Chess960 starts
	 * @param context   generation context
	 */
	private static void writeFenShardOrExit(
			Path target,
			int fenCount,
			boolean chess960,
			GenerationContext context) {
		try {
			writeFenShard(target, fenCount, chess960, context);
		} catch (IOException | IllegalStateException e) {
			System.err.println(COMMAND_LABEL + ": failed to write " + target + ": " + e.getMessage());
			if (context.verbose()) {
				e.printStackTrace(System.err);
			}
			System.exit(1);
		}
	}

	/**
	 * Writes a shard file of random FENs to disk.
	 *
	 * @param target    output path
	 * @param fenCount  number of FENs to write
	 * @param chess960  whether to seed from Chess960 starts
	 * @param context   generation context
	 * @throws IOException when writing fails
	 */
	private static void writeFenShard(
			Path target,
			int fenCount,
			boolean chess960,
			GenerationContext context) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(target)) {
			int accepted = 0;
			long attempts = 0L;
			while (accepted < fenCount && attempts < context.maxAttempts()) {
				int chunk = (int) Math.min(context.batchSize(), context.maxAttempts() - attempts);
				List<Position> positions = Setup.getRandomPositions(chunk, chess960);
				for (int i = 0; i < positions.size() && accepted < fenCount; i++) {
					Position p = positions.get(i);
					attempts++;
					context.stats().candidates++;
					if (context.filter().matches(p)) {
						writer.write(p.toString());
						writer.newLine();
						accepted++;
						context.stats().accepted++;
						context.bar().step();
					}
				}
			}
			if (accepted < fenCount) {
				throw new IllegalStateException("accepted " + accepted + " of " + fenCount
						+ " requested FENs after " + attempts + " candidates; loosen filters or raise "
						+ OPT_MAX_ATTEMPTS);
			}
		}
	}

	/**
	 * Computes the default candidate attempt limit.
	 *
	 * @param fenCount requested accepted FENs per shard
	 * @param batchSize random-generation batch size
	 * @param filter active filter
	 * @return default candidate limit per shard
	 */
	private static long defaultMaxAttempts(int fenCount, int batchSize, FenFilter filter) {
		if (filter == null || !filter.active()) {
			return fenCount;
		}
		long byAccepted = (long) fenCount * 1_000L;
		long byBatch = (long) batchSize * 100L;
		return Math.max(10_000L, Math.max(byAccepted, byBatch));
	}

	/**
	 * Prints the command summary after generation completes.
	 *
	 * @param files number of written files
	 * @param chess960Files number of Chess960 files
	 * @param outDir output directory
	 * @param filter active filter
	 * @param stats aggregate generation stats
	 */
	private static void printSummary(
			int files,
			int chess960Files,
			Path outDir,
			FenFilter filter,
			GenerationStats stats) {
		if (filter.active()) {
			System.out.printf(
					"fen generate wrote %d files (%d Chess960) to %s; accepted %d of %d candidates; filters: %s%n",
					files,
					chess960Files,
					outDir.toAbsolutePath(),
					stats.accepted,
					stats.candidates,
					filter.describe());
			return;
		}
		System.out.printf(
				"fen generate wrote %d files (%d Chess960) to %s%n",
				files,
				chess960Files,
				outDir.toAbsolutePath());
	}

	/**
	 * Describes one non-king piece count family.
	 *
	 * @param plural option stem, e.g. {@code rooks}
	 * @param whitePiece white piece code
	 * @param blackPiece black piece code
	 */
	private record PieceCountSpec(String plural, byte whitePiece, byte blackPiece) {
	}

	/**
	 * Shared generation settings for shard writers.
	 *
	 * @param batchSize positions sampled per random-generation batch
	 * @param maxAttempts candidate limit per shard
	 * @param filter position filter
	 * @param bar progress bar
	 * @param stats aggregate generation stats
	 * @param verbose whether to print stack traces on shard failures
	 */
	private record GenerationContext(
			int batchSize,
			long maxAttempts,
			FenFilter filter,
			Bar bar,
			GenerationStats stats,
			boolean verbose) {
	}

	/**
	 * Aggregate generation counters.
	 */
	private static final class GenerationStats {

		/**
		 * Candidate positions inspected.
		 */
		private long candidates;

		/**
		 * Candidate positions accepted and written.
		 */
		private long accepted;
	}

	/**
	 * Inclusive integer range used by filters.
	 */
	private static final class IntRange {

		/**
		 * Inclusive lower bound, or null.
		 */
		private Integer min;

		/**
		 * Inclusive upper bound, or null.
		 */
		private Integer max;

		/**
		 * Returns whether this range constrains values.
		 *
		 * @return true when min or max is set
		 */
		private boolean active() {
			return min != null || max != null;
		}

		/**
		 * Tests a value against the range.
		 *
		 * @param value candidate value
		 * @return true when accepted
		 */
		private boolean matches(int value) {
			return (min == null || value >= min) && (max == null || value <= max);
		}

		/**
		 * Describes the range for summary output.
		 *
		 * @param label user-facing label
		 * @return description or null when inactive
		 */
		private String describe(String label) {
			if (!active()) {
				return null;
			}
			if (min != null && min.equals(max)) {
				return label + "=" + min;
			}
			if (min != null && max != null) {
				return label + "=" + min + ".." + max;
			}
			if (min != null) {
				return label + ">=" + min;
			}
			return label + "<=" + max;
		}
	}

	/**
	 * Filter compiled from {@code fen generate} CLI flags.
	 */
	private static final class FenFilter {

		/**
		 * Side to move, null for either side.
		 */
		private Boolean whiteToMove;

		/**
		 * Whether to keep queenless positions with at most the endgame piece limit.
		 */
		private boolean endgame;

		/**
		 * Whether to keep queenless positions with at most the late-endgame piece limit.
		 */
		private boolean lateEndgame;

		/**
		 * Whether to keep positions with only kings and pawns.
		 */
		private boolean kingPawnEndgame;

		/**
		 * Whether to keep queenless minor-piece endgames without rooks.
		 */
		private boolean minorEndgame;

		/**
		 * Whether to keep queenless rook endgames without minor pieces.
		 */
		private boolean rookEndgame;

		/**
		 * Whether to reject positions containing either queen.
		 */
		private boolean queenless;

		/**
		 * Whether to require opposite-colored bishops.
		 */
		private boolean oppositeBishops;

		/**
		 * Whether to require a legal en-passant capture.
		 */
		private boolean enPassant;

		/**
		 * Whether to require a legal promotion move.
		 */
		private boolean promotion;

		/**
		 * Whether to require a legal underpromotion move.
		 */
		private boolean underpromotion;

		/**
		 * Whether to require at least one legal capture.
		 */
		private boolean capture;

		/**
		 * Whether to require any castling right in the FEN state.
		 */
		private boolean castleRights;

		/**
		 * Whether to require at least one legal castling move.
		 */
		private boolean legalCastle;

		/**
		 * Whether to require the side to move to be in check.
		 */
		private boolean inCheck;

		/**
		 * Whether to require the side to move not to be in check.
		 */
		private boolean notInCheck;

		/**
		 * Whether to require checkmate.
		 */
		private boolean checkmate;

		/**
		 * Whether to require stalemate.
		 */
		private boolean stalemate;

		/**
		 * Accepted total occupied-square count range.
		 */
		private final IntRange totalPieces = new IntRange();

		/**
		 * Accepted White occupied-square count range.
		 */
		private final IntRange whitePieces = new IntRange();

		/**
		 * Accepted Black occupied-square count range.
		 */
		private final IntRange blackPieces = new IntRange();

		/**
		 * Accepted combined material range in centipawns.
		 */
		private final IntRange totalMaterial = new IntRange();

		/**
		 * Accepted White material range in centipawns.
		 */
		private final IntRange whiteMaterial = new IntRange();

		/**
		 * Accepted Black material range in centipawns.
		 */
		private final IntRange blackMaterial = new IntRange();

		/**
		 * Accepted White-minus-Black material range in centipawns.
		 */
		private final IntRange materialDiff = new IntRange();

		/**
		 * Accepted fullmove-number range.
		 */
		private final IntRange fullmove = new IntRange();

		/**
		 * Accepted halfmove-clock range.
		 */
		private final IntRange halfmove = new IntRange();

		/**
		 * Accepted legal-move-count range.
		 */
		private final IntRange legalMoves = new IntRange();

		/**
		 * Accepted total counts by configured piece family.
		 */
		private final IntRange[] pieceTotals = newPieceRanges();

		/**
		 * Accepted White counts by configured piece family.
		 */
		private final IntRange[] whitePieceTotals = newPieceRanges();

		/**
		 * Accepted Black counts by configured piece family.
		 */
		private final IntRange[] blackPieceTotals = newPieceRanges();

		/**
		 * Maximum absolute material difference in centipawns, or null when unset.
		 */
		private Integer maxMaterialImbalance;

		/**
		 * Parses filters from CLI arguments.
		 *
		 * @param a argument parser
		 * @return compiled filter
		 */
		private static FenFilter parse(Argv a) {
			FenFilter filter = new FenFilter();
			filter.applyStage(a.string(OPT_STAGE));
			filter.endgame |= a.flag(OPT_ENDGAME);
			filter.lateEndgame |= a.flag(OPT_LATE_ENDGAME);
			filter.kingPawnEndgame |= a.flag(OPT_KING_PAWN_ENDGAME, "--pawn-endgame");
			filter.minorEndgame |= a.flag(OPT_MINOR_ENDGAME);
			filter.rookEndgame |= a.flag(OPT_ROOK_ENDGAME);
			filter.queenless |= a.flag(OPT_QUEENLESS);
			filter.oppositeBishops |= a.flag(OPT_OPPOSITE_BISHOPS, "--opposite-colored-bishops");
			filter.enPassant = a.flag(OPT_EN_PASSANT, OPT_EP);
			filter.promotion = a.flag(OPT_PROMOTION, OPT_PROMOTIONS);
			filter.underpromotion = a.flag(OPT_UNDERPROMOTION, OPT_UNDERPROMOTIONS);
			filter.capture = a.flag(OPT_CAPTURE, OPT_CAPTURES);
			filter.castleRights = a.flag(OPT_CASTLE_RIGHTS, OPT_CASTLING_RIGHTS);
			filter.legalCastle = a.flag(OPT_LEGAL_CASTLE, OPT_LEGAL_CASTLING);
			filter.inCheck = a.flag(OPT_IN_CHECK);
			filter.notInCheck = a.flag(OPT_NOT_IN_CHECK);
			filter.checkmate = a.flag(OPT_CHECKMATE);
			filter.stalemate = a.flag(OPT_STALEMATE);
			filter.whiteToMove = parseSide(a.string(OPT_SIDE));
			filter.maxMaterialImbalance = parseNonNegativeOptional(a.integer(OPT_MAX_MATERIAL_IMBALANCE),
					OPT_MAX_MATERIAL_IMBALANCE);
			readRange(a, filter.totalPieces, "--pieces", "--min-pieces", "--max-pieces", 0, MAX_BOARD_PIECES);
			readRange(a, filter.whitePieces, "--white-pieces", "--min-white-pieces", "--max-white-pieces", 0,
					MAX_BOARD_PIECES);
			readRange(a, filter.blackPieces, "--black-pieces", "--min-black-pieces", "--max-black-pieces", 0,
					MAX_BOARD_PIECES);
			readRange(a, filter.totalMaterial, "--material", "--min-material", "--max-material", 0, MAX_MATERIAL_CP);
			readRange(a, filter.whiteMaterial, "--white-material", "--min-white-material", "--max-white-material", 0,
					MAX_MATERIAL_CP);
			readRange(a, filter.blackMaterial, "--black-material", "--min-black-material", "--max-black-material", 0,
					MAX_MATERIAL_CP);
			readRange(a, filter.materialDiff, "--material-diff", "--min-material-diff", "--max-material-diff",
					-MAX_MATERIAL_CP, MAX_MATERIAL_CP);
			readRange(a, filter.fullmove, "--fullmove", "--min-fullmove", "--max-fullmove", 1, Integer.MAX_VALUE);
			readRange(a, filter.halfmove, "--halfmove", "--min-halfmove", "--max-halfmove", 0, Integer.MAX_VALUE);
			readRange(a, filter.legalMoves, "--legal-moves", "--min-legal-moves", "--max-legal-moves", 0,
					Integer.MAX_VALUE);
			filter.readPieceRanges(a);
			filter.validateConflicts();
			return filter;
		}

		/**
		 * Returns whether this filter constrains generation.
		 *
		 * @return true when any filter is active
		 */
		private boolean active() {
			return whiteToMove != null
					|| anyTrue(
							endgame,
							lateEndgame,
							kingPawnEndgame,
							minorEndgame,
							rookEndgame,
							queenless,
							oppositeBishops,
							enPassant,
							promotion,
							underpromotion,
							capture,
							castleRights,
							legalCastle,
							inCheck,
							notInCheck,
							checkmate,
							stalemate)
					|| maxMaterialImbalance != null
					|| anyActive(
							totalPieces,
							whitePieces,
							blackPieces,
							totalMaterial,
							whiteMaterial,
							blackMaterial,
							materialDiff,
							fullmove,
							halfmove,
							legalMoves)
					|| anyActive(pieceTotals)
					|| anyActive(whitePieceTotals)
					|| anyActive(blackPieceTotals);
		}

		/**
		 * Tests a position against all selected filters.
		 *
		 * @param position candidate position
		 * @return true when accepted
		 */
		private boolean matches(Position position) {
			if (!matchesSideAndState(position) || !matchesMaterial(position)) {
				return false;
			}
			if (needsLegalMoves()) {
				MoveList moves = position.legalMoves();
				if (!matchesLegalMoveFilters(position, moves)) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Returns a concise user-facing filter description.
		 *
		 * @return filter summary
		 */
		private String describe() {
			List<String> parts = new ArrayList<>();
			addFlag(parts, endgame, "endgame");
			addFlag(parts, lateEndgame, "late-endgame");
			addFlag(parts, kingPawnEndgame, "king-pawn-endgame");
			addFlag(parts, minorEndgame, "minor-endgame");
			addFlag(parts, rookEndgame, "rook-endgame");
			addFlag(parts, queenless, "queenless");
			addFlag(parts, oppositeBishops, "opposite-bishops");
			addFlag(parts, enPassant, "en-passant");
			addFlag(parts, promotion, "promotion");
			addFlag(parts, underpromotion, "underpromotion");
			addFlag(parts, capture, "capture");
			addFlag(parts, castleRights, "castle-rights");
			addFlag(parts, legalCastle, "legal-castle");
			addFlag(parts, inCheck, "in-check");
			addFlag(parts, notInCheck, "not-in-check");
			addFlag(parts, checkmate, "checkmate");
			addFlag(parts, stalemate, "stalemate");
			if (whiteToMove != null) {
				parts.add("side=" + (whiteToMove ? "white" : "black"));
			}
			addRange(parts, totalPieces, "pieces");
			addRange(parts, whitePieces, "white-pieces");
			addRange(parts, blackPieces, "black-pieces");
			addRange(parts, totalMaterial, "material");
			addRange(parts, whiteMaterial, "white-material");
			addRange(parts, blackMaterial, "black-material");
			addRange(parts, materialDiff, "material-diff");
			addRange(parts, fullmove, "fullmove");
			addRange(parts, halfmove, "halfmove");
			addRange(parts, legalMoves, "legal-moves");
			if (maxMaterialImbalance != null) {
				parts.add("material-imbalance<=" + maxMaterialImbalance);
			}
			describePieceRanges(parts);
			return parts.isEmpty() ? "none" : String.join(", ", parts);
		}

		/**
		 * Applies a named stage preset.
		 *
		 * @param raw raw stage value
		 */
		private void applyStage(String raw) {
			if (raw == null || raw.isBlank()) {
				return;
			}
			String stage = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
			switch (stage) {
				case "endgame" -> endgame = true;
				case "late", "late-endgame" -> lateEndgame = true;
				case "king-pawn", "king-pawn-endgame", "pawn", "pawn-endgame" -> kingPawnEndgame = true;
				case "minor", "minor-endgame" -> minorEndgame = true;
				case "rook", "rook-endgame" -> rookEndgame = true;
				case "queenless" -> queenless = true;
				default -> throw new IllegalArgumentException(COMMAND_LABEL + ": unsupported " + OPT_STAGE + " '"
						+ raw + "' (use endgame, late-endgame, king-pawn, minor, rook, or queenless)");
			}
		}

		/**
		 * Reads all piece-count range options.
		 *
		 * @param a argument parser
		 */
		private void readPieceRanges(Argv a) {
			for (int i = 0; i < PIECE_COUNT_SPECS.length; i++) {
				String plural = PIECE_COUNT_SPECS[i].plural();
				readRange(a, pieceTotals[i], "--" + plural, "--min-" + plural, "--max-" + plural, 0,
						MAX_BOARD_PIECES);
				readRange(a, whitePieceTotals[i], "--white-" + plural, "--min-white-" + plural,
						"--max-white-" + plural, 0, MAX_BOARD_PIECES);
				readRange(a, blackPieceTotals[i], "--black-" + plural, "--min-black-" + plural,
						"--max-black-" + plural, 0, MAX_BOARD_PIECES);
			}
		}

		/**
		 * Validates incompatible flags.
		 */
		private void validateConflicts() {
			if (inCheck && notInCheck) {
				throw new IllegalArgumentException(COMMAND_LABEL + ": choose at most one of " + OPT_IN_CHECK
						+ " and " + OPT_NOT_IN_CHECK);
			}
			if (checkmate && stalemate) {
				throw new IllegalArgumentException(COMMAND_LABEL + ": choose at most one of " + OPT_CHECKMATE
						+ " and " + OPT_STALEMATE);
			}
			if (checkmate && notInCheck) {
				throw new IllegalArgumentException(COMMAND_LABEL + ": " + OPT_CHECKMATE
						+ " conflicts with " + OPT_NOT_IN_CHECK);
			}
			if (stalemate && inCheck) {
				throw new IllegalArgumentException(COMMAND_LABEL + ": " + OPT_STALEMATE
						+ " conflicts with " + OPT_IN_CHECK);
			}
		}

		/**
		 * Tests side-to-move and check/terminal filters.
		 *
		 * @param position candidate position
		 * @return true when accepted
		 */
		private boolean matchesSideAndState(Position position) {
			if (whiteToMove != null && position.isWhiteToMove() != whiteToMove) {
				return false;
			}
			if (!fullmove.matches(position.fullMoveNumber()) || !halfmove.matches(position.halfMoveClock())) {
				return false;
			}
			if (castleRights && position.castlingRights() == 0) {
				return false;
			}
			if (inCheck || notInCheck || checkmate || stalemate) {
				boolean checked = position.inCheck();
				if (inCheck && !checked) {
					return false;
				}
				if (notInCheck && checked) {
					return false;
				}
				if ((checkmate || stalemate) && !matchesTerminal(position, checked)) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Tests checkmate and stalemate filters.
		 *
		 * @param position candidate position
		 * @param checked whether side to move is in check
		 * @return true when accepted
		 */
		private boolean matchesTerminal(Position position, boolean checked) {
			boolean hasMove = position.hasLegalMove();
			if (checkmate && (!checked || hasMove)) {
				return false;
			}
			return !stalemate || (!checked && !hasMove);
		}

		/**
		 * Tests material, piece-count, and preset filters.
		 *
		 * @param position candidate position
		 * @return true when accepted
		 */
		private boolean matchesMaterial(Position position) {
			int whitePieceCount = position.countWhitePieces();
			int blackPieceCount = position.countBlackPieces();
			int totalPieceCount = whitePieceCount + blackPieceCount;
			int whiteMaterialCp = position.countWhiteMaterial();
			int blackMaterialCp = position.countBlackMaterial();
			int materialDifference = whiteMaterialCp - blackMaterialCp;
			if (!totalPieces.matches(totalPieceCount)
					|| !whitePieces.matches(whitePieceCount)
					|| !blackPieces.matches(blackPieceCount)
					|| !totalMaterial.matches(whiteMaterialCp + blackMaterialCp)
					|| !whiteMaterial.matches(whiteMaterialCp)
					|| !blackMaterial.matches(blackMaterialCp)
					|| !materialDiff.matches(materialDifference)) {
				return false;
			}
			if (maxMaterialImbalance != null && Math.abs(materialDifference) > maxMaterialImbalance) {
				return false;
			}
			PieceCounts counts = pieceCounts(position);
			return matchesPieceRanges(counts) && matchesPresetMaterial(position, counts, totalPieceCount);
		}

		/**
		 * Tests piece-count ranges.
		 *
		 * @param counts cached piece counts
		 * @return true when accepted
		 */
		private boolean matchesPieceRanges(PieceCounts counts) {
			for (int i = 0; i < PIECE_COUNT_SPECS.length; i++) {
				if (!pieceTotals[i].matches(counts.total(i))
						|| !whitePieceTotals[i].matches(counts.white(i))
						|| !blackPieceTotals[i].matches(counts.black(i))) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Tests named material presets.
		 *
		 * @param position candidate position
		 * @param counts cached counts
		 * @param totalPieceCount total occupied squares
		 * @return true when accepted
		 */
		private boolean matchesPresetMaterial(Position position, PieceCounts counts, int totalPieceCount) {
			int queens = counts.total(PIECE_INDEX_QUEENS);
			int rooks = counts.total(PIECE_INDEX_ROOKS);
			int bishops = counts.total(PIECE_INDEX_BISHOPS);
			int knights = counts.total(PIECE_INDEX_KNIGHTS);
			int minors = bishops + knights;
			if (queenless && queens != 0) {
				return false;
			}
			if (endgame && (queens != 0 || totalPieceCount > ENDGAME_MAX_PIECES)) {
				return false;
			}
			if (lateEndgame && (queens != 0 || totalPieceCount > LATE_ENDGAME_MAX_PIECES)) {
				return false;
			}
			if (kingPawnEndgame && (queens + rooks + minors) != 0) {
				return false;
			}
			if (minorEndgame && (queens != 0 || rooks != 0 || minors == 0)) {
				return false;
			}
			if (rookEndgame && (queens != 0 || minors != 0 || counts.white(PIECE_INDEX_ROOKS) == 0
					|| counts.black(PIECE_INDEX_ROOKS) == 0)) {
				return false;
			}
			return !oppositeBishops || position.hasOppositeColoredBishops();
		}

		/**
		 * Returns whether legal move generation is needed.
		 *
		 * @return true when any selected filter inspects legal moves
		 */
		private boolean needsLegalMoves() {
			return enPassant
					|| promotion
					|| underpromotion
					|| capture
					|| legalCastle
					|| legalMoves.active();
		}

		/**
		 * Tests filters that inspect legal moves.
		 *
		 * @param position candidate position
		 * @param moves legal moves
		 * @return true when accepted
		 */
		private boolean matchesLegalMoveFilters(Position position, MoveList moves) {
			if (!legalMoves.matches(moves.size())) {
				return false;
			}
			if (enPassant && !hasEnPassantCapture(position, moves)) {
				return false;
			}
			if (promotion && !hasPromotion(moves, false)) {
				return false;
			}
			if (underpromotion && !hasPromotion(moves, true)) {
				return false;
			}
			if (capture && !hasCapture(position, moves)) {
				return false;
			}
			return !legalCastle || hasLegalCastle(position, moves);
		}

		/**
		 * Adds piece ranges to the summary.
		 *
		 * @param parts summary parts
		 */
		private void describePieceRanges(List<String> parts) {
			for (int i = 0; i < PIECE_COUNT_SPECS.length; i++) {
				String name = PIECE_COUNT_SPECS[i].plural();
				addRange(parts, pieceTotals[i], name);
				addRange(parts, whitePieceTotals[i], "white-" + name);
				addRange(parts, blackPieceTotals[i], "black-" + name);
			}
		}
	}

	/**
	 * Cached piece counts for one candidate position.
	 */
	private static final class PieceCounts {

		/**
		 * White counts by configured piece family.
		 */
		private final int[] white;

		/**
		 * Black counts by configured piece family.
		 */
		private final int[] black;

		/**
		 * Creates cached counts.
		 *
		 * @param white white piece counts
		 * @param black black piece counts
		 */
		private PieceCounts(int[] white, int[] black) {
			this.white = white;
			this.black = black;
		}

		/**
		 * Returns total count for one piece family.
		 *
		 * @param index family index
		 * @return total count
		 */
		private int total(int index) {
			return white[index] + black[index];
		}

		/**
		 * Returns White count for one piece family.
		 *
		 * @param index family index
		 * @return White count
		 */
		private int white(int index) {
			return white[index];
		}

		/**
		 * Returns Black count for one piece family.
		 *
		 * @param index family index
		 * @return Black count
		 */
		private int black(int index) {
			return black[index];
		}
	}

	/**
	 * Creates a fresh piece-range array.
	 *
	 * @return range array
	 */
	private static IntRange[] newPieceRanges() {
		IntRange[] ranges = new IntRange[PIECE_COUNT_SPECS.length];
		for (int i = 0; i < ranges.length; i++) {
			ranges[i] = new IntRange();
		}
		return ranges;
	}

	/**
	 * Reads exact/min/max range options.
	 *
	 * @param a argument parser
	 * @param range target range
	 * @param exactOpt exact-count option
	 * @param minOpt minimum option
	 * @param maxOpt maximum option
	 * @param minAllowed minimum legal value
	 * @param maxAllowed maximum legal value
	 */
	private static void readRange(
			Argv a,
			IntRange range,
			String exactOpt,
			String minOpt,
			String maxOpt,
			int minAllowed,
			int maxAllowed) {
		Integer exact = exactOpt == null ? null : a.integer(exactOpt);
		Integer min = minOpt == null ? null : a.integer(minOpt);
		Integer max = maxOpt == null ? null : a.integer(maxOpt);
		if (exact != null && (min != null || max != null)) {
			throw new IllegalArgumentException(COMMAND_LABEL + ": " + exactOpt
					+ " cannot be combined with " + minOpt + " or " + maxOpt);
		}
		if (exact != null) {
			validateRangeValue(exactOpt, exact, minAllowed, maxAllowed);
			range.min = exact;
			range.max = exact;
			return;
		}
		if (min != null) {
			validateRangeValue(minOpt, min, minAllowed, maxAllowed);
			range.min = min;
		}
		if (max != null) {
			validateRangeValue(maxOpt, max, minAllowed, maxAllowed);
			range.max = max;
		}
		if (range.min != null && range.max != null && range.min > range.max) {
			throw new IllegalArgumentException(COMMAND_LABEL + ": " + minOpt + " must be <= " + maxOpt);
		}
	}

	/**
	 * Validates a range endpoint.
	 *
	 * @param opt option name
	 * @param value value
	 * @param minAllowed minimum legal value
	 * @param maxAllowed maximum legal value
	 */
	private static void validateRangeValue(String opt, int value, int minAllowed, int maxAllowed) {
		if (value < minAllowed || value > maxAllowed) {
			throw new IllegalArgumentException(COMMAND_LABEL + ": " + opt + " must be between "
					+ minAllowed + " and " + maxAllowed);
		}
	}

	/**
	 * Parses a non-negative optional integer.
	 *
	 * @param value raw value
	 * @param opt option name
	 * @return value or null
	 */
	private static Integer parseNonNegativeOptional(Integer value, String opt) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(COMMAND_LABEL + ": " + opt + " must be non-negative");
		}
		return value;
	}

	/**
	 * Parses side-to-move filter text.
	 *
	 * @param raw raw side value
	 * @return true for White, false for Black, null for either side
	 */
	private static Boolean parseSide(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String side = raw.trim().toLowerCase(Locale.ROOT);
		return switch (side) {
			case "w", "white" -> Boolean.TRUE;
			case "b", "black" -> Boolean.FALSE;
			default -> throw new IllegalArgumentException(COMMAND_LABEL + ": unsupported " + OPT_SIDE + " '"
					+ raw + "' (use white|black|w|b)");
		};
	}

	/**
	 * Counts configured piece families for a position.
	 *
	 * @param position candidate position
	 * @return cached counts
	 */
	private static PieceCounts pieceCounts(Position position) {
		int[] white = new int[PIECE_COUNT_SPECS.length];
		int[] black = new int[PIECE_COUNT_SPECS.length];
		for (int i = 0; i < PIECE_COUNT_SPECS.length; i++) {
			PieceCountSpec spec = PIECE_COUNT_SPECS[i];
			white[i] = position.countPieces(spec.whitePiece());
			black[i] = position.countPieces(spec.blackPiece());
		}
		return new PieceCounts(white, black);
	}

	/**
	 * Tests whether any range is active.
	 *
	 * @param ranges ranges
	 * @return true when at least one constrains values
	 */
	private static boolean anyActive(IntRange... ranges) {
		for (IntRange range : ranges) {
			if (range.active()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Tests whether any boolean flag is true.
	 *
	 * @param values boolean values
	 * @return true when at least one value is true
	 */
	private static boolean anyTrue(boolean... values) {
		for (boolean value : values) {
			if (value) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether a position has a legal en-passant capture.
	 *
	 * @param position candidate position
	 * @param moves legal moves
	 * @return true when accepted
	 */
	private static boolean hasEnPassantCapture(Position position, MoveList moves) {
		byte ep = position.enPassantSquare();
		if (ep == Field.NO_SQUARE) {
			return false;
		}
		for (int i = 0; i < moves.size(); i++) {
			short move = moves.raw(i);
			if (Move.getToIndex(move) == ep
					&& Piece.isPawn(position.pieceAt(Move.getFromIndex(move)))
					&& position.isCapture(move)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether a legal promotion is available.
	 *
	 * @param moves legal moves
	 * @param underOnly whether to require underpromotion
	 * @return true when accepted
	 */
	private static boolean hasPromotion(MoveList moves, boolean underOnly) {
		for (int i = 0; i < moves.size(); i++) {
			short move = moves.raw(i);
			if (underOnly ? Move.isUnderPromotion(move) : Move.isPromotion(move)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether a legal capture is available.
	 *
	 * @param position candidate position
	 * @param moves legal moves
	 * @return true when accepted
	 */
	private static boolean hasCapture(Position position, MoveList moves) {
		for (int i = 0; i < moves.size(); i++) {
			if (position.isCapture(moves.raw(i))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether a legal castling move is available.
	 *
	 * @param position candidate position
	 * @param moves legal moves
	 * @return true when accepted
	 */
	private static boolean hasLegalCastle(Position position, MoveList moves) {
		for (int i = 0; i < moves.size(); i++) {
			if (position.isCastle(moves.raw(i))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Appends a flag description when active.
	 *
	 * @param parts destination list
	 * @param active whether active
	 * @param label label
	 */
	private static void addFlag(List<String> parts, boolean active, String label) {
		if (active) {
			parts.add(label);
		}
	}

	/**
	 * Appends a range description when active.
	 *
	 * @param parts destination list
	 * @param range range
	 * @param label label
	 */
	private static void addRange(List<String> parts, IntRange range, String label) {
		String description = range.describe(label);
		if (description != null) {
			parts.add(description);
		}
	}
}
