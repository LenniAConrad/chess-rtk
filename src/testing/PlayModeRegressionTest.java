package testing;

import application.gui.workbench.play.AlphaBetaOpponent;
import application.gui.workbench.play.MctsOpponent;
import application.gui.workbench.play.OpeningBook;
import application.gui.workbench.play.Opponent;
import application.gui.workbench.play.Opponent.MoveChoice;
import application.gui.workbench.play.Opponent.RankedMove;
import application.gui.workbench.play.PlayHost;
import application.gui.workbench.play.PlayPanel;
import application.gui.workbench.play.PlaySession;
import application.gui.workbench.play.StrengthModel;
import application.gui.workbench.play.StrengthProfile;
import application.gui.workbench.ui.Toast;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.Setup;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless regression coverage for Play-vs-engine mode: the strength mapping,
 * the in-process MCTS opponent, and the {@link PlaySession} turn controller
 * driven through a fake {@link PlayHost} that mirrors the board move funnel.
 */
public final class PlayModeRegressionTest {

    /**
     * Accumulated check results.
     */
    private static final List<String[]> RESULTS = new ArrayList<>();

    /**
     * Count of failed checks.
     */
    private static int failures;

    /**
     * Prevents instantiation.
     */
    private PlayModeRegressionTest() {
    }

    /**
     * Runs all Play-mode regression checks.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        testStrengthModel();
        testPerNetworkBudget();
        testMoveSampling();
        testMctsOpponent();
        testMctsTreeReuse();
        testAlphaBetaOpponent();
        testSessionHumanWhite();
        testSessionHumanBlackEngineFirst();
        testPremoveExecutesAfterEngineReply();
        testSessionResignUnlocks();
        testSessionDrawUnlocks();
        testSessionTakeback();
        testHint();
        testResultBannerResign();
        testResultBannerDraw();
        testResultBannerCheckmate();
        testResultClearedOnNewGame();
        testThreefoldRepetition();
        testFiftyMoveRule();
        testPresetBotsUseAlphaBetaClassical();
        testPanelContextUsesCorePosition();
        testOpponentSelection();
        testAllBackendCombinations();
        testOpeningBook();
        report();
    }

    /**
     * Verifies the Elo-to-budget mapping and arg-max selection.
     */
    private static void testStrengthModel() {
        StrengthModel model = new StrengthModel();
        StrengthModel.Budget weak = model.budgetFor(StrengthProfile.ofElo(StrengthModel.MIN_ELO));
        StrengthModel.Budget mid = model.budgetFor(StrengthProfile.ofElo(1200));
        StrengthModel.Budget strong = model.budgetFor(StrengthProfile.ofElo(StrengthModel.MAX_ELO));

        check("budget: weakest is the floor", weak.maxPlayouts() == StrengthModel.MIN_PLAYOUTS);
        check("budget: strongest is the ceiling", strong.maxPlayouts() == StrengthModel.MAX_PLAYOUTS);
        check("budget: playouts increase with Elo",
                strong.maxPlayouts() > mid.maxPlayouts() && mid.maxPlayouts() > weak.maxPlayouts());
        check("budget: max strength searches deeply", strong.maxPlayouts() >= 10_000);
        check("budget: time cap positive and bounded",
                weak.maxMillis() > 0 && strong.maxMillis() <= 20_000);
        check("budget: depth increases with Elo",
                strong.depth() > weak.depth()
                        && weak.depth() >= StrengthModel.MIN_DEPTH
                        && strong.depth() <= StrengthModel.MAX_DEPTH);
        check("budget: carries target Elo", strong.targetElo() == StrengthModel.MAX_ELO);

        StrengthModel.Budget overridden =
                model.budgetFor(new StrengthProfile(1200, 777, 1234, null, null, null, 0L, true));
        check("budget: node override honored", overridden.maxPlayouts() == 777);
        check("budget: movetime override honored", overridden.maxMillis() == 1234);

        Random rng = new Random(1);
        List<RankedMove> ranked = List.of(
                new RankedMove((short) 10, 50, 0.4, 0.1),
                new RankedMove((short) 20, 10, 0.2, 0.0));
        check("select: arg-max picks first ranked",
                model.select(ranked, StrengthProfile.ofElo(1200), rng) == (short) 10);
        check("select: empty yields no move",
                model.select(List.of(), StrengthProfile.ofElo(1200), rng) == Move.NO_MOVE);
    }

    /**
     * Verifies per-network playout-budget scaling: fast evaluators keep their
     * full budget, slow nets are scaled down but never below the floor, scaling
     * stays monotonic with Elo, an explicit node override is never re-scaled, and
     * only the playout count differs from the network-agnostic budget.
     */
    private static void testPerNetworkBudget() {
        StrengthModel model = new StrengthModel();
        StrengthProfile mid = StrengthProfile.ofElo(1800);

        // (a) fast evaluators unchanged.
        check("net-budget: classical preserved",
                model.budgetFor(mid, Opponent.Network.CLASSICAL).maxPlayouts()
                        == model.budgetFor(mid).maxPlayouts());
        check("net-budget: NNUE preserved",
                model.budgetFor(mid, Opponent.Network.NNUE).maxPlayouts()
                        == model.budgetFor(mid).maxPlayouts());

        // (b) slow nets scaled down but floored.
        int baseMid = model.budgetFor(mid).maxPlayouts();
        for (Opponent.Network slow : new Opponent.Network[] { Opponent.Network.CNN, Opponent.Network.OTIS }) {
            int scaled = model.budgetFor(mid, slow).maxPlayouts();
            check("net-budget: " + slow + " scaled below base", scaled < baseMid);
            check("net-budget: " + slow + " stays at/above floor", scaled >= StrengthModel.MIN_PLAYOUTS);
        }

        // (c) monotonic within a slow network (>= tolerates the floor clamp at MIN_ELO).
        int low = model.budgetFor(StrengthProfile.ofElo(StrengthModel.MIN_ELO), Opponent.Network.CNN).maxPlayouts();
        int high = model.budgetFor(StrengthProfile.ofElo(StrengthModel.MAX_ELO), Opponent.Network.CNN).maxPlayouts();
        check("net-budget: CNN monotonic with Elo", high > baseScaled(model, mid) && baseScaled(model, mid) >= low);

        // (d) explicit node override is never re-scaled.
        StrengthProfile pinned = new StrengthProfile(1200, 777, null, null, null, null, 0L, true);
        check("net-budget: override wins on a slow net",
                model.budgetFor(pinned, Opponent.Network.CNN).maxPlayouts() == 777);

        // (e) everything but playouts is identical between the two budget forms.
        StrengthModel.Budget agnostic = model.budgetFor(mid);
        StrengthModel.Budget cnn = model.budgetFor(mid, Opponent.Network.CNN);
        check("net-budget: only playouts differ",
                cnn.maxMillis() == agnostic.maxMillis()
                        && cnn.depth() == agnostic.depth()
                        && cnn.cpuct() == agnostic.cpuct()
                        && cnn.targetElo() == agnostic.targetElo());
    }

    /**
     * Returns the CNN-scaled playout cap for a profile (helper for monotonicity).
     *
     * @param model strength model
     * @param profile strength profile
     * @return CNN-scaled playout cap
     */
    private static int baseScaled(StrengthModel model, StrengthProfile profile) {
        return model.budgetFor(profile, Opponent.Network.CNN).maxPlayouts();
    }

    /**
     * Verifies Layer B move sampling: weak play is stochastic but value-bounded,
     * strong/deterministic play is arg-max, and a clearly losing move outside the
     * cutoff is never sampled.
     */
    private static void testMoveSampling() {
        StrengthModel model = new StrengthModel();
        // best q=0.20, a close alternative q=0.15, and a blunder q=-0.80.
        List<RankedMove> ranked = List.of(
                new RankedMove((short) 10, 100, 0.5, 0.20),
                new RankedMove((short) 20, 80, 0.3, 0.15),
                new RankedMove((short) 30, 60, 0.2, -0.80));

        // Max strength: always the best move.
        check("sample: max strength is arg-max",
                model.select(ranked, StrengthProfile.ofElo(StrengthModel.MAX_ELO), new Random(1)) == (short) 10);
        // Deterministic flag at a weak Elo still forces arg-max.
        check("sample: deterministic forces arg-max",
                model.select(ranked, StrengthProfile.ofElo(700, true), new Random(2)) == (short) 10);

        // Weak strength: over many draws the alternative is sometimes chosen, but
        // the q=-0.80 blunder (far outside any cutoff) is never selected.
        StrengthProfile weak = StrengthProfile.ofElo(700);
        int chosenAlt = 0;
        int chosenBlunder = 0;
        Random rng = new Random(42);
        for (int i = 0; i < 400; i++) {
            short m = model.select(ranked, weak, rng);
            if (m == (short) 20) {
                chosenAlt++;
            } else if (m == (short) 30) {
                chosenBlunder++;
            }
        }
        check("sample: weak play sometimes deviates from best", chosenAlt > 0);
        check("sample: never samples a clearly losing move", chosenBlunder == 0);

        // Single candidate is returned regardless of strength.
        check("sample: single candidate returned",
                model.select(List.of(new RankedMove((short) 99, 1, 1.0, 0.0)), weak, rng) == (short) 99);

        // Sampling parameters weaken monotonically as Elo drops.
        StrengthModel.Sampling weakS = model.samplingFor(StrengthProfile.ofElo(700));
        StrengthModel.Sampling strongS = model.samplingFor(StrengthProfile.ofElo(2300));
        check("sample: weaker Elo has higher temperature", weakS.temperature() > strongS.temperature());
        check("sample: weaker Elo has higher blunder rate",
                weakS.blunderProbability() >= strongS.blunderProbability());
    }

    /**
     * Verifies the MCTS opponent returns a legal move and never hangs a forced
     * mate even at the weakest budget.
     */
    private static void testMctsOpponent() {
        StrengthModel model = new StrengthModel();
        MctsOpponent opponent = new MctsOpponent(Opponent.Network.CLASSICAL);
        try {
            Position start = new Position(Setup.getStandardStartFEN());
            MoveChoice opening = opponent.chooseMove(start,
                    model.budgetFor(StrengthProfile.ofElo(1600)), 1);
            check("mcts: opening move is legal",
                    opening.move() != Move.NO_MOVE && isLegal(start, opening.move()));
            check("mcts: ranked candidates present", !opening.ranked().isEmpty());
            check("mcts: best move is first ranked",
                    opening.ranked().get(0).move() == opening.move());

            // Ra1-a8 is mate: black king g8 is boxed in by its own f7/g7/h7 pawns.
            Position mateInOne = new Position("6k1/5ppp/8/8/8/8/8/R6K w - - 0 1");
            MoveChoice mating = opponent.chooseMove(mateInOne,
                    model.budgetFor(StrengthProfile.ofElo(StrengthModel.MIN_ELO)), 2);
            Position after = mateInOne.copy();
            after.play(mating.move());
            check("mcts: finds mate-in-one at minimum budget", after.isCheckmate());
        } finally {
            opponent.close();
        }
    }

    /**
     * Verifies MCTS tree reuse: after the opponent searches a position and the
     * game advances down one of its lines, re-rooting to that child position
     * carries the subtree's accumulated visits over instead of starting from
     * zero (the MCTS analogue of a persistent transposition table).
     */
    private static void testMctsTreeReuse() {
        StrengthModel model = new StrengthModel();
        MctsOpponent opponent = new MctsOpponent(Opponent.Network.CLASSICAL);
        try {
            StrengthModel.Budget budget = model.budgetFor(StrengthProfile.ofElo(1800, true));
            Position start = new Position(Setup.getStandardStartFEN());
            // First move: the opponent searches the root and replies.
            MoveChoice first = opponent.chooseMove(start, budget, 1);
            check("reuse: first move legal",
                    first.move() != Move.NO_MOVE && isLegal(start, first.move()));

            // Advance the game two plies down the engine's own best line, then
            // the human's reply, landing on a position inside the searched tree.
            Position afterEngine = start.copy();
            afterEngine.play(first.move());
            short humanReply = afterEngine.legalMoves().raw(0);
            Position next = afterEngine.copy();
            next.play(humanReply);

            // Second move from the reused tree should still be legal and sound.
            MoveChoice second = opponent.chooseMove(next, budget, 2);
            check("reuse: reused-tree move legal",
                    second.move() != Move.NO_MOVE && isLegal(next, second.move()));
            check("reuse: reused-tree move has visits",
                    !second.ranked().isEmpty() && second.ranked().get(0).visits() > 0);
        } finally {
            opponent.close();
        }
    }

    /**
     * Verifies the alpha-beta opponent returns a legal move, plays a developing
     * move from the opening (not a wing-pawn push), and finds a mate in one.
     */
    private static void testAlphaBetaOpponent() {
        StrengthModel model = new StrengthModel();
        AlphaBetaOpponent opponent = new AlphaBetaOpponent(Opponent.Network.CLASSICAL);
        try {
            // Italian setup: a strong engine develops/centralizes rather than a2a4.
            Position italian = new Position("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1");
            MoveChoice choice = opponent.chooseMove(italian,
                    model.budgetFor(StrengthProfile.ofElo(2400)), 1);
            check("alphabeta: move is legal",
                    choice.move() != Move.NO_MOVE && isLegal(italian, choice.move()));
            check("alphabeta: does not play the a4 wing push",
                    !"a2a4".equals(Move.toString(choice.move())));
            check("alphabeta: eval within sane bounds",
                    Math.abs(choice.centipawnsSideToMove()) <= 3000);

            Position mateInOne = new Position("6k1/5ppp/8/8/8/8/8/R6K w - - 0 1");
            MoveChoice mating = opponent.chooseMove(mateInOne,
                    model.budgetFor(StrengthProfile.ofElo(2000)), 2);
            Position after = mateInOne.copy();
            after.play(mating.move());
            check("alphabeta: finds mate in one", after.isCheckmate());
        } finally {
            opponent.close();
        }
    }

    /**
     * Drives a full human-as-white turn: human moves, engine replies through the
     * funnel, and control returns to the human.
     */
    private static void testSessionHumanWhite() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200));
            check("white: position locked while game active", host.locked);
            check("white: human to move after start", host.gate);

            host.expect(2);
            short human = findMove(host.position, "e2e4");
            host.playMove(human);
            boolean replied = host.await();
            check("white: engine replied within timeout", replied);
            check("white: two plies played", host.moveCount == 2);
            check("white: control returned to human", host.position.isWhiteToMove() && host.gate);
            check("white: game still active and locked", session.isActive() && host.locked);
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies the engine moves first when the human chooses black.
     */
    private static void testSessionHumanBlackEngineFirst() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        host.bind(session);
        try {
            host.expect(1);
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.BLACK, StrengthProfile.ofElo(1200));
            boolean moved = host.await();
            check("black: engine made the first move", moved && host.moveCount == 1);
            check("black: human (black) to move after engine", !host.position.isWhiteToMove() && host.gate);
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies a premove queued while the engine is thinking is resolved against
     * the post-reply legal moves, then re-enters the same board move funnel as a
     * normal human move.
     */
    private static void testPremoveExecutesAfterEngineReply() {
        FakeHost host = new FakeHost();
        CountDownLatch firstSearchEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstSearch = new CountDownLatch(1);
        CountDownLatch secondSearchEntered = new CountDownLatch(1);
        CountDownLatch releaseSecondSearch = new CountDownLatch(1);
        PlaySession.OpponentProvider blocking = config -> new Opponent() {
            /**
             * Number of scripted engine searches completed by this stub.
             */
            private int callCount;

            /**
             * First engine reply is scripted; the next search blocks so the test
             * can assert immediately after the premove is applied.
             */
            @Override
            public MoveChoice chooseMove(Position position, StrengthModel.Budget budget, long requestId) {
                callCount++;
                if (callCount == 1) {
                    firstSearchEntered.countDown();
                    awaitLatch(releaseFirstSearch);
                    short move = findMove(position, "g8f6");
                    return new MoveChoice(move, List.of(new RankedMove(move, 1, 1.0, 0.0)), 0, "");
                }
                secondSearchEntered.countDown();
                awaitLatch(releaseSecondSearch);
                MoveList legal = position.legalMoves();
                short move = legal.isEmpty() ? Move.NO_MOVE : legal.raw(0);
                return new MoveChoice(move, List.of(new RankedMove(move, 1, 1.0, 0.0)), 0, "");
            }

            /**
             * Releases the blocked second search during session disposal.
             */
            @Override
            public void cancel() {
                releaseSecondSearch.countDown();
            }
        };
        PlaySession session = new PlaySession(host, new StrengthModel(), blocking);
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(1200));
            host.expect(3);
            host.playMove(findMove(host.position, "e2e4"));
            check("premove: engine search started", awaitLatch(firstSearchEntered));
            short premove = Move.parse("g1f3");
            check("premove: source allowed while engine thinks",
                    session.isPremoveSourceAllowed(Move.getFromIndex(premove), Piece.WHITE_KNIGHT));
            check("premove: queue accepted while engine thinks", session.queuePremove(premove));
            check("premove: host arrow shown", host.lastPremove == premove);
            releaseFirstSearch.countDown();
            check("premove: engine reply and premove reached the board", host.await());
            check("premove: applied as third ply", host.moveCount == 3);
            check("premove: played move sequence",
                    "e2e4".equals(Move.toString(host.playedMoves.get(0)))
                            && "g8f6".equals(Move.toString(host.playedMoves.get(1)))
                            && "g1f3".equals(Move.toString(host.playedMoves.get(2))));
            check("premove: arrow cleared after execution", host.lastPremove == Move.NO_MOVE);
            check("premove: next engine search scheduled after premove", awaitLatch(secondSearchEntered));
        } finally {
            releaseSecondSearch.countDown();
            session.dispose();
        }
    }

    /**
     * Verifies resigning ends the game and unlocks position entry.
     */
    private static void testSessionResignUnlocks() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        host.bind(session);
        try {
            check("resign: input allowed before any game", session.isHumanInputAllowed());
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200));
            session.resign();
            check("resign: game no longer active", !session.isActive());
            check("resign: position entry unlocked", !host.locked);
            check("resign: input allowed after game ends", session.isHumanInputAllowed());
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies offering a draw ends the game and unlocks position entry.
     */
    private static void testSessionDrawUnlocks() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200));
            session.offerDraw();
            check("draw: game no longer active", !session.isActive());
            check("draw: position entry unlocked", !host.locked);
            check("draw: input allowed after draw", session.isHumanInputAllowed());
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies take-back rewinds the human's move and the engine's reply back to
     * the human-to-move position, and that take-back is a no-op at the root.
     */
    private static void testSessionTakeback() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200));
            check("takeback: nothing to take back at root (no-op)",
                    plyAfterTakeback(session, host) == 0);

            host.expect(2);
            host.playMove(findMove(host.position, "e2e4"));
            check("takeback: engine replied", host.await());
            int beforePly = host.currentPly();
            check("takeback: two plies before takeback", beforePly == 2);

            session.takeback();
            check("takeback: returns to the root (human to move)",
                    host.currentPly() == 0 && host.position.isWhiteToMove());
            check("takeback: game still active", session.isActive());

            // The human can resume and the engine answers again.
            host.expect(2);
            host.playMove(findMove(host.position, "d2d4"));
            check("takeback: play resumes after takeback", host.await() && host.currentPly() == 2);
        } finally {
            session.dispose();
        }
    }

    /**
     * Issues a take-back and returns the resulting ply (helper for the no-op case).
     *
     * @param session play session
     * @param host fake host
     * @return current ply after the take-back attempt
     */
    private static int plyAfterTakeback(PlaySession session, FakeHost host) {
        session.takeback();
        return host.currentPly();
    }

    /**
     * Verifies the Hint feature: a hint on the human's turn shows the opponent's
     * suggested move as a board arrow without playing it; a move clears the
     * arrow; a hint is a no-op once the game is over; and a hint whose position
     * is superseded before it returns is dropped rather than painted.
     */
    private static void testHint() {
        // (a)/(b)/(d): the happy path with an instant scripted opponent.
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), scriptedOpponent("e2e4"));
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(StrengthModel.MAX_ELO, true),
                    new PlaySession.Config(Opponent.Search.MCTS, Opponent.Network.CLASSICAL));

            short expected = findMove(host.position, "e2e4");
            host.expectHint();
            session.requestHint();
            check("hint: arrow shown within timeout", host.awaitHint());
            check("hint: suggested move matches the opponent's choice", host.lastHint == expected);
            check("hint: nothing was played", host.moveCount == 0);
            check("hint: still the human's turn and game active",
                    host.position.isWhiteToMove() && session.isActive());

            int clearsBefore = host.clearHintCount;
            host.expect(2);
            host.playMove(findMove(host.position, "e2e4"));   // human e4, engine replies
            check("hint: engine replied", host.await());
            check("hint: a move clears the hint arrow",
                    host.clearHintCount > clearsBefore && host.lastHint == Move.NO_MOVE);
        } finally {
            session.dispose();
        }

        // (c): a hint is a no-op once the game is over (no search is submitted).
        FakeHost overHost = new FakeHost();
        PlaySession overSession =
                new PlaySession(overHost, new StrengthModel(), scriptedOpponent("e2e4"));
        overHost.bind(overSession);
        try {
            overSession.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(1200));
            overSession.resign();
            int hintsBefore = overHost.hintCount;
            overSession.requestHint();
            check("hint: no-op after the game ends",
                    overHost.hintCount == hintsBefore && !overSession.isActive());
        } finally {
            overSession.dispose();
        }

        // (e): a hint whose board moves on before it returns is dropped. A human
        // move does not bump the generation, so only the position guard can catch
        // it — a blocking opponent holds the hint search until after the move.
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeHost staleHost = new FakeHost();
        PlaySession.OpponentProvider blocking = config -> new Opponent() {
            /**
             * Blocks until released, then returns a deterministic legal move.
             */
            @Override
            public MoveChoice chooseMove(Position position, StrengthModel.Budget budget, long requestId) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                MoveList legal = position.legalMoves();
                short move = legal.isEmpty() ? Move.NO_MOVE : legal.raw(0);
                return new MoveChoice(move, List.of(new RankedMove(move, 1, 1.0, 0.0)), 0, "");
            }

            /**
             * Cancels the fake opponent.
             */
            @Override
            public void cancel() {
                // nothing to interrupt
            }
        };
        PlaySession staleSession = new PlaySession(staleHost, new StrengthModel(), blocking);
        staleHost.bind(staleSession);
        try {
            staleSession.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(1200));
            staleSession.requestHint();
            boolean searching = awaitLatch(entered);
            staleHost.expect(2);
            staleHost.playMove(findMove(staleHost.position, "e2e4"));  // board moves on
            release.countDown();
            boolean replied = staleHost.await();
            flushEdt();
            check("hint: in-flight hint dropped when the board moves on",
                    searching && replied && staleHost.hintCount == 0
                            && staleHost.lastHint == Move.NO_MOVE);
        } finally {
            staleSession.dispose();
        }
    }

    /**
     * Awaits a latch with a fixed timeout, restoring the interrupt flag.
     *
     * @param latch latch to await
     * @return true when released before the timeout
     */
    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Drains the AWT event queue so any {@code invokeLater} callbacks (e.g. a
     * superseded hint's apply step) have run before the next assertion.
     */
    private static void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ex) {
            // best-effort flush
        }
    }

    /**
     * Verifies the persistent result banner fires once on resignation, with the
     * expected message and toast kind.
     */
    private static void testResultBannerResign() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        RecordingListener rec = new RecordingListener();
        session.setListener(rec);
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200));
            int before = rec.resultCount;
            session.resign();
            check("banner: resign fires one result", rec.resultCount == before + 1);
            check("banner: resign message", "You resigned — engine wins".equals(rec.lastResultMessage));
            check("banner: resign kind WARNING", rec.lastResultKind == Toast.Kind.WARNING);
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies the result banner fires on an agreed draw.
     */
    private static void testResultBannerDraw() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        RecordingListener rec = new RecordingListener();
        session.setListener(rec);
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200));
            session.offerDraw();
            check("banner: draw message", "Draw agreed".equals(rec.lastResultMessage));
            check("banner: draw kind INFO", rec.lastResultKind == Toast.Kind.INFO);
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies the result banner fires with a checkmate result. The human (White)
     * delivers mate-in-one against a scripted engine, so {@code finishGame} runs
     * for a checkmate the human won.
     */
    private static void testResultBannerCheckmate() {
        FakeHost host = new FakeHost();
        // Scholar's mate: human (White) plays e4, Bc4, Qh5, Qxf7#; the scripted
        // engine (Black) walks into it with e5, Nc6, Nf6.
        PlaySession session = new PlaySession(host, new StrengthModel(),
                scriptedOpponent("e7e5", "b8c6", "g8f6"));
        RecordingListener rec = new RecordingListener();
        session.setListener(rec);
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200),
                    new PlaySession.Config(Opponent.Search.MCTS, Opponent.Network.CLASSICAL));
            for (String uci : new String[] { "e2e4", "f1c4", "d1h5" }) {
                host.expect(2);
                host.playMove(findMove(host.position, uci));   // each answered by the scripted engine
                host.await();
            }
            int before = rec.resultCount;
            host.playMove(findMove(host.position, "h5f7"));    // Qxf7#
            check("banner: checkmate ends the game", !session.isActive());
            check("banner: checkmate fired a result", rec.resultCount == before + 1);
            check("banner: checkmate message mentions win",
                    rec.lastResultMessage != null && rec.lastResultMessage.contains("win"));
            check("banner: checkmate kind SUCCESS", rec.lastResultKind == Toast.Kind.SUCCESS);
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies starting a new game clears any prior banner via a null result.
     */
    private static void testResultClearedOnNewGame() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        RecordingListener rec = new RecordingListener();
        session.setListener(rec);
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200));
            session.resign();
            check("clear: banner set after resign", rec.lastResultMessage != null);
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE, StrengthProfile.ofElo(1200));
            check("clear: new game clears the banner", rec.lastResultMessage == null);
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies threefold repetition ends the game as a draw. The human (White)
     * and a scripted engine (Black) shuffle knights out and back so the start
     * position recurs a third time, which must end the game.
     */
    private static void testThreefoldRepetition() {
        FakeHost host = new FakeHost();
        // Black answers each white knight move by mirroring it, returning to the
        // start position every two full moves.
        PlaySession.OpponentProvider engine = scriptedOpponent("g8f6", "f6g8", "g8f6", "f6g8");
        PlaySession session = new PlaySession(host, new StrengthModel(), engine);
        host.bind(session);
        try {
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(StrengthModel.MAX_ELO, true),
                    new PlaySession.Config(Opponent.Search.MCTS, Opponent.Network.CLASSICAL));
            // White: Nf3 Ng1 Nf3 Ng1; each move is answered by Black off-EDT, so
            // wait for the reply (two board plies) before the next white move.
            String[] whiteMoves = { "g1f3", "f3g1", "g1f3", "f3g1" };
            for (String uci : whiteMoves) {
                if (!session.isActive()) {
                    break;
                }
                host.expect(2);
                host.playMove(findMove(host.position, uci));
                host.await();
            }
            check("threefold: game drew on repetition", !session.isActive());
            check("threefold: position entry unlocked", !host.locked);
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies the fifty-move rule ends the game as a draw. Starting from a
     * position whose halfmove clock is one short of 100, a single non-pawn,
     * non-capture move trips the rule.
     */
    private static void testFiftyMoveRule() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        host.bind(session);
        try {
            // Kings and rooks only; halfmove clock 99. One quiet rook move -> 100.
            session.start("4k3/r7/8/8/8/8/R7/4K3 w - - 99 60", PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(StrengthModel.MAX_ELO, true),
                    new PlaySession.Config(Opponent.Search.MCTS, Opponent.Network.CLASSICAL));
            check("fifty-move: active before the limiting move", session.isActive());
            host.playMove(findMove(host.position, "a2a4"));
            check("fifty-move: game drew at the limit", !session.isActive());
            check("fifty-move: position entry unlocked", !host.locked);
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies every named one-tap Workbench Play opponent uses the stable
     * alpha-beta + classical backend. Custom still exposes the experimental
     * search/network combinations, but presets should vary strength only.
     */
    private static void testPresetBotsUseAlphaBetaClassical() {
        try {
            java.lang.reflect.Field botsField = PlayPanel.class.getDeclaredField("BOTS");
            botsField.setAccessible(true);
            java.util.List<?> bots = (java.util.List<?>) botsField.get(null);
            check("presets: bot list is non-empty", !bots.isEmpty());

            for (Object bot : bots) {
                java.lang.reflect.Method nameMethod = bot.getClass().getDeclaredMethod("name");
                java.lang.reflect.Method eloMethod = bot.getClass().getDeclaredMethod("elo");
                java.lang.reflect.Method searchMethod = bot.getClass().getDeclaredMethod("search");
                java.lang.reflect.Method networkMethod = bot.getClass().getDeclaredMethod("network");
                java.lang.reflect.Method deterministicMethod = bot.getClass().getDeclaredMethod("deterministic");
                java.lang.reflect.Method openingBookMethod = bot.getClass().getDeclaredMethod("openingBook");
                java.lang.reflect.Method legacyMethod = PlayPanel.class.getDeclaredMethod("isLegacyPresetBackend",
                        bot.getClass(), Opponent.Search.class, Opponent.Network.class);
                java.lang.reflect.Method legacySelectionMethod =
                        PlayPanel.class.getDeclaredMethod("isLegacyPresetSelection",
                                bot.getClass(), int.class, Opponent.Search.class, Opponent.Network.class);
                nameMethod.setAccessible(true);
                eloMethod.setAccessible(true);
                searchMethod.setAccessible(true);
                networkMethod.setAccessible(true);
                deterministicMethod.setAccessible(true);
                openingBookMethod.setAccessible(true);
                legacyMethod.setAccessible(true);
                legacySelectionMethod.setAccessible(true);

                String name = String.valueOf(nameMethod.invoke(bot));
                int elo = ((Integer) eloMethod.invoke(bot)).intValue();
                Opponent.Search search = (Opponent.Search) searchMethod.invoke(bot);
                Opponent.Network network = (Opponent.Network) networkMethod.invoke(bot);
                boolean deterministic = ((Boolean) deterministicMethod.invoke(bot)).booleanValue();
                boolean openingBook = ((Boolean) openingBookMethod.invoke(bot)).booleanValue();
                check("presets: " + name + " uses alpha-beta",
                        search == Opponent.Search.ALPHA_BETA);
                check("presets: " + name + " uses classical eval",
                        network == Opponent.Network.CLASSICAL);
                if ("Rookie".equals(name)) {
                    check("presets: Rookie uses a true beginner budget", elo <= 600);
                    check("presets: Rookie is sampled, not arg-max", !deterministic);
                    check("presets: Rookie does not use opening book", !openingBook);
                } else if ("Casual".equals(name)) {
                    check("presets: Casual does not use opening book", !openingBook);
                } else if ("Maximum".equals(name)) {
                    check("presets: Maximum uses deterministic arg-max", deterministic);
                    check("presets: Maximum uses opening book", openingBook);
                } else {
                    check("presets: " + name + " remains sampled", !deterministic);
                    check("presets: " + name + " uses opening book", openingBook);
                }

                boolean legacyExpected = "Club".equals(name) || "Expert".equals(name)
                        || "Master".equals(name) || "Maximum".equals(name);
                Opponent.Search legacySearch = "Maximum".equals(name)
                        ? Opponent.Search.MCTS : Opponent.Search.ALPHA_BETA;
                Opponent.Network legacyNetwork = "Maximum".equals(name)
                        ? Opponent.Network.OTIS : Opponent.Network.NNUE;
                boolean legacy = (Boolean) legacyMethod.invoke(null, bot, legacySearch, legacyNetwork);
                check("presets: " + name + " legacy backend migration",
                        legacy == legacyExpected);
                boolean legacyRookie = (Boolean) legacySelectionMethod.invoke(null, bot, 800,
                        Opponent.Search.ALPHA_BETA, Opponent.Network.CLASSICAL);
                check("presets: " + name + " legacy Rookie 800 migration",
                        legacyRookie == "Rookie".equals(name));
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            check("presets: bot backend introspection failed", false);
        }
    }

    /**
     * Verifies the Play panel derives side-to-move and material summaries from a
     * core-parsed position, including deterministic fallback for malformed FEN.
     */
    private static void testPanelContextUsesCorePosition() {
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), firstLegalOpponent());
        host.bind(session);
        try {
            PlayPanel valid = new PlayPanel(session,
                    () -> "4k3/8/8/8/8/8/8/4KQ2 b - - 0 1");
            String validContext = valid.workspaceContext();
            check("panel: context uses core side to move", validContext.contains("Black to move"));
            check("panel: context uses core material units",
                    validContext.contains("Material +9") || validContext.contains("Material -9"));

            PlayPanel malformed = new PlayPanel(session, () -> "Q b - - 0 1");
            String malformedContext = malformed.workspaceContext();
            check("panel: malformed FEN does not leak side token",
                    malformedContext.contains("White to move"));
            check("panel: malformed FEN falls back to material even",
                    malformedContext.contains("Material even"));
        } finally {
            session.dispose();
        }
    }

    /**
     * Returns a provider whose opponent plays a fixed sequence of UCI moves, one
     * per call, for scripting deterministic controller scenarios.
     *
     * @param ucis moves to play in order
     * @return scripted opponent provider
     */
    private static PlaySession.OpponentProvider scriptedOpponent(String... ucis) {
        return config -> new Opponent() {
            /**
             * Index of the next scripted UCI move to return.
             */
            private int index;

            /**
             * Returns the next scripted move, falling back to the first legal move.
             */
            @Override
            public MoveChoice chooseMove(Position position, StrengthModel.Budget budget, long requestId) {
                short move = index < ucis.length ? findMove(position, ucis[index]) : Move.NO_MOVE;
                index++;
                if (move == Move.NO_MOVE) {
                    MoveList legal = position.legalMoves();
                    move = legal.isEmpty() ? Move.NO_MOVE : legal.raw(0);
                }
                return new MoveChoice(move, List.of(new RankedMove(move, 1, 1.0, 0.0)), 0, "");
            }

            /**
             * Cancels the scripted fake opponent.
             */
            @Override
            public void cancel() {
                // nothing to interrupt
            }
        };
    }

    /**
     * Verifies the session passes the exact search+network configuration to the
     * provider, and only rebuilds the opponent when the configuration changes.
     */
    private static void testOpponentSelection() {
        FakeHost host = new FakeHost();
        java.util.List<PlaySession.Config> requested = new ArrayList<>();
        PlaySession.OpponentProvider recording = config -> {
            requested.add(config);
            return new Opponent() {
                /**
                 * Records the requested config and returns a deterministic legal move.
                 */
                @Override
                public MoveChoice chooseMove(Position position, StrengthModel.Budget budget, long requestId) {
                    MoveList legal = position.legalMoves();
                    short move = legal.isEmpty() ? Move.NO_MOVE : legal.raw(0);
                    return new MoveChoice(move, List.of(new RankedMove(move, 1, 1.0, 0.0)), 0, "");
                }

                /**
                 * Cancels the recording fake opponent.
                 */
                @Override
                public void cancel() {
                    // nothing to interrupt
                }
            };
        };
        PlaySession session = new PlaySession(host, new StrengthModel(), recording);
        host.bind(session);
        try {
            PlaySession.Config mctsClassical =
                    new PlaySession.Config(Opponent.Search.MCTS, Opponent.Network.CLASSICAL);
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(1600), mctsClassical);
            check("select: exact config is created",
                    requested.size() == 1 && requested.get(0).equals(mctsClassical));

            // Restarting with the same config must not rebuild the opponent.
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(1600), mctsClassical);
            check("select: same config reuses the opponent", requested.size() == 1);

            // A different config (search and network both change) rebuilds.
            PlaySession.Config abNnue =
                    new PlaySession.Config(Opponent.Search.ALPHA_BETA, Opponent.Network.NNUE);
            session.start(Setup.getStandardStartFEN(), PlaySession.Side.WHITE,
                    StrengthProfile.ofElo(1600), abNnue);
            check("select: changed config rebuilds",
                    requested.size() == 2 && requested.get(1).equals(abNnue) && session.isActive());
        } finally {
            session.dispose();
        }
    }

    /**
     * Verifies every search × network combination builds and returns a legal
     * move from the opening position (neural nets fall back to classical if their
     * weights are absent, so this never fails on a clean checkout).
     */
    private static void testAllBackendCombinations() {
        StrengthModel model = new StrengthModel();
        Position start = new Position(Setup.getStandardStartFEN());
        StrengthModel.Budget budget = model.budgetFor(StrengthProfile.ofElo(1200, true));
        for (Opponent.Search search : Opponent.Search.values()) {
            for (Opponent.Network network : Opponent.Network.values()) {
                Opponent opp = search == Opponent.Search.MCTS
                        ? new MctsOpponent(network)
                        : new AlphaBetaOpponent(network);
                try {
                    MoveChoice c = opp.chooseMove(start, budget, 1);
                    check("combo: " + search + "+" + network + " plays a legal move",
                            c.move() != Move.NO_MOVE && isLegal(start, c.move()));
                } finally {
                    try {
                        opp.close();
                    } catch (Exception ex) {
                        // best-effort
                    }
                }
            }
        }
    }

    /**
     * Verifies the opening book: it loads non-empty, answers the standard start
     * with a legal stable move, is reproducible under a fixed seed, and misses
     * (returns NO_MOVE) on an out-of-book position.
     */
    private static void testOpeningBook() {
        OpeningBook book = new OpeningBook();
        check("book: index is non-empty", book.size() > 0);

        Position start = new Position(Setup.getStandardStartFEN());
        check("book: contains the start position", book.contains(start));
        short det1 = book.move(start, null);
        short det2 = book.move(start, null);
        check("book: deterministic start move is legal",
                det1 != Move.NO_MOVE && isLegal(start, det1));
        check("book: deterministic start move is stable", det1 == det2);

        // Fixed-seed weighted selection is reproducible.
        short seeded1 = book.move(start, new java.util.Random(7));
        short seeded2 = book.move(start, new java.util.Random(7));
        check("book: seeded start move is legal and reproducible",
                seeded1 != Move.NO_MOVE && isLegal(start, seeded1) && seeded1 == seeded2);

        // An artificial, certainly-out-of-book position misses.
        Position outOfBook = new Position("8/8/8/3k4/8/3K4/8/8 w - - 0 1");
        check("book: out-of-book position misses",
                !book.contains(outOfBook) && book.move(outOfBook, null) == Move.NO_MOVE);

        // A game from a NON-standard start never short-circuits to a book move:
        // the scripted opponent's reply is used, proving startedFromStandard gates it.
        FakeHost host = new FakeHost();
        PlaySession session = new PlaySession(host, new StrengthModel(), scriptedOpponent("a7a6"));
        session.setBookEnabled(true);
        host.bind(session);
        try {
            // Human is white; from a non-standard FEN where it is white to move.
            session.start("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1",
                    PlaySession.Side.WHITE, StrengthProfile.ofElo(1200, true),
                    new PlaySession.Config(Opponent.Search.MCTS, Opponent.Network.CLASSICAL));
            String nonStandard = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1";
            host.expect(2);
            host.playMove(findMove(host.position, "g1f3"));
            boolean replied = host.await();
            // The engine reply must be the scripted a7a6, not a book move: rebuild
            // the expected board (Nf3 then a6) and compare FENs.
            Position expected = new Position(nonStandard);
            expected.play(findMove(expected, "g1f3"));
            expected.play(findMove(expected, "a7a6"));
            check("book: non-standard start uses the scripted move, not the book",
                    replied && host.position.toString().equals(expected.toString()));
        } finally {
            session.dispose();
        }
    }

    /**
     * Returns a provider whose opponent always plays the first legal move, for
     * deterministic controller tests, regardless of the requested configuration.
     *
     * @return deterministic opponent provider
     */
    private static PlaySession.OpponentProvider firstLegalOpponent() {
        return config -> new Opponent() {
            /**
             * Returns the first legal move for the current position.
             */
            @Override
            public MoveChoice chooseMove(Position position, StrengthModel.Budget budget, long requestId) {
                MoveList legal = position.legalMoves();
                short move = legal.isEmpty() ? Move.NO_MOVE : legal.raw(0);
                return new MoveChoice(move, List.of(new RankedMove(move, 1, 1.0, 0.0)), 0, "");
            }

            /**
             * Cancels the first-legal fake opponent.
             */
            @Override
            public void cancel() {
                // nothing to interrupt
            }
        };
    }

    /**
     * Finds a legal move by UCI text.
     *
     * @param position chess position
     * @param uci UCI move text
     * @return encoded move, or {@link Move#NO_MOVE}
     */
    private static short findMove(Position position, String uci) {
        MoveList legal = position.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            if (Move.toString(legal.raw(i)).equals(uci)) {
                return legal.raw(i);
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Returns whether a move is legal in a position.
     *
     * @param position chess position
     * @param move encoded chess move
     * @return true when legal
     */
    private static boolean isLegal(Position position, short move) {
        MoveList legal = position.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            if (legal.raw(i) == move) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records a check result.
     *
     * @param label check label
     * @param condition pass condition
     */
    private static void check(String label, boolean condition) {
        RESULTS.add(new String[] { condition ? "PASS" : "FAIL", label });
        if (!condition) {
            failures++;
        }
    }

    /**
     * Prints results and exits with a status reflecting failures.
     */
    private static void report() {
        for (String[] row : RESULTS) {
            System.out.println(row[0] + " PlayModeRegressionTest: " + row[1]);
        }
        // The engine reply uses the AWT event thread, which keeps the JVM alive,
        // so exit explicitly.
        System.exit(failures > 0 ? 1 : 0);
    }

    /**
     * Captures the most recent {@link PlaySession.Listener} callbacks so result-
     * banner tests can assert what the panel would display.
     */
    private static final class RecordingListener implements PlaySession.Listener {

        /**
         * Number of {@code onResult} calls observed.
         */
        private int resultCount;

        /**
         * Last result message (null after a clear).
         */
        private String lastResultMessage;

        /**
         * Last result toast kind (null after a clear).
         */
        private Toast.Kind lastResultKind;

        /**
         * Receives status updates from the play session.
         */
        @Override
        public void onStatus(String status, boolean gameActive, boolean engineThinking) {
            // status line not asserted here
        }

        /**
         * Records result toast messages from the play session.
         */
        @Override
        public void onResult(String message, Toast.Kind kind) {
            resultCount++;
            lastResultMessage = message;
            lastResultKind = kind;
        }
    }

    /**
     * Fake {@link PlayHost} that mirrors the board move funnel: applying a move
     * advances an internal position and re-enters the session, exactly as the
     * real window's {@code playMove} does.
     */
    private static final class FakeHost implements PlayHost {

        /**
         * Current position.
         */
        private Position position;

        /**
         * Position history (index 0 is the root), so the fake can rewind for
         * {@link #showPly(int)} the way the real game model does.
         */
        private final List<Position> history = new ArrayList<>();

        /**
         * Moves applied through the fake board funnel.
         */
        private final List<Short> playedMoves = new ArrayList<>();

        /**
         * Owning session.
         */
        private PlaySession session;

        /**
         * Applied-move count.
         */
        private int moveCount;

        /**
         * Latch released as moves are applied.
         */
        private CountDownLatch latch = new CountDownLatch(0);

        /**
         * Last input-gate state.
         */
        private boolean gate = true;

        /**
         * Whether position entry is locked.
         */
        private boolean locked;

        /**
         * Binds the session this host drives.
         *
         * @param session workbench session
         */
        void bind(PlaySession session) {
            this.session = session;
        }

        /**
         * Arms the latch for an expected number of applied moves.
         *
         * @param moves expected move count
         */
        void expect(int moves) {
            latch = new CountDownLatch(moves);
        }

        /**
         * Waits for the expected moves to be applied.
         *
         * @return true when satisfied before timeout
         */
        boolean await() {
            try {
                return latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        /**
         * Plays a move on the fake host board.
         */
        @Override
        public void playMove(short move) {
            Position next = position.copy();
            next.play(move);
            position = next;
            // Mirror GameModel.append: a move from an earlier ply truncates the
            // future line before extending it.
            while (history.size() > moveCount + 1) {
                history.remove(history.size() - 1);
            }
            history.add(position.copy());
            playedMoves.add(move);
            moveCount++;
            // Re-enter the controller exactly as the real funnel does (synchronously,
            // within playMove) BEFORE signaling the latch, so a test that wakes on the
            // latch observes the input gate the controller set for the new turn.
            if (session != null) {
                session.onMovePlayed(position.copy());
            }
            latch.countDown();
        }

        /**
         * Starts a new fake-host game from a FEN string.
         */
        @Override
        public void startNewGame(String fen) {
            position = new Position(fen.trim());
            history.clear();
            history.add(position.copy());
            playedMoves.clear();
            moveCount = 0;
        }

        /**
         * Returns the fake host's current FEN.
         */
        @Override
        public String currentFen() {
            return position.toString();
        }

        /**
         * Returns a copy of the fake host's current position.
         */
        @Override
        public Position currentPosition() {
            return position == null ? null : position.copy();
        }

        /**
         * Returns the fake host's current ply.
         */
        @Override
        public int currentPly() {
            return moveCount;
        }

        /**
         * Moves the fake host to the requested history ply.
         */
        @Override
        public void showPly(int ply) {
            int target = Math.max(0, Math.min(ply, history.size() - 1));
            position = history.get(target).copy();
            moveCount = target;
        }

        /**
         * Records board orientation requests.
         */
        @Override
        public void setBoardWhiteDown(boolean whiteDown) {
            // no board in headless test
        }

        /**
         * Records toast requests from the session.
         */
        @Override
        public void toast(Toast.Kind kind, String message) {
            // ignored in headless test
        }

        /**
         * Records that evaluation is thinking.
         */
        @Override
        public void setEvalThinking() {
            // ignored in headless test
        }

        /**
         * Records evaluation centipawn updates.
         */
        @Override
        public void setEvalWhiteCp(int whiteCentipawns) {
            // ignored in headless test
        }

        /**
         * Records whether human input is allowed.
         */
        @Override
        public void setInputGate(boolean humanAllowed) {
            gate = humanAllowed;
        }

        /**
         * Records whether position entry is locked.
         */
        @Override
        public void setPositionEntryLocked(boolean value) {
            locked = value;
        }

        /**
         * Last hint move shown (NO_MOVE after a clear).
         */
        private short lastHint = Move.NO_MOVE;

        /**
         * Number of {@code showHint} calls observed.
         */
        private int hintCount;

        /**
         * Number of {@code clearHint} calls observed.
         */
        private int clearHintCount;

        /**
         * Last premove arrow shown (NO_MOVE after a clear).
         */
        private short lastPremove = Move.NO_MOVE;

        /**
         * Latch released when a hint arrow is shown.
         */
        private CountDownLatch hintLatch = new CountDownLatch(0);

        /**
         * Arms the latch for the next shown hint.
         */
        void expectHint() {
            hintLatch = new CountDownLatch(1);
        }

        /**
         * Waits for a hint arrow to be shown.
         *
         * @return true when shown before the timeout
         */
        boolean awaitHint() {
            try {
                return hintLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        /**
         * Records the currently shown hint.
         */
        @Override
        public void showHint(short move) {
            lastHint = move;
            hintCount++;
            hintLatch.countDown();
        }

        /**
         * Clears the currently shown hint.
         */
        @Override
        public void clearHint() {
            lastHint = Move.NO_MOVE;
            clearHintCount++;
        }

        /**
         * Records the currently shown premove.
         */
        @Override
        public void showPremove(short move) {
            lastPremove = move;
        }

        /**
         * Clears the currently shown premove.
         */
        @Override
        public void clearPremove() {
            lastPremove = Move.NO_MOVE;
        }
    }
}
