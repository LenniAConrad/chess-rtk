package chess.describe;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic template-based position text generator.
 */
public final class ClassicalPositionDescriptionGenerator {

    /**
     * Default candidate count for normal detail.
     */
    private static final int DEFAULT_NORMAL_CANDIDATES = 3;

    /**
     * Default candidate count for full detail.
     */
    private static final int DEFAULT_FULL_CANDIDATES = 5;

    /**
     * Generates text with default budget for the requested detail.
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
     * @param candidateBudget max candidate moves, or negative for detail default
     * @return generated text
     */
    public String generate(PositionDescriptionInput input, PositionDescriptionDetail detail, int candidateBudget) {
        if (input == null) {
            throw new IllegalArgumentException("input == null");
        }
        PositionDescriptionDetail resolved = detail == null ? PositionDescriptionDetail.NORMAL : detail;
        return switch (resolved) {
            case BRIEF -> brief(input);
            case NORMAL -> normal(input, candidateLimit(candidateBudget, DEFAULT_NORMAL_CANDIDATES));
            case FULL -> full(input, candidateLimit(candidateBudget, DEFAULT_FULL_CANDIDATES));
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

    /**
     * Builds the compact one-sentence description.
     *
     * @param input structured input
     * @return brief description
     */
    private static String brief(PositionDescriptionInput input) {
        return sentence(side(input) + " to move in " + article(input.phase()) + " " + input.phase()
                + " position with "
                + materialPhrase(input.material()) + " and " + legalMovePhrase(input.moves().legal()));
    }

    /**
     * Builds the default multi-sentence description.
     *
     * @param input structured input
     * @param candidateLimit candidate output limit
     * @return normal description
     */
    private static String normal(PositionDescriptionInput input, int candidateLimit) {
        List<String> sentences = new ArrayList<>();
        sentences.add(side(input) + " to move in " + article(input.phase()) + " " + input.phase()
                + " position; status is " + input.status());
        sentences.add("Material is " + materialPhrase(input.material()) + " (White "
                + input.material().white().compact() + ", Black " + input.material().black().compact() + ")");
        sentences.add(moveSentence(input.moves()));
        sentences.add("Classical static evaluation is " + signed(input.evaluation().cpWhite()) + " cp for White");
        String threats = threatSentence(input.threats());
        if (!threats.isEmpty()) {
            sentences.add(threats);
        }
        String candidates = candidateSentence(input.candidates(), candidateLimit);
        if (!candidates.isEmpty()) {
            sentences.add(candidates);
        }
        return joinSentences(sentences);
    }

    /**
     * Builds the detailed topic-grouped description.
     *
     * @param input structured input
     * @param candidateLimit candidate output limit
     * @return full description
     */
    private static String full(PositionDescriptionInput input, int candidateLimit) {
        List<String> sentences = new ArrayList<>();
        sentences.add("Side and phase: " + input.sideToMove() + " to move, " + input.phase()
                + ", " + input.status());
        sentences.add("Material: " + materialPhrase(input.material()) + "; White "
                + input.material().white().compact() + "; Black " + input.material().black().compact());
        sentences.add("Mobility: " + moveSentence(input.moves()));
        sentences.add("Evaluation: " + signed(input.evaluation().cpWhite())
                + " cp for White from " + input.evaluation().source()
                + ", with WDL " + wdl(input.evaluation()));
        String threats = threatSentence(input.threats());
        sentences.add("Tactics: " + (threats.isEmpty() ? "no immediate forcing threats" : lowerFirst(threats)));
        String candidates = candidateSentence(input.candidates(), candidateLimit);
        sentences.add(candidates.isEmpty() ? "Candidate moves: none" : candidates);
        sentences.add("Source signals: " + input.tags().size() + " cheap tags, FEN " + input.fen());
        return joinSentences(sentences);
    }

    /**
     * Formats legal-move and forcing-move facts.
     *
     * @param moves move summary
     * @return move sentence without final punctuation guarantee
     */
    private static String moveSentence(PositionDescriptionInput.MoveSummary moves) {
        List<String> facts = new ArrayList<>();
        facts.add(legalMovePhrase(moves.legal()));
        if (moves.forcing() > 0) {
            facts.add(moves.forcing() + " forcing");
        }
        if (moves.captures() > 0) {
            facts.add(moves.captures() + " captures");
        }
        if (moves.checks() > 0) {
            facts.add(moves.checks() + " checks");
        }
        if (moves.mates() > 0) {
            facts.add(moves.mates() + " mates");
        }
        if (moves.promotions() > 0) {
            facts.add(moves.promotions() + " promotions");
        }
        if (moves.castles() > 0) {
            facts.add(moves.castles() + " castles");
        }
        if (moves.forcing() == 0 && moves.legal() > 0) {
            facts.add("no forcing moves");
        }
        return "Legal moves: " + String.join(", ", facts);
    }

    /**
     * Formats immediate tactical signals.
     *
     * @param threats threat labels
     * @return threat sentence, or empty text
     */
    private static String threatSentence(List<String> threats) {
        if (threats == null || threats.isEmpty()) {
            return "";
        }
        return "Immediate signals: " + String.join(", ", threats);
    }

    /**
     * Formats the top candidate moves.
     *
     * @param candidates candidate moves
     * @param limit maximum moves to include
     * @return candidate sentence, or empty text
     */
    private static String candidateSentence(List<PositionDescriptionInput.CandidateMove> candidates, int limit) {
        if (limit == 0 || candidates == null || candidates.isEmpty()) {
            return "";
        }
        int count = Math.min(limit, candidates.size());
        List<String> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PositionDescriptionInput.CandidateMove move = candidates.get(i);
            parts.add(move.san() + " (" + move.reason() + ")");
        }
        return "Candidate moves: " + String.join(", ", parts);
    }

    /**
     * Formats a material-balance phrase.
     *
     * @param material material summary
     * @return material phrase
     */
    private static String materialPhrase(PositionDescriptionInput.Material material) {
        int cp = material.balanceCp();
        if (cp == 0) {
            return "equal material";
        }
        String side = cp > 0 ? "White" : "Black";
        int pawns = Math.abs(cp) / 100;
        int rest = Math.abs(cp) % 100;
        if (rest == 0 && pawns > 0) {
            return side + " up " + pawns + " pawn" + (pawns == 1 ? "" : "s");
        }
        return side + " up " + Math.abs(cp) + " cp";
    }

    /**
     * Formats a legal-move count with pluralization.
     *
     * @param legalMoves legal move count
     * @return legal-move phrase
     */
    private static String legalMovePhrase(int legalMoves) {
        return legalMoves + " legal move" + (legalMoves == 1 ? "" : "s");
    }

    /**
     * Normalizes, punctuates, and joins sentences.
     *
     * @param sentences raw sentence fragments
     * @return joined text
     */
    private static String joinSentences(List<String> sentences) {
        List<String> cleaned = new ArrayList<>(sentences.size());
        for (String sentence : sentences) {
            if (sentence == null || sentence.isBlank()) {
                continue;
            }
            cleaned.add(sentence(sentence));
        }
        return String.join(" ", cleaned);
    }

    /**
     * Ensures a fragment ends with terminal punctuation.
     *
     * @param text sentence fragment
     * @return punctuated sentence
     */
    private static String sentence(String text) {
        String trimmed = text.trim();
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            return trimmed;
        }
        return trimmed + ".";
    }

    /**
     * Formats the side-to-move label for sentence starts.
     *
     * @param input structured input
     * @return title-case side label
     */
    private static String side(PositionDescriptionInput input) {
        return "white".equals(input.sideToMove()) ? "White" : "Black";
    }

    /**
     * Chooses an English indefinite article for a following word.
     *
     * @param word following word
     * @return {@code a} or {@code an}
     */
    private static String article(String word) {
        if (word == null || word.isEmpty()) {
            return "a";
        }
        char first = Character.toLowerCase(word.charAt(0));
        return first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u' ? "an" : "a";
    }

    /**
     * Formats a signed integer with an explicit positive sign.
     *
     * @param value signed value
     * @return signed text
     */
    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    /**
     * Formats a WDL tuple from an evaluation.
     *
     * @param evaluation evaluation summary
     * @return WDL text or {@code -}
     */
    private static String wdl(PositionDescriptionInput.Evaluation evaluation) {
        if (evaluation.wdl() == null) {
            return "-";
        }
        return evaluation.wdl().win() + "/" + evaluation.wdl().draw() + "/" + evaluation.wdl().loss();
    }

    /**
     * Lowercases the first character of a non-empty phrase.
     *
     * @param text input text
     * @return text with a lowercase first character
     */
    private static String lowerFirst(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
