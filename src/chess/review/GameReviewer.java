package chess.review;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;
import chess.eval.Classical;
import chess.pgn.GameIdentity;
import chess.struct.Game;
import chess.tag.Delta;
import chess.tag.Generator;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Engine;
import chess.uci.Evaluation;
import chess.uci.Output;
import chess.uci.Protocol;

/**
 * Deterministic game-review row assembler.
 *
 * <p>This class owns the reusable review orchestration that sits below the CLI
 * and Workbench. The first implementation is deliberately offline-only: it
 * uses the in-process {@link AlphaBeta} searcher with caller-supplied fixed
 * limits, then feeds the resulting scores into {@link Classifier} and
 * {@link ReviewRow}. External UCI orchestration can reuse the same row assembly
 * contract later without changing the emitted JSON shape.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GameReviewer {

	/**
	 * Engine label recorded in offline review rows.
	 */
	public static final String OFFLINE_ENGINE_LABEL = "offline-alpha-beta";

	/**
	 * Search-mode label recorded in offline review rows.
	 */
	public static final String OFFLINE_SEARCH_MODE = "offline";

	/**
	 * Search-mode label recorded in external-UCI review rows.
	 */
	public static final String UCI_SEARCH_MODE = "uci";

	/**
	 * Maximum number of PV plies carried into one review row.
	 */
	private static final int PV_CAP = 8;

	/**
	 * Classifier centipawn surrogate used for mate scores.
	 */
	private static final int MATE_CLASSIFIER_CP = 100_000;

	/**
	 * Default UCI hash value recorded for offline rows.
	 */
	private static final int OFFLINE_HASH_MB = 0;

	/**
	 * Utility class; prevent instantiation.
	 */
	private GameReviewer() {
		// utility
	}

	/**
	 * Reviews a list of games using the built-in offline searcher.
	 *
	 * @param games parsed PGN games
	 * @param options review options
	 * @return review output rows and counts
	 */
	public static Review reviewOffline(List<Game> games, Options options) {
		Objects.requireNonNull(games, "games");
		Objects.requireNonNull(options, "options");
		try (ReviewBackend backend = new OfflineBackend(options)) {
			return review(games, options.limit(), options.sourceLabel(), options.thresholds(), backend);
		} catch (IOException ex) {
			throw new IllegalStateException("offline review failed", ex);
		}
	}

	/**
	 * Reviews a list of games using an external UCI engine.
	 *
	 * @param games parsed PGN games
	 * @param options UCI review options
	 * @return review output rows and counts
	 * @throws IOException if the engine cannot be started or queried
	 */
	public static Review reviewUci(List<Game> games, UciOptions options) throws IOException {
		Objects.requireNonNull(games, "games");
		Objects.requireNonNull(options, "options");
		try (ReviewBackend backend = new UciBackend(options)) {
			return review(games, options.limit(), options.sourceLabel(), options.thresholds(), backend);
		}
	}

	/**
	 * Reviews games through a backend-neutral search adapter.
	 *
	 * @param games parsed PGN games
	 * @param limit maximum rows to emit, or zero for no cap
	 * @param sourceLabel source prefix such as {@code pgn:file.pgn}
	 * @param thresholds classifier thresholds
	 * @param backend review backend
	 * @return review output rows and counts
	 * @throws IOException when backend analysis fails
	 */
	private static Review review(
			List<Game> games,
			int limit,
			String sourceLabel,
			Classifier.Thresholds thresholds,
			ReviewBackend backend) throws IOException {
		List<ReviewRow> rows = new ArrayList<>();
		for (int i = 0; i < games.size(); i++) {
			if (limit > 0 && rows.size() >= limit) {
				break;
			}
			reviewGame(backend, games.get(i), i + 1, limit, sourceLabel, thresholds, rows);
		}
		return new Review(List.copyOf(rows), games.size());
	}

	/**
	 * Reviews one game and appends rows until the optional global cap is reached.
	 *
	 * @param searcher offline searcher
	 * @param game game to review
	 * @param gameIndex one-based game index in the input source
	 * @param options review options
	 * @param rows destination rows
	 * @param backend review engine backend
	 * @param limit maximum games or rows to process
	 * @param sourceLabel label for the reviewed input source
	 * @param thresholds review classification thresholds
	 * @throws java.io.IOException if external I/O or engine communication fails
	 */
	private static void reviewGame(
			ReviewBackend backend,
			Game game,
			int gameIndex,
			int limit,
			String sourceLabel,
			Classifier.Thresholds thresholds,
			List<ReviewRow> rows) throws IOException {
		if (game == null) {
			return;
		}
		String gameId = GameIdentity.compute(game);
		String source = sourceLabel + "#" + gameIndex;
		Map<String, String> headers = game.getTags();
		Position cursor = game.getStartPosition() == null
				? new Position(Game.STANDARD_START_FEN)
				: game.getStartPosition().copy();
		List<String> sourceOpeningTags = Generator.tags(cursor);
		int ply = 0;
		for (Game.Node node = game.getMainline(); node != null; node = node.getNext()) {
			if (limit > 0 && rows.size() >= limit) {
				return;
			}
			if (node.getSan() == null || node.getSan().isBlank()) {
				continue;
			}
			ReviewRow row = reviewPly(backend, gameId, source, headers, cursor, node.getSan(),
					ply, thresholds, sourceOpeningTags);
			rows.add(row);
			cursor = cursor.copy().play(SAN.fromAlgebraic(cursor, node.getSan()));
			ply++;
		}
	}

	/**
	 * Reviews a single legal ply.
	 *
	 * @param searcher offline searcher
	 * @param gameId canonical game identifier
	 * @param source game source label
	 * @param headers PGN headers
	 * @param before position before the played move
	 * @param playedSanSource SAN from the PGN node
	 * @param ply zero-based ply index
	 * @param options review options
	 * @param sourceOpeningTags opening tags from the game start
	 * @param backend review engine backend
	 * @param thresholds review classification thresholds
	 * @return review row
	 * @throws java.io.IOException if external I/O or engine communication fails
	 */
	private static ReviewRow reviewPly(
			ReviewBackend backend,
			String gameId,
			String source,
			Map<String, String> headers,
			Position before,
			String playedSanSource,
			int ply,
			Classifier.Thresholds thresholds,
			List<String> sourceOpeningTags) throws IOException {
		short playedMove = SAN.fromAlgebraic(before, playedSanSource);
		String playedSan = safeSan(before, playedMove, playedSanSource);
		Position after = before.copy().play(playedMove);
		BackendAnalysis beforeAnalysis = backend.analyze(before);
		BackendAnalysis afterAnalysis = backend.analyze(after);
		List<String> beforeTags = Generator.inheritOpeningTags(Generator.tags(before), sourceOpeningTags);
		List<String> afterTags = Generator.inheritOpeningTags(Generator.tags(after), beforeTags);
		Delta delta = Delta.diff(beforeTags, afterTags);
		Classifier.Verdict verdict = Classifier.classify(new Classifier.Request(
				beforeAnalysis.bestScore().score(1),
				afterAnalysis.bestScore().score(-1),
				scoreOrNull(beforeAnalysis.secondBestScore()),
				thresholds,
				isTheoryPosition(beforeTags, ply)));
		short bestMove = beforeAnalysis.bestMove();
		return new ReviewRow(
				new ReviewRow.GameRef(
						gameId,
						source,
						header(headers, "Event"),
						header(headers, "White"),
						header(headers, "Black"),
						extractValue(beforeTags, "OPENING: eco="),
						extractQuotedValue(beforeTags, "OPENING: name=\"")),
				new ReviewRow.Ply(
						ply,
						before.fullMoveNumber(),
						before.isWhiteToMove() ? ReviewRow.Color.WHITE : ReviewRow.Color.BLACK,
						before.toString()),
				new ReviewRow.MoveChoice(
						Move.toString(playedMove),
						playedSan,
						bestMove == Move.NO_MOVE ? null : Move.toString(bestMove),
						bestMove == Move.NO_MOVE ? null : safeSan(before, bestMove, null),
						beforeAnalysis.pvBest(),
						secondBestCp(beforeAnalysis.secondBestScore())),
				new ReviewRow.Assessment(
						beforeAnalysis.bestScore().eval(1),
						afterAnalysis.bestScore().eval(-1),
						verdict,
						mistakeMotif(delta.added())),
				new ReviewRow.Tags(
						beforeTags,
						afterTags,
						delta.added(),
						delta.removed(),
						changedLines(delta.changed())),
				phase(beforeTags),
				recommendedAction(verdict.category()),
				null,
				backend.repro());
	}

	/**
	 * Returns the classifier score for an optional second-best raw score.
	 *
	 * @param score side-to-move raw score
	 * @return score from the mover's perspective, or {@code null}
	 */
	private static Classifier.Score scoreOrNull(RawScore score) {
		return score == null ? null : score.score(1);
	}

	/**
	 * Returns the published second-best centipawn score from the mover's perspective.
	 *
	 * @param score side-to-move raw score
	 * @return second-best score, or {@code null}
	 */
	private static Integer secondBestCp(RawScore score) {
		return score == null ? null : Integer.valueOf(score.classifierCp(1));
	}

	/**
	 * Returns a capped PV as UCI moves.
	 *
	 * @param raw raw PV move array
	 * @param fallbackMove best root move used when no PV is available
	 * @return UCI PV moves
	 */
	private static List<String> pv(short[] raw, short fallbackMove) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < raw.length && out.size() < PV_CAP; i++) {
			if (raw[i] != Move.NO_MOVE) {
				out.add(Move.toString(raw[i]));
			}
		}
		if (out.isEmpty() && fallbackMove != Move.NO_MOVE) {
			out.add(Move.toString(fallbackMove));
		}
		return out;
	}

	/**
	 * Returns a capped PV from UCI output.
	 *
	 * @param output parsed UCI output
	 * @param fallbackMove best root move used when no PV is available
	 * @return UCI PV moves
	 */
	private static List<String> pv(Output output, short fallbackMove) {
		short[] moves = output == null ? null : output.getMoves();
		return pv(moves == null ? new short[0] : moves, fallbackMove);
	}

	/**
	 * Returns a SAN string, falling back to the source token or UCI.
	 *
	 * @param position pre-move position
	 * @param move move to render
	 * @param fallback fallback SAN text
	 * @return SAN or deterministic fallback
	 */
	private static String safeSan(Position position, short move, String fallback) {
		if (move == Move.NO_MOVE) {
			return fallback;
		}
		try {
			return SAN.toAlgebraic(position, move);
		} catch (IllegalArgumentException ex) {
			return fallback == null || fallback.isBlank() ? Move.toString(move) : fallback;
		}
	}

	/**
	 * Returns a PGN header value.
	 *
	 * @param headers header map
	 * @param key header key
	 * @return header value or {@code null}
	 */
	private static String header(Map<String, String> headers, String key) {
		String value = headers == null ? null : headers.get(key);
		return value == null || value.isBlank() || "?".equals(value) ? null : value;
	}

	/**
	 * Extracts a raw value after a tag prefix.
	 *
	 * @param tags source tags
	 * @param prefix tag prefix
	 * @return extracted value or {@code null}
	 */
	private static String extractValue(List<String> tags, String prefix) {
		for (String tag : tags) {
			if (tag.startsWith(prefix)) {
				String value = tag.substring(prefix.length());
				return value.isBlank() ? null : value;
			}
		}
		return null;
	}

	/**
	 * Extracts a quoted tag value after a prefix.
	 *
	 * @param tags source tags
	 * @param prefix tag prefix including opening quote
	 * @return unquoted value or {@code null}
	 */
	private static String extractQuotedValue(List<String> tags, String prefix) {
		for (String tag : tags) {
			if (!tag.startsWith(prefix)) {
				continue;
			}
			String rest = tag.substring(prefix.length());
			int end = rest.indexOf('"');
			String value = end >= 0 ? rest.substring(0, end) : rest;
			return value.isBlank() ? null : value.replace("\\\"", "\"");
		}
		return null;
	}

	/**
	 * Extracts the coarse phase from tags.
	 *
	 * @param tags source tags
	 * @return phase or {@code null}
	 */
	private static String phase(List<String> tags) {
		return extractValue(tags, "META: phase=");
	}

	/**
	 * Returns whether a ply is still considered opening theory for suppression.
	 *
	 * @param tags pre-move tags
	 * @param ply zero-based ply index
	 * @return true when the row appears to be in early opening theory
	 */
	private static boolean isTheoryPosition(List<String> tags, int ply) {
		return ply < 20 && (extractValue(tags, "OPENING: eco=") != null
				|| extractQuotedValue(tags, "OPENING: name=\"") != null);
	}

	/**
	 * Picks a stable recommended action from the mistake category.
	 *
	 * @param category mistake category
	 * @return row action label
	 */
	private static String recommendedAction(Classifier.Category category) {
		return switch (category) {
			case BLUNDER, MISTAKE -> "drill_puzzle";
			case INACCURACY -> "describe_study";
			case OK -> "none";
		};
	}

	/**
	 * Extracts a motif from newly-added tags.
	 *
	 * @param added added tag lines
	 * @return motif or {@code null}
	 */
	private static String mistakeMotif(List<String> added) {
		for (String tag : added) {
			int index = tag.indexOf("motif=");
			if (index >= 0) {
				String rest = tag.substring(index + "motif=".length());
				int end = rest.indexOf(' ');
				return end >= 0 ? rest.substring(0, end) : rest;
			}
		}
		return null;
	}

	/**
	 * Converts structured changes to the row's compact string list.
	 *
	 * @param changes changed tag records
	 * @return stable change descriptions
	 */
	private static List<String> changedLines(List<Delta.Change> changes) {
		if (changes == null || changes.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (Delta.Change change : changes) {
			out.add(change.key + ": " + change.from + " -> " + change.to);
		}
		return out;
	}

	/**
	 * Backend adapter used by review row assembly.
	 */
	private interface ReviewBackend extends AutoCloseable {

		/**
		 * Analyzes a position from its side-to-move perspective.
		 *
		 * @param position position to analyze
		 * @return backend analysis
		 * @throws IOException when the backend cannot complete the search
		 */
		BackendAnalysis analyze(Position position) throws IOException;

		/**
		 * Returns row reproducibility metadata for this backend.
		 *
		 * @return reproducibility block
		 */
		ReviewRow.Repro repro();

		/**
		 * Closes backend resources.
		 *
		 * @throws IOException when close fails
		 */
		@Override
		void close() throws IOException;
	}

	/**
	 * Offline built-in alpha-beta backend adapter.
	 */
	private static final class OfflineBackend implements ReviewBackend {

		/**
		 * Offline review options.
		 */
		private final Options options;

		/**
		 * Built-in searcher.
		 */
		private final AlphaBeta searcher;

		/**
		 * Creates the offline adapter.
		 *
		 * @param options review options
		 */
		private OfflineBackend(Options options) {
			this.options = options;
			this.searcher = new AlphaBeta(new Classical(), false);
			this.searcher.setSearchThreads(1);
		}

		/**
		 * Searches a position with fixed alpha-beta limits.
		 */
		@Override
		public BackendAnalysis analyze(Position position) {
			Result result = searcher.search(position, options.limits());
			short bestMove = result.bestMove();
			return new BackendAnalysis(
					RawScore.fromResult(result),
					bestMove,
					pv(result.principalVariation(), bestMove),
					null);
		}

		/**
		 * Returns offline reproducibility metadata.
		 */
		@Override
		public ReviewRow.Repro repro() {
			return new ReviewRow.Repro(
					OFFLINE_ENGINE_LABEL,
					null,
					options.limits().maxNodes(),
					options.limits().maxDurationMillis(),
					1,
					1,
					OFFLINE_HASH_MB,
					OFFLINE_SEARCH_MODE,
					options.crtkVersion(),
					true);
		}

		/**
		 * Closes the built-in searcher.
		 */
		@Override
		public void close() {
			searcher.close();
		}
	}

	/**
	 * External UCI backend adapter.
	 */
	private static final class UciBackend implements ReviewBackend {

		/**
		 * UCI review options.
		 */
		private final UciOptions options;

		/**
		 * Engine process wrapper.
		 */
		private final Engine engine;

		/**
		 * Engine label recorded in rows.
		 */
		private final String engineLabel;

		/**
		 * Creates and configures the UCI adapter.
		 *
		 * @param options UCI review options
		 * @throws IOException when the engine cannot be started
		 */
		private UciBackend(UciOptions options) throws IOException {
			this.options = options;
			requireProtocolCommand(options.protocol().getSetThreadAmount(), "setThreadAmount");
			requireProtocolCommand(options.protocol().getSetHashSize(), "setHashSize");
			requireProtocolCommand(options.protocol().getSetMultiPivotAmount(), "setMultiPivotAmount");
			if (options.showWdl()) {
				requireProtocolCommand(options.protocol().getShowWinDrawLoss(), "showWinDrawLoss");
			}
			this.engine = new Engine(options.protocol());
			this.engineLabel = protocolLabel(options.protocol());
			engine.setThreadAmount(options.threads());
			engine.setHashSize(options.hash());
			engine.setMultiPivot(options.multipv());
			if (options.showWdl()) {
				engine.showWinDrawLoss(true);
			}
		}

		/**
		 * Runs a bounded UCI search.
		 */
		@Override
		public BackendAnalysis analyze(Position position) throws IOException {
			Analysis analysis = new Analysis();
			engine.analyse(position, analysis, null, options.maxNodes(), options.maxDurationMillis());
			Output best = analysis.getBestOutput(1);
			Output second = analysis.getBestOutput(2);
			short bestMove = analysis.getBestMove(1);
			return new BackendAnalysis(
					RawScore.fromOutput(best),
					bestMove,
					pv(best, bestMove),
					second == null ? null : RawScore.fromOutput(second));
		}

		/**
		 * Returns UCI reproducibility metadata.
		 */
		@Override
		public ReviewRow.Repro repro() {
			return new ReviewRow.Repro(
					engineLabel,
					options.protocolPath(),
					options.maxNodes(),
					options.maxDurationMillis(),
					options.multipv(),
					options.threads(),
					options.hash(),
					UCI_SEARCH_MODE,
					options.crtkVersion(),
					false);
		}

		/**
		 * Closes the engine process.
		 */
		@Override
		public void close() {
			engine.close();
		}

		/**
		 * Requires a protocol command template needed for reproducible review.
		 *
		 * @param command command template
		 * @param key protocol key name
		 * @throws IOException when the template is missing
		 */
		private static void requireProtocolCommand(String command, String key) throws IOException {
			if (command == null || command.isBlank()) {
				throw new IOException("protocol is missing required review command: " + key);
			}
		}

		/**
		 * Returns a stable engine label from protocol metadata.
		 *
		 * @param protocol protocol metadata
		 * @return engine label
		 */
		private static String protocolLabel(Protocol protocol) {
			return protocol.getName() != null && !protocol.getName().isBlank()
					? protocol.getName()
					: protocol.getPath();
		}
	}

	/**
	 * Backend analysis for one side-to-move position.
	 *
	 * @param bestScore best PV score from the side-to-move perspective
	 * @param bestMove best root move
	 * @param pvBest capped best PV in UCI notation
	 * @param secondBestScore optional MultiPV-2 score from the side-to-move perspective
	 */
	private record BackendAnalysis(
			RawScore bestScore,
			short bestMove,
			List<String> pvBest,
			RawScore secondBestScore) {

		/**
		 * Creates a backend analysis record.
		 *
		 * @param bestScore best-move score
		 * @param bestMove engine best move
		 * @param pvBest principal variation for the best move
		 * @param secondBestScore second-best move score
		 */
		private BackendAnalysis {
			Objects.requireNonNull(bestScore, "bestScore");
			pvBest = pvBest == null ? List.of() : List.copyOf(pvBest);
		}
	}

	/**
	 * Raw side-to-move score from a backend.
	 *
	 * @param cp centipawn score, or {@code null} for mate scores
	 * @param mate mate score, or {@code null} for centipawn scores
	 * @param wdl optional WDL tuple from the side-to-move perspective
	 */
	private record RawScore(Integer cp, Integer mate, ReviewRow.Wdl wdl) {

		/**
		 * Creates a raw score.
		 *
		 * @param cp centipawn score
		 * @param mate mate score, or null when absent
		 * @param wdl win-draw-loss probabilities
		 */
		private RawScore {
			if (cp == null && mate == null) {
				throw new IllegalArgumentException("raw score must include cp or mate");
			}
		}

		/**
		 * Converts a built-in search result.
		 *
		 * @param result search result
		 * @return raw score
		 */
		private static RawScore fromResult(Result result) {
			if (result.isMateScore()) {
				return new RawScore(null, result.mateIn(), null);
			}
			return new RawScore(result.scoreCentipawns(), null, null);
		}

		/**
		 * Converts parsed UCI output.
		 *
		 * @param output UCI output
		 * @return raw score
		 */
		private static RawScore fromOutput(Output output) {
			if (output == null || !output.hasEvaluation()) {
				throw new IllegalArgumentException("engine produced no usable evaluation");
			}
			Evaluation evaluation = output.getEvaluation();
			ReviewRow.Wdl wdl = wdl(output.getChances());
			if (evaluation.isMate()) {
				return new RawScore(null, evaluation.getValue(), wdl);
			}
			return new RawScore(evaluation.getValue(), null, wdl);
		}

		/**
		 * Returns this score as a row eval block from the mover's perspective.
		 *
		 * @param sign {@code 1} for same side, {@code -1} to invert
		 * @return eval block
		 */
		private ReviewRow.Eval eval(int sign) {
			Integer signedCp = cp == null ? null : Integer.valueOf(sign * cp.intValue());
			Integer signedMate = mate == null ? null : Integer.valueOf(sign * mate.intValue());
			return new ReviewRow.Eval(signedCp, signedMate, wdl(sign));
		}

		/**
		 * Returns this score as a classifier score from the mover's perspective.
		 *
		 * @param sign {@code 1} for same side, {@code -1} to invert
		 * @return classifier score
		 */
		private Classifier.Score score(int sign) {
			Double winShare = winShare(sign);
			return winShare == null
					? Classifier.Score.centipawns(classifierCp(sign))
					: Classifier.Score.withWinShare(classifierCp(sign), winShare.doubleValue());
		}

		/**
		 * Returns a centipawn-like score for classifier arithmetic.
		 *
		 * @param sign {@code 1} for same side, {@code -1} to invert
		 * @return centipawn-like score
		 */
		private int classifierCp(int sign) {
			if (cp != null) {
				return sign * cp.intValue();
			}
			int signedMate = sign * mate.intValue();
			int distance = Math.min(999, Math.abs(signedMate));
			return signedMate >= 0
					? MATE_CLASSIFIER_CP - distance
					: -MATE_CLASSIFIER_CP + distance;
		}

		/**
		 * Returns this score's WDL tuple from the requested perspective.
		 *
		 * @param sign {@code 1} for same side, {@code -1} to invert
		 * @return WDL tuple or {@code null}
		 */
		private ReviewRow.Wdl wdl(int sign) {
			if (wdl == null) {
				return null;
			}
			return sign >= 0 ? wdl : new ReviewRow.Wdl(wdl.loss(), wdl.draw(), wdl.win());
		}

		/**
		 * Returns WDL win share from the requested perspective.
		 *
		 * @param sign {@code 1} for same side, {@code -1} to invert
		 * @return win share or {@code null}
		 */
		private Double winShare(int sign) {
			ReviewRow.Wdl perspective = wdl(sign);
			return perspective == null ? null : Double.valueOf(perspective.win() / 1000.0d);
		}

		/**
		 * Converts UCI WDL chances to row WDL.
		 *
		 * @param chances UCI chances
		 * @return row WDL or {@code null}
		 */
		private static ReviewRow.Wdl wdl(Chances chances) {
			return chances == null
					? null
					: new ReviewRow.Wdl(
							chances.getWinChance(),
							chances.getDrawChance(),
							chances.getLossChance());
		}
	}

	/**
	 * Review options for offline game review.
	 *
	 * @param limits fixed alpha-beta search limits
	 * @param limit maximum rows to emit, or zero for no cap
	 * @param sourceLabel source prefix such as {@code pgn:file.pgn}
	 * @param thresholds classifier thresholds
	 * @param crtkVersion CRTK version string
	 */
	public record Options(
			Limits limits,
			int limit,
			String sourceLabel,
			Classifier.Thresholds thresholds,
			String crtkVersion) {

		/**
		 * Creates and validates options.
		 *
		 * @param limits search limits
		 * @param limit maximum games or rows to process
		 * @param sourceLabel label for the reviewed input source
		 * @param thresholds review classification thresholds
		 * @param crtkVersion ChessRTK version string
		 */
		public Options {
			Objects.requireNonNull(limits, "limits");
			if (limit < 0) {
				throw new IllegalArgumentException("limit must be non-negative");
			}
			Objects.requireNonNull(sourceLabel, "sourceLabel");
			thresholds = thresholds == null ? Classifier.Thresholds.classical() : thresholds;
			Objects.requireNonNull(crtkVersion, "crtkVersion");
		}
	}

	/**
	 * Review options for external UCI game review.
	 *
	 * @param protocol parsed UCI protocol metadata
	 * @param protocolPath protocol TOML path used for provenance
	 * @param maxNodes node budget per search
	 * @param maxDurationMillis watchdog budget per search
	 * @param multipv requested MultiPV count
	 * @param threads requested engine threads
	 * @param hash requested UCI hash in MB
	 * @param showWdl whether to request UCI WDL output
	 * @param limit maximum rows to emit, or zero for no cap
	 * @param sourceLabel source prefix such as {@code pgn:file.pgn}
	 * @param thresholds classifier thresholds
	 * @param crtkVersion CRTK version string
	 */
	public record UciOptions(
			Protocol protocol,
			String protocolPath,
			long maxNodes,
			long maxDurationMillis,
			int multipv,
			int threads,
			int hash,
			boolean showWdl,
			int limit,
			String sourceLabel,
			Classifier.Thresholds thresholds,
			String crtkVersion) {

		/**
		 * Creates and validates UCI options.
		 *
		 * @param protocol engine protocol name
		 * @param protocolPath external engine protocol path
		 * @param maxNodes maximum search node budget
		 * @param maxDurationMillis maximum search duration in milliseconds
		 * @param multipv number of principal variations to request
		 * @param threads engine thread count
		 * @param hash engine hash size in megabytes
		 * @param showWdl whether WDL output is requested
		 * @param limit maximum games or rows to process
		 * @param sourceLabel label for the reviewed input source
		 * @param thresholds review classification thresholds
		 * @param crtkVersion ChessRTK version string
		 */
		public UciOptions {
			Objects.requireNonNull(protocol, "protocol");
			Objects.requireNonNull(protocolPath, "protocolPath");
			if (maxNodes <= 0L || maxDurationMillis <= 0L) {
				throw new IllegalArgumentException("UCI review budgets must be positive");
			}
			if (multipv <= 0 || threads <= 0 || hash < 0 || limit < 0) {
				throw new IllegalArgumentException("multipv/threads must be positive, hash/limit non-negative");
			}
			Objects.requireNonNull(sourceLabel, "sourceLabel");
			thresholds = thresholds == null ? Classifier.Thresholds.classical() : thresholds;
			Objects.requireNonNull(crtkVersion, "crtkVersion");
		}
	}

	/**
	 * Review output.
	 *
	 * @param rows emitted rows
	 * @param gamesRead number of parsed games inspected
	 */
	public record Review(List<ReviewRow> rows, int gamesRead) {

		/**
		 * Creates a review output.
		 *
		 * @param rows review rows
		 * @param gamesRead number of games read from the source
		 */
		public Review {
			rows = rows == null ? List.of() : List.copyOf(rows);
		}
	}
}
