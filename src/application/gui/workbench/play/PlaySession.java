package application.gui.workbench.play;

import application.gui.workbench.play.Opponent.MoveChoice;
import application.gui.workbench.ui.Toast;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;

/**
 * Controller for a human-versus-engine game in the workbench.
 *
 * <p>
 * The session is driven entirely from the event-dispatch thread except for the
 * engine search itself, which runs on a single background worker. After every
 * move that reaches the board funnel, {@link #onMovePlayed(Position)} decides
 * whether it is the engine's turn and, if so, runs the {@link Opponent} off the
 * EDT and re-applies its reply through {@link PlayHost#playMove(short)} on the
 * EDT. A monotonic {@link #generation} counter supersedes in-flight searches
 * when the user starts a new game, resigns, or the window closes, so a stale
 * reply is never applied.
 * </p>
 *
 * <p>
 * The session talks to the window only through {@link PlayHost}, so its turn,
 * supersede, and terminal logic can be unit-tested against fakes with no Swing.
 * </p>
 */
public final class PlaySession {

    /**
     * Which color the human plays.
     */
    public enum Side {
        /**
         * Human plays White.
         */
        WHITE,

        /**
         * Human plays Black.
         */
        BLACK,

        /**
         * Color is chosen at random when the game starts.
         */
        RANDOM
    }

    /**
     * Per-move time cap for a hint search, in milliseconds. A hint always plays
     * at maximum strength (the best move, regardless of the game's chosen
     * weakness) but is time-bounded so the suggestion appears promptly.
     */
    private static final int HINT_MOVETIME_MS = 2_000;

    /**
     * Window callbacks.
     */
    private final PlayHost host;

    /**
     * Opponent backend selection: a search algorithm paired with an evaluation
     * network. Used as the cache key so the opponent is only rebuilt when the
     * selection actually changes.
     *
     * @param search search algorithm
     * @param network evaluation network
     */
    public record Config(Opponent.Search search, Opponent.Network network) {
    }

    /**
     * Creates an {@link Opponent} for a selected backend, so the concrete engine
     * classes stay out of the controller and tests can inject a fake.
     */
    @FunctionalInterface
    public interface OpponentProvider {
        /**
         * Creates an opponent for the requested search/network configuration.
         *
         * @param config selected search + network
         * @return a fresh opponent
         */
        Opponent create(Config config);
    }

    /**
     * Strength mapping and move selection.
     */
    private final StrengthModel model;

    /**
     * Factory for opponent backends.
     */
    private final OpponentProvider provider;

    /**
     * Current engine opponent, recreated when the selected backend changes.
     */
    private Opponent opponent;

    /**
     * Configuration backing {@link #opponent}, or {@code null} before first use.
     */
    private Config opponentConfig;

    /**
     * Single background worker that owns the engine search.
     */
    private final ExecutorService searchExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "play-engine");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Monotonic request generation; bumped to supersede in-flight searches.
     */
    private final AtomicLong generation = new AtomicLong();

    /**
     * Random source seeded per game for side resolution and future sampling.
     */
    private Random rng = new Random();

    /**
     * Whether a game is currently in progress.
     */
    private boolean active;

    /**
     * Whether the human plays the white pieces this game.
     */
    private boolean humanIsWhite = true;

    /**
     * Lowest ply a take-back may return to: 1 when the engine made the game's
     * first move (so its forced opening move is never undone), otherwise 0.
     */
    private int floorPly;

    /**
     * Core position signature ({@link Position#signatureCore()}) at each ply,
     * indexed by ply (entry 0 is the start position). Used to detect threefold
     * repetition; trimmed on take-back so an abandoned line is forgotten.
     */
    private final List<Long> history = new ArrayList<>();

    /**
     * Whether an engine search is currently scheduled or running.
     */
    private boolean awaitingEngine;

    /**
     * One queued human premove, resolved after the engine reply reaches the
     * board. The move is a from/to shape and may be illegal until then.
     */
    private short pendingPremove = Move.NO_MOVE;

    /**
     * Current strength profile.
     */
    private StrengthProfile profile = StrengthProfile.ofElo(1200);

    /**
     * Opening book, lazily built on first use.
     */
    private OpeningBook openingBook;

    /**
     * Whether the opening book may answer engine moves. Off by default so the
     * headless regression harness (which scripts exact engine replies from the
     * standard start) is unaffected; the Play panel enables it from preferences.
     */
    private boolean bookEnabled;

    /**
     * Whether the current game began from the standard start position — the only
     * case the ECO book covers, so book lookups are skipped otherwise.
     */
    private boolean startedFromStandard;

    /**
     * Observer notified of status changes (turn, thinking, result), or null.
     * Invoked on the event-dispatch thread, like every other session method.
     */
    private Listener listener;

    /**
     * Observer of Play-session status changes, so the panel can show whose turn
     * it is, when the engine is thinking, and the game result, and enable or
     * disable its in-game buttons.
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * Called after the session's status changes.
         *
         * @param status human-readable status line
         * @param gameActive whether a game is currently in progress
         * @param engineThinking whether the engine is currently searching
         */
        void onStatus(String status, boolean gameActive, boolean engineThinking);

        /**
         * Called when a game finishes with a result, and when a new game starts
         * (with a null message) so a prior result can be cleared. The default is
         * a no-op so this stays a functional interface and existing lambda
         * listeners keep compiling.
         *
         * @param message result text, or null to clear a prior result
         * @param kind toast kind classifying the result, or null when clearing
         */
        default void onResult(String message, Toast.Kind kind) {
            // optional; panels that show a persistent result banner override this
        }
    }

    /**
     * Creates a play session.
     *
     * @param host window callbacks
     * @param model strength mapping and selection
     * @param provider factory for opponent backends
     */
    public PlaySession(PlayHost host, StrengthModel model, OpponentProvider provider) {
        this.host = host;
        this.model = model;
        this.provider = provider;
    }

    /**
     * Starts a new game with the default opponent (alpha-beta + classical).
     *
     * @param fen start position FEN
     * @param side which color the human plays
     * @param profile strength profile
     */
    public void start(String fen, Side side, StrengthProfile profile) {
        start(fen, side, profile,
                new Config(Opponent.Search.ALPHA_BETA, Opponent.Network.CLASSICAL));
    }

    /**
     * Starts a new game from a FEN with the given side, strength, and opponent
     * configuration (search algorithm + evaluation network).
     *
     * @param fen start position FEN
     * @param side which color the human plays
     * @param profile strength profile
     * @param config opponent search + network to play against
     */
    public void start(String fen, Side side, StrengthProfile profile, Config config) {
        try {
            new Position(fen.trim());
        } catch (RuntimeException ex) {
            host.toast(Toast.Kind.ERROR, "Invalid start position");
            return;
        }
        // Supersede anything in flight before reconfiguring.
        generation.incrementAndGet();
        if (opponent != null) {
            opponent.cancel();
        }
        awaitingEngine = false;
        if (!selectOpponent(config)) {
            return;
        }

        this.profile = profile;
        long seed = profile.rngSeed() != 0L ? profile.rngSeed() : System.nanoTime();
        this.rng = new Random(seed);
        this.humanIsWhite = switch (side) {
            case WHITE -> true;
            case BLACK -> false;
            case RANDOM -> rng.nextBoolean();
        };
        this.active = true;
        // Clear any banner left from the previous game (finishGame leaves the
        // result showing; nothing else resets it).
        notifyResult(null, null);

        host.startNewGame(fen);
        host.setBoardWhiteDown(humanIsWhite);
        host.setPositionEntryLocked(true);
        host.clearHint();
        clearPremove();

        Position pos = host.currentPosition();
        if (pos == null) {
            return;
        }
        // Seed the repetition history with the start position.
        history.clear();
        history.add(pos.signatureCore());
        // The ECO opening book only covers lines from the standard start.
        startedFromStandard = pos.signatureCore()
                == chess.core.Setup.getStandardStartPosition().signatureCore();
        // When the engine owns the opening move, ply 1 is its forced move and a
        // take-back must never undo it.
        floorPly = engineToMove(pos) ? 1 : 0;
        if (isTerminal(pos)) {
            finishGame(pos, null);
            return;
        }
        if (engineToMove(pos)) {
            scheduleEngineReply(pos);
        } else {
            host.setInputGate(true);
            host.toast(Toast.Kind.INFO, "Your move");
            notifyStatus("Your move");
        }
    }

    /**
     * Reacts to any move that has just been applied to the board (human or
     * engine). Schedules an engine reply when it becomes the engine's turn and
     * ends the game on a terminal position.
     *
     * @param after position after the move
     */
    public void onMovePlayed(Position after) {
        if (!active || after == null) {
            return;
        }
        // A move makes any prior hint stale; clear the suggested-move arrow.
        host.clearHint();
        // Record the new position before the terminal check so threefold
        // repetition (which needs this position counted) is detectable. Keep the
        // history aligned with the board ply, accounting for take-backs.
        recordPosition(after);
        if (isTerminal(after)) {
            finishGame(after, null);
            return;
        }
        if (engineToMove(after)) {
            if (!awaitingEngine) {
                scheduleEngineReply(after);
            }
        } else {
            if (tryPlayPremove(after)) {
                return;
            }
            host.setInputGate(true);
            notifyStatus(after.inCheck() ? "Your move — check" : "Your move");
        }
    }

    /**
     * Resigns the current game; the engine wins.
     */
    public void resign() {
        if (!active) {
            return;
        }
        finishGame(host.currentPosition(), "You resigned — engine wins", Toast.Kind.WARNING);
    }

    /**
     * Offers a draw. In v1 the engine always accepts.
     */
    public void offerDraw() {
        if (!active) {
            return;
        }
        finishGame(host.currentPosition(), "Draw agreed", Toast.Kind.INFO);
    }

    /**
     * Computes the human's best move with the configured opponent and shows it on
     * the board as a suggested-move arrow, without playing it. Ignored unless it
     * is the human's turn (so a hint search never races the engine's reply), and
     * the result is discarded if the board moves on before it returns.
     *
     * <p>
     * The hint always plays at maximum strength — the best move regardless of the
     * game's chosen Elo — but is time-capped for responsiveness. It runs on the
     * same single background worker as engine replies (serialized, never racing)
     * and never re-enters the move funnel.
     * </p>
     */
    public void requestHint() {
        if (!active || !isHumanInputAllowed()) {
            return;
        }
        final Position searched = host.currentPosition();
        if (searched == null) {
            return;
        }
        final long gen = generation.get();
        notifyStatus("Computing hint…");
        // Maximum strength, deterministic arg-max, but time-capped so the hint is
        // snappy regardless of the game's strength setting.
        StrengthProfile hintProfile = new StrengthProfile(
                StrengthModel.MAX_ELO, null, HINT_MOVETIME_MS, null, null, null, 0L, true);
        final StrengthModel.Budget budget = opponentConfig != null
                ? model.budgetFor(hintProfile, opponentConfig.network())
                : model.budgetFor(hintProfile);
        searchExecutor.submit(() -> {
            try {
                MoveChoice choice = opponent.chooseMove(searched, budget, gen);
                short hint = choice.ranked().isEmpty()
                        ? choice.move()
                        : choice.ranked().get(0).move();
                SwingUtilities.invokeLater(() -> applyHint(gen, hint, searched));
            } catch (RuntimeException ex) {
                // A failed hint is silent; the player simply sees no arrow.
            }
        });
    }

    /**
     * Takes back the human's last move and the engine's reply, returning the
     * board to the most recent human-to-move position. Ignored while the engine
     * is thinking or when it is not the human's turn.
     */
    public void takeback() {
        if (!active) {
            return;
        }
        if (awaitingEngine) {
            host.toast(Toast.Kind.INFO, "Wait for the engine to move");
            return;
        }
        Position pos = host.currentPosition();
        if (pos == null || pos.isWhiteToMove() != humanIsWhite) {
            return;
        }
        int ply = host.currentPly();
        int target = Math.max(floorPly, ply - 2);
        if (target >= ply) {
            host.toast(Toast.Kind.INFO, "Nothing to take back");
            return;
        }
        // Supersede any pending reply, step the board back, and hand the move
        // back to the human. The next human move truncates the abandoned line.
        generation.incrementAndGet();
        opponent.cancel();
        awaitingEngine = false;
        host.clearHint();
        clearPremove();
        // Forget the abandoned line's positions so it cannot inflate the
        // repetition count when the human replays. History keeps plies 0..target.
        while (history.size() > target + 1) {
            history.remove(history.size() - 1);
        }
        host.showPly(target);
        host.setInputGate(true);
    }

    /**
     * Stops the current game without a result toast (e.g. leaving Play mode).
     */
    public void stop() {
        if (!active) {
            return;
        }
        active = false;
        generation.incrementAndGet();
        opponent.cancel();
        awaitingEngine = false;
        host.clearHint();
        clearPremove();
        host.saveGameState("Aborted");
        host.setPositionEntryLocked(false);
        host.setInputGate(true);
        notifyStatus("No game");
    }

    /**
     * Registers the status observer (typically the Play panel). Replaces any
     * previous listener.
     *
     * @param listener observer, or null to clear
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Enables or disables the opening book for engine replies (default off so the
     * headless harness's scripted standard-start games are unaffected). The Play
     * panel sets this from the user's preference.
     *
     * @param enabled true to let the book answer engine moves from the standard
     *        start position
     */
    public void setBookEnabled(boolean enabled) {
        this.bookEnabled = enabled;
    }

    /**
     * Returns the opening book, building it on first use.
     *
     * @return opening book
     */
    private OpeningBook openingBook() {
        if (openingBook == null) {
            openingBook = new OpeningBook();
        }
        return openingBook;
    }

    /**
     * Pushes a status line to the listener, if any.
     *
     * @param status human-readable status
     */
    private void notifyStatus(String status) {
        if (listener != null) {
            listener.onStatus(status, active, awaitingEngine);
        }
    }

    /**
     * Pushes a game result (or a clear) to the listener, if any.
     *
     * @param message result text, or null to clear
     * @param kind toast kind, or null when clearing
     */
    private void notifyResult(String message, Toast.Kind kind) {
        if (listener != null) {
            listener.onResult(message, kind);
        }
    }

    /**
     * Returns whether a game is in progress.
     *
     * @return true when active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns the human side resolved for the current or most recent game.
     *
     * @return true when the human has the white pieces
     */
    public boolean isHumanWhite() {
        return humanIsWhite;
    }

    /**
     * Returns whether the human may move right now. Always true when no game is
     * active, so ordinary analysis input is unaffected.
     *
     * @return true when human input is allowed
     */
    public boolean isHumanInputAllowed() {
        if (!active) {
            return true;
        }
        if (awaitingEngine) {
            return false;
        }
        Position pos = host.currentPosition();
        return pos != null && pos.isWhiteToMove() == humanIsWhite;
    }

    /**
     * Returns whether the human may queue a premove while the engine is thinking.
     *
     * @return true when premove input is allowed
     */
    public boolean isPremoveInputAllowed() {
        if (!active || !awaitingEngine) {
            return false;
        }
        Position pos = host.currentPosition();
        return pos != null && pos.isWhiteToMove() != humanIsWhite;
    }

    /**
     * Returns whether a board square may start a premove gesture.
     *
     * @param square source square
     * @param piece piece on the source square
     * @return true when the human owns the piece and premove input is open
     */
    public boolean isPremoveSourceAllowed(byte square, byte piece) {
        if (!isPremoveInputAllowed() || !isSquare(square) || piece == Piece.EMPTY) {
            return false;
        }
        if (Piece.isWhite(piece) != humanIsWhite) {
            return false;
        }
        Position pos = host.currentPosition();
        return pos != null && pos.getBoard()[square] == piece;
    }

    /**
     * Queues one premove. A later premove replaces the previous one.
     *
     * @param move from/to candidate, usually not legal until the engine replies
     * @return true when the premove was accepted
     */
    public boolean queuePremove(short move) {
        if (!isPremoveInputAllowed() || move == Move.NO_MOVE) {
            return false;
        }
        byte from = Move.getFromIndex(move);
        byte to = Move.getToIndex(move);
        if (!isSquare(from) || !isSquare(to) || from == to) {
            return false;
        }
        Position pos = host.currentPosition();
        if (pos == null) {
            return false;
        }
        byte piece = pos.getBoard()[from];
        if (piece == Piece.EMPTY || Piece.isWhite(piece) != humanIsWhite) {
            return false;
        }
        pendingPremove = move;
        host.clearHint();
        host.showPremove(move);
        notifyStatus("Premove queued: " + Move.toString(move));
        return true;
    }

    /**
     * Attempts to execute the queued premove in the just-returned human-to-move
     * position.
     *
     * @param position current position after the engine reply
     * @return true when a legal premove was played
     */
    private boolean tryPlayPremove(Position position) {
        if (pendingPremove == Move.NO_MOVE || position == null || position.isWhiteToMove() != humanIsWhite) {
            return false;
        }
        short queued = pendingPremove;
        clearPremove();
        short legal = resolvePremove(position, queued);
        if (legal == Move.NO_MOVE) {
            return false;
        }
        host.setInputGate(false);
        host.playMove(legal);
        return true;
    }

    /**
     * Resolves a queued from/to candidate against the current legal moves.
     *
     * @param position position where the premove should execute
     * @param queued queued premove shape
     * @return legal move to play, or {@link Move#NO_MOVE}
     */
    private static short resolvePremove(Position position, short queued) {
        byte from = Move.getFromIndex(queued);
        byte to = Move.getToIndex(queued);
        byte promotion = Move.getPromotion(queued);
        MoveList legalMoves = position.legalMoves();
        for (int i = 0; i < legalMoves.size(); i++) {
            short move = legalMoves.raw(i);
            if (Move.getFromIndex(move) != from || Move.getToIndex(move) != to) {
                continue;
            }
            if (Move.getPromotion(move) == promotion) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Clears the stored premove and board arrow.
     */
    private void clearPremove() {
        pendingPremove = Move.NO_MOVE;
        host.clearPremove();
    }

    /**
     * Returns whether a byte is a board square.
     *
     * @param square square candidate
     * @return true for 0..63
     */
    private static boolean isSquare(byte square) {
        return square != Field.NO_SQUARE && square >= 0 && square < 64;
    }

    /**
     * Stops any game and releases the background worker and opponent.
     */
    public void dispose() {
        stop();
        searchExecutor.shutdownNow();
        closeOpponent();
    }

    /**
     * Ensures {@link #opponent} matches the requested configuration, creating it
     * lazily and closing any previous backend.
     *
     * @param config requested search + network
     * @return true when a usable opponent is ready
     */
    private boolean selectOpponent(Config config) {
        if (opponent != null && config.equals(opponentConfig)) {
            return true;
        }
        closeOpponent();
        try {
            opponent = provider.create(config);
            opponentConfig = config;
            return true;
        } catch (RuntimeException ex) {
            opponent = null;
            opponentConfig = null;
            host.toast(Toast.Kind.ERROR, "Could not start opponent: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Closes the current opponent, if any.
     */
    private void closeOpponent() {
        if (opponent != null) {
            try {
                opponent.close();
            } catch (Exception ex) {
                // best-effort cleanup
            }
            opponent = null;
            opponentConfig = null;
        }
    }

    /**
     * Schedules an engine reply for the engine-to-move position off the EDT.
     *
     * @param position engine-to-move position
     */
    private void scheduleEngineReply(Position position) {
        awaitingEngine = true;
        host.setInputGate(false);
        host.setEvalThinking();
        host.toast(Toast.Kind.INFO, "Engine thinking…");
        notifyStatus("Engine thinking…");

        final long gen = generation.get();
        final Position searched = position.copy();
        final StrengthProfile activeProfile = profile;

        // Opening book: from the standard start, answer instantly from the ECO
        // book instead of searching. Deterministic profiles take the most-frequent
        // line; otherwise a frequency-weighted pick (seeded by the per-game rng).
        // Re-enter through the same applyEngineReply path so supersede/generation,
        // history and turn handling are identical to a searched move.
        if (bookEnabled && startedFromStandard) {
            short bookMove = openingBook().move(searched,
                    activeProfile.deterministic() ? null : rng);
            if (bookMove != Move.NO_MOVE) {
                MoveChoice bookChoice = new MoveChoice(bookMove, java.util.List.of(), 0, "book");
                SwingUtilities.invokeLater(() -> applyEngineReply(gen, bookMove, bookChoice));
                return;
            }
        }
        // Scale the playout budget for the selected network's per-playout cost so
        // slow nets (CNN/OTIS) stay responsive at the chosen Elo. opponentConfig
        // is always set by selectOpponent before a game is active; fall back to
        // the network-agnostic budget defensively.
        final StrengthModel.Budget budget = opponentConfig != null
                ? model.budgetFor(activeProfile, opponentConfig.network())
                : model.budgetFor(activeProfile);
        searchExecutor.submit(() -> {
            try {
                MoveChoice choice = opponent.chooseMove(searched, budget, gen);
                short selected = model.select(choice.ranked(), activeProfile, rng);
                SwingUtilities.invokeLater(() -> applyEngineReply(gen, selected, choice));
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> failEngine(gen, ex));
            }
        });
    }

    /**
     * Applies an engine reply on the EDT if it has not been superseded.
     *
     * @param gen generation the reply was computed for
     * @param move chosen move
     * @param choice full engine decision (for evaluation display)
     */
    private void applyEngineReply(long gen, short move, MoveChoice choice) {
        if (!active || gen != generation.get()) {
            return;
        }
        awaitingEngine = false;
        if (move == Move.NO_MOVE) {
            finishGame(host.currentPosition(), null);
            return;
        }
        // choice centipawns are from the engine (side-to-move) perspective.
        int engineCp = choice.centipawnsSideToMove();
        int whiteCp = humanIsWhite ? -engineCp : engineCp;
        host.setEvalWhiteCp(whiteCp);
        // Re-enter the board funnel; the resulting move fires onMovePlayed again,
        // which handles the human's turn or a terminal position (e.g. engine mate).
        host.playMove(move);
    }

    /**
     * Shows a computed hint on the board if it has not been superseded. A human
     * move does not bump {@link #generation} (the engine reply that follows keeps
     * the same generation), so the generation guard alone is not enough: the hint
     * is also dropped unless the board still holds the exact position it was
     * computed for, so a late hint never paints onto a different position.
     *
     * @param gen generation the hint was computed for
     * @param move suggested move (a no-move result shows nothing)
     * @param searched position the hint was computed for
     */
    private void applyHint(long gen, short move, Position searched) {
        if (!active || gen != generation.get()) {
            return;
        }
        Position current = host.currentPosition();
        if (current == null || current.signatureCore() != searched.signatureCore()) {
            return;
        }
        if (move == Move.NO_MOVE) {
            return;
        }
        host.showHint(move);
        notifyStatus(current.inCheck() ? "Your move — check" : "Your move");
    }

    /**
     * Ends the game after an engine failure.
     *
     * @param gen generation the failure belongs to
     * @param ex failure
     */
    private void failEngine(long gen, RuntimeException ex) {
        if (!active || gen != generation.get()) {
            return;
        }
        awaitingEngine = false;
        String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        finishGame(host.currentPosition(), "Engine error: " + detail);
    }

    /**
     * Finalizes the game from a terminal position, deriving the result text.
     *
     * @param pos final position (may be null)
     * @param overrideMessage explicit result message, or null to derive one
     */
    private void finishGame(Position pos, String overrideMessage) {
        if (overrideMessage != null) {
            finishGame(pos, overrideMessage, Toast.Kind.WARNING);
        } else {
            finishGame(pos, resultMessage(pos), resultKind(pos, null));
        }
    }

    /**
     * Finalizes the game, unlocking input and announcing the result.
     *
     * @param pos final position (may be null)
     * @param message result message
     * @param kind toast kind for the result
     */
    private void finishGame(Position pos, String message, Toast.Kind kind) {
        active = false;
        generation.incrementAndGet();
        opponent.cancel();
        awaitingEngine = false;
        host.clearHint();
        clearPremove();
        host.setPositionEntryLocked(false);
        host.setInputGate(true);
        host.toast(kind, message);
        host.saveGameState("Finished");
        notifyStatus(message);
        notifyResult(message, kind);
    }

    /**
     * Returns whether it is the engine's turn in a position.
     *
     * @param pos position
     * @return true when the engine is to move
     */
    private boolean engineToMove(Position pos) {
        return pos.isWhiteToMove() != humanIsWhite;
    }

    /**
     * Records a position's core signature at the current board ply, keeping the
     * history array aligned with the board. Replaying after a take-back (which
     * left a shorter history) simply overwrites the abandoned tail.
     *
     * @param pos position just applied to the board
     */
    private void recordPosition(Position pos) {
        int ply = host.currentPly();
        while (history.size() > ply) {
            history.remove(history.size() - 1);
        }
        history.add(pos.signatureCore());
    }

    /**
     * Returns whether a position ends the game by checkmate, stalemate,
     * insufficient material, the fifty-move rule, or threefold repetition.
     *
     * @param pos position
     * @return true when terminal
     */
    private boolean isTerminal(Position pos) {
        return pos.legalMoves().isEmpty()
                || pos.isInsufficientMaterial()
                || isFiftyMoveDraw(pos)
                || isThreefoldRepetition(pos);
    }

    /**
     * Returns whether the fifty-move rule applies (100 half-moves without a
     * pawn move or capture).
     *
     * @param pos position
     * @return true at or beyond the fifty-move limit
     */
    private static boolean isFiftyMoveDraw(Position pos) {
        return pos.halfMoveClock() >= 100;
    }

    /**
     * Returns whether the current position has occurred three times in this
     * game (threefold repetition). The current position is the last recorded
     * entry, so a count of three or more signals a draw.
     *
     * @param pos current position
     * @return true on threefold repetition
     */
    private boolean isThreefoldRepetition(Position pos) {
        long key = pos.signatureCore();
        int count = 0;
        for (long seen : history) {
            if (seen == key) {
                count++;
            }
        }
        return count >= 3;
    }

    /**
     * Builds a result message for a terminal position.
     *
     * @param pos final position
     * @return built a result message for a terminal position
     */
    private String resultMessage(Position pos) {
        if (pos == null) {
            return "Game over";
        }
        if (pos.isCheckmate()) {
            boolean humanMated = pos.isWhiteToMove() == humanIsWhite;
            return humanMated ? "Checkmate — engine wins" : "Checkmate — you win";
        }
        if (pos.isStalemate()) {
            return "Stalemate — draw";
        }
        if (pos.isInsufficientMaterial()) {
            return "Draw — insufficient material";
        }
        if (isThreefoldRepetition(pos)) {
            return "Draw — threefold repetition";
        }
        if (isFiftyMoveDraw(pos)) {
            return "Draw — fifty-move rule";
        }
        return "Game over";
    }

    /**
     * Chooses a toast kind for the result.
     *
     * @param pos final position
     * @param overrideMessage explicit message when present (e.g. resignation)
     * @return toast kind
     */
    private Toast.Kind resultKind(Position pos, String overrideMessage) {
        if (overrideMessage != null) {
            return Toast.Kind.WARNING;
        }
        if (pos != null && pos.isCheckmate()) {
            boolean humanMated = pos.isWhiteToMove() == humanIsWhite;
            return humanMated ? Toast.Kind.WARNING : Toast.Kind.SUCCESS;
        }
        return Toast.Kind.INFO;
    }
}
