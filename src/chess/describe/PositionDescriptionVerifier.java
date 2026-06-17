package chess.describe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounding verifier for generated position-description prose.
 *
 * <p>The verifier is intentionally conservative. It does not try to prove that
 * prose is complete or beautiful; it only rejects concrete claims that can be
 * checked against {@link PositionDescriptionInput}. That makes it useful as an
 * inference-time guard for future model text while staying harmless for the
 * deterministic classical generator.</p>
 */
public final class PositionDescriptionVerifier {

    /**
     * SAN-like token pattern used to identify concrete move claims in prose.
     */
    private static final Pattern SAN_TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9_])(?:O-O(?:-O)?|[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?)(?![A-Za-z0-9_])");

    /**
     * Claim that White is the side to move.
     */
    private static final Pattern WHITE_TO_MOVE = Pattern.compile(
            "(?i)\\bWhite\\s+(?:is\\s+)?to\\s+move\\b");

    /**
     * Claim that Black is the side to move.
     */
    private static final Pattern BLACK_TO_MOVE = Pattern.compile(
            "(?i)\\bBlack\\s+(?:is\\s+)?to\\s+move\\b");

    /**
     * Claim that White has a decisive or favorable evaluation.
     */
    private static final Pattern WHITE_FAVORED = Pattern.compile(
            "(?i)\\bWhite\\s+is\\s+(?:clearly\\s+better|winning|decisive|crushing|simply\\s+winning)\\b");

    /**
     * Claim that Black has a decisive or favorable evaluation.
     */
    private static final Pattern BLACK_FAVORED = Pattern.compile(
            "(?i)\\bBlack\\s+is\\s+(?:clearly\\s+better|winning|decisive|crushing|simply\\s+winning)\\b");

    /**
     * Claim that a position has a single necessary move.
     */
    private static final Pattern ONLY_MOVE_CLAIM = Pattern.compile(
            "(?i)\\b(?:only\\s+(?:legal\\s+)?move|forced\\s+move|must\\s+play|has\\s+to\\s+play)\\b");

    /**
     * Common named-opening claims that require an opening-name fact.
     */
    private static final Pattern NAMED_OPENING_CLAIM = Pattern.compile(
            "(?i)\\b(Najdorf|Sicilian(?:\\s+Defense)?|Ruy\\s+Lopez|Spanish\\s+Opening|French\\s+Defense|"
                    + "Caro-Kann|Queen's\\s+Gambit|King's\\s+Indian|London\\s+System|English\\s+Opening|"
                    + "King's\\s+Pawn\\s+Game)\\b");

    /**
     * Opening-name tag prefix from the canonical tag stream.
     */
    private static final String OPENING_NAME_PREFIX = "OPENING: name=\"";

    /**
     * Opening-name tag prefix from lower-level feature extraction.
     */
    private static final String META_OPENING_PREFIX = "META: opening=\"";

    /**
     * Minimum centipawn margin treated as a directional evaluation claim.
     */
    private static final int DIRECTIONAL_EVAL_CP = 120;

    /**
     * Prevents instantiation.
     */
    private PositionDescriptionVerifier() {
        // utility
    }

    /**
     * Verifies one generated description against structured input facts.
     *
     * @param input structured description input
     * @param text generated text
     * @return verification result
     */
    public static Verification verify(PositionDescriptionInput input, String text) {
        if (input == null) {
            throw new IllegalArgumentException("input == null");
        }
        List<Violation> violations = new ArrayList<>();
        if (text == null || text.isBlank()) {
            violations.add(new Violation("empty", "description text is empty"));
            return new Verification(violations);
        }
        verifySideToMove(input, text, violations);
        verifyEvaluationDirection(input, text, violations);
        verifyMaterialClaims(input, text, violations);
        verifyOnlyMoveClaims(input, text, violations);
        verifyOpeningClaims(input, text, violations);
        verifySanMentions(input, text, violations);
        return new Verification(violations);
    }

    /**
     * Returns generated text when grounded, otherwise a supplied classical fallback.
     *
     * @param input structured description input
     * @param text candidate generated text
     * @param fallback fallback text
     * @return grounded text or fallback
     */
    public static String groundedOrFallback(PositionDescriptionInput input, String text, String fallback) {
        Verification verification = verify(input, text);
        return verification.grounded() ? text : fallback;
    }

    /**
     * Verifies side-to-move claims.
     *
     * @param input structured input
     * @param text generated text
     * @param violations destination violations
     */
    private static void verifySideToMove(PositionDescriptionInput input, String text, List<Violation> violations) {
        String side = lower(input.sideToMove());
        if ("white".equals(side) && BLACK_TO_MOVE.matcher(text).find()) {
            violations.add(new Violation("side_to_move", "text says Black is to move, input says White"));
        } else if ("black".equals(side) && WHITE_TO_MOVE.matcher(text).find()) {
            violations.add(new Violation("side_to_move", "text says White is to move, input says Black"));
        }
    }

    /**
     * Verifies directional evaluation claims.
     *
     * @param input structured input
     * @param text generated text
     * @param violations destination violations
     */
    private static void verifyEvaluationDirection(PositionDescriptionInput input, String text,
            List<Violation> violations) {
        int cpWhite = input.evaluation().cpWhite();
        if (cpWhite >= DIRECTIONAL_EVAL_CP && BLACK_FAVORED.matcher(text).find()) {
            violations.add(new Violation("evaluation", "text favors Black, input evaluation favors White"));
        } else if (cpWhite <= -DIRECTIONAL_EVAL_CP && WHITE_FAVORED.matcher(text).find()) {
            violations.add(new Violation("evaluation", "text favors White, input evaluation favors Black"));
        }
    }

    /**
     * Verifies a narrow set of material claims.
     *
     * @param input structured input
     * @param text generated text
     * @param violations destination violations
     */
    private static void verifyMaterialClaims(PositionDescriptionInput input, String text, List<Violation> violations) {
        String lower = text.toLowerCase(Locale.ROOT);
        PositionDescriptionInput.PieceCounts white = input.material().white();
        PositionDescriptionInput.PieceCounts black = input.material().black();
        verifyMaterialSurplusClaim(violations, lower, "white", "White",
                white.queens() - black.queens(), "queen");
        verifyMaterialSurplusClaim(violations, lower, "black", "Black",
                black.queens() - white.queens(), "queen");
        verifyMaterialSurplusClaim(violations, lower, "white", "White",
                white.rooks() - black.rooks(), "rook");
        verifyMaterialSurplusClaim(violations, lower, "black", "Black",
                black.rooks() - white.rooks(), "rook");
        verifyMaterialSurplusClaim(violations, lower, "white", "White",
                white.bishops() - black.bishops(), "bishop");
        verifyMaterialSurplusClaim(violations, lower, "black", "Black",
                black.bishops() - white.bishops(), "bishop");
        verifyMaterialSurplusClaim(violations, lower, "white", "White",
                white.knights() - black.knights(), "knight");
        verifyMaterialSurplusClaim(violations, lower, "black", "Black",
                black.knights() - white.knights(), "knight");
        verifyMaterialSurplusClaim(violations, lower, "white", "White",
                white.pawns() - black.pawns(), "pawn");
        verifyMaterialSurplusClaim(violations, lower, "black", "Black",
                black.pawns() - white.pawns(), "pawn");
        verifyMaterialSurplusClaim(violations, lower, "white", "White",
                white.bishops() + white.knights() - black.bishops() - black.knights(), "piece");
        verifyMaterialSurplusClaim(violations, lower, "black", "Black",
                black.bishops() + black.knights() - white.bishops() - white.knights(), "piece");
    }

    /**
     * Verifies a side-specific surplus claim for one piece family.
     *
     * @param violations destination violations
     * @param lower lower-case text
     * @param side lower-case side label
     * @param displaySide display side label
     * @param actualSurplus actual side surplus for this family
     * @param noun piece-family noun
     */
    private static void verifyMaterialSurplusClaim(List<Violation> violations, String lower, String side,
            String displaySide, int actualSurplus, String noun) {
        if (actualSurplus <= 0 && claimsMaterialSurplus(lower, side, noun)) {
            violations.add(new Violation("material",
                    "text says " + displaySide + " is a " + noun + " up, input does not"));
        }
    }

    /**
     * Returns whether text claims one side has a material surplus.
     *
     * @param lower lower-case text
     * @param side lower-case side label
     * @param noun piece-family noun
     * @return true when a supported phrase is present
     */
    private static boolean claimsMaterialSurplus(String lower, String side, String noun) {
        return lower.contains(side + " is a " + noun + " up")
                || lower.contains(side + " was a " + noun + " up")
                || lower.contains(side + " has a " + noun + " more")
                || lower.contains(side + " has an extra " + noun)
                || lower.contains(side + "'s extra " + noun);
    }

    /**
     * Verifies claims that a position has only one playable move.
     *
     * @param input structured input
     * @param text generated text
     * @param violations destination violations
     */
    private static void verifyOnlyMoveClaims(PositionDescriptionInput input, String text, List<Violation> violations) {
        if (input.moves().legal() != 1 && ONLY_MOVE_CLAIM.matcher(text).find()) {
            violations.add(new Violation("only_move",
                    "text claims an only move, input has " + input.moves().legal() + " legal moves"));
        }
    }

    /**
     * Verifies named-opening claims against available opening-name facts.
     *
     * @param input structured input
     * @param text generated text
     * @param violations destination violations
     */
    private static void verifyOpeningClaims(PositionDescriptionInput input, String text, List<Violation> violations) {
        Matcher matcher = NAMED_OPENING_CLAIM.matcher(text);
        Set<String> reported = new LinkedHashSet<>();
        Set<String> allowed = openingNames(input);
        while (matcher.find()) {
            String claim = matcher.group(1);
            if (!isAllowedOpeningClaim(claim, allowed) && reported.add(claim.toLowerCase(Locale.ROOT))) {
                violations.add(new Violation("opening",
                        "text names opening " + claim + " without a matching opening fact"));
            }
        }
    }

    /**
     * Returns opening names recorded in the structured tag list.
     *
     * @param input structured input
     * @return lower-case opening names
     */
    private static Set<String> openingNames(PositionDescriptionInput input) {
        Set<String> names = new LinkedHashSet<>();
        for (String tag : input.tags()) {
            String value = quotedTagValue(tag, OPENING_NAME_PREFIX);
            if (value == null) {
                value = quotedTagValue(tag, META_OPENING_PREFIX);
            }
            if (value != null && !value.isBlank()) {
                names.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    /**
     * Returns whether a named-opening claim is backed by a structured opening fact.
     *
     * @param claim matched claim text
     * @param allowed lower-case allowed opening names
     * @return true when a fact backs the claim
     */
    private static boolean isAllowedOpeningClaim(String claim, Set<String> allowed) {
        String normalized = claim == null ? "" : claim.toLowerCase(Locale.ROOT);
        for (String name : allowed) {
            if (name.contains(normalized) || normalized.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts a quoted tag value after a known prefix.
     *
     * @param tag source tag
     * @param prefix quoted-value prefix
     * @return unescaped value, or null
     */
    private static String quotedTagValue(String tag, String prefix) {
        if (tag == null || prefix == null || !tag.startsWith(prefix)) {
            return null;
        }
        int start = prefix.length();
        int end = tag.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return tag.substring(start, end).replace("\\\"", "\"");
    }

    /**
     * Verifies concrete SAN move mentions against input candidates.
     *
     * @param input structured input
     * @param text generated text
     * @param violations destination violations
     */
    private static void verifySanMentions(PositionDescriptionInput input, String text, List<Violation> violations) {
        Set<String> allowedSan = allowedSan(input);
        Matcher matcher = SAN_TOKEN.matcher(text);
        Set<String> reported = new LinkedHashSet<>();
        while (matcher.find()) {
            String san = matcher.group();
            if (!allowedSan.contains(normalizeSan(san)) && reported.add(san)) {
                violations.add(new Violation("san", "text names non-candidate move " + san));
            }
        }
    }

    /**
     * Returns normalized candidate SAN strings.
     *
     * @param input structured input
     * @return allowed SAN set
     */
    private static Set<String> allowedSan(PositionDescriptionInput input) {
        Set<String> allowed = new LinkedHashSet<>();
        for (PositionDescriptionInput.CandidateMove candidate : input.candidates()) {
            if (candidate.san() != null && !candidate.san().isBlank()) {
                allowed.add(normalizeSan(candidate.san()));
            }
        }
        return allowed;
    }

    /**
     * Normalizes SAN for comparison.
     *
     * @param san SAN text
     * @return normalized SAN
     */
    private static String normalizeSan(String san) {
        return san == null ? "" : san.trim();
    }

    /**
     * Lowercases a value safely.
     *
     * @param value source value
     * @return lower-case value
     */
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * Result of grounding verification.
     *
     * @param violations concrete grounding violations
     */
    public record Verification(List<Violation> violations) {

        /**
         * Creates a verification result.
         */
        public Verification {
            violations = List.copyOf(violations == null ? List.of() : violations);
        }

        /**
         * Returns whether no grounding violation was found.
         *
         * @return true when grounded
         */
        public boolean grounded() {
            return violations.isEmpty();
        }
    }

    /**
     * One grounding violation.
     *
     * @param kind violation kind
     * @param message human-readable message
     */
    public record Violation(String kind, String message) {
    }
}
