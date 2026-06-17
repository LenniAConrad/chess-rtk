package chess.describe;

import static chess.describe.DescriptionLexicon.article;
import static chess.describe.DescriptionLexicon.capitalize;
import static chess.describe.DescriptionLexicon.joinList;
import static chess.describe.DescriptionLexicon.numberWord;
import static chess.describe.DescriptionLexicon.pawns;
import static chess.describe.DescriptionLexicon.sentence;
import static chess.describe.DescriptionLexicon.wdlTriple;

import java.util.ArrayList;
import java.util.List;

import chess.classical.Wdl;
import chess.describe.PositionDescriptionInput.CandidateMove;
import chess.describe.PositionDescriptionInput.Material;
import chess.describe.PositionDescriptionInput.MoveSummary;
import chess.describe.PositionDescriptionInput.PieceCounts;

/**
 * Deterministic position-text generator written in a human annotator's voice.
 *
 * <p>
 * The engine first classifies the position once into a {@link Verdict} (terminal
 * state, evaluation band, favored side) and a {@link MaterialGap} read from
 * piece-count differences, then renders the requested detail level from that
 * shared assessment. Every phrase is a pure function of the structured input, so
 * output stays byte-identical across runs - a requirement of the CLI
 * training export and the golden regression tests.
 * </p>
 *
 * <p>
 * Faithfulness is a hard rule: the prose never states a chess fact absent from
 * the input. It does not name openings, label a move a sacrifice, or claim what a
 * capture takes, because none of that is in the data.
 * </p>
 */
public final class ClassicalPositionDescriptionGenerator {

    /**
     * Default candidate count woven into normal detail.
     */
    private static final int DEFAULT_NORMAL_CANDIDATES = 3;

    /**
     * Default candidate count woven into full detail.
     */
    private static final int DEFAULT_FULL_CANDIDATES = 5;

    /**
     * Largest absolute centipawn score still read as dead level.
     */
    private static final int EVAL_LEVEL = 25;

    /**
     * Largest absolute centipawn score still read as only a slight edge.
     */
    private static final int EVAL_SLIGHT = 120;

    /**
     * Largest absolute centipawn score still read as a clear, non-decisive edge.
     */
    private static final int EVAL_CLEAR = 400;

    /**
     * Largest absolute centipawn score read as winning rather than crushing.
     */
    private static final int EVAL_WINNING = 900;

    /**
     * WDL draw permille at or above which the position is treated as a dead draw.
     */
    private static final int WDL_DRAWN = 950;

    /**
     * WDL loss permille at or above which the side to move is called lost.
     */
    private static final int WDL_LOST = 850;

    /**
     * Largest legal-move count still described as almost no room.
     */
    private static final int ROOM_BOXED = 3;

    /**
     * Largest legal-move count still described as a cramped choice.
     */
    private static final int ROOM_CRAMPED = 12;

    /**
     * Legal-move count at or above which the side is described as having plenty of room.
     */
    private static final int ROOM_PLENTY = 31;

    /**
     * Generates text with the default candidate budget for the requested detail.
     *
     * @param input structured input
     * @param detail detail level
     * @return generated text
     */
    public String generate(PositionDescriptionInput input, PositionDescriptionDetail detail) {
        return generate(input, detail, -1);
    }

    /**
     * Generates text with an optional candidate budget.
     *
     * @param input structured input
     * @param detail detail level
     * @param candidateBudget max candidate moves, or negative for the detail default
     * @return generated text
     */
    public String generate(PositionDescriptionInput input, PositionDescriptionDetail detail, int candidateBudget) {
        if (input == null) {
            throw new IllegalArgumentException("input == null");
        }
        PositionDescriptionDetail resolved = detail == null ? PositionDescriptionDetail.NORMAL : detail;
        Verdict verdict = classify(input);
        MaterialGap material = materialGap(input.material());
        return switch (resolved) {
            case BRIEF -> brief(input, verdict, material);
            case NORMAL -> normal(input, verdict, material, candidateLimit(candidateBudget, DEFAULT_NORMAL_CANDIDATES));
            case FULL -> full(input, verdict, material, candidateLimit(candidateBudget, DEFAULT_FULL_CANDIDATES));
        };
    }

    /**
     * Resolves a caller-provided candidate count against a detail default.
     *
     * @param requested requested budget
     * @param fallback detail-specific fallback
     * @return non-negative candidate count
     */
    private static int candidateLimit(int requested, int fallback) {
        return requested < 0 ? fallback : Math.max(0, requested);
    }

    // ---------------------------------------------------------------------
    // Brief
    // ---------------------------------------------------------------------

    /**
     * Builds the one-sentence description.
     *
     * @param input structured input
     * @param verdict classified verdict
     * @param material material gap
     * @return brief text
     */
    private static String brief(PositionDescriptionInput input, Verdict verdict, MaterialGap material) {
        String side = side(input);
        return switch (verdict.terminal()) {
            case CHECKMATE -> sentence(side + " is checkmated: it is " + side
                    + " to move, there is no legal reply, and the game is over");
            case STALEMATE -> sentence("It is " + side
                    + " to move with no legal reply, so the position is stalemated and the game is drawn");
            case DEAD_DRAW -> sentence(deadDrawLead(input)
                    + ", a dead draw with too little left on the board to force mate");
            default -> {
                if (verdict.mateIn() != 0) {
                    yield sentence(forcedMateLead(input, verdict));
                }
                String body = briefBody(input, verdict, material, side);
                if (verdict.terminal() == Terminal.IN_CHECK) {
                    body = body + ", with the king in check";
                }
                yield sentence(body);
            }
        };
    }

    /**
     * Builds the running-position brief sentence body.
     *
     * @param input structured input
     * @param verdict classified verdict
     * @param material material gap
     * @param side side-to-move label
     * @return brief sentence body
     */
    private static String briefBody(PositionDescriptionInput input, Verdict verdict, MaterialGap material, String side) {
        String base = sideToMovePhase(input);
        if (material.idiom() != null) {
            boolean ahead = side.equals(material.favored());
            String gap = material.idiom() + (ahead ? " up" : " down");
            String connector = ahead ? ", " : ", but ";
            String tail = verdict.stmLost() ? "and simply lost"
                    : verdict.band() == Band.DEAD_LEVEL ? "though the game is far from decided"
                    : "and " + evalClause(verdict);
            return base + connector + gap + ", " + tail;
        }
        if (verdict.band() == Band.DEAD_LEVEL) {
            return base + ", with the material level and little to choose between the two sides";
        }
        return base + ", and though the material is level, " + evalClause(verdict);
    }

    // ---------------------------------------------------------------------
    // Normal
    // ---------------------------------------------------------------------

    /**
     * Builds the short multi-sentence description.
     *
     * @param input structured input
     * @param verdict classified verdict
     * @param material material gap
     * @param candidateLimit candidate weave budget
     * @return normal text
     */
    private static String normal(PositionDescriptionInput input, Verdict verdict, MaterialGap material,
            int candidateLimit) {
        if (verdict.terminal().isTerminal()) {
            return terminalProse(input, verdict, material, false);
        }
        if (verdict.mateIn() != 0) {
            return forcedMateProse(input, verdict, false);
        }
        List<String> out = new ArrayList<>();
        out.add(headlineSentence(input, verdict, material));
        if (verdict.terminal() == Terminal.IN_CHECK) {
            out.add(sentence("The king is in check and must be met before anything else"));
        }
        out.add(assessmentSentence(input, verdict));
        out.addAll(StrategicPlanNarrator.sentences(input, false));
        weaveCandidates(out, input.candidates(), input.moves(), verdict, candidateLimit, false);
        return joinSentences(out);
    }

    // ---------------------------------------------------------------------
    // Full
    // ---------------------------------------------------------------------

    /**
     * Builds the long flowing description.
     *
     * @param input structured input
     * @param verdict classified verdict
     * @param material material gap
     * @param candidateLimit candidate weave budget
     * @return full text
     */
    private static String full(PositionDescriptionInput input, Verdict verdict, MaterialGap material,
            int candidateLimit) {
        if (verdict.terminal().isTerminal()) {
            return terminalProse(input, verdict, material, true);
        }
        if (verdict.mateIn() != 0) {
            return forcedMateProse(input, verdict, true);
        }
        List<String> out = new ArrayList<>();
        out.add(headlineSentence(input, verdict, material));
        out.add(assessmentSentence(input, verdict));
        out.add(kingAndRoomSentence(input, verdict));
        out.addAll(StrategicPlanNarrator.sentences(input, true));
        weaveCandidates(out, input.candidates(), input.moves(), verdict, candidateLimit, true);
        if (input.moves().mates() == 0) {
            out.add(closingSentence(input, verdict));
        }
        return joinSentences(out);
    }

    // ---------------------------------------------------------------------
    // Shared sentence builders
    // ---------------------------------------------------------------------

    /**
     * Builds the opening headline sentence shared by normal and full detail.
     *
     * @param input structured input
     * @param verdict classified verdict
     * @param material material gap
     * @return headline sentence
     */
    private static String headlineSentence(PositionDescriptionInput input, Verdict verdict, MaterialGap material) {
        String side = side(input);
        String base = sideToMovePhase(input);
        if (material.idiom() != null) {
            boolean ahead = side.equals(material.favored());
            String gap = material.idiom() + (ahead ? " up" : " down");
            String connector = ahead ? ", " : ", but ";
            String tail = verdict.stmLost() ? "and clearly losing"
                    : "and " + evalClause(verdict);
            return sentence(base + connector + gap + ", " + tail);
        }
        if (verdict.band() == Band.DEAD_LEVEL) {
            return sentence(base + ", and the position is dead level");
        }
        return sentence(base + ", and though the material is level, " + evalClause(verdict));
    }

    /**
     * Builds the evaluation/WDL corroboration sentence.
     *
     * @param input structured input
     * @param verdict classified verdict
     * @return assessment sentence
     */
    private static String assessmentSentence(PositionDescriptionInput input, Verdict verdict) {
        Wdl wdl = input.evaluation().wdl();
        boolean haveWdl = wdl != null;
        String label = isEngineEval(input) ? "engine evaluation" : "static evaluation";
        String subject = "The " + label + " of " + pawns(input.evaluation().cpWhite()) + " for White";
        if (haveWdl) {
            subject = subject + " and a WDL of " + wdlTriple(wdl);
        }
        // Plural verb when the evaluation and the WDL are both subjects; singular
        // when the evaluation stands alone (an engine read carries no WDL).
        String amount = haveWdl ? " amount" : " amounts";
        String point = haveWdl ? " point" : " points";
        String mark = haveWdl ? " mark" : " marks";
        String leave = haveWdl ? " leave" : " leaves";
        return switch (verdict.band()) {
            case DEAD_LEVEL -> sentence(subject + amount
                    + " to next to nothing; this is as balanced as a position gets");
            case SLIGHT -> sentence(subject + point
                    + " to no more than a nuance, the sort of edge that rarely survives accurate play");
            case CLEAR -> sentence(subject + mark + " a real and lasting pull for "
                    + favoredOrSide(verdict, input) + ", a genuine edge if not yet a decisive one");
            case WINNING, DECISIVE -> sentence(subject + leave + " no doubt: "
                    + (verdict.stmLost() ? side(input) + " is simply lost"
                            : favoredOrSide(verdict, input) + " is winning by force"));
        };
    }

    /**
     * Tests whether the evaluation came from a real engine search.
     *
     * @param input structured input
     * @return true when the evaluation source is an engine search
     */
    private static boolean isEngineEval(PositionDescriptionInput input) {
        String source = input.evaluation().source();
        return source != null && source.startsWith("engine");
    }

    /**
     * Builds the king-safety and board-room sentence for full detail.
     *
     * @param input structured input
     * @param verdict classified verdict
     * @return king-and-room sentence
     */
    private static String kingAndRoomSentence(PositionDescriptionInput input, Verdict verdict) {
        String side = side(input);
        String king;
        if (verdict.terminal() == Terminal.IN_CHECK) {
            king = "The " + side.toLowerCase(java.util.Locale.ROOT)
                    + " king is in check and must be attended to first";
        } else {
            // Only "not in check" is derivable from the data; "no danger" would
            // overstate, since an unchecked king can still face a mating attack.
            king = "Neither king is in check";
        }
        int legal = input.moves().legal();
        String room;
        if (legal <= ROOM_BOXED) {
            room = side + " is all but boxed in, with only " + numberWord(legal) + " legal "
                    + plural("move", legal);
        } else if (legal <= ROOM_CRAMPED) {
            room = side + " has a cramped, limited choice";
        } else if (legal < ROOM_PLENTY) {
            room = side + " has a normal spread of options";
        } else {
            room = side + " has plenty of room, " + numberWord(legal) + " legal moves in all";
        }
        return sentence(king + ", and " + room);
    }

    /**
     * Builds the closing character sentence for full detail.
     *
     * <p>
     * The closer must be sided: for a decided position the side to move may be the
     * winning side or the losing side, so the wording is chosen from the favored
     * side rather than the band alone.
     * </p>
     *
     * @param input structured input
     * @param verdict classified verdict
     * @return closing sentence
     */
    private static String closingSentence(PositionDescriptionInput input, Verdict verdict) {
        String favored = verdict.favored();
        boolean ours = side(input).equals(favored);
        return switch (verdict.band()) {
            case DEAD_LEVEL -> sentence("It is the kind of balanced position where the plan, "
                    + "not the material count, will decide matters");
            case SLIGHT -> sentence("There is everything still to play for");
            case CLEAR -> sentence("It is the sort of edge that patient, purposeful play should make tell for "
                    + favored);
            case WINNING, DECISIVE -> ours
                    ? sentence("Only an accident could let the win slip from here")
                    : sentence("Short of a blunder by " + favored + ", the win should not slip");
        };
    }

    // ---------------------------------------------------------------------
    // Terminal prose
    // ---------------------------------------------------------------------

    /**
     * Builds prose for a finished or drawn-by-material position.
     *
     * @param input structured input
     * @param verdict classified verdict
     * @param material material gap
     * @param full true for full detail, false for normal
     * @return terminal prose
     */
    private static String terminalProse(PositionDescriptionInput input, Verdict verdict, MaterialGap material,
            boolean full) {
        String side = side(input);
        String winner = "White".equals(side) ? "Black" : "White";
        return switch (verdict.terminal()) {
            case CHECKMATE -> full
                    ? joinSentences(List.of(
                            sentence("The position is terminal: " + side + " is to move and has been checkmated"),
                            sentence("The king stands in check with no legal reply - nothing to capture the "
                                    + "checker, nothing to interpose, nowhere to run"),
                            sentence(mateMaterialFootnote(material, side)),
                            sentence("There is nothing to assess and nothing to play"),
                            sentence("The full point goes to " + winner)))
                    : joinSentences(List.of(
                            sentence(side + " is checkmated"),
                            sentence("The king is in check with no legal reply, so there is nothing left to "
                                    + "calculate"),
                            sentence(mateMaterialFootnote(material, side) + "; the game is over, won for " + winner)));
            case STALEMATE -> full
                    ? joinSentences(List.of(
                            sentence("The position is terminal: " + side + " is to move but has no legal reply"),
                            sentence("The king is not in check, yet every move is illegal, so the game is "
                                    + "stalemated and drawn"),
                            sentence("There is nothing to assess and nothing to play"),
                            sentence("The point is split")))
                    : joinSentences(List.of(
                            sentence("It is " + side + " to move with no legal reply"),
                            sentence("The king is not in check, so this is stalemate and the game is drawn")));
            default -> deadDrawProse(input, full);
        };
    }

    /**
     * Builds prose for an insufficient-material or WDL-confirmed dead draw.
     *
     * @param input structured input
     * @param full true for full detail, false for normal
     * @return dead-draw prose
     */
    private static String deadDrawProse(PositionDescriptionInput input, boolean full) {
        String lead = deadDrawLead(input);
        boolean haveWdl = input.evaluation().wdl() != null;
        String wdl = wdlTriple(input.evaluation().wdl());
        boolean bareKings = onlyKings(input.material());
        if (!full) {
            List<String> out = new ArrayList<>();
            out.add(sentence(capitalize(lead) + ", and there is simply nothing to play for"));
            out.add(sentence(haveWdl
                    ? "With too little material left to deliver mate the game is drawn, and the WDL of "
                            + wdl + " confirms it"
                    : "With too little material left to deliver mate, the game is drawn"));
            if (bareKings) {
                out.add(sentence(capitalize(kingShuffleClause(input.candidates()))));
            }
            return joinSentences(out);
        }
        List<String> out = new ArrayList<>();
        out.add(sentence(capitalize(lead)));
        out.add(sentence("The material is level and, with too little left to force a checkmate, the result is "
                + "not in doubt"));
        out.add(sentence(haveWdl
                ? "The evaluation reads a nominal " + pawns(input.evaluation().cpWhite())
                        + " for White, but the WDL of " + wdl + " tells the true story: a dead draw in which "
                        + "neither side can make progress"
                : "Neither side can make progress: with this little material it is a dead draw"));
        if (bareKings) {
            out.add(sentence(capitalize(kingShuffleClause(input.candidates()))));
        }
        out.add(sentence("The point has long since been split"));
        return joinSentences(out);
    }

    /**
     * Builds the dead-draw lead clause, naming a bare-king ending when it applies.
     *
     * @param input structured input
     * @return lead clause
     */
    private static String deadDrawLead(PositionDescriptionInput input) {
        String side = side(input);
        if (onlyKings(input.material())) {
            return side + " is to move in a bare king-and-king endgame";
        }
        return side + " is to move in the endgame";
    }

    /**
     * Builds the material footnote shown after a mate, where the count no longer matters.
     *
     * @param material material gap
     * @param side mated side label
     * @return footnote clause
     */
    private static String mateMaterialFootnote(MaterialGap material, String side) {
        if (material.idiom() == null) {
            return "The material was level, but the count is beside the point once the king cannot escape";
        }
        String leader = material.favored();
        return capitalize(leader) + " was " + material.idiom() + " up, but the material count is beside the "
                + "point once mate is on the board";
    }

    // ---------------------------------------------------------------------
    // Forced mate (engine search)
    // ---------------------------------------------------------------------

    /**
     * Builds the one-clause forced-mate verdict from the side-to-move's view.
     *
     * @param input structured input
     * @param verdict classified verdict carrying the mate distance
     * @return forced-mate lead clause
     */
    private static String forcedMateLead(PositionDescriptionInput input, Verdict verdict) {
        String base = sideToMovePhase(input);
        String winner = verdict.favored();
        int n = Math.abs(verdict.mateIn());
        String distance = "a forced mate in " + numberWord(n);
        if (side(input).equals(winner)) {
            // Name the mating move when it is a mate in one the static scan also saw.
            CandidateMove finisher = n == 1 ? firstReason(input.candidates(), "mates") : null;
            String move = finisher == null ? "" : ", " + finisher.san() + " to finish";
            return base + " with " + distance + " in hand" + move;
        }
        return base + ", but facing " + distance + " for " + winner;
    }

    /**
     * Builds normal/full prose for a position with a forced mate found by search.
     *
     * <p>
     * A forced mate settles the game, so the description states it plainly and does
     * not pad with material nuance, mobility, or candidate suggestions.
     * </p>
     *
     * @param input structured input
     * @param verdict classified verdict carrying the mate distance
     * @param full true for full detail, false for normal
     * @return forced-mate prose
     */
    private static String forcedMateProse(PositionDescriptionInput input, Verdict verdict, boolean full) {
        String winner = verdict.favored();
        boolean ours = side(input).equals(winner);
        List<String> out = new ArrayList<>();
        out.add(sentence(capitalize(forcedMateLead(input, verdict))));
        out.add(sentence("The engine's search finds the mate by force, so the material count and the "
                + "evaluation in pawns no longer matter"));
        if (full) {
            out.add(sentence(ours
                    ? capitalize(winner) + " need only follow the forcing line through to the end"
                    : "There is no defence to be found over the board, only the length of the mate to delay"));
            out.add(sentence("Nothing else on the board bears on the result now"));
        }
        return joinSentences(out);
    }

    // ---------------------------------------------------------------------
    // Candidate weaving
    // ---------------------------------------------------------------------

    /**
     * Weaves forcing and constructive candidate clauses into the sentence list.
     *
     * @param out sentence accumulator
     * @param candidates ordered candidate moves
     * @param moves move summary
     * @param verdict classified verdict
     * @param limit candidate weave budget
     * @param full true for full detail
     */
    private static void weaveCandidates(List<String> out, List<CandidateMove> candidates, MoveSummary moves,
            Verdict verdict, int limit, boolean full) {
        CandidateMove mate = firstReason(candidates, "mates");
        if (mate != null) {
            out.add(sentence("Mate is on the board: " + mate.san() + " ends it"));
            return;
        }
        if (moves.mates() > 0) {
            out.add(sentence("Mate is available"));
            return;
        }
        if (limit == 0 || candidates.isEmpty()) {
            if (moves.forcing() == 0 && !full) {
                out.add(sentence("The position is quiet, with nothing yet forced"));
            }
            return;
        }
        if (isKingShuffle(candidates)) {
            // A live position whose only replies are king moves (e.g. escaping a
            // check). State that plainly; the dismissive "none of it matters" of a
            // bare-king dead draw is handled separately and would be false here.
            out.add(sentence("Only king moves are available - " + joinList(sansOf(candidates))));
            return;
        }
        List<CandidateMove> forcing = new ArrayList<>();
        List<CandidateMove> constructive = new ArrayList<>();
        for (CandidateMove move : candidates) {
            (isForcing(move.reason()) ? forcing : constructive).add(move);
        }
        int forcingCap = full ? 3 : 2;
        int constructiveCap = full ? Math.max(1, limit - 1) : Math.max(1, Math.min(3, limit));
        String forcingClause = forcingClause(forcing, forcingCap);
        boolean hasForcing = !forcingClause.isEmpty();
        if (hasForcing) {
            out.add(sentence(forcingClause));
        } else if (moves.forcing() == 0 && verdict.band().compareTo(Band.WINNING) >= 0) {
            out.add(sentence("The position is quiet, with no forcing resource to clutch at"));
        }
        String constructiveClause = constructiveClause(constructive, constructiveCap, hasForcing);
        if (!constructiveClause.isEmpty()) {
            out.add(sentence(constructiveClause));
        }
    }

    /**
     * Builds the forcing-tries clause from forcing candidates.
     *
     * @param forcing forcing candidate moves
     * @param cap maximum forcing moves to name
     * @return forcing clause, or empty text
     */
    private static String forcingClause(List<CandidateMove> forcing, int cap) {
        if (forcing.isEmpty()) {
            return "";
        }
        // Collapse underpromotions to the same square (a8=Q/R/B/N) to a single try,
        // keeping the highest-priority one, which sorts first.
        List<CandidateMove> picked = new ArrayList<>();
        List<String> promoSquares = new ArrayList<>();
        for (CandidateMove move : forcing) {
            if ("promotes".equals(move.reason())) {
                String square = promotionSquare(move.san());
                if (promoSquares.contains(square)) {
                    continue;
                }
                promoSquares.add(square);
            }
            picked.add(move);
            if (picked.size() >= cap) {
                break;
            }
        }
        // Captures and promotions carry no trailing clause and read first. Checking
        // moves are folded into a single shared "with check" group: repeating
        // ", which comes with check" per move would collide with the list commas.
        List<String> plain = new ArrayList<>();
        List<String> checks = new ArrayList<>();
        for (CandidateMove move : picked) {
            if ("gives check".equals(move.reason())) {
                checks.add(move.san());
            } else {
                plain.add(forcingPhrase(move));
            }
        }
        List<String> parts = new ArrayList<>(plain);
        if (checks.size() == 1) {
            parts.add(checks.get(0) + ", which comes with check");
        } else if (checks.size() >= 2) {
            parts.add("the checks " + joinList(checks));
        }
        String lead = picked.size() == 1 ? "There is a forcing try in " : "There are forcing tries in ";
        return lead + joinList(parts);
    }

    /**
     * Returns the landing square of a promotion SAN for dedup, ignoring the piece.
     *
     * @param san promotion SAN such as {@code a8=Q} or {@code axb8=N+}
     * @return SAN prefix up to the promotion marker
     */
    private static String promotionSquare(String san) {
        int marker = san.indexOf('=');
        return marker < 0 ? san : san.substring(0, marker);
    }

    /**
     * Returns the first candidate carrying the given reason.
     *
     * @param candidates candidate moves
     * @param reason reason label to match
     * @return matching candidate, or null
     */
    private static CandidateMove firstReason(List<CandidateMove> candidates, String reason) {
        for (CandidateMove move : candidates) {
            if (reason.equals(move.reason())) {
                return move;
            }
        }
        return null;
    }

    /**
     * Maps a non-checking forcing candidate to its woven phrase.
     *
     * <p>
     * Checking moves are grouped separately by {@link #forcingClause}; only
     * captures, en-passant captures, and promotions reach this method.
     * </p>
     *
     * @param move forcing candidate
     * @return forcing phrase
     */
    private static String forcingPhrase(CandidateMove move) {
        return switch (move.reason()) {
            case "promotes" -> "the promotion " + move.san();
            case "captures en passant" -> "the en-passant capture " + move.san();
            default -> "the capture " + move.san();
        };
    }

    /**
     * Builds the constructive-plan clause from non-forcing candidates.
     *
     * @param constructive constructive candidate moves
     * @param cap maximum constructive moves to name
     * @param hasForcing true when a forcing clause precedes this one
     * @return constructive clause, or empty text
     */
    private static String constructiveClause(List<CandidateMove> constructive, int cap, boolean hasForcing) {
        if (constructive.isEmpty()) {
            return "";
        }
        CandidateMove lead = constructive.get(0);
        String frame = hasForcing ? "For something quieter, " : "The natural course is to ";
        StringBuilder sb = new StringBuilder(frame).append(constructiveVerb(lead));
        int extra = Math.min(cap - 1, constructive.size() - 1);
        if (extra > 0) {
            List<String> alts = new ArrayList<>(extra);
            for (int i = 1; i <= extra; i++) {
                alts.add(constructive.get(i).san());
            }
            sb.append(", with ").append(joinList(alts))
                    .append(extra == 1 ? " as an alternative" : " as alternatives");
        }
        return sb.toString();
    }

    /**
     * Maps a constructive candidate to its leading verb phrase.
     *
     * @param move constructive candidate
     * @return verb phrase including the move SAN
     */
    private static String constructiveVerb(CandidateMove move) {
        return switch (move.reason()) {
            case "takes central space" -> "claim the centre with " + move.san();
            case "advances a pawn" -> "push the pawn with " + move.san();
            case "castles" -> "castle with " + move.san();
            case "improves a piece" -> "improve the piece with " + move.san();
            default -> "develop with " + move.san();
        };
    }

    /**
     * Builds the bare-king shuffle clause.
     *
     * @param candidates candidate king moves
     * @return shuffle clause
     */
    private static String kingShuffleClause(List<CandidateMove> candidates) {
        return "the king can only shuffle - " + joinList(sansOf(candidates)) + " - and none of it matters";
    }

    /**
     * Collects the SAN strings of candidate moves in order.
     *
     * @param candidates candidate moves
     * @return SAN list
     */
    private static List<String> sansOf(List<CandidateMove> candidates) {
        List<String> sans = new ArrayList<>(candidates.size());
        for (CandidateMove move : candidates) {
            sans.add(move.san());
        }
        return sans;
    }

    /**
     * Tests whether every candidate is a king move improving its placement.
     *
     * @param candidates candidate moves
     * @return true when all candidates are bare-king shuffles
     */
    private static boolean isKingShuffle(List<CandidateMove> candidates) {
        if (candidates.isEmpty()) {
            return false;
        }
        for (CandidateMove move : candidates) {
            if (!"improves a piece".equals(move.reason()) || !move.san().startsWith("K")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tests whether a candidate reason denotes a forcing move.
     *
     * @param reason candidate reason label
     * @return true when the reason is forcing
     */
    private static boolean isForcing(String reason) {
        return switch (reason) {
            case "gives check", "mates", "promotes", "captures", "captures en passant" -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------------
    // Classification
    // ---------------------------------------------------------------------

    /**
     * Classifies the position into a verdict from status, evaluation, and WDL.
     *
     * @param input structured input
     * @return verdict
     */
    private static Verdict classify(PositionDescriptionInput input) {
        Terminal terminal = terminal(input);
        Wdl wdl = input.evaluation().wdl();
        int cpWhite = input.evaluation().cpWhite();
        int mateIn = input.evaluation().mateIn();
        boolean drawnByWdl = wdl != null && wdl.draw() >= WDL_DRAWN && Math.abs(cpWhite) <= 30;
        if (terminal == Terminal.INSUFFICIENT || (terminal == Terminal.NONE && drawnByWdl && input.moves().mates() == 0)) {
            terminal = Terminal.DEAD_DRAW;
        }
        if (mateIn != 0) {
            // A forced mate found by search settles everything; the mating side is
            // favored, and the side to move is lost when it is the one being mated.
            String winner = mateIn > 0 ? "White" : "Black";
            boolean stmLost = !side(input).equals(winner);
            return new Verdict(terminal, Band.DECISIVE, winner, stmLost, mateIn);
        }
        Band band = band(cpWhite, input.moves().mates());
        String favored = band == Band.DEAD_LEVEL ? null : cpWhite > 0 ? "White" : cpWhite < 0 ? "Black" : null;
        if (favored == null && band == Band.DECISIVE) {
            // The only way here is a mate-in-one with a flat material score: the
            // side to move is the one delivering mate, so it is the favored side.
            favored = side(input);
        }
        boolean stmLost = wdl != null && wdl.loss() >= WDL_LOST;
        return new Verdict(terminal, band, favored, stmLost, 0);
    }

    /**
     * Buckets the white-perspective centipawn score into an evaluation band.
     *
     * @param cpWhite centipawns from White's perspective
     * @param mateMoves mate-in-one count
     * @return evaluation band
     */
    private static Band band(int cpWhite, int mateMoves) {
        int abs = Math.abs(cpWhite);
        if (mateMoves > 0 || abs > EVAL_WINNING) {
            return Band.DECISIVE;
        }
        if (abs <= EVAL_LEVEL) {
            return Band.DEAD_LEVEL;
        }
        if (abs <= EVAL_SLIGHT) {
            return Band.SLIGHT;
        }
        if (abs <= EVAL_CLEAR) {
            return Band.CLEAR;
        }
        return Band.WINNING;
    }

    /**
     * Maps the status label to a terminal classification.
     *
     * @param input structured input
     * @return terminal classification
     */
    private static Terminal terminal(PositionDescriptionInput input) {
        return switch (input.status()) {
            case "checkmate" -> Terminal.CHECKMATE;
            case "stalemate" -> Terminal.STALEMATE;
            case "insufficient material" -> Terminal.INSUFFICIENT;
            case "check" -> Terminal.IN_CHECK;
            default -> Terminal.NONE;
        };
    }

    /**
     * Reads the material gap from per-side piece-count differences.
     *
     * @param material material summary
     * @return material gap
     */
    private static MaterialGap materialGap(Material material) {
        PieceCounts w = material.white();
        PieceCounts b = material.black();
        int dq = w.queens() - b.queens();
        int dr = w.rooks() - b.rooks();
        int db = w.bishops() - b.bishops();
        int dn = w.knights() - b.knights();
        int dp = w.pawns() - b.pawns();
        int minor = db + dn;
        if (dq == 0 && dr == 0 && minor == 0 && dp == 0) {
            return new MaterialGap(null, null);
        }
        if (dq == 0 && dp == 0 && ((dr == 1 && minor == -1) || (dr == -1 && minor == 1))) {
            return new MaterialGap(dr > 0 ? "White" : "Black", "the exchange");
        }
        int points = 9 * dq + 5 * dr + 3 * minor + dp;
        String favored = points > 0 ? "White" : points < 0 ? "Black" : null;
        if (favored == null) {
            return new MaterialGap(null, null);
        }
        int sign = points > 0 ? 1 : -1;
        String idiom = dominantIdiom(sign * dq, sign * dr, sign * minor, sign * dp);
        return new MaterialGap(favored, idiom);
    }

    /**
     * Names the dominant material surplus for the favored side from signed diffs.
     *
     * @param dq favored-side queen surplus
     * @param dr favored-side rook surplus
     * @param minor favored-side minor-piece surplus
     * @param dp favored-side pawn surplus
     * @return material idiom, such as {@code a queen} or {@code two pawns}
     */
    private static String dominantIdiom(int dq, int dr, int minor, int dp) {
        if (dq > 0) {
            return dq == 1 ? "a queen" : numberWord(dq) + " queens";
        }
        if (dr > 0) {
            return dr == 1 ? "a rook" : dr == 2 ? "two rooks" : numberWord(dr) + " rooks";
        }
        if (minor > 0) {
            return minor == 1 ? "a piece" : minor == 2 ? "two pieces" : numberWord(minor) + " pieces";
        }
        if (dp > 0) {
            return dp == 1 ? "a pawn" : dp == 2 ? "two pawns" : numberWord(dp) + " pawns";
        }
        return "an edge in material";
    }

    // ---------------------------------------------------------------------
    // Small phrase helpers
    // ---------------------------------------------------------------------

    /**
     * Builds the "{Side} is to move in the (phase)" opening clause.
     *
     * @param input structured input
     * @return side-and-phase clause
     */
    private static String sideToMovePhase(PositionDescriptionInput input) {
        return side(input) + " is to move " + phaseClause(input.phase());
    }

    /**
     * Phrases the coarse game phase naturally.
     *
     * @param phase phase label
     * @return phase clause
     */
    private static String phaseClause(String phase) {
        return switch (phase) {
            case "opening" -> "in the opening";
            case "middlegame" -> "in the middlegame";
            case "endgame" -> "in the endgame";
            default -> "in " + article(phase) + " " + phase + " position";
        };
    }

    /**
     * Builds the standalone evaluation verdict clause naming the favored side.
     *
     * @param verdict classified verdict
     * @return verdict clause
     */
    private static String evalClause(Verdict verdict) {
        return switch (verdict.band()) {
            case DEAD_LEVEL -> "the position is dead level";
            case SLIGHT -> "White".equals(verdict.favored())
                    ? "White holds a slight edge" : "Black is the more comfortable";
            case CLEAR -> verdict.favored() + " is clearly better";
            case WINNING -> verdict.favored() + " is winning";
            case DECISIVE -> verdict.favored() + " is completely winning";
        };
    }

    /**
     * Returns the favored side, falling back to the side to move when level.
     *
     * @param verdict classified verdict
     * @param input structured input
     * @return side label
     */
    private static String favoredOrSide(Verdict verdict, PositionDescriptionInput input) {
        return verdict.favored() == null ? side(input) : verdict.favored();
    }

    /**
     * Returns the title-case side-to-move label.
     *
     * @param input structured input
     * @return side label
     */
    private static String side(PositionDescriptionInput input) {
        return "white".equals(input.sideToMove()) ? "White" : "Black";
    }

    /**
     * Tests whether only the two kings remain on the board.
     *
     * @param material material summary
     * @return true when both sides have no non-king material
     */
    private static boolean onlyKings(Material material) {
        return material.whiteCp() == 0 && material.blackCp() == 0;
    }

    /**
     * Returns a singular or plural noun for a count.
     *
     * @param noun singular noun
     * @param count count
     * @return pluralized noun
     */
    private static String plural(String noun, int count) {
        return count == 1 ? noun : noun + "s";
    }

    /**
     * Joins non-blank sentences with single spaces.
     *
     * @param sentences sentence fragments
     * @return joined text
     */
    private static String joinSentences(List<String> sentences) {
        List<String> cleaned = new ArrayList<>(sentences.size());
        for (String value : sentences) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value.trim());
            }
        }
        return String.join(" ", cleaned);
    }

    // ---------------------------------------------------------------------
    // Classified types
    // ---------------------------------------------------------------------

    /**
     * Terminal or near-terminal position classification.
     */
    private enum Terminal {
        /**
         * A live, non-terminal position.
         */
        NONE,

        /**
         * The side to move is in check but has legal replies.
         */
        IN_CHECK,

        /**
         * The status reports insufficient mating material.
         */
        INSUFFICIENT,

        /**
         * A dead draw: insufficient material or a WDL-confirmed draw.
         */
        DEAD_DRAW,

        /**
         * The side to move is stalemated.
         */
        STALEMATE,

        /**
         * The side to move is checkmated.
         */
        CHECKMATE;

        /**
         * Tests whether this classification ends or draws the game outright.
         *
         * @return true for checkmate, stalemate, or a dead draw
         */
        boolean isTerminal() {
            return this == CHECKMATE || this == STALEMATE || this == DEAD_DRAW;
        }
    }

    /**
     * Evaluation strength band ordered from balanced to crushing.
     */
    private enum Band {
        /**
         * Within a token margin of equality.
         */
        DEAD_LEVEL,

        /**
         * No more than a nuance of an edge.
         */
        SLIGHT,

        /**
         * A real, durable, non-decisive advantage.
         */
        CLEAR,

        /**
         * A winning advantage.
         */
        WINNING,

        /**
         * A crushing or mating advantage.
         */
        DECISIVE
    }

    /**
     * Classified position verdict.
     *
     * @param terminal terminal classification
     * @param band evaluation strength band
     * @param favored evaluation-favored side label, or null when level
     * @param stmLost true when the side-to-move WDL marks it lost
     * @param mateIn forced-mate distance from White's perspective (0 when none)
     */
    private record Verdict(Terminal terminal, Band band, String favored, boolean stmLost, int mateIn) {
    }

    /**
     * Material gap read from piece-count differences.
     *
     * @param favored side ahead on material, or null when level
     * @param idiom natural idiom for the gap, or null when level
     */
    private record MaterialGap(String favored, String idiom) {
    }
}
